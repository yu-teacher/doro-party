package com.doro.party.domain.push;

import com.doro.party.domain.push.PushTestSupport.TestDevice;
import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.support.PartyHttpTestBase;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CENTRAL: 파티는 직접 보내지 않고 알림을 아웃박스에 쌓아 알림 서비스에 요청하고, 푸시 설정 API 는 알림 서비스로 넘긴다.
 * 알림 서비스는 가짜 서버로 흉내 내고, 파티가 보낸 요청(경로·토큰·본문)과 실패 처리(다시 시도·버림)를 확인한다.
 */
class NotifyCentralTest extends PartyHttpTestBase {

    private record Seen(String method, String path, String token, String userAgent, String body) {
    }

    private static final String TOKEN = "party-central-token-" + "t".repeat(40);
    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    private static volatile int sendStatus = 200;
    private static volatile String prefsReply = "{\"success\":true,\"data\":{\"apps\":[{\"app\":\"party\",\"displayName\":\"도로 파티\",\"types\":["
            + "{\"type\":\"comment\",\"enabled\":false},{\"type\":\"friend\",\"enabled\":true},{\"type\":\"share\",\"enabled\":true}]}]}}";
    private static final HttpServer NOTIFY = startNotify();

    private static HttpServer startNotify() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                SEEN.add(new Seen(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("X-Doro-Service-Token"),
                        exchange.getRequestHeaders().getFirst("User-Agent"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                int code = 200;
                String reply = "{\"success\":true,\"data\":{}}";
                if (path.equals("/internal/v1/notifications")) {
                    code = sendStatus;
                    reply = code == 200 ? "{\"success\":true,\"data\":{\"outcome\":\"ACCEPTED\",\"accepted\":1}}" : "{\"success\":false,\"code\":\"NOTIFY-X\",\"message\":\"x\"}";
                } else if (path.endsWith("/preferences")) {
                    reply = prefsReply;
                } else if (path.endsWith("/test")) {
                    reply = "{\"success\":true,\"data\":{\"devices\":2,\"delivered\":1}}";
                } else if (path.equals("/api/v1/notify/config")) {
                    reply = "{\"success\":true,\"data\":{\"enabled\":true,\"publicKey\":\"PUBKEY\"}}";
                }
                byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void central(DynamicPropertyRegistry registry) {
        registry.add("party.notify.mode", () -> "CENTRAL");
        registry.add("party.notify.base-url", () -> "http://127.0.0.1:" + NOTIFY.getAddress().getPort());
        registry.add("party.notify.service-token", () -> TOKEN);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotifyOutboxRetrier retrier;
    @Autowired private NotifyOutbox outbox;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("pushExecutor") private Executor pushExecutor;

    @BeforeEach
    void reset() {
        SEEN.clear();
        sendStatus = 200;
        jdbc.update("delete from notify_outbox");
    }

    private void awaitPushIdle() {
        var executor = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) pushExecutor;
        await().atMost(Duration.ofSeconds(10)).until(() -> executor.getThreadPoolExecutor().getQueue().isEmpty() && executor.getActiveCount() == 0);
    }

    private List<Seen> sent() {
        return SEEN.stream().filter(s -> s.path().equals("/internal/v1/notifications")).toList();
    }

    @Test
    @DisplayName("댓글: 파티는 직접 보내지 않고 아웃박스에 쌓고, 재시도기가 알림 서비스에 (dryRun 아닌) 실제 요청으로 보낸 뒤 행을 지운다")
    void commentGoesThroughOutbox() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "맛집");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("골목 국밥집", 37.5, 127.0, "WISH", null, null));
        TestDevice ownerPhone = new TestDevice();   // 파티 자체 구독이 있어도 CENTRAL 에서는 파티가 직접 보내지 않는다
        send(owner, put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(ownerPhone.subscriptionJson())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class)).isZero();   // 구독은 알림 서비스로 넘어갔다

