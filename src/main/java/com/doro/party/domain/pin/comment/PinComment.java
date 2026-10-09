package com.doro.party.domain.pin.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 핀에 남긴 댓글 한 개. 시각은 DB 의 정밀도(마이크로초)에 맞춰 서비스가 정해 읽음 표시와 정확히 비교된다. */
@Entity
@Table(name = "pin_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pin_id", nullable = false, updatable = false)
    private UUID pinId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Builder
    private PinComment(UUID pinId, UUID userId, String body, Instant createdAt) {
        this.pinId = pinId;
        this.userId = userId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public void edit(String body, Instant now) {
        this.body = body;
        this.editedAt = now;
    }
}
