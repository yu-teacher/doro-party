package com.doro.party.domain.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 알림 종류별 켜기/끄기. 행이 없으면 모두 켜진 것이다. */
@Entity
@Table(name = "push_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean comments = true;

    @Column(name = "friend_requests", nullable = false)
    private boolean friendRequests = true;

    @Column(nullable = false)
    private boolean shares = true;

    public PushPreferences(UUID userId, boolean comments, boolean friendRequests, boolean shares) {
        this.userId = userId;
        this.comments = comments;
        this.friendRequests = friendRequests;
        this.shares = shares;
    }

    public void update(boolean comments, boolean friendRequests, boolean shares) {
        this.comments = comments;
        this.friendRequests = friendRequests;
        this.shares = shares;
    }
}
