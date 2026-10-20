package com.doro.party.domain.push;

import com.doro.party.domain.pin.comment.PinCommentRepository;
import com.doro.party.domain.push.PushEvents.CommentPosted;
import com.doro.party.domain.push.PushEvents.FriendAccepted;
import com.doro.party.domain.push.PushEvents.FriendRequested;
import com.doro.party.domain.push.PushEvents.MapShared;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 커밋된 변경에 대한 푸시를 받을 사람에게 보낸다. 요청 스레드가 아니라 전용 스레드 풀에서 실행되고, 어떤 오류도 호출한 요청에는 영향을 주지 않는다
 * (알림은 부가 기능이다: 못 보내도 댓글·친구 요청은 이미 저장돼 있다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushSender sender;
    private final PushPreferencesRepository preferences;
    private final PinCommentRepository comments;
    private final DoroGuardClient guard;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentPosted(CommentPosted event) {
        if (!sender.enabled()) {
            return;
        }
        run("comment", () -> {
            if (event.rootCommentId() == null) {
                notifyNewComment(event);
            } else {
                notifyReply(event);
            }
        });
    }

    /** 맨 위 댓글: 핀을 꽂은 사람과 그 핀에 댓글을 남긴 사람. */
    private void notifyNewComment(CommentPosted event) {
        Set<UUID> recipients = new LinkedHashSet<>(comments.findAuthorIdsOfPin(event.pinId()));
        recipients.add(event.pinCreatorId());
        recipients.remove(event.authorId());
        PushMessage message = new PushMessage(
                event.pinName(),
                event.authorNickname() + ": " + event.body(),
                commentUrl(event, event.commentId()),
                "comment:" + event.pinId());
        for (UUID recipient : recipients) {
            // 지금도 이 지도를 볼 수 있는 사람에게만 보낸다(공유가 풀린 사람에게 핀 이름과 댓글이 새지 않게)
            deliver(recipient, message, prefs -> prefs.comments(), canView(event.mapId(), recipient));
        }
    }

    /**
     * 답글: 답한 상대에게는 "내 댓글에 답글" 로 알리고, 그 스레드에 글을 남긴 사람들과 핀을 꽂은 사람에게는 일반 알림으로 알린다.
     * 다른 스레드에만 참여한 사람에게는 보내지 않는다(모든 답글이 모두에게 가면 소음이 된다). 같은 스레드의 알림은 하나로 합쳐진다.
     */
    private void notifyReply(CommentPosted event) {
        Set<UUID> others = new LinkedHashSet<>(comments.findAuthorIdsOfThread(event.rootCommentId()));
        others.add(event.pinCreatorId());
        UUID direct = event.replyToUserId();
        others.remove(event.authorId());
        others.remove(direct);
        String url = commentUrl(event, event.commentId());
        String tag = "reply:" + event.rootCommentId();
        if (direct != null && !direct.equals(event.authorId())) {
            PushMessage message = new PushMessage(event.pinName(), event.authorNickname() + "님이 내 댓글에 답글: " + event.body(), url, tag);
            deliver(direct, message, prefs -> prefs.comments(), canView(event.mapId(), direct));
        }
        PushMessage message = new PushMessage(event.pinName(), event.authorNickname() + "(답글): " + event.body(), url, tag);
        for (UUID recipient : others) {
            deliver(recipient, message, prefs -> prefs.comments(), canView(event.mapId(), recipient));
        }
    }

    /** 알림을 누르면 그 핀을 열고 해당 댓글로 이동한다. */
    private static String commentUrl(CommentPosted event, UUID commentId) {
        return "./?map=" + event.mapId() + "&pin=" + event.pinId() + "&comment=" + commentId;
    }

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendRequested(FriendRequested event) {
        run("friend-request", () -> deliver(event.toUserId(),
                new PushMessage("친구 요청", event.fromNickname() + "님이 친구 요청을 보냈어요", "./friends", "friend-request:" + event.toUserId()),
                prefs -> prefs.friendRequests(), true));
    }

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendAccepted(FriendAccepted event) {
        run("friend-accepted", () -> deliver(event.toUserId(),
                new PushMessage("친구가 됐어요", event.byNickname() + "님이 친구 요청을 수락했어요", "./friends", "friend-accepted:" + event.toUserId()),
                prefs -> prefs.friendRequests(), true));
    }

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMapShared(MapShared event) {
        if (!sender.enabled()) {
            return;
        }
        run("share", () -> deliver(event.toUserId(),
                new PushMessage("지도가 공유됐어요", event.ownerNickname() + "님이 「" + event.mapName() + "」 지도를 공유했어요", "./?map=" + event.mapId(), "share:" + event.mapId()),
                prefs -> prefs.shares(), canView(event.mapId(), event.toUserId())));
    }

    private void deliver(UUID userId, PushMessage message, Predicate<PushDtos.PreferencesDto> wanted, boolean allowed) {
        if (!allowed) {
            return;
        }
        PushDtos.PreferencesDto prefs = preferences.findById(userId).map(PushDtos.PreferencesDto::from).orElseGet(PushDtos.PreferencesDto::defaults);
        if (wanted.test(prefs)) {
            sender.sendToUser(userId, message, true);
        }
    }

    /** Guard 가 응답하지 못하면 보내지 않는다(권한을 확인하지 못한 채 내용을 보내지 않는다). */
    private boolean canView(UUID mapId, UUID userId) {
        try {
            return guard.checkOrThrow(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.USER, userId.toString(), null);
        } catch (RuntimeException e) {
            log.warn("Push skipped: permission check failed, mapId={}, userId={}", mapId, userId, e);
            return false;
        }
    }

    private void run(String kind, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Push dispatch failed: kind={}", kind, e);
        }
    }
}
