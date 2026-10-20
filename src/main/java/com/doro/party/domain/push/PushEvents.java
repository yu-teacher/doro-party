package com.doro.party.domain.push;

import java.util.UUID;

/**
 * 푸시를 보내야 하는 일들. 서비스는 DB 변경과 같은 트랜잭션에서 이 이벤트를 발행하고, 커밋된 뒤에만(그리고 요청 스레드 밖에서)
 * {@link PushDispatcher} 가 받아 보낸다. 롤백된 변경은 알림이 가지 않고, 푸시 서비스가 느려도 사용자의 요청은 늦어지지 않는다.
 */
public final class PushEvents {

    private PushEvents() {
    }

    /**
     * 핀에 댓글이 달렸다. 맨 위 댓글이면 받는 사람은 핀을 꽂은 사람과 그 핀에 댓글을 남긴 사람(쓴 사람 제외).
     * 답글이면(rootCommentId 가 있다) 답한 상대, 그 스레드에 글을 남긴 사람들, 핀을 꽂은 사람이 받는다.
     */
    public record CommentPosted(UUID mapId, UUID pinId, String pinName, UUID pinCreatorId, UUID authorId, String authorNickname, String body,
                                UUID commentId, UUID rootCommentId, UUID replyToUserId) {
    }

    public record FriendRequested(UUID toUserId, String fromNickname) {
    }

    public record FriendAccepted(UUID toUserId, String byNickname) {
    }

    public record MapShared(UUID toUserId, UUID mapId, String mapName, String ownerNickname) {
    }
}
