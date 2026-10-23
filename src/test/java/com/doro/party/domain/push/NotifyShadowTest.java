package com.doro.party.domain.push;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 알림 서비스 이관 검증(SHADOW)의 클라이언트: 무엇을 보내고, 언제 아무것도 하지 않고, 실패해도 조용한지. */
class NotifyShadowTest {

    private record Seen(String method, String path, String token, String body) {
    }

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String reply = "{\"success\":true,\"data\":{\"outcome\":\"DRY_RUN\",\"accepted\":1,\"suppressed\":0,\"noSubscription\":0,\"devices\":1}}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("X-Doro-Service-Token"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private NotifyShadow shadow(NotifyProperties.Mode mode, String token) {
        return new NotifyShadow(new NotifyProperties(mode, "http://127.0.0.1:" + server.getAddress().getPort(), token, "/party", Duration.ofSeconds(1), Duration.ofSeconds(2), 10));
    }

    private static final PushMessage MESSAGE = new PushMessage("핀 이름", "닉: 댓글", "./?map=m1&pin=p1&comment=c1", "comment:p1");

    @Test
    @DisplayName("SHADOW: 같은 알림을 dryRun 으로 물어 보고, 토큰과 절대 경로 주소를 보낸다")
    void asksWithDryRun() {
        NotifyShadow shadow = shadow(NotifyProperties.Mode.SHADOW, "tok-123456");
        UUID user = UUID.randomUUID();
        shadow.compare("comment", user, MESSAGE, true, new PushSender.Summary(1, 1));
        await().atMost(Duration.ofSeconds(5)).until(() -> seen.size() == 1);
        Seen request = seen.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/internal/v1/notifications");
        assertThat(request.token()).isEqualTo("tok-123456");
        assertThat(request.body()).contains("\"dryRun\":true").contains("\"app\":\"party\"").contains("\"type\":\"comment\"")
                .contains("\"url\":\"/party/?map=m1&pin=p1&comment=c1\"").contains(user.toString());
    }

    @Test
    @DisplayName("LEGACY(기본)이거나 토큰이 없으면 아무것도 부르지 않는다")
    void inactiveDoesNothing() throws Exception {
        shadow(NotifyProperties.Mode.LEGACY, "tok-123456").compare("comment", UUID.randomUUID(), MESSAGE, true, new PushSender.Summary(1, 1));
        shadow(NotifyProperties.Mode.SHADOW, "").compare("comment", UUID.randomUUID(), MESSAGE, true, new PushSender.Summary(1, 1));
        Thread.sleep(300);
        assertThat(seen).isEmpty();
    }

    @Test
    @DisplayName("알림 서비스가 오류를 내거나 닿지 않아도 예외를 던지지 않는다")
    void failuresAreQuiet() throws Exception {
        status = 500;
        NotifyShadow shadow = shadow(NotifyProperties.Mode.SHADOW, "tok-123456");
        shadow.compare("share", UUID.randomUUID(), MESSAGE, true, new PushSender.Summary(0, 0));
        await().atMost(Duration.ofSeconds(5)).until(() -> seen.size() == 1);
        server.stop(0);
        shadow.compare("share", UUID.randomUUID(), MESSAGE, true, new PushSender.Summary(0, 0));
        Thread.sleep(300);
    }

    @Test
    @DisplayName("앱이 뜨면 알림 종류 3개를 등록한다(SHADOW 일 때만)")
    void registersTypes() {
        NotifyShadow shadow = shadow(NotifyProperties.Mode.SHADOW, "tok-123456");
        shadow.registerTypes();
        await().atMost(Duration.ofSeconds(5)).until(() -> seen.size() == 1);
        assertThat(seen.get(0).method()).isEqualTo("PUT");
        assertThat(seen.get(0).path()).isEqualTo("/internal/v1/apps/party/types");
        assertThat(seen.get(0).body()).contains("\"type\":\"comment\"").contains("\"type\":\"friend\"").contains("\"type\":\"share\"");
    }

    @Test
    @DisplayName("상대 주소는 앱 범위의 절대 경로로 바뀌고, 이미 절대인 주소는 그대로다")
    void convertsUrls() {
        NotifyShadow shadow = shadow(NotifyProperties.Mode.SHADOW, "tok-123456");
        assertThat(shadow.toNotifyUrl("./friends")).isEqualTo("/party/friends");
        assertThat(shadow.toNotifyUrl("./?map=1")).isEqualTo("/party/?map=1");
        assertThat(shadow.toNotifyUrl("/party/x")).isEqualTo("/party/x");
    }

    @Test
    @DisplayName("비교 기준: 보냈다면 같은 수의 기기, 껐다면 '끔', 기기가 없다면 '기기 없음'이어야 같다")
    void matchRules() {
        NotifyShadow shadow = shadow(NotifyProperties.Mode.SHADOW, "tok-123456");
        // 같은 경우는 예외 없이 지나간다. 다른 경우도 기록만 남기고 예외는 없다(여기서는 호출이 안전한지 확인한다).
        shadow.record("comment", true, new PushSender.Summary(2, 2), 1, 0, 0, 2);
        shadow.record("comment", false, new PushSender.Summary(0, 0), 0, 1, 0, 0);
        shadow.record("comment", true, new PushSender.Summary(0, 0), 0, 0, 1, 0);
        shadow.record("comment", true, new PushSender.Summary(2, 2), 1, 0, 0, 1);
    }
}
