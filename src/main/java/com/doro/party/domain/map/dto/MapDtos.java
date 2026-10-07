package com.doro.party.domain.map.dto;

import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.user.entity.PartyUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class MapDtos {

    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;

    private MapDtos() {
    }

    public record MapRequest(
            @NotBlank @Size(max = NAME_MAX) String name,
            @Size(max = DESCRIPTION_MAX) String description
    ) {
    }

    /** 이 지도에서 내 권한: 주인(OWNER) / 핀을 꽂을 수 있는 편집자(EDITOR) / 보기만 하는 열람자(VIEWER). */
    public enum MapRole {
        OWNER,
        EDITOR,
        VIEWER
    }

    /** {@code mine}: 내가 만든 지도인지(role 이 OWNER 인지와 같다). */
    public record MapResponse(
            UUID id,
            String name,
            String description,
            UUID ownerId,
            String ownerNickname,
            String ownerColor,
            boolean mine,
            MapRole role,
            long pinCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static MapResponse from(PartyMap map, MapRole role, PartyUser owner, long pinCount) {
            return new MapResponse(map.getId(), map.getName(), map.getDescription(), map.getOwnerId(),
                    owner.getNickname(), owner.getColor(), role == MapRole.OWNER, role, pinCount, map.getCreatedAt(), map.getUpdatedAt());
        }
    }
}
