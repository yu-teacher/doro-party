package com.doro.party.domain.auth;

import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.support.PartyHttpTestBase;
import com.hunnit_beasts.doro.sdk.bff.BffAuthService;
import com.hunnit_beasts.doro.sdk.bff.BffCrypto;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * BFF 의 회복력과 로그인 CSRF 방어: IAM 장애·과부하가 사용자를 로그아웃시키지 않고, 콜백은 로그인을 시작한 바로 그 브라우저에서만 끝낼 수 있다.
 * 가짜 IAM 서버와 실제 DB 를 쓴다.
 */
class BffHardeningTest extends PartyHttpTestBase {

    @Autowired private AuthSessionRepository sessions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BffCrypto crypto;
    @Autowired private PlatformTransactionManager transactionManager;

    private int sessionCountOf(UUID userId) {
        return jdbc.queryForObject("select count(*) from auth_sessions where user_id = ?", Integer.class, userId);
    }

    /** 액세스 토큰이 이미 만료된 세션을 시간 흐름에 기대지 않고 만든다. */
    private String expiredSession(UUID userId, String email) throws Exception {
        String cookie = login(userId, email, null);
        int updated = jdbc.update("update auth_sessions set access_token_enc = ?, access_expires_at = ? where session_hash = ?",
                crypto.encrypt(IAM.expiredAccessToken(userId, email, CLIENT_ID)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), BffAuthService.sha256Hex(cookie));
        assertThat(updated).isEqualTo(1);
        return cookie;
    }

