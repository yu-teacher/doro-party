package com.doro.party.support;

import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.security.jwks.JwksKeyProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 쿠키로 인증된 HTTP 테스트의 공통 기반. 가짜 IAM 서버와 실제 DB·Guard 를 쓰고,
 * 실제 로그인 흐름(PKCE 인가 요청 → 콜백)을 거쳐 사용자별 세션 쿠키를 만든다.
 * 같은 설정이면 Spring 컨텍스트가 재사용되므로 가짜 IAM 은 JVM 이 끝날 때만 내린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PartyHttpTestBase {

    protected static final String SITE = "https://party.test";
    protected static final String COOKIE = "doro_party_session";
    protected static final String CSRF = "X-Party-Csrf";
    protected static final String CLIENT_ID = "doro-party";

    protected static final FakeIam IAM = startIam();

    private static FakeIam startIam() {
        try {
            FakeIam iam = new FakeIam();
            Runtime.getRuntime().addShutdownHook(new Thread(iam::close));
            return iam;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("party.auth.authorize-url", () -> "https://doro.test/oauth2/authorize");
        registry.add("party.auth.token-url", () -> IAM.baseUrl() + "/oauth2/token");
        registry.add("party.auth.revoke-url", () -> IAM.baseUrl() + "/oauth2/revoke");
        registry.add("party.auth.redirect-uri", () -> SITE + "/party/api/v1/bff/callback");
        registry.add("party.auth.web-base-path", () -> "/party");
        registry.add("party.auth.cookie-secure", () -> "true");
        registry.add("doro.iam.revocation-check", () -> "OFF");
        // 개발용 버킷과 섞이지 않도록 테스트 전용 버킷을 쓴다
        registry.add("party.storage.bucket", () -> "doro-party-test");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired private JwksKeyProvider jwks;

    @BeforeEach
    void resetIam() {
        jwks.registerKey(FakeIam.KID, IAM.publicKey());
        IAM.rejectRefresh = false;
        IAM.unavailable = false;
        IAM.accessTtlSeconds = 900;
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

    /** 로그인한 테스트 사용자. 세션 쿠키로 요청을 인증한다. */
    public record TestUser(UUID id, String email, String cookie) {

        /** 이 사용자로 요청을 보낸다. 상태를 바꾸는 메서드에는 CSRF 증명(헤더·Origin)도 붙인다. */
        public RequestBuilder sign(AbstractMockHttpServletRequestBuilder<?> request) {
            request.cookie(new Cookie(COOKIE, cookie));
            request.header(CSRF, "1");
            request.header("Origin", SITE);
            return request;
        }
    }

    /** 새 사용자를 만들고 실제 로그인 흐름으로 세션을 받는다. */
    protected TestUser newUser() throws Exception {
        UUID id = UUID.randomUUID();
        String email = newEmail();
        return new TestUser(id, email, login(id, email, null));
    }

    protected record StartedLogin(String state, String challenge, String location) {}

    protected StartedLogin startLogin(String returnPath) throws Exception {
        var request = get("/api/v1/bff/login");
        if (returnPath != null) {
            request.param("return", returnPath);
        }
        MockHttpServletResponse response = mockMvc.perform(request).andExpect(status().isFound()).andReturn().getResponse();
        String location = response.getHeader("Location");
        var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        return new StartedLogin(params.getFirst("state"), params.getFirst("code_challenge"), location);
    }

    protected MockHttpServletResponse callback(String code, String state) throws Exception {
        return mockMvc.perform(get("/api/v1/bff/callback").param("code", code).param("state", state)).andReturn().getResponse();
    }

    protected String cookieValueOf(MockHttpServletResponse response) {
        String header = response.getHeader("Set-Cookie");
        assertThat(header).as("세션 쿠키가 발급되어야 한다").isNotNull().startsWith(COOKIE + "=");
        return header.substring((COOKIE + "=").length(), header.indexOf(';'));
    }

    /** 로그인 전체를 수행하고 세션 쿠키 값을 돌려준다. */
    protected String login(UUID userId, String email, String returnPath) throws Exception {
        StartedLogin started = startLogin(returnPath);
        String code = IAM.issueCode(userId, email, started.challenge(), CLIENT_ID);
        MockHttpServletResponse response = callback(code, started.state());
        assertThat(response.getStatus()).isEqualTo(302);
        return cookieValueOf(response);
    }

    protected String newEmail() {
        return "party-" + UUID.randomUUID().toString().substring(0, 8) + "@doro.local";
    }

    protected Cookie session(String value) {
        return new Cookie(COOKIE, value);
    }
}
