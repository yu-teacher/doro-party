package com.doro.party.domain.group;

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

@Entity
@Table(name = "party_group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "group_id", nullable = false)
        private UUID groupId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public static Key of(UUID groupId, UUID userId) {
            Key key = new Key();
            key.groupId = groupId;
            key.userId = userId;
            return key;
        }
    }

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupRole role;

    @Column(name = "joined_at", nullable = false, updatable = false, insertable = false)
    private Instant joinedAt;

    public GroupMember(UUID groupId, UUID userId, GroupRole role) {
        this.id = Key.of(groupId, userId);
        this.role = role;
    }

    public UUID groupId() {
        return id.getGroupId();
    }

    public UUID userId() {
        return id.getUserId();
    }

    public void changeRole(GroupRole role) {
        this.role = role;
    }
}
