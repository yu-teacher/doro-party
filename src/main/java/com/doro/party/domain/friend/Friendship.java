package com.doro.party.domain.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 두 사용자 사이의 친구 관계(또는 요청) 하나. 방향과 상관없이 같은 쌍이 두 번 생기지 않도록 두 ID 를 정렬해 저장한다.
 * 생성·상태 변경은 동시 요청에서도 안전하도록 저장소의 원자적 쿼리로만 한다(JPA save 를 쓰지 않는다).
 */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    public enum Status {
        PENDING,
        ACCEPTED
    }

    @Id
    private UUID id;

    @Column(name = "user_low_id", nullable = false, updatable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false, updatable = false)
    private UUID userHighId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public boolean involves(UUID userId) {
        return userLowId.equals(userId) || userHighId.equals(userId);
    }

    /** 이 관계에서 내 상대방. */
    public UUID otherThan(UUID userId) {
        return userLowId.equals(userId) ? userHighId : userLowId;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * 같은 쌍을 항상 같은 순서로 놓기 위한 정렬. DB 의 uuid 비교(바이트 순서)와 같아야 하므로 Java 의 UUID.compareTo(부호 있는 long 비교)가 아니라
     * 소문자 16진수 문자열 순서를 쓴다(고정 폭이라 문자열 순서가 바이트 순서와 같다).
     */
    public static UUID[] orderedPair(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? new UUID[]{a, b} : new UUID[]{b, a};
    }
}
