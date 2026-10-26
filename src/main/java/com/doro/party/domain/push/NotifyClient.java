package com.doro.party.domain.push;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 플랫폼 알림 서비스(doro-notify)의 내부 API 를 부르는 클라이언트(CENTRAL). 호출자 서비스 토큰으로 인증하고, 자기 사용자의 기기·설정을
 * 자기 앱({@value #APP}) 범위 안에서 대신 다룬다. 응답 본문·구독 값은 로그에 남기지 않는다(상태 코드만).
 */
@Slf4j
@Component
public class NotifyClient {

    static final String APP = "party";

    /** HTTP 응답. body 는 JSON 이면 파싱한 것(아니면 null). */
    public record Reply(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public JsonNode data() {
            return body == null ? null : body.path("data");
        }

        public String message() {
            return body == null ? null : body.path("message").asText(null);
        }
    }

    /** 알림 서비스에 닿지 못했다(연결·시간 초과). 재시도할 수 있는 실패다. */
    public static class UnreachableException extends RuntimeException {
        public UnreachableException(Throwable cause) {
            super("notify service unreachable: " + cause.getClass().getSimpleName());
        }
    }

    private final NotifyProperties properties;
    private final PushProperties push;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public NotifyClient(NotifyProperties properties, PushProperties push) {
        this.properties = properties;
        this.push = push;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public boolean central() {
        return properties.central();
    }

    // ------------------------------------------------------------------ 알림 요청(아웃박스가 부른다)

    /** 알림 한 건을 요청한다. 같은 멱등 키 재요청은 알림 서비스가 중복으로 거른다(재시도해도 두 번 가지 않는다). */
    public Reply send(UUID requestId, UUID recipient, String type, String title, String body, String tag, String url, Instant expiresAt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("idempotencyKey", APP + ":" + requestId);
        request.put("app", APP);
        request.put("type", type);
        request.put("recipients", List.of(recipient));
        request.put("title", PushMessage.clip(title, PushMessage.TITLE_MAX));
        request.put("body", PushMessage.clip(body, PushMessage.BODY_MAX));
        request.put("tag", tag);
        request.put("url", properties.toNotifyUrl(url));
        request.put("urgency", "high");
        request.put("ttlSeconds", push.ttl().toSeconds());
        request.put("expiresAt", expiresAt.toString());
        return call("POST", "/internal/v1/notifications", request, null);
    }

    // ------------------------------------------------------------------ 사용자 대신 다루는 API

    public Reply config() {
        return call("GET", "/api/v1/notify/config", null, null);
    }

    public Reply subscribe(UUID userId, String deviceId, String endpoint, String p256dh, String auth, String userAgent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("endpoint", endpoint);
        body.put("keys", Map.of("p256dh", p256dh, "auth", auth));
        return call("PUT", userPath(userId) + "/subscriptions", body, userAgent);
    }

    public Reply unsubscribe(UUID userId, String endpoint) {
        return call("DELETE", userPath(userId) + "/subscriptions", Map.of("endpoint", endpoint), null);
    }

    public Reply preferences(UUID userId) {
        return call("GET", userPath(userId) + "/preferences", null, null);
    }

    public Reply updatePreferences(UUID userId, Map<String, Boolean> byType) {
        List<Map<String, Object>> changes = byType.entrySet().stream()
                .<Map<String, Object>>map(e -> Map.of("app", APP, "type", e.getKey(), "enabled", e.getValue())).toList();
        return call("PUT", userPath(userId) + "/preferences", Map.of("changes", changes), null);
    }

    public Reply test(UUID userId) {
        return call("POST", userPath(userId) + "/test", null, null);
    }

    private static String userPath(UUID userId) {
        return "/internal/v1/apps/" + APP + "/users/" + userId;
    }

    /** 알림 서비스의 실패 응답을 파티의 오류로 바꾼다: 로그인 문제는 401, 입력 문제는 400, 그 밖(5xx·연결 실패)은 503. */
    public static PartyException toPartyException(Reply reply) {
        if (reply.status() == 401) {
            return new PartyException(ErrorCode.UNAUTHORIZED);
        }
        if (reply.status() == 400 || reply.status() == 403 || reply.status() == 404 || reply.status() == 409) {
            String message = reply.message();
            return new PartyException(ErrorCode.INVALID_INPUT, message == null || message.isBlank() ? "푸시 알림 설정을 처리하지 못했어요." : message);
        }
        return new PartyException(ErrorCode.SERVICE_UNAVAILABLE, "알림 서비스에 일시적으로 연결할 수 없어요. 잠시 후 다시 시도해 주세요.");
    }

    private Reply call(String method, String path, Object body, String userAgent) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(properties.readTimeout())
                    .header("X-Doro-Service-Token", properties.serviceToken());
            if (userAgent != null && !userAgent.isBlank()) {
                request.header("User-Agent", userAgent);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode parsed = null;
            try {
                parsed = response.body() == null || response.body().isBlank() ? null : json.readTree(response.body());
            } catch (IOException ignored) {
                // JSON 이 아닌 응답: 상태 코드만 쓴다
            }
            return new Reply(response.statusCode(), parsed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnreachableException(e);
        } catch (IOException e) {
            throw new UnreachableException(e);
        }
    }
}
