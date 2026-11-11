package com.doro.party.support;

import com.doro.party.domain.push.PushTestSupport;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.security.jwks.JwksKeyProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증된 HTTP 테스트의 공통 기반. 실제 DB·Guard 를 쓰고, 운영처럼 게이트웨이가 붙여 주는 {@code Authorization: Bearer} 로 요청한다.
 * 토큰은 시험용 키({@link TestTokens})로 서명하고, 그 공개 키를 JWKS 제공자에 등록해 SDK 가 실제 검증을 거치게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PartyHttpTestBase {

    protected static final String CLIENT_ID = "doro-party";

    protected static final TestTokens TOKENS = new TestTokens();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("doro.iam.revocation-check", () -> "OFF");
        // 개발용 버킷과 섞이지 않도록 테스트 전용 버킷을 쓴다
        registry.add("party.storage.bucket", () -> "doro-party-test");
        // Guard 삭제 재시도기는 시험에서 직접 호출한다(배경 실행이 시험 중인 아웃박스 행을 건드리지 않게 주기를 길게)
        registry.add("party.guard-outbox.interval", () -> "PT24H");
        // 알림 서비스 아웃박스 재시도기도 시험에서 직접 호출한다(CENTRAL 시험 전용 설정은 그 시험이 덮어쓴다)
        registry.add("party.notify.outbox-interval", () -> "PT24H");
        // 웹 푸시: 시험용 VAPID 키와 가짜 푸시 서비스(localhost, http). 운영과 달리 https 요구만 끈다.
        registry.add("party.push.vapid-public-key", () -> PushTestSupport.KEYS.publicKey);
        registry.add("party.push.vapid-private-key", () -> PushTestSupport.KEYS.privateKey);
        registry.add("party.push.subject", () -> "mailto:test@example.test");
        registry.add("party.push.allowed-hosts", () -> "localhost");
        registry.add("party.push.require-https", () -> "false");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired private JwksKeyProvider jwks;

    @BeforeEach
    void registerTestKey() {
        jwks.registerKey(TestTokens.KID, TOKENS.publicKey());
    }

    @Autowired protected GuardTuples guardTuples;

    // ------------------------------------------------------------------ 지도·핀 도우미

    protected ResultActions send(TestUser user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(user.sign(request));
    }

    protected String createMap(TestUser owner, String name) throws Exception {
        String body = send(owner, post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"설명\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    protected static String pinBody(String name, double lat, double lng, String status, Integer rating, String tagsJson) {
        return "{\"name\":\"" + name + "\",\"sharedMemo\":\"메모\",\"lat\":" + lat + ",\"lng\":" + lng
                + (status == null ? "" : ",\"status\":\"" + status + "\"")
                + (rating == null ? "" : ",\"rating\":" + rating)
                + (tagsJson == null ? "" : ",\"tags\":" + tagsJson) + "}";
    }

    protected String createPin(TestUser user, String mapId, String body) throws Exception {
        String response = send(user, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    protected ResultActions putPin(TestUser user, String mapId, String pinId, String body) throws Exception {
        return send(user, put("/api/v1/maps/{m}/pins/{p}", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected void grant(String mapId, String relation, TestUser user) {
        guardTuples.write(PartyGuard.MAP, mapId, relation, PartyGuard.USER, user.id().toString());
    }

    /** 초대 링크로 두 사람을 친구로 만든다. */
    protected void befriend(TestUser a, TestUser b) throws Exception {
        String body = send(a, post("/api/v1/friends/invite")).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.data.code");
        send(b, post("/api/v1/friends/invite/{c}/accept", code)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    /** 지도를 친구에게 공유한다(role: VIEWER / EDITOR). */
    protected ResultActions shareMap(TestUser owner, String mapId, TestUser target, String role) throws Exception {
        return send(owner, put("/api/v1/maps/{m}/shares/{u}", mapId, target.id()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    /** 로그인한 테스트 사용자. 게이트웨이가 붙여 주는 것과 같은 Bearer 액세스 토큰으로 요청을 인증한다. */
    public record TestUser(UUID id, String email, String accessToken) {

        /** 이 사용자로 요청을 보낸다. */
        public RequestBuilder sign(AbstractMockHttpServletRequestBuilder<?> request) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            return request;
        }
    }

    /**
     * 새 사용자를 만든다. 운영의 웹처럼 로그인 직후 먼저 세션 조회를 불러 도로 파티 사용자 행을 만든다
     * (gateway 모드에서는 이 조회가 사용자 행을 만든다).
     */
    protected TestUser newUser() throws Exception {
        UUID id = UUID.randomUUID();
        String email = newEmail();
        TestUser user = new TestUser(id, email, TOKENS.accessToken(id, email, CLIENT_ID));
        send(user, get("/api/v1/bff/session")).andExpect(status().isOk()).andExpect(jsonPath("$.data.authenticated").value(true));
        return user;
    }

    protected String newEmail() {
        return "party-" + UUID.randomUUID().toString().substring(0, 8) + "@doro.local";
    }
}
