package com.doro.party.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** 지도를 모임에 공유한 기록: 모임의 모든 멤버가 그 지도의 viewer 가 된다. 지도 주인은 그대로이다. */
@Entity
@Table(name = "map_group_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapGroupShare {

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "map_id", nullable = false)
        private UUID mapId;

        @Column(name = "group_id", nullable = false)
        private UUID groupId;

        public static Key of(UUID mapId, UUID groupId) {
            Key key = new Key();
            key.mapId = mapId;
            key.groupId = groupId;
            return key;
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "shared_by", nullable = false, updatable = false)
    private UUID sharedBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public MapGroupShare(UUID mapId, UUID groupId, UUID sharedBy) {
        this.id = Key.of(mapId, groupId);
        this.sharedBy = sharedBy;
    }

    public UUID mapId() {
        return id.getMapId();
    }

    public UUID groupId() {
        return id.getGroupId();
    }
}
