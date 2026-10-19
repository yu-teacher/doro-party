package com.doro.party.domain.push;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.push.PushDtos.PreferencesDto;
import com.doro.party.domain.push.PushDtos.PushConfigResponse;
import com.doro.party.domain.push.PushDtos.TestResult;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    static final int USER_AGENT_MAX = 300;

    private final PushProperties properties;
    private final VapidSigner signer;
    private final PushEndpointPolicy endpointPolicy;
    private final PushSubscriptionRepository subscriptions;
    private final PushPreferencesRepository preferences;
    private final PartyUserService userService;
    private final PushSender sender;
    private final Clock clock;

    public PushConfigResponse config() {
        return new PushConfigResponse(signer.enabled(), signer.publicKey());
    }

    /**
     * 이 기기를 내 알림 기기로 등록한다. 주소는 허용된 푸시 서비스 도메인이어야 하고, 키는 올바른 형식이어야 한다(서버가 대신 접속하고 이 키로 암호화한다).
     * 같은 기기를 다시 등록하면 갱신이고, 다른 계정이 쓰던 기기를 이 계정으로 로그인해 등록하면 이 계정으로 옮겨진다.
     */
    @Transactional
    public void subscribe(DoroUser doroUser, String endpoint, String p256dh, String auth, String userAgent) {
        requireEnabled();
        endpointPolicy.validate(endpoint);
        requireValidKeys(p256dh, auth);
        PartyUser user = userService.getOrCreateUser(doroUser);

        subscriptions.upsert(UUID.randomUUID(), user.getId(), endpoint.strip(), p256dh, auth, clip(userAgent), clock.instant());

        List<PushSubscription> mine = subscriptions.findAllByUserIdOrderByCreatedAtAsc(user.getId());
        int excess = mine.size() - properties.maxSubscriptionsPerUser();
        for (PushSubscription device : mine) {
            if (excess <= 0) {
                break;
            }
            if (!device.getEndpoint().equals(endpoint.strip())) {
                subscriptions.deleteBySubscriptionId(device.getId());
                excess--;
            }
        }
        log.info("Push subscription saved: userId={}", user.getId());
    }

    /** 이 기기의 구독을 지운다. 없어도(이미 지웠어도) 오류가 아니고, 남의 구독은 지울 수 없다. */
    @Transactional
    public void unsubscribe(DoroUser doroUser, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint.strip(), doroUser.userId());
    }

    @Transactional(readOnly = true)
    public PreferencesDto preferences(DoroUser doroUser) {
        return preferences.findById(doroUser.userId()).map(PreferencesDto::from).orElseGet(PreferencesDto::defaults);
    }

    @Transactional
    public PreferencesDto updatePreferences(DoroUser doroUser, PreferencesDto update) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        PushPreferences saved = preferences.findById(user.getId()).orElseGet(() -> new PushPreferences(user.getId(), true, true, true));
        saved.update(update.comments(), update.friendRequests(), update.shares());
        return PreferencesDto.from(preferences.save(saved));
    }

    /** 내 기기들로 시험 알림을 보낸다(알림 설정이 잘 됐는지 확인하는 용도). 푸시가 꺼져 있으면 400. */
    public TestResult sendTest(DoroUser doroUser) {
        requireEnabled();
        PushSender.Summary summary = sender.sendToUser(doroUser.userId(),
                new PushMessage("알림이 켜졌어요", "이 기기로 도로 파티 알림이 와요", "./", "test:" + doroUser.userId()), false);
        return new TestResult(summary.devices(), summary.delivered());
    }

    private void requireEnabled() {
        if (!signer.enabled()) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "이 서버는 푸시 알림을 쓸 수 없어요.");
        }
    }

    private static void requireValidKeys(String p256dh, String auth) {
        try {
            WebPushEncryptor.parsePublicKey(Base64.getUrlDecoder().decode(p256dh));
            if (Base64.getUrlDecoder().decode(auth).length != WebPushEncryptor.AUTH_SECRET_BYTES) {
                throw new IllegalArgumentException("auth length");
            }
        } catch (IllegalArgumentException e) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "푸시 구독 키가 올바르지 않습니다.");
        }
    }

    private static String clip(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= USER_AGENT_MAX ? userAgent : userAgent.substring(0, USER_AGENT_MAX);
    }
}
