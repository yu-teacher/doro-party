package com.doro.party.domain.pin.comment;

import com.doro.party.domain.user.entity.PartyUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PinCommentDtos {

    public static final int BODY_MAX = 500;

    private PinCommentDtos() {
    }

    public record CommentRequest(@NotBlank @Size(max = BODY_MAX) String body) {
    }

    /** 클라이언트가 화면에 본 마지막 댓글의 시각. 그 시각까지를 읽은 것으로 표시한다. */
    public record ReadRequest(@NotNull Instant upTo) {
    }

    public record CommentResponse(UUID id, UUID pinId, UUID userId, String authorNickname, String authorColor, String body, Instant createdAt, Instant editedAt) {
        public static CommentResponse from(PinComment comment, PartyUser author) {
            return new CommentResponse(comment.getId(), comment.getPinId(), comment.getUserId(), author.getNickname(), author.getColor(),
                    comment.getBody(), comment.getCreatedAt(), comment.getEditedAt());
        }
    }

    public record UnreadPin(UUID mapId, UUID pinId, String pinName, long unread) {
    }
}
