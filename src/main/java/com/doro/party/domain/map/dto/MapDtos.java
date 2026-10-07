package com.doro.party.domain.map.dto;

import com.doro.party.domain.map.entity.PartyMap;
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

    /** {@code mine}: 내가 만든 지도인지. 공유받은 지도의 권한(viewer/editor)은 공유 기능(M3)에서 추가한다. */
    public record MapResponse(
            UUID id,
            String name,
            String description,
            UUID ownerId,
            boolean mine,
            long pinCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static MapResponse from(PartyMap map, UUID viewerId, long pinCount) {
            return new MapResponse(map.getId(), map.getName(), map.getDescription(), map.getOwnerId(),
                    map.isOwnedBy(viewerId), pinCount, map.getCreatedAt(), map.getUpdatedAt());
        }
    }
}
