package com.doro.party.domain.share;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.friend.Friendship;
import com.doro.party.domain.friend.FriendshipRepository;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.share.ShareDtos.MemberView;
import com.doro.party.domain.share.ShareDtos.ShareView;
import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지도를 친구에게 공유한다. DB 의 공유 행이 원본이고 Guard 튜플은 그에 맞춰 쓰고 지운다(GuardTuples 의 원칙:
 * 쓰기는 트랜잭션 안에서 실패하면 함께 되돌리고, 삭제는 커밋된 뒤에).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final MapUserShareRepository shares;
    private final PartyMapRepository maps;
    private final PartyUserRepository users;
    private final FriendshipRepository friendships;
    private final GuardTuples guardTuples;
    private final PartyLimits limits;

    /** 이 지도를 공유받은 사람 목록(주인용). */
    @Transactional(readOnly = true)
    public List<ShareView> list(UUID mapId) {
        List<MapUserShare> rows = shares.findByMapId(mapId);
        Map<UUID, PartyUser> people = usersById(rows.stream().map(MapUserShare::userId).toList());
        return rows.stream().map(row -> new ShareView(UserSummary.from(people.get(row.userId())), row.getRole(), row.getCreatedAt())).toList();
    }

    /** 이 지도를 같이 보는 사람(주인 + 공유받은 사람). 지도를 볼 수 있는 사람이면 누구나 본다. */
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID mapId) {
        PartyMap map = maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        List<MapUserShare> rows = shares.findByMapId(mapId);
        List<UUID> ids = new ArrayList<>();
        ids.add(map.getOwnerId());
        rows.forEach(row -> ids.add(row.userId()));
        Map<UUID, PartyUser> people = usersById(ids);
        List<MemberView> members = new ArrayList<>();
        members.add(member(people.get(map.getOwnerId()), "OWNER"));
        rows.forEach(row -> members.add(member(people.get(row.userId()), row.getRole().name())));
        return members;
    }

    /** 친구에게 공유하거나 권한을 바꾼다. 친구가 아니면 거절한다. 같은 요청을 반복해도 안전하다. */
    @Transactional
    public ShareView share(UUID mapId, DoroUser owner, UUID targetId, ShareRole role) {
        // 같은 지도에 대한 동시 공유 요청을 직렬화한다(개수 상한과 역할 변경이 엇갈리지 않게)
        PartyMap map = maps.findByIdForUpdate(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        if (targetId.equals(map.getOwnerId())) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "지도 주인에게는 공유할 수 없습니다.");
        }
        PartyUser target = users.findById(targetId).orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        UUID[] pair = Friendship.orderedPair(map.getOwnerId(), targetId);
        if (!friendships.areFriends(pair[0], pair[1])) {
            throw new PartyException(ErrorCode.NOT_FRIENDS, "친구에게만 지도를 공유할 수 있습니다.");
        }

        MapUserShare.Key key = MapUserShare.Key.of(mapId, targetId);
        MapUserShare existing = shares.findById(key).orElse(null);
        MapUserShare saved;
        if (existing == null) {
            if (shares.countByMapId(mapId) >= limits.maxSharesPerMap()) {
                throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "지도 하나는 최대 " + limits.maxSharesPerMap() + "명에게 공유할 수 있습니다.");
            }
            saved = shares.saveAndFlush(new MapUserShare(mapId, targetId, role));
            guardTuples.write(PartyGuard.MAP, mapId.toString(), role.guardRelation(), PartyGuard.USER, targetId.toString());
            log.info("Map shared: mapId={}, with={}, role={}", mapId, targetId, role);
        } else if (existing.getRole() != role) {
            ShareRole previous = existing.getRole();
            existing.changeRole(role);
            saved = existing;
            // 새 권한을 먼저 쓰고 이전 권한은 커밋된 뒤에 지운다(잠깐이라도 권한이 사라지는 일이 없게)
            guardTuples.write(PartyGuard.MAP, mapId.toString(), role.guardRelation(), PartyGuard.USER, targetId.toString());
            guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), previous.guardRelation(), PartyGuard.USER, targetId.toString());
            log.info("Map share role changed: mapId={}, with={}, {} -> {}", mapId, targetId, previous, role);
        } else {
            saved = existing;
        }
        return new ShareView(UserSummary.from(target), saved.getRole(), saved.getCreatedAt());
    }

    /** 공유를 끊는다. 지도 주인이거나, 공유받은 본인(나가기)만 할 수 있다. */
    @Transactional
    public void revoke(UUID mapId, DoroUser actor, UUID targetId) {
        PartyMap map = maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        if (!map.isOwnedBy(actor.userId()) && !actor.userId().equals(targetId)) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
        MapUserShare share = shares.findById(MapUserShare.Key.of(mapId, targetId))
                .orElseThrow(() -> new PartyException(ErrorCode.SHARE_NOT_FOUND));
        removeShare(share);
        log.info("Map share revoked: mapId={}, user={}, by={}", mapId, targetId, actor.userId());
    }

    /** 친구를 끊을 때: 내가 그 사람에게 공유한 지도를 모두 회수한다(그 사람이 나에게 준 공유는 그대로). */
    @Transactional
    public int revokeGiven(UUID ownerId, UUID recipientId) {
        List<MapUserShare> given = shares.findGivenTo(ownerId, recipientId);
        given.forEach(this::removeShare);
        if (!given.isEmpty()) {
            log.info("Shares revoked on unfriend: owner={}, recipient={}, count={}", ownerId, recipientId, given.size());
        }
        return given.size();
    }

    /** 지도를 지울 때: 공유받은 사람들의 Guard 권한을 커밋 뒤에 지운다(공유 행은 지도와 함께 DB 가 지운다). */
    public void releaseGuardForMap(UUID mapId) {
        for (MapUserShare share : shares.findByMapId(mapId)) {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), share.getRole().guardRelation(), PartyGuard.USER, share.userId().toString());
        }
    }

    private void removeShare(MapUserShare share) {
        shares.delete(share);
        guardTuples.deleteAfterCommit(PartyGuard.MAP, share.mapId().toString(), share.getRole().guardRelation(), PartyGuard.USER, share.userId().toString());
    }

    private Map<UUID, PartyUser> usersById(List<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(PartyUser::getId, Function.identity()));
    }

    private static MemberView member(PartyUser user, String role) {
        return new MemberView(user.getId(), user.getNickname(), user.getColor(), role);
    }
}
