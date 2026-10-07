package com.doro.party.domain.share;

import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ShareDtos {

    private ShareDtos() {
    }

    public record ShareRequest(@NotNull ShareRole role) {
    }

    /** 지도를 공유받은 사람(주인 화면용: 사용자명까지 보인다). */
    public record ShareView(UserSummary user, ShareRole role, Instant sharedAt) {
    }

    /** 이 지도를 같이 보는 사람. 주인은 OWNER. 공유받은 사람끼리도 서로의 닉네임·색은 볼 수 있지만 사용자명은 보이지 않는다. */
    public record MemberView(UUID userId, String nickname, String color, String role) {
    }
}
