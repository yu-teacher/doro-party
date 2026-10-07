package com.doro.party.domain.user.dto;

import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.UsernamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class PartyUserDtos {

    private PartyUserDtos() {
    }

    public static final int NICKNAME_MAX = 20;

    /** 내 프로필 수정 요청. 사용자명은 친구가 나를 찾는 이름이고, 닉네임은 친구에게 보이는 이름이다. */
    public record UpdateProfileRequest(
            @NotBlank @Size(max = NICKNAME_MAX) String nickname,
            @NotBlank @Size(min = UsernamePolicy.MIN_LENGTH, max = UsernamePolicy.MAX_LENGTH) String username
    ) {
    }

    /** 다른 사람에게 보여 주는 최소한의 정보. 이메일 같은 계정 정보는 없다. */
    public record UserSummary(UUID id, String username, String nickname, String color) {
        public static UserSummary from(PartyUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getNickname(), user.getColor());
        }
    }

    public record UserProfileResponse(UUID id, String username, String nickname, String color) {

        public static UserProfileResponse from(PartyUser user) {
            return new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname(), user.getColor());
        }
    }
}
