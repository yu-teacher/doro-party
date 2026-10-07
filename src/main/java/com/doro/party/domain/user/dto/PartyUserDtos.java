package com.doro.party.domain.user.dto;

import com.doro.party.domain.user.entity.PartyUser;

import java.util.UUID;

public final class PartyUserDtos {

    private PartyUserDtos() {
    }

    public record UserProfileResponse(UUID id, String username, String nickname, String color) {

        public static UserProfileResponse from(PartyUser user) {
            return new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname(), user.getColor());
        }
    }
}
