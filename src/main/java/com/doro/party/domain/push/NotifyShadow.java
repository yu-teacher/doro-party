package com.doro.party.domain.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 이관 검증(SHADOW): 파티가 직접 보낸 알림과 같은 알림을 플랫폼 알림 서비스에 {@code dryRun} 으로 물어 보고, 받을 기기 수·설정 판정이 같은지 비교해 기록한다.
 * 알림 서비스는 판정만 하고 아무것도 보내지 않으므로 사용자에게 알림이 두 번 가지 않는다. 비교는 별도 스레드 풀에서 하고, 어떤 오류도 파티의 실제 발송에 영향을 주지 않는다.
 * 로그에는 사용자·기기·알림 내용을 남기지 않는다(종류와 숫자만).
 */
@Slf4j
@Component
public class NotifyShadow {

    private static final String APP = "party";

    private final NotifyProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final ThreadPoolTaskExecutor executor;
    private final AtomicLong matched = new AtomicLong();
    private final AtomicLong mismatched = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public NotifyShadow(NotifyProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-shadow-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Notify shadow queue is full; skipping a comparison"));
        executor.setDaemon(true);
        executor.initialize();
    }

    /** 지금까지 일치한 비교 수(시험·상태 확인용). */
    long matchedCount() {
        return matched.get();
    }

    long mismatchedCount() {
        return mismatched.get();
    }

    long failedCount() {
        return failed.get();
    }

    public boolean active() {
        return properties.shadowing();
    }

    /** 앱이 뜨면 알림 종류를 알림 서비스에 등록한다(멱등). 허브 화면의 문구가 여기서 나온다. */
    @EventListener(ApplicationReadyEvent.class)
    void registerTypes() {
        if (!active()) {
            return;
        }
        executor.execute(() -> {
            try {
                Map<String, Object> body = Map.of("types", List.of(
                        type("comment", "새 댓글·답글", "내 핀이나 내가 남긴 댓글에 댓글·답글이 달리면 알려줘요"),
                        type("friend", "친구 요청·수락", "친구 요청을 받거나 내 요청이 수락되면 알려줘요"),
                        type("share", "지도 공유", "친구가 내게 지도를 공유하면 알려줘요")));
                HttpResponse<String> response = http.send(request("PUT", "/internal/v1/apps/" + APP + "/types", body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                log.info("Notify shadow: types registered, status={}", response.statusCode());
            } catch (Exception e) {
                log.warn("Notify shadow: type registration failed, reason={}", e.getClass().getSimpleName());
            }
        });
    }

    private static Map<String, Object> type(String id, String label, String description) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", id);
        type.put("label", label);
        type.put("description", description);
        type.put("defaultOn", true);
        return type;
    }

    /**
     * 파티가 한 사용자에게 알림을 처리한 결과와 알림 서비스의 판정을 비교한다.
     *
     * @param partyWanted 파티의 설정이 이 종류를 켜 둔 상태인가(꺼져 있으면 파티는 보내지 않았고 알림 서비스도 '끔'으로 판정해야 한다)
     * @param legacy      파티가 실제로 보낸 결과(보내지 않았으면 기기 0)
     */
    public void compare(String type, UUID userId, PushMessage message, boolean partyWanted, PushSender.Summary legacy) {
        if (!active()) {
            return;
        }
        executor.execute(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("idempotencyKey", "shadow:" + UUID.randomUUID());
                body.put("app", APP);
                body.put("type", type);
                body.put("recipients", List.of(userId));
                body.put("title", message.title());
                body.put("body", message.body());
                body.put("tag", message.tag());
                body.put("url", toNotifyUrl(message.url()));
                body.put("dryRun", true);
                HttpResponse<String> response = http.send(request("POST", "/internal/v1/notifications", body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    failed.incrementAndGet();
                    log.warn("Notify shadow: request failed, type={}, status={}", type, response.statusCode());
                    return;
                }
                JsonNode data = json.readTree(response.body()).path("data");
                record(type, partyWanted, legacy, data.path("accepted").asInt(), data.path("suppressed").asInt(), data.path("noSubscription").asInt(), data.path("devices").asInt());
            } catch (Exception e) {
                failed.incrementAndGet();
                log.warn("Notify shadow: comparison failed, type={}, reason={}", type, e.getClass().getSimpleName());
            }
        });
    }

    /** 같다고 보는 기준: 파티가 보냈다면 알림 서비스도 같은 수의 기기에 보낼 것이고, 설정을 꺼 뒀다면 '끔'이어야 하고, 기기가 없다면 '기기 없음'이어야 한다. */
    void record(String type, boolean partyWanted, PushSender.Summary legacy, int accepted, int suppressed, int noSubscription, int devices) {
        boolean match;
        if (!partyWanted) {
            match = suppressed == 1;
        } else if (legacy.devices() == 0) {
            match = noSubscription == 1;
        } else {
            match = accepted == 1 && devices == legacy.devices();
        }
        if (match) {
            long total = matched.incrementAndGet();
            if (total % 20 == 1) {
                log.info("Notify shadow: match, type={}, matched={}, mismatched={}, failed={}", type, total, mismatched.get(), failed.get());
            }
        } else {
            long total = mismatched.incrementAndGet();
            log.warn("Notify shadow: MISMATCH, type={}, partyWanted={}, partyDevices={}, notifyAccepted={}, notifySuppressed={}, notifyNoSubscription={}, notifyDevices={}, mismatched={}",
                    type, partyWanted, legacy.devices(), accepted, suppressed, noSubscription, devices, total);
        }
    }

    String toNotifyUrl(String url) {
        return properties.toNotifyUrl(url);
    }

    private HttpRequest request(String method, String path, Object body) throws Exception {
        return HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                .timeout(properties.readTimeout())
                .header("Content-Type", "application/json")
                .header("X-Doro-Service-Token", properties.serviceToken())
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
    }
}