        send(viewer, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"여기 웨이팅 길어요\"}")).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).until(() -> outbox.count() == 1);
        awaitPushIdle();
        assertThat(sent()).isEmpty();                       // 재시도기를 부르기 전에는 보내지 않았다
        assertThat(ownerPhone.received()).isEmpty();          // 파티가 직접 보내지 않았다

        NotifyOutboxRetrier.Result result = retrier.processDue();
        assertThat(result.sent()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
        Seen request = sent().get(0);
        assertThat(request.token()).isEqualTo(TOKEN);
        assertThat(request.body()).contains("\"app\":\"party\"").contains("\"type\":\"comment\"").contains(owner.id().toString())
                .contains("\"title\":\"골목 국밥집\"").contains("\"url\":\"/party/?map=" + mapId + "&pin=" + pinId + "&comment=")
                .contains("\"tag\":\"comment:" + pinId + "\"").contains("\"urgency\":\"high\"").contains("\"idempotencyKey\":\"party:").doesNotContain("dryRun");
    }

    @Test
    @DisplayName("알림 서비스가 오류를 내면 행은 남아 다시 시도하고(같은 멱등 키), 나아지면 보낸 뒤 지운다")
    void retriesUntilItWorks() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "재시도");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        sendStatus = 503;
        send(viewer, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕\"}")).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).until(() -> outbox.count() == 1);

        assertThat(retrier.processDue().retry()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from notify_outbox", Integer.class)).isEqualTo(1);
        // 다음 시도 시각 전에는 건드리지 않는다
        assertThat(retrier.processDue().retry()).isZero();

        jdbc.update("update notify_outbox set next_attempt_at = now() - interval '1 second'");
        sendStatus = 200;
        assertThat(retrier.processDue().sent()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
        List<Seen> requests = sent();
        assertThat(requests).hasSize(2);
        String key = requests.get(0).body().replaceAll(".*\"idempotencyKey\":\"([^\"]+)\".*", "$1");
        assertThat(requests.get(1).body()).contains(key);
    }

    @Test
    @DisplayName("요청 자체가 거절되면(400) 다시 보내도 같으므로 버리고, 만료된 행도 보내지 않고 버린다")
    void dropsRejectedAndExpired() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "버림");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        sendStatus = 400;
        send(viewer, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"하나\"}")).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).until(() -> outbox.count() == 1);
        assertThat(retrier.processDue().dropped()).isEqualTo(1);
        assertThat(outbox.count()).isZero();

        send(viewer, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"둘\"}")).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).until(() -> outbox.count() == 1);
        jdbc.update("update notify_outbox set expires_at = now() - interval '1 second'");
        SEEN.clear();
        sendStatus = 200;
        assertThat(retrier.processDue().dropped()).isEqualTo(1);
        assertThat(sent()).isEmpty();
    }

    @Test
    @DisplayName("푸시 설정 API 는 알림 서비스로 넘어간다: 구독(기기 표식·사용자 UA), 해제, 설정 이름 변환, 시험 알림, 설정(config)")
    void settingsApiIsForwarded() throws Exception {
        TestUser user = newUser();
        TestDevice device = new TestDevice();
        send(user, put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).header("User-Agent", "TestBrowser/1").content(device.subscriptionJson())).andExpect(status().isOk());
        Seen subscribe = SEEN.stream().filter(s -> s.method().equals("PUT") && s.path().endsWith("/subscriptions")).findFirst().orElseThrow();
        assertThat(subscribe.path()).isEqualTo("/internal/v1/apps/party/users/" + user.id() + "/subscriptions");
        assertThat(subscribe.token()).isEqualTo(TOKEN);
        assertThat(subscribe.userAgent()).isEqualTo("TestBrowser/1");
        assertThat(subscribe.body()).contains("\"deviceId\":\"party-").contains("\"endpoint\":\"" + device.endpoint + "\"");

        send(user, delete("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + device.endpoint + "\"}")).andExpect(status().isOk());
        assertThat(SEEN.stream().anyMatch(s -> s.method().equals("DELETE") && s.body().contains(device.endpoint))).isTrue();

        send(user, get("/api/v1/push/preferences")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments").value(false)).andExpect(jsonPath("$.data.friendRequests").value(true)).andExpect(jsonPath("$.data.shares").value(true));
        send(user, put("/api/v1/push/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"comments\":false,\"friendRequests\":true,\"shares\":false}")).andExpect(status().isOk());
        Seen update = SEEN.stream().filter(s -> s.method().equals("PUT") && s.path().endsWith("/preferences")).findFirst().orElseThrow();
        assertThat(update.body()).contains("\"type\":\"comment\"").contains("\"type\":\"friend\"").contains("\"type\":\"share\"").contains("\"app\":\"party\"");

        send(user, post("/api/v1/push/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data.devices").value(2)).andExpect(jsonPath("$.data.delivered").value(1));
        send(user, get("/api/v1/push/config")).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true)).andExpect(jsonPath("$.data.publicKey").value("PUBKEY"));
        assertThat(jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class)).isZero();
    }
}
