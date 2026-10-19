package com.doro.party.domain.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 브라우저의 {@code PushSubscription.toJSON()} 모양을 그대로 받는다. */
public final class PushDtos {

    private PushDtos() {
    }

    public record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {
    }

    public record SubscribeRequest(@NotBlank @Size(max = PushEndpointPolicy.MAX_LENGTH) String endpoint, @NotNull @Valid Keys keys) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = PushEndpointPolicy.MAX_LENGTH) String endpoint) {
    }

    /** enabled 가 false 면 서버에 VAPID 가 설정되지 않아 푸시를 켤 수 없다. */
    public record PushConfigResponse(boolean enabled, String publicKey) {
    }

    public record PreferencesDto(boolean comments, boolean friendRequests, boolean shares) {
        static PreferencesDto from(PushPreferences preferences) {
            return new PreferencesDto(preferences.isComments(), preferences.isFriendRequests(), preferences.isShares());
        }

        static PreferencesDto defaults() {
            return new PreferencesDto(true, true, true);
        }
    }

    /** 이 기기들로 시험 알림을 보낸 결과. */
    public record TestResult(int devices, int delivered) {
    }
}
