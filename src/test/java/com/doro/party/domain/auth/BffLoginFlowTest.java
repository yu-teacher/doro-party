package com.doro.party.domain.auth;

import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.domain.auth.repository.LoginAttemptRepository;
import com.doro.party.support.FakeIam;
import com.doro.party.support.PartyHttpTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BFF 로그인 전체 흐름: PKCE 인가 요청, 콜백, 세션 쿠키, CSRF, 토큰 갱신, 로그아웃. 가짜 IAM 서버와 실제 DB 를 쓴다. */
class BffLoginFlowTest extends PartyHttpTestBase {

    @Autowired private AuthSessionRepository sessions;
    @Autowired private LoginAttemptRepository attempts;
    @Autowired private JdbcTemplate jdbc;

    // ------------------------------------------------------------------ 로그인 시작

    @Test
    @DisplayName("로그인 시작: 인가 요청에 PKCE(S256)와 state 가 실리고, 서버에는 state 해시만 남는다")
    void loginStartBuildsPkceAuthorizationRequest() throws Exception {
        StartedLogin started = startLogin("/maps/42");

        assertThat(started.location()).startsWith("https://doro.test/oauth2/authorize?");
        var params = UriComponentsBuilder.fromUriString(started.location()).build().getQueryParams();
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("doro-party");
        assertThat(java.net.URLDecoder.decode(params.getFirst("redirect_uri"), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(SITE + "/party/api/v1/bff/callback");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(started.state()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(started.challenge()).hasSizeGreaterThanOrEqualTo(40);
        // verifier 는 브라우저로 가지 않는다
        assertThat(started.location()).doesNotContain("code_verifier");
        assertThat(attempts.findById(BffAuthService.sha256Hex(started.state()))).isPresent();
        assertThat(attempts.findById(started.state())).as("state 원문으로는 저장되지 않는다").isEmpty();
    }

    // ------------------------------------------------------------------ 콜백

    @Test
    @DisplayName("콜백: 세션 쿠키(HttpOnly, Secure, SameSite=Lax)를 발급하고 원래 경로로 이동하며, 토큰은 암호화해 저장한다")
    void callbackIssuesHardenedCookieAndStoresEncryptedTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        StartedLogin started = startLogin("/maps/42");
        String code = IAM.issueCode(userId, newEmail(), started.challenge(), "doro-party");

        MockHttpServletResponse response = callback(code, started.state());

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/party/maps/42");
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/party");
        String cookieValue = cookieValueOf(response);
        var stored = sessions.findBySessionHash(BffAuthService.sha256Hex(cookieValue)).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(userId);
        assertThat(stored.getAccessTokenEnc()).startsWith("v1:");
        assertThat(stored.getRefreshTokenEnc()).startsWith("v1:").doesNotContain("refresh-");
        assertThat(sessions.findBySessionHash(cookieValue)).as("쿠키 원문으로는 저장되지 않는다").isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from party_users where id = ?", Long.class, userId))
                .as("첫 로그인 때 도로 파티 사용자가 만들어진다").isEqualTo(1L);
    }

    @Test
    @DisplayName("state 는 한 번만 쓸 수 있다: 같은 콜백을 다시 보내면 실패한다")
    void stateIsSingleUse() throws Exception {
        StartedLogin started = startLogin(null);
        String code = IAM.issueCode(UUID.randomUUID(), newEmail(), started.challenge(), "doro-party");

        assertThat(callback(code, started.state()).getHeader("Set-Cookie")).isNotNull();
        MockHttpServletResponse replay = callback(IAM.issueCode(UUID.randomUUID(), newEmail(), started.challenge(), "doro-party"), started.state());

        assertThat(replay.getHeader("Location")).isEqualTo("/party/?login_error=failed");
    }

    @Test
    @DisplayName("알 수 없는 state, 누락된 파라미터, IAM 의 거부(error)는 쿠키 없이 로그인 실패로 끝난다")
    void invalidCallbacksFail() throws Exception {
        assertThat(callback("any-code", "unknown-state").getHeader("Location")).isEqualTo("/party/?login_error=failed");
        assertThat(mockMvc.perform(get("/api/v1/bff/callback")).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/party/?login_error=failed");
        MockHttpServletResponse denied = mockMvc.perform(get("/api/v1/bff/callback").param("error", "access_denied")).andReturn().getResponse();
        assertThat(denied.getHeader("Location")).isEqualTo("/party/?login_error=cancelled");
        assertThat(denied.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("PKCE: 다른 verifier 로는 코드를 교환할 수 없다 (콜백 실패)")
    void codeBoundToTheOriginalChallenge() throws Exception {
        StartedLogin started = startLogin(null);
        // 공격자가 자기 challenge 로 받은 코드를 이 로그인에 끼워 넣는다
        String foreignCode = IAM.issueCode(UUID.randomUUID(), newEmail(), FakeIam.s256("attacker-verifier"), "doro-party");

        MockHttpServletResponse response = callback(foreignCode, started.state());

        assertThat(response.getHeader("Location")).isEqualTo("/party/?login_error=failed");
        // 실패하면 쿠키를 발급하지 않고, 이전에 남아 있을 수 있는 쿠키를 지운다(값이 비고 Max-Age=0)
        assertThat(response.getHeader("Set-Cookie")).startsWith("doro_party_session=;").contains("Max-Age=0");
    }

    @Test
    @DisplayName("다른 클라이언트용 토큰(cid 불일치)은 세션으로 만들지 않는다")
    void tokenForAnotherClientIsRejected() throws Exception {
        StartedLogin started = startLogin(null);
        String code = IAM.issueCode(UUID.randomUUID(), newEmail(), started.challenge(), "some-other-app");

        assertThat(callback(code, started.state()).getHeader("Location")).isEqualTo("/party/?login_error=failed");
    }

    @Test
    @DisplayName("돌아갈 경로가 외부 주소면 / 로 보낸다 (오픈 리다이렉트 방지)")
    void externalReturnPathIsIgnored() throws Exception {
        StartedLogin started = startLogin("//evil.example/phish");
        String code = IAM.issueCode(UUID.randomUUID(), newEmail(), started.challenge(), "doro-party");

        assertThat(callback(code, started.state()).getHeader("Location")).isEqualTo("/party/");
    }

    // ------------------------------------------------------------------ 세션 인증

    @Test
    @DisplayName("세션 쿠키로 요청하면 로그인한 사용자로 인식되고, 쿠키가 없거나 틀리면 비로그인이다")
    void sessionCookieAuthenticatesRequests() throws Exception {
        UUID userId = UUID.randomUUID();
        String cookie = login(userId, newEmail(), null);

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.user.username").exists())
                .andExpect(jsonPath("$.data.user.email").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("USER"));
        mockMvc.perform(get("/api/v1/bff/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
        mockMvc.perform(get("/api/v1/bff/session").cookie(session("forged-cookie-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    // ------------------------------------------------------------------ CSRF

    @Test
    @DisplayName("쿠키로 상태를 바꾸는 요청은 CSRF 헤더가 있어야 하고, Origin 이 있다면 이 사이트여야 한다")
    void cookieAuthenticatedWritesRequireCsrfProof() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        String body = "{}";

        mockMvc.perform(post("/api/v1/csrf-probe").cookie(session(cookie)).contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403-03"));
        mockMvc.perform(post("/api/v1/csrf-probe").cookie(session(cookie)).header(CSRF, "1").header("Origin", "https://evil.example")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403-03"));
        // 올바른 증명이 있으면 CSRF 검사를 통과해 라우팅(없는 경로라 404)까지 간다
        mockMvc.perform(post("/api/v1/csrf-probe").cookie(session(cookie)).header(CSRF, "1").header("Origin", SITE)
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        // 읽기 요청은 CSRF 헤더가 필요 없다
        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    // ------------------------------------------------------------------ 토큰 갱신

    @Test
    @DisplayName("액세스 토큰이 곧 만료되면 요청 중에 갱신하고, 리프레시 토큰을 회전된 값으로 교체한다")
    void expiringAccessTokenIsRefreshedAndRotated() throws Exception {
        IAM.accessTtlSeconds = 30; // 갱신 여유(60초)보다 짧아 곧바로 갱신 대상이 된다
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        IAM.accessTtlSeconds = 900;
        String before = sessions.findBySessionHash(BffAuthService.sha256Hex(cookie)).orElseThrow().getRefreshTokenEnc();
        int refreshesBefore = IAM.refreshCalls.get();

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(true));

        assertThat(IAM.refreshCalls.get() - refreshesBefore).isEqualTo(1);
        var after = sessions.findBySessionHash(BffAuthService.sha256Hex(cookie)).orElseThrow();
        assertThat(after.getRefreshTokenEnc()).as("리프레시 토큰이 회전됨").isNotEqualTo(before);
        assertThat(after.getAccessExpiresAt()).isAfter(java.time.Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("같은 세션에 동시 요청이 와도 갱신은 한 번만 일어난다 (회전된 토큰 재사용으로 IAM 이 탈취로 판단하지 않게)")
    void concurrentRequestsRefreshOnlyOnce() throws Exception {
        IAM.accessTtlSeconds = 30;
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        IAM.accessTtlSeconds = 900;
        int refreshesBefore = IAM.refreshCalls.get();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                    .andReturn().getResponse().getContentAsString().contains("\"authenticated\":true")));
        }
        for (Future<Boolean> result : results) {
            assertThat(result.get(30, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();

        assertThat(IAM.refreshCalls.get() - refreshesBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("IAM 이 갱신을 거부하면(폐기·만료) 세션을 지우고 비로그인으로 처리한다")
    void rejectedRefreshEndsTheSession() throws Exception {
        IAM.accessTtlSeconds = 30;
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        IAM.rejectRefresh = true;

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(false));

        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isEmpty();
    }

    @Test
    @DisplayName("IAM 에 일시적으로 닿지 못하면 세션은 유지된다 (아직 유효한 액세스 토큰으로 계속 동작)")
    void iamOutageDoesNotEndTheSession() throws Exception {
        IAM.accessTtlSeconds = 30;
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        IAM.unavailable = true;

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(true));

        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isPresent();
    }

    @Test
    @DisplayName("절대 만료가 지난 세션은 리프레시가 가능해도 쓸 수 없다")
    void absoluteExpiryEndsTheSession() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        jdbc.update("update auth_sessions set expires_at = now() - interval '1 minute' where session_hash = ?",
                BffAuthService.sha256Hex(cookie));

        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(false));

        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isEmpty();
    }

    // ------------------------------------------------------------------ 로그아웃

    @Test
    @DisplayName("로그아웃: CSRF 헤더가 있어야 하고, 서버 세션을 지우며 IAM 에 리프레시 토큰 폐기를 요청하고 쿠키를 지운다")
    void logoutRemovesSessionAndRevokesAtIam() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        int revokesBefore = IAM.revokeCalls.get();

        mockMvc.perform(post("/api/v1/bff/logout").cookie(session(cookie))).andExpect(status().isForbidden());
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).as("CSRF 헤더 없이는 지워지지 않는다").isPresent();

        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/bff/logout").cookie(session(cookie)).header(CSRF, "1").header("Origin", SITE))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isEmpty();
        assertThat(IAM.revokeCalls.get() - revokesBefore).isEqualTo(1);
        assertThat(IAM.lastRevokedToken).startsWith("refresh-");
        mockMvc.perform(get("/api/v1/bff/session").cookie(session(cookie)))
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    @DisplayName("IAM 폐기 호출이 실패해도 로그아웃은 완료된다")
    void logoutSucceedsEvenIfIamIsDown() throws Exception {
        String cookie = login(UUID.randomUUID(), newEmail(), null);
        IAM.unavailable = false;
        // 폐기 엔드포인트가 응답하지 않는 상황: 서버를 내리는 대신 잘못된 토큰으로도 로컬 삭제가 보장되는지만 확인한다
        mockMvc.perform(post("/api/v1/bff/logout").cookie(session(cookie)).header(CSRF, "1")).andExpect(status().isOk());

        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(cookie))).isEmpty();
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 성공한다 (지울 것이 없을 뿐)")
    void logoutWithoutCookieIsOk() throws Exception {
        mockMvc.perform(post("/api/v1/bff/logout")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새로 로그인하면 이전 세션 쿠키는 폐기된다 (세션 고정 방지)")
    void newLoginInvalidatesPreviousCookie() throws Exception {
        String first = login(UUID.randomUUID(), newEmail(), null);
        UUID userId = UUID.randomUUID();
        StartedLogin started = startLogin(null);
        String code = IAM.issueCode(userId, newEmail(), started.challenge(), "doro-party");

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/bff/callback").param("code", code).param("state", started.state())
                .cookie(session(first))).andReturn().getResponse();

        assertThat(cookieValueOf(response)).isNotEqualTo(first);
        assertThat(sessions.findBySessionHash(BffAuthService.sha256Hex(first))).isEmpty();
    }
}
