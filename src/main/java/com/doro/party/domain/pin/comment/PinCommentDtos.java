package com.doro.party.domain.pin.comment;

import com.doro.party.domain.user.entity.PartyUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PinCommentDtos {

    public static final int BODY_MAX = 500;
    /** 지워진 댓글의 작성자 색(작성자 정보는 숨긴다) */
    static final String DELETED_COLOR = "#64748B";

    private PinCommentDtos() {
    }

    /** 댓글 고치기. */
    public record CommentRequest(@NotBlank @Size(max = BODY_MAX) String body) {
    }

    /**
     * 댓글 남기기. parentId 가 있으면 그 댓글에 대한 답글이다(맨 위 댓글이든 답글이든 그 핀의 댓글이면 된다).
     * 서버가 맨 위 댓글에 붙이고, 누구에게 답한 글인지를 기록한다.
     */
    public record CreateCommentRequest(@NotBlank @Size(max = BODY_MAX) String body, UUID parentId) {
    }

    /** 클라이언트가 화면에 본 마지막 댓글의 시각. 그 시각까지를 읽은 것으로 표시한다. */
    public record ReadRequest(@NotNull Instant upTo) {
    }

    /**
     * 댓글 하나. 지워진 댓글(deleted)은 답글이 달려 있어 남겨 둔 것이라 내용과 작성자를 숨긴다.
     * parentId 가 있으면 답글이고, replyToNickname 은 누구에게 답한 글인지다.
     */
    public record CommentResponse(UUID id, UUID pinId, UUID userId, String authorNickname, String authorColor, String body, Instant createdAt, Instant editedAt,
                                  UUID parentId, String replyToNickname, boolean deleted) {
        public static CommentResponse from(PinComment comment, PartyUser author, PartyUser replyTo) {
            if (comment.isDeleted()) {
                return new CommentResponse(comment.getId(), comment.getPinId(), null, "", DELETED_COLOR, "", comment.getCreatedAt(), null, comment.getParentId(), null, true);
            }
            return new CommentResponse(comment.getId(), comment.getPinId(), comment.getUserId(), author.getNickname(), author.getColor(), comment.getBody(),
                    comment.getCreatedAt(), comment.getEditedAt(), comment.getParentId(), replyTo == null ? null : replyTo.getNickname(), false);
        }
    }

    public record UnreadPin(UUID mapId, UUID pinId, String pinName, long unread) {
    }
}
