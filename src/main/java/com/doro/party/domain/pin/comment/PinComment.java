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

    /** 답글이 속한 맨 위 댓글. 맨 위 댓글이면 null (답글은 한 단계만 있다). */
    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    /** 이 답글이 향한 사람(답한 댓글의 작성자). 맨 위 댓글이면 null. */
    @Column(name = "reply_to_user_id", updatable = false)
    private UUID replyToUserId;

    /** 답글이 달린 댓글을 지우면 내용만 비우고 이 시각을 남긴다(답글은 그대로 보인다). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder
    private PinComment(UUID pinId, UUID userId, String body, Instant createdAt, UUID parentId, UUID replyToUserId) {
        this.pinId = pinId;
        this.userId = userId;
        this.body = body;
        this.createdAt = createdAt;
        this.parentId = parentId;
        this.replyToUserId = replyToUserId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public void edit(String body, Instant now) {
        this.body = body;
        this.editedAt = now;
    }

    /** 내용을 비우고 삭제 표시만 남긴다. */
    public void markDeleted(Instant now) {
        this.body = "";
        this.deletedAt = now;
    }

}
