package com.doro.party.domain.map.entity;

import com.doro.party.domain.share.FriendAccess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 사람이 만드는 주제별 지도. 공유와 접근 권한은 Guard(party_map) 튜플이 관리한다. */
@DynamicUpdate
@Entity
@Table(name = "party_maps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** 친구 전체에게 공개하는 범위(NONE 이면 비공개). Guard 튜플은 이 값에 맞춰 쓰인다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "friend_access", nullable = false, length = 10)
    private FriendAccess friendAccess = FriendAccess.NONE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private PartyMap(UUID ownerId, String name, String description) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeFriendAccess(FriendAccess friendAccess) {
        this.friendAccess = friendAccess;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }
}
