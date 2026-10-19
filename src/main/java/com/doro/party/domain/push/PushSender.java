package com.doro.party.domain.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** 사용자의 모든 기기에 메시지를 암호화해 보내고, 결과에 따라 구독을 정리한다(없어진 구독은 지우고, 거절이 계속되면 지운다). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSender {

    public record Summary(int devices, int delivered) {
    }

    private final PushProperties properties;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushClient client;
    private final WebPushEncryptor encryptor;
    private final PushEndpointPolicy endpointPolicy;
    private final Clock clock;

    public boolean enabled() {
        return properties.enabled();
    }

    public Summary sendToUser(UUID userId, PushMessage message, boolean urgent) {
        if (!enabled()) {
            return new Summary(0, 0);
        }
        List<PushSubscription> devices = subscriptions.findAllByUserIdOrderByCreatedAtAsc(userId);
        byte[] json = message.toJson();
        int delivered = 0;
        for (PushSubscription device : devices) {
            if (sendToDevice(device, json, message.tag(), urgent)) {
                delivered++;
            }
        }
        log.info("Push sent: userId={}, devices={}, delivered={}, tag={}", userId, devices.size(), delivered, tagKind(message.tag()));
        return new Summary(devices.size(), delivered);
    }

    private boolean sendToDevice(PushSubscription device, byte[] json, String tag, boolean urgent) {
        try {
            // 저장된 주소도 다시 확인한다(허용 목록이 바뀌었거나 옛 데이터가 있어도 목록 밖으로는 접속하지 않는다)
            URI endpoint = endpointPolicy.validate(device.getEndpoint());
            byte[] body = encryptor.encrypt(Base64.getUrlDecoder().decode(device.getP256dh()), Base64.getUrlDecoder().decode(device.getAuth()), json);
            WebPushClient.Result result = client.send(endpoint, body, tag, properties.ttl(), urgent);
            switch (result.outcome()) {
                case DELIVERED -> {
                    subscriptions.recordSuccess(device.getId(), clock.instant());
                    return true;
                }
                case GONE -> {
                    subscriptions.deleteBySubscriptionId(device.getId());
                    log.info("Push subscription removed (gone): subscriptionId={}", device.getId());
                }
                case REJECTED -> {
                    subscriptions.recordRejection(device.getId());
                    if (subscriptions.deleteIfRejectedTooOften(device.getId(), properties.failuresBeforeRemoval()) > 0) {
                        log.warn("Push subscription removed after repeated rejections: subscriptionId={}", device.getId());
                    }
                }
                case RETRYABLE -> {
                    // 푸시 서비스의 일시적 문제: 구독은 그대로 둔다
                }
            }
        } catch (RuntimeException e) {
            // 저장된 키가 깨져 있거나 주소가 정책에 맞지 않으면 이 기기만 건너뛴다(다른 기기와 요청 처리는 계속)
            log.warn("Push skipped for a device: subscriptionId={}, reason={}", device.getId(), e.getClass().getSimpleName());
            subscriptions.recordRejection(device.getId());
            subscriptions.deleteIfRejectedTooOften(device.getId(), properties.failuresBeforeRemoval());
        }
        return false;
    }

    /** 로그에는 알림의 종류만 남긴다(태그의 뒷부분에는 핀·지도 식별자가 들어 있다). */
    private static String tagKind(String tag) {
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(0, colon);
    }
}
