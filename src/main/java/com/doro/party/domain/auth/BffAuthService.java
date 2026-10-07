package com.doro.party.domain.auth;

import com.doro.party.domain.auth.entity.AuthSession;
import com.doro.party.domain.auth.entity.LoginAttempt;
import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.domain.auth.repository.LoginAttemptRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import com.hunnit_beasts.doro.sdk.security.DoroTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BFF 로그인: Doro OAuth(인가 코드 + PKCE)로 로그인하고, 토큰은 서버에만 두며 브라우저에는 세션 쿠키만 준다.
 * 네트워크 호출(IAM)은 DB 트랜잭션 밖에서 하고, 트랜잭션은 짧은 읽기·쓰기에만 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BffAuthService {

    /** 로그인에 실패했다(만료·재사용된 state, 코드 교환 거부, 검증 실패). 사유는 호출자에게 자세히 알리지 않는다. */
    public static class LoginFailedException extends RuntimeException {
        public LoginFailedException(String message) {
            super(message);
        }
    }

    public record LoginResult(String sessionCookieValue, String returnPath) {}

    private static final int RANDOM_BYTES = 32;
    private static final int LOCK_STRIPES = 64;
    /** last_used_at 갱신은 요청마다 쓰지 않고 이 간격으로만 한다. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_RETURN_PATH_LENGTH = 500;

    private final BffProperties props;
    private final DoroOAuthClient oauth;
    private final SessionCrypto crypto;
    private final AuthSessionRepository sessions;
    private final LoginAttemptRepository attempts;
    private final DoroTokenVerifier verifier;
    private final PartyUserService userService;
    private final PlatformTransactionManager transactionManager;

    private final SecureRandom random = new SecureRandom();
    /** 같은 세션의 동시 갱신을 직렬화한다. 리프레시 토큰은 회전되므로 두 요청이 같은 토큰을 쓰면 IAM 이 탈취로 판단한다. */
    private final ReentrantLock[] refreshLocks = newLocks();

    private static ReentrantLock[] newLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ 로그인 시작

    /** 인가 요청 주소를 만든다. state 와 PKCE verifier 는 서버에 보관하고, 브라우저에는 state 와 challenge 만 간다. */
    public String startLogin(String returnPath) {
        String state = randomToken();
        String verifier = randomToken();
        Instant now = Instant.now();
        LoginAttempt attempt = new LoginAttempt(sha256Hex(state), crypto.encrypt(verifier), safeReturnPath(returnPath),
                now, now.plus(props.getLoginAttemptTtl()));
        tx().executeWithoutResult(status -> attempts.save(attempt));
        return oauth.buildAuthorizeUrl(state, codeChallenge(verifier));
    }

    // ------------------------------------------------------------------ 콜백

    /**
     * 인가 코드를 토큰으로 교환하고 세션을 만든다.
     *
     * @param previousCookie 이미 가진 세션 쿠키(있으면 새 로그인 때 폐기해 세션 고정을 막는다)
     */
    public LoginResult completeLogin(String code, String state, String previousCookie) {
        if (isBlank(code) || isBlank(state)) {
            throw new LoginFailedException("code/state 누락");
        }
        String stateHash = sha256Hex(state);
        // state 는 한 번만 쓴다: 삭제된 행이 1 개인 요청만 통과한다.
        LoginAttempt attempt = tx().execute(status -> {
            Optional<LoginAttempt> found = attempts.findById(stateHash);
            int deleted = attempts.deleteByStateHash(stateHash);
            return found.filter(a -> deleted == 1).orElse(null);
        });
        if (attempt == null || attempt.isExpired(Instant.now())) {
            throw new LoginFailedException("알 수 없거나 만료되었거나 이미 사용된 state");
        }

        DoroOAuthClient.TokenSet tokens;
        try {
            tokens = oauth.exchangeCode(code, crypto.decrypt(attempt.getCodeVerifierEnc()));
        } catch (DoroOAuthClient.RejectedException e) {
            throw new LoginFailedException("코드 교환 거부: " + e.getMessage());
        }

        DoroUser user = verifyAccessToken(tokens.accessToken());
        if (user == null || !user.isAuthenticated() || !props.getClientId().equals(user.clientId())) {
            throw new LoginFailedException("이 클라이언트의 유효한 액세스 토큰이 아님");
        }
        userService.getOrCreateUser(user);

        String cookieValue = randomToken();
        Instant now = Instant.now();
        AuthSession session = new AuthSession(sha256Hex(cookieValue), user.userId(),
                crypto.encrypt(tokens.accessToken()), tokens.accessExpiresAt(),
                crypto.encrypt(tokens.refreshToken()), now, now.plus(props.getSessionTtl()));
        tx().executeWithoutResult(status -> {
            sessions.save(session);
            if (!isBlank(previousCookie)) {
                sessions.deleteBySessionHash(sha256Hex(previousCookie));
            }
        });
        log.info("BFF login completed: userId={}", user.userId());
        return new LoginResult(cookieValue, attempt.getReturnPath());
    }

    // ------------------------------------------------------------------ 요청 인증

    /** 세션 쿠키를 사용자로 바꾼다. 만료·폐기·검증 실패면 비어 있다. 액세스 토큰이 곧 만료되면 먼저 갱신한다. */
    public Optional<DoroUser> authenticate(String cookieValue) {
        if (isBlank(cookieValue)) {
            return Optional.empty();
        }
        String hash = sha256Hex(cookieValue);
        AuthSession session = sessions.findBySessionHash(hash).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (session.isExpired(now)) {
            deleteSession(hash);
            return Optional.empty();
        }

        if (needsRefresh(session, now)) {
            session = refreshUnderLock(hash);
            if (session == null) {
                return Optional.empty();
            }
        }

        String accessToken;
        try {
            accessToken = crypto.decrypt(session.getAccessTokenEnc());
        } catch (IllegalStateException e) {
            log.error("BFF session token could not be decrypted (encryption key changed or data corrupted); dropping the session", e);
            deleteSession(hash);
            return Optional.empty();
        }

        DoroUser user = verifyAccessToken(accessToken);
        if (user == null || !user.isAuthenticated() || !props.getClientId().equals(user.clientId())
                || !user.userId().equals(session.getUserId())) {
            // IAM 세션이 폐기됐거나(다른 곳에서 로그아웃) 토큰이 맞지 않는다: 이 세션은 더 쓸 수 없다.
            deleteSession(hash);
            return Optional.empty();
        }
        touchIfStale(session, now);
        return Optional.of(user);
    }

    private boolean needsRefresh(AuthSession session, Instant now) {
        return !session.getAccessExpiresAt().minus(props.getRefreshMargin()).isAfter(now);
    }

    /** 같은 세션의 동시 갱신 중 하나만 IAM 을 호출한다. 나머지는 기다렸다가 갱신된 토큰을 읽는다. */
    private AuthSession refreshUnderLock(String hash) {
        ReentrantLock lock = refreshLocks[Math.floorMod(hash.hashCode(), LOCK_STRIPES)];
        lock.lock();
        try {
            AuthSession current = sessions.findBySessionHash(hash).orElse(null);
            if (current == null) {
                return null;
            }
            if (!needsRefresh(current, Instant.now())) {
                return current; // 기다리는 동안 다른 요청이 이미 갱신했다
            }
            DoroOAuthClient.TokenSet tokens;
            try {
                tokens = oauth.refresh(crypto.decrypt(current.getRefreshTokenEnc()));
            } catch (DoroOAuthClient.RejectedException e) {
                log.info("BFF session refresh rejected by IAM; ending the session: {}", e.getMessage());
                deleteSession(hash);
                return null;
            } catch (DoroOAuthClient.UnavailableException e) {
                log.warn("BFF session refresh skipped (IAM unavailable): {}", e.getMessage());
                return current; // 세션은 유지한다. 만료된 액세스 토큰은 아래 검증에서 걸러져 이번 요청만 익명이 된다.
            } catch (IllegalStateException e) {
                log.error("BFF refresh token could not be decrypted; dropping the session", e);
                deleteSession(hash);
                return null;
            }
            current.replaceTokens(crypto.encrypt(tokens.accessToken()), tokens.accessExpiresAt(), crypto.encrypt(tokens.refreshToken()));
            AuthSession updated = current;
            tx().executeWithoutResult(status -> sessions.save(updated));
            return updated;
        } finally {
            lock.unlock();
        }
    }

    private void touchIfStale(AuthSession session, Instant now) {
        if (session.getLastUsedAt().plus(TOUCH_INTERVAL).isAfter(now)) {
            return;
        }
        session.touch(now);
        tx().executeWithoutResult(status -> sessions.save(session));
    }

    private DoroUser verifyAccessToken(String accessToken) {
        try {
            return verifier.verify(accessToken);
        } catch (RuntimeException e) {
            log.warn("BFF access token rejected: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ 로그아웃

    /** 세션을 지우고 IAM 세션도 끝낸다(RFC 7009). IAM 호출이 실패해도 로컬 로그아웃은 항상 완료한다. */
    public void logout(String cookieValue) {
        if (isBlank(cookieValue)) {
            return;
        }
        String hash = sha256Hex(cookieValue);
        sessions.findBySessionHash(hash).ifPresent(session -> {
            try {
                oauth.revoke(crypto.decrypt(session.getRefreshTokenEnc()));
            } catch (RuntimeException e) {
                log.warn("IAM session revocation failed during logout (local session is removed anyway): {}", e.getMessage());
            }
        });
        deleteSession(hash);
    }

    private void deleteSession(String hash) {
        tx().executeWithoutResult(status -> sessions.deleteBySessionHash(hash));
    }

    // ------------------------------------------------------------------ 보조

    /** 로그인 후 돌아갈 곳. 사이트 안의 경로만 허용한다 (오픈 리다이렉트 방지). */
    static String safeReturnPath(String path) {
        if (path == null || path.isBlank() || path.length() > MAX_RETURN_PATH_LENGTH
                || !path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("://")) {
            return "/";
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) {
                return "/";
            }
        }
        return path;
    }

    private String randomToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
