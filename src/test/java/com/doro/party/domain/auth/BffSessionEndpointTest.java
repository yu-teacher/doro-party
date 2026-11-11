package com.doro.party.domain.auth;

import com.doro.party.support.PartyHttpTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인·세션·토큰 갱신은 별도 BFF(doro-bff)가 하고, 이 서비스에는 로그인 코드가 없다. 서비스는 게이트웨이가 붙여 주는 Bearer 만 검증하므로
 * 쿠키만 보내면 익명이고, 세션 조회({@code GET /api/v1/bff/session})만 이 서비스가 제공한다.
 */
class BffSessionEndpointTest extends PartyHttpTestBase {

    /** 예전 내장 로그인에 쓰던 SDK 클래스. SDK 에 아직 남아 있더라도 이 서비스에서 빈이 만들어지면 안 된다(SDK 에서 지워졌으면 건너뛴다). */
    private static final String[] EMBEDDED_BFF_CLASSES = {
            "com.hunnit_beasts.doro.sdk.bff.BffSessionStore",
            "com.hunnit_beasts.doro.sdk.bff.BffLoginAttemptStore",
            "com.hunnit_beasts.doro.sdk.bff.BffController",
            "com.hunnit_beasts.doro.sdk.bff.BffAuthService",
            "com.hunnit_beasts.doro.sdk.bff.BffSessionAuthFilter",
    };

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("SDK 의 내장 BFF 구성 요소(저장소·컨트롤러·세션 필터)는 만들어지지 않는다")
    void noEmbeddedBffBeans() {
        for (String name : EMBEDDED_BFF_CLASSES) {
            Class<?> type;
            try {
                type = Class.forName(name);
            } catch (ClassNotFoundException e) {
                continue;
            }
            assertThat(context.getBeanNamesForType(type)).as(name).isEmpty();
        }
    }

    @Test
    @DisplayName("로그인·콜백·로그아웃 경로는 이 서비스에 없다(게이트웨이가 BFF 로 보낸다)")
    void loginIsNotServedHere() throws Exception {
        mockMvc.perform(get("/api/v1/bff/login")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/bff/callback").param("code", "c").param("state", "s")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/bff/logout")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Bearer 가 없으면 익명이다(세션 쿠키는 이 서비스가 해석하지 않는다)")
    void sessionEndpointIsAnonymousWithoutBearer() throws Exception {
        mockMvc.perform(get("/api/v1/bff/session").cookie(new Cookie("doro_party_session", "whatever")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    @DisplayName("유효한 Bearer 면 로그인 상태와 내 프로필을 돌려준다(처음이면 사용자 행을 만든다)")
    void sessionEndpointWithBearer() throws Exception {
        TestUser user = newUser();

        send(user, get("/api/v1/bff/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.user.id").value(user.id().toString()));
    }

    @Test
    @DisplayName("다른 OAuth 클라이언트에게 발급된 토큰은 받지 않는다(익명)")
    void foreignClientTokenIsAnonymous() throws Exception {
        String foreign = TOKENS.accessToken(UUID.randomUUID(), newEmail(), "some-other-client");

        mockMvc.perform(get("/api/v1/bff/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }
}