    @Test
    @DisplayName("IAM 장애 중 액세스 토큰이 만료돼도 세션은 지워지지 않고, IAM 이 돌아오면 다시 쓸 수 있다")
    void sessionSurvivesAnIamOutageWithAnExpiredAccessToken() throws Exception {
        String cookie = expiredSession(UUID.randomUUID(), newEmail());
        IAM.unavailable = true;

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(false));
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).as("IAM 이 잠깐 죽었다고 세션을 지우면 안 된다").isPresent();

        IAM.unavailable = false;
        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    @Test
    @DisplayName("IAM 이 429 로 답해도 세션은 지워지지 않는다")
    void sessionSurvivesIamRateLimit() throws Exception {
        String cookie = expiredSession(UUID.randomUUID(), newEmail());
        IAM.tooManyRequests = true;

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(false));
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).as("429 는 일시적이므로 세션을 지우면 안 된다").isPresent();

        IAM.tooManyRequests = false;
        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    @Test
    @DisplayName("로그인을 시작하면 그 브라우저에 '로그인 진행 중' 쿠키(HttpOnly, Secure, SameSite=Lax, 로그인 API 경로)가 발급된다")
    void loginStartBindsTheBrowser() throws Exception {
        var response = mockMvc.perform(get("/api/v1/bff/login")).andReturn().getResponse();

        String header = response.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(LOGIN_COOKIE + "=")).findFirst().orElse(null);
        assertThat(header).as("로그인 진행 중 쿠키").isNotNull();
        assertThat(header).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/party/api/v1/bff");
    }

    @Test
    @DisplayName("공격자가 시작한 로그인의 콜백 주소를 피해자 브라우저가 열어도 세션이 만들어지지 않고 IAM 도 호출하지 않는다 (로그인 CSRF)")
    void callbackFromAnotherBrowserIsRejected() throws Exception {
        UUID attackerId = UUID.randomUUID();
        StartedLogin attackerFlow = startLogin(null);
        String code = IAM.issueCode(attackerId, newEmail(), attackerFlow.challenge(), CLIENT_ID);
        int exchangesBefore = IAM.codeExchanges.get();

        // 피해자 브라우저는 공격자의 '로그인 진행 중' 쿠키가 없다
        MockHttpServletResponse victim = mockMvc.perform(get("/api/v1/bff/callback").param("code", code).param("state", attackerFlow.state()))
                .andReturn().getResponse();

        assertThat(victim.getHeader("Location")).isEqualTo("/party/?login_error=failed");
        assertThat(victim.getHeaders("Set-Cookie")).as("피해자에게 공격자의 세션을 심으면 안 된다").noneMatch(h -> h.startsWith(COOKIE + "="));
        assertThat(sessionCountOf(attackerId)).isZero();
        assertThat(IAM.codeExchanges.get()).as("코드 교환 전에 거부한다").isEqualTo(exchangesBefore);
    }

    @Test
    @DisplayName("자기가 시작한 다른 로그인의 쿠키로는 남이 시작한 로그인을 끝낼 수 없다")
    void anotherLoginsCookieDoesNotMatch() throws Exception {
        UUID attackerId = UUID.randomUUID();
        StartedLogin attackerFlow = startLogin(null);
        StartedLogin victimOwnFlow = startLogin(null);
        String code = IAM.issueCode(attackerId, newEmail(), attackerFlow.challenge(), CLIENT_ID);

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/bff/callback").param("code", code).param("state", attackerFlow.state())
                .cookie(new Cookie(LOGIN_COOKIE, loginCookieOf(victimOwnFlow.state())))).andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("/party/?login_error=failed");
        assertThat(sessionCountOf(attackerId)).isZero();
    }

    @Test
    @DisplayName("가짜 콜백 링크가 이미 로그인한 사용자의 세션 쿠키를 지우지 않는다 (강제 로그아웃 방지)")
    void failedCallbackKeepsAnExistingSessionCookie() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);

        MockHttpServletResponse forged = mockMvc.perform(get("/api/v1/bff/callback").param("code", "x").param("state", "unknown-state")
                .cookie(session(cookie))).andReturn().getResponse();

        assertThat(forged.getHeader("Location")).isEqualTo("/party/?login_error=failed");
        assertThat(forged.getHeaders("Set-Cookie")).noneMatch(h -> h.startsWith(COOKIE + "="));
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isPresent();
    }

    @Test
    @DisplayName("잘못된 형식의 돌아갈 경로로 로그인을 시작해도 500 이 아니라 /로 처리된다")
    void malformedReturnPathDoesNotFail() throws Exception {
        for (String bad : new String[]{"/a b", "/%zz", "/a\u0000b", "/[", "/ x", "/a{b}|"}) {
            StartedLogin started = startLogin(bad);
            String code = IAM.issueCode(UUID.randomUUID(), newEmail(), started.challenge(), CLIENT_ID);
            MockHttpServletResponse response = callback(code, started.state());
            assertThat(response.getStatus()).as("return=%s", bad).isEqualTo(302);
            assertThat(response.getHeader("Location")).as("return=%s", bad).startsWith("/party/");
        }
    }

    @Test
    @DisplayName("마지막 사용 시각 갱신은 그 컬럼만 바꾸고, 그사이 회전된 토큰을 옛 값으로 되돌리지 않는다")
    void touchDoesNotOverwriteRotatedTokens() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        String hash = BffAuthService.sha256Hex(cookie);
        String staleRefresh = sessions.findBySessionHash(hash).orElseThrow().getRefreshTokenEnc();
        jdbc.update("update auth_sessions set refresh_token_enc = 'v1:rotated-by-other-request', last_used_at = now() - interval '10 minutes' where session_hash = ?", hash);

        int updated = new TransactionTemplate(transactionManager).execute(status -> sessions.touchLastUsed(hash, Instant.now()));

        assertThat(updated).isEqualTo(1);
        assertThat(jdbc.queryForObject("select refresh_token_enc from auth_sessions where session_hash = ?", String.class, hash))
                .isEqualTo("v1:rotated-by-other-request").isNotEqualTo(staleRefresh);
        assertThat(jdbc.queryForObject("select last_used_at > now() - interval '1 minute' from auth_sessions where session_hash = ?", Boolean.class, hash))
                .isTrue();
    }

    @Test
    @DisplayName("토큰 갱신 저장은 토큰 세 컬럼만 바꾼다")
    void updateTokensChangesOnlyTokenColumns() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        String hash = BffAuthService.sha256Hex(cookie);
        var before = sessions.findBySessionHash(hash).orElseThrow();
        Instant newExpiry = Instant.now().plusSeconds(1234);

        int updated = new TransactionTemplate(transactionManager).execute(status -> sessions.updateTokens(hash, "v1:a", newExpiry, "v1:r"));

        assertThat(updated).isEqualTo(1);
        var after = jdbc.queryForMap("select access_token_enc, refresh_token_enc, user_id, expires_at from auth_sessions where session_hash = ?", hash);
        assertThat(after.get("access_token_enc")).isEqualTo("v1:a");
        assertThat(after.get("refresh_token_enc")).isEqualTo("v1:r");
        assertThat(after.get("user_id")).isEqualTo(before.getUserId());
    }
}
