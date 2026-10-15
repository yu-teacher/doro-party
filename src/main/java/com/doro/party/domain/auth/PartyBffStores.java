package com.doro.party.domain.auth;

import com.doro.party.domain.auth.entity.AuthSession;
import com.doro.party.domain.auth.entity.LoginAttempt;
import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.domain.auth.repository.LoginAttemptRepository;
import com.hunnit_beasts.doro.sdk.bff.BffLoginAttempt;
import com.hunnit_beasts.doro.sdk.bff.BffLoginAttemptStore;
import com.hunnit_beasts.doro.sdk.bff.BffSession;
import com.hunnit_beasts.doro.sdk.bff.BffSessionStore;
import lombok.RequiredArgsConstructor;
import com.doro.party.config.ConditionalOnEmbeddedBff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SDK BFF 의 저장소 계약({@link BffSessionStore}, {@link BffLoginAttemptStore})을 도로 파티 DB(JPA)로 구현한다. 메서드마다 짧은 트랜잭션이다. */
@Component
@ConditionalOnEmbeddedBff
@RequiredArgsConstructor
public class PartyBffStores implements BffSessionStore, BffLoginAttemptStore {

    private final AuthSessionRepository sessions;
    private final LoginAttemptRepository attempts;

    // ---------------------------------------------------------------- 세션

    @Override
    @Transactional(readOnly = true)
    public Optional<BffSession> find(String sessionHash) {
        return sessions.findBySessionHash(sessionHash).map(PartyBffStores::toSession);
    }

    @Override
    @Transactional
    public void create(BffSession session, String previousSessionHash) {
        sessions.save(new AuthSession(session.sessionHash(), session.userId(), session.accessTokenEnc(), session.accessExpiresAt(),
                session.refreshTokenEnc(), session.createdAt(), session.expiresAt()));
        if (previousSessionHash != null) {
            sessions.deleteBySessionHash(previousSessionHash);
        }
    }

    @Override
    @Transactional
    public void updateTokens(String sessionHash, String accessTokenEnc, Instant accessExpiresAt, String refreshTokenEnc) {
        sessions.updateTokens(sessionHash, accessTokenEnc, accessExpiresAt, refreshTokenEnc);
    }

    @Override
    @Transactional
    public void touchLastUsed(String sessionHash, Instant now) {
        sessions.touchLastUsed(sessionHash, now);
    }

    @Override
    @Transactional
    public void delete(String sessionHash) {
        sessions.deleteBySessionHash(sessionHash);
    }

    @Override
    @Transactional
    public int deleteAllByUserId(UUID userId) {
        return sessions.deleteAllByUserId(userId);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return sessions.deleteExpired(now) + attempts.deleteExpired(now);
    }

    // ---------------------------------------------------------------- 로그인 시도

    @Override
    @Transactional
    public void save(BffLoginAttempt attempt) {
        attempts.save(new LoginAttempt(attempt.stateHash(), attempt.codeVerifierEnc(), attempt.returnPath(), attempt.browserHash(),
                attempt.createdAt(), attempt.expiresAt()));
    }

    /** state 는 한 번만: 실제로 지운 행이 1 일 때만 값을 돌려준다(동시에 두 번 오면 하나만 성공). */
    @Override
    @Transactional
    public Optional<BffLoginAttempt> consume(String stateHash) {
        Optional<LoginAttempt> found = attempts.findById(stateHash);
        int deleted = attempts.deleteByStateHash(stateHash);
        return found.filter(a -> deleted == 1).map(a ->
                new BffLoginAttempt(a.getStateHash(), a.getCodeVerifierEnc(), a.getReturnPath(), a.getBrowserHash(), a.getCreatedAt(), a.getExpiresAt()));
    }

    private static BffSession toSession(AuthSession s) {
        return new BffSession(s.getSessionHash(), s.getUserId(), s.getAccessTokenEnc(), s.getAccessExpiresAt(), s.getRefreshTokenEnc(),
                s.getCreatedAt(), s.getLastUsedAt(), s.getExpiresAt());
    }
}
