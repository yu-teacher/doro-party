package com.doro.party.domain.push;

import com.doro.party.domain.push.PushDtos.PreferencesDto;
import com.doro.party.domain.push.PushDtos.PushConfigResponse;
import com.doro.party.domain.push.PushDtos.TestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CENTRAL 모드에서 파티의 푸시 설정 API(/api/v1/push/*)를 알림 서비스로 넘기는 어댑터. 화면과 응답 모양은 그대로라서(옛 화면도 그대로 동작한다)
 * 파티는 구독·설정 데이터를 더 이상 쓰지 않고, 알림 서비스가 기기·설정의 기준이 된다. 파티의 알림 종류 이름은 알림 서비스의 종류로 바꿔 전달한다
 * (comments→comment, friendRequests→friend, shares→share).
 */
@Service
@RequiredArgsConstructor
public class PushCentral {

    private final NotifyClient client;

    public PushConfigResponse config() {
        JsonNode data = require(client.config()).data();
        return new PushConfigResponse(data.path("enabled").asBoolean(false), data.path("publicKey").asText(null));
    }

    /** 이 기기를 내 알림 기기로 등록한다. 기기 표식은 구독 주소로 만든 안정된 값이다(같은 기기를 다시 등록하면 같은 표식). */
    public void subscribe(DoroUser user, String endpoint, String p256dh, String auth, String userAgent) {
        require(client.subscribe(user.userId(), deviceId(endpoint.strip()), endpoint.strip(), p256dh, auth, userAgent));
    }

    public void unsubscribe(DoroUser user, String endpoint) {
        require(client.unsubscribe(user.userId(), endpoint.strip()));
    }

    public PreferencesDto preferences(DoroUser user) {
        return toDto(require(client.preferences(user.userId())).data());
    }

    public PreferencesDto updatePreferences(DoroUser user, PreferencesDto update) {
        Map<String, Boolean> byType = new LinkedHashMap<>();
        byType.put("comment", update.comments());
        byType.put("friend", update.friendRequests());
        byType.put("share", update.shares());
        return toDto(require(client.updatePreferences(user.userId(), byType)).data());
    }

    public TestResult sendTest(DoroUser user) {
        JsonNode data = require(client.test(user.userId())).data();
        return new TestResult(data.path("devices").asInt(), data.path("delivered").asInt());
    }

    /** 알림 서비스가 돌려준 설정(앱 하나)에서 파티의 세 종류를 읽는다. 없는 종류는 켜짐(기본값). */
    static PreferencesDto toDto(JsonNode data) {
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (JsonNode app : data.path("apps")) {
            if (NotifyClient.APP.equals(app.path("app").asText())) {
                for (JsonNode type : app.path("types")) {
                    enabled.put(type.path("type").asText(), type.path("enabled").asBoolean(true));
                }
            }
        }
        return new PreferencesDto(enabled.getOrDefault("comment", true), enabled.getOrDefault("friend", true), enabled.getOrDefault("share", true));
    }

    static String deviceId(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return "party-" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private NotifyClient.Reply require(NotifyClient.Reply reply) {
        if (!reply.ok()) {
            throw NotifyClient.toPartyException(reply);
        }
        return reply;
    }
}
