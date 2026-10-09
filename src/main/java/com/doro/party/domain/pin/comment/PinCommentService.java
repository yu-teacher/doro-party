package com.doro.party.domain.pin.comment;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.pin.comment.PinCommentDtos.CommentRequest;
import com.doro.party.domain.pin.comment.PinCommentDtos.CommentResponse;
import com.doro.party.domain.pin.comment.PinCommentDtos.UnreadPin;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.service.PinAccess;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 핀 댓글. 지도를 볼 수 있는 사람(viewer 이상)이면 누구나 쓴다 — 접근은 컨트롤러의 {@code @DoroGuard} 가 판정하고,
 * 여기서는 핀이 그 지도의 것인지(IDOR)와 댓글 단위 규칙(쓴 사람만 고치고, 쓴 사람·핀을 꽂은 사람·지도 주인이 지운다)을 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PinCommentService {

    /** "새 댓글" 목록 한 번에 돌려주는 핀의 최대 개수 */
    static final int UNREAD_PIN_LIMIT = 50;

    private final PinCommentRepository comments;
    private final PinAccess access;
    private final PartyUserService userService;
    private final PartyUserRepository users;
    private final PartyLimits limits;
    private final DoroGuardClient guard;
    private final Clock clock;

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    @Transactional
    public CommentResponse add(UUID mapId, UUID pinId, DoroUser doroUser, CommentRequest request) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        access.requirePinForUpdate(mapId, pinId);
        if (comments.countByPinId(pinId) >= limits.maxCommentsPerPin()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "댓글은 핀마다 최대 " + limits.maxCommentsPerPin() + "개까지 남길 수 있습니다.");
        }
        PinComment saved = comments.save(PinComment.builder().pinId(pinId).userId(user.getId()).body(request.body().strip()).createdAt(now()).build());
        // 내가 쓴 댓글까지는 읽은 것이다(이후 남이 쓴 댓글만 "새 댓글" 이 된다)
        comments.markRead(pinId, user.getId(), saved.getCreatedAt());
        log.info("Comment added: commentId={}, pinId={}, userId={}", saved.getId(), pinId, user.getId());
        return CommentResponse.from(saved, user);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID mapId, UUID pinId) {
        access.requirePin(mapId, pinId);
        List<PinComment> found = comments.findAllByPinIdOrderByCreatedAtAscIdAsc(pinId);
        Map<UUID, PartyUser> authors = users.findAllById(found.stream().map(PinComment::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, u -> u));
        return found.stream().map(comment -> CommentResponse.from(comment, authors.get(comment.getUserId()))).toList();
    }

    @Transactional
    public CommentResponse edit(UUID mapId, UUID pinId, UUID commentId, DoroUser doroUser, CommentRequest request) {
        access.requirePin(mapId, pinId);
        PinComment comment = comments.findByIdAndPinId(commentId, pinId).orElseThrow(() -> new PartyException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(doroUser.userId())) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
        comment.edit(request.body().strip(), now());
        PartyUser author = userService.getOrCreateUser(doroUser);
        return CommentResponse.from(comment, author);
    }

    /** 쓴 사람, 그 핀을 꽂은 사람, 지도 주인이 지울 수 있다. */
    @Transactional
    public void delete(UUID mapId, UUID pinId, UUID commentId, DoroUser doroUser) {
        Pin pin = access.requirePin(mapId, pinId);
        PinComment comment = comments.findByIdAndPinId(commentId, pinId).orElseThrow(() -> new PartyException(ErrorCode.COMMENT_NOT_FOUND));
        UUID me = doroUser.userId();
        if (!comment.getUserId().equals(me) && !pin.getCreatedBy().equals(me) && !access.isMapOwner(mapId, me)) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
        comments.delete(comment);
    }

    /** 화면에 본 마지막 댓글 시각까지를 읽은 것으로 표시한다. */
    @Transactional
    public void markRead(UUID mapId, UUID pinId, DoroUser doroUser, Instant upTo) {
        access.requirePin(mapId, pinId);
        comments.markRead(pinId, doroUser.userId(), upTo.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * 내가 만든 핀이거나 내가 댓글을 남긴 핀의 새 댓글. 지금 볼 수 없는 지도의 핀은 제외한다.
     * Guard 가 응답하지 못하면(checkOrThrow) 비어 있는 것으로 오해하지 않고 503 으로 전파한다.
     */
    @Transactional(readOnly = true)
    public List<UnreadPin> unread(DoroUser doroUser) {
        UUID me = doroUser.userId();
        Map<UUID, Boolean> viewable = new HashMap<>();
        return comments.findUnread(me, UNREAD_PIN_LIMIT).stream()
                .filter(row -> viewable.computeIfAbsent(row.getMapId(),
                        mapId -> guard.checkOrThrow(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.USER, me.toString(), null)))
                .map(row -> new UnreadPin(row.getMapId(), row.getPinId(), row.getPinName(), row.getUnread()))
                .toList();
    }
}
