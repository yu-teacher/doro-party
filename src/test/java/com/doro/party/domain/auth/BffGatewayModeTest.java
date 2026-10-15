package com.doro.party.domain.auth;

import com.hunnit_beasts.doro.sdk.bff.BffController;
import com.hunnit_beasts.doro.sdk.bff.BffLoginAttemptStore;
import com.hunnit_beasts.doro.sdk.bff.BffSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * party.auth.mode=gateway: 로그인·세션·토큰 갱신은 별도 BFF(doro-bff)가 하고, 이 서비스에는 BFF 코드가 돌지 않는다.
 * 서비스는 게이트웨이가 붙여 주는 Bearer 만 검증하므로, 쿠키만 보내면 익명이다.
 */
@SpringBootTest(properties = "party.auth.mode=gateway")
@AutoConfigureMockMvc
class BffGatewayModeTest {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("gateway 모드에는 SDK BFF 구성 요소(저장소·컨트롤러)가 만들어지지 않는다")
    void noEmbeddedBffBeans() {
        assertThat(context.getBeanNamesForType(BffSessionStore.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BffLoginAttemptStore.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BffController.class)).isEmpty();
    }

    @Test
    @DisplayName("로그인 경로는 이 서비스에 없다(게이트웨이가 BFF 로 보낸다)")
    void loginIsNotServedHere() throws Exception {
        mockMvc.perform(get("/api/v1/bff/login")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("세션 조회는 여전히 이 서비스가 제공하고, Bearer 가 없으면 익명이다(쿠키는 이 서비스가 해석하지 않는다)")
    void sessionEndpointIsAnonymousWithoutBearer() throws Exception {
        mockMvc.perform(get("/api/v1/bff/session").cookie(new jakarta.servlet.http.Cookie("doro_party_session", "whatever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }
}
