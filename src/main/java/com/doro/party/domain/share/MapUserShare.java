package com.doro.party.domain.share;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** 지도를 친구 한 명에게 공유한 기록. 접근 판정은 Guard 튜플이 하고, 이 행은 목록과 회수의 원본이다. */
@Entity
@Table(name = "map_user_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapUserShare {

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "map_id", nullable = false)
        private UUID mapId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public static Key of(UUID mapId, UUID userId) {
            Key key = new Key();
            key.mapId = mapId;
            key.userId = userId;
            return key;
        }
    }

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShareRole role;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public MapUserShare(UUID mapId, UUID userId, ShareRole role) {
        this.id = Key.of(mapId, userId);
        this.role = role;
    }

    public UUID mapId() {
        return id.getMapId();
    }

    public UUID userId() {
        return id.getUserId();
    }

    public void changeRole(ShareRole role) {
        this.role = role;
    }
}
