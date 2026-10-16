package com.doro.party.domain.user.sync;

import com.doro.party.domain.friend.FriendInviteRepository;
import com.doro.party.domain.friend.Friendship;
import com.doro.party.domain.friend.FriendshipRepository;
import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.domain.group.GroupMember;
import com.doro.party.domain.group.GroupMemberRepository;
import com.doro.party.domain.group.GroupRole;
import com.doro.party.domain.group.MapGroupShare;
import com.doro.party.domain.group.MapGroupShareRepository;
import com.doro.party.domain.group.PartyGroup;
import com.doro.party.domain.group.PartyGroupRepository;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.comment.PinCommentRepository;
import com.doro.party.domain.pin.note.PinPrivateNoteRepository;
import com.doro.party.domain.pin.photo.PinPhotoRepository;
import com.doro.party.domain.share.FriendAccess;
import com.doro.party.domain.share.MapUserShare;
import com.doro.party.domain.share.MapUserShareRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.infra.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Doro 에서 영구 탈퇴한 사용자의 도로 파티 데이터를 정리한다. 지도·핀·댓글처럼 다른 사람과 함께 쓰는 기록은 남기고 작성자만
 * '탈퇴한 사용자' 로 보이게 하며, 본인만 볼 수 있던 데이터(사적 메모·초대 링크·친구 관계·읽음 표시·올린 사진)는 지운다.
 * 아무도 볼 수 없게 되는 지도(공유받은 사람도, 다른 사람의 기록도 없는 지도)는 내용과 함께 지운다.
 *
 * <p>한 번의 DB 트랜잭션 안에서 DB 를 고치고, 커밋 전에 사진 파일과 Guard 튜플을 지운다. 파일·튜플 삭제가 실패하면 예외가 나서 DB 도
 * 되돌아가므로(deleted_at 이 남지 않는다) 다음 주기에 같은 사용자부터 처음부터 다시 처리한다. 모든 단계는 반복해도 안전하다
 * (이미 없는 파일·튜플을 지우는 것은 오류가 아니다). 이미 처리한 사용자에게 다시 호출하면 아무것도 바꾸지 않는다(멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedAccountAnonymizer {

    static final String PLACEHOLDER_NICKNAME = "탈퇴한 사용자";

    private final PartyUserRepository users;
    private final AuthSessionRepository authSessions;
    private final FriendshipRepository friendships;
    private final FriendInviteRepository friendInvites;
    private final PartyGroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final MapGroupShareRepository groupShares;
    private final MapUserShareRepository userShares;
    private final PartyMapRepository maps;
    private final PinPhotoRepository photos;
    private final PinPrivateNoteRepository privateNotes;
    private final PinCommentRepository comments;
    private final ObjectStorage storage;
    private final GuardTuples guardTuples;

    /** 지울 Guard 튜플 하나. */
    private record Tuple(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        static Tuple user(String namespace, String objectId, String relation, UUID userId) {
            return new Tuple(namespace, objectId, relation, PartyGuard.USER, userId.toString(), null);
        }
    }

    /** @return 이번 호출에서 익명화했으면 true, 도로 파티 사용자가 아니거나 이미 처리됐으면 false */
    @Transactional
    public boolean anonymize(UUID userId, Instant now) {
        PartyUser user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            return false;
        }

        List<Tuple> revoke = new ArrayList<>();
        List<Tuple> grant = new ArrayList<>();
        Set<String> photoKeys = new LinkedHashSet<>(photos.objectKeysUploadedBy(userId));

        // 로그인 세션(embedded 모드의 암호화된 토큰). gateway 모드에서는 이 서비스에 세션이 없어 0건이다.
        authSessions.deleteAllByUserId(userId);

        // 본인에게만 보이던 것: 사적 메모, 댓글 읽음 표시, 친구 초대 링크
        privateNotes.deleteAllByUserId(userId);
        comments.deleteReadsByUserId(userId);
        friendInvites.deleteByOwnerId(userId);

        removeFriendships(userId, revoke);
        removeReceivedShares(userId, revoke);
        // 모임을 먼저 정리해야 "다른 멤버가 있는 모임에 공유된 지도" 인지 정확히 알 수 있다.
        leaveGroups(userId, revoke, grant);
        handleOwnedMaps(userId, revoke, photoKeys);

        // 이 사용자가 올린 사진 기록(남는 지도에 올린 것 포함). 파일은 아래에서 지우고, 기록은 같은 트랜잭션에서 지운다.
        photos.deleteAllUploadedBy(userId);

        user = users.findById(userId).orElseThrow();
        String newUsername = "deleted-" + userId.toString().replace("-", "");
        user.anonymize(newUsername, PLACEHOLDER_NICKNAME, now);
        users.flush();

        // 커밋 전에 파일과 튜플을 지운다. 하나라도 실패하면 예외로 트랜잭션 전체가 되돌아가고 다음 주기에 다시 처리한다.
        for (String key : photoKeys) {
            storage.delete(key);
        }
        for (Tuple t : grant) {
            guardTuples.write(t.namespace(), t.objectId(), t.relation(), t.subjectNamespace(), t.subjectId(), t.subjectRelation());
        }
        for (Tuple t : revoke) {
            guardTuples.deleteOrThrow(t.namespace(), t.objectId(), t.relation(), t.subjectNamespace(), t.subjectId(), t.subjectRelation());
        }

        log.info("Party data anonymized after Doro account deletion: userId={}, photos={}", userId, photoKeys.size());
        return true;
    }

    /** 친구 관계·요청을 모두 지우고, 서로의 Guard 친구 목록에서도 뺀다. */
    private void removeFriendships(UUID userId, List<Tuple> revoke) {
        for (Friendship friendship : friendships.findAllInvolving(userId)) {
            if (friendship.isPending()) {
                continue;
            }
            UUID other = friendship.otherThan(userId);
            revoke.add(Tuple.user(PartyGuard.FRIENDS, userId.toString(), PartyGuard.FRIEND, other));
            revoke.add(Tuple.user(PartyGuard.FRIENDS, other.toString(), PartyGuard.FRIEND, userId));
        }
        friendships.deleteAllInvolving(userId);
    }

    /** 다른 사람의 지도를 공유받은 것: 공유 행과 권한을 거둔다. */
    private void removeReceivedShares(UUID userId, List<Tuple> revoke) {
        for (MapUserShare share : userShares.findByUserId(userId)) {
            revoke.add(Tuple.user(PartyGuard.MAP, share.mapId().toString(), share.getRole().guardRelation(), userId));
            userShares.delete(share);
        }
        userShares.flush();
    }

    /**
     * 속한 모임에서 나간다. 방장이었고 다른 멤버가 있으면 가장 먼저 들어온 멤버에게 방장을 넘기고(모임과 그 지도 공유가 계속되도록),
     * 혼자였으면 모임을 지운다. 이 사용자가 모임에 공유해 둔 지도는 여기서 거두지 않는다: 지도를 남길지는 아래에서 지도별로 정한다.
     */
    private void leaveGroups(UUID userId, List<Tuple> revoke, List<Tuple> grant) {
        for (GroupMember mine : groupMembers.findByUserId(userId)) {
            UUID groupId = mine.groupId();
            PartyGroup group = groups.findByIdForUpdate(groupId).orElse(null);
            if (group == null) {
                continue;
            }
            List<GroupMember> others = groupMembers.findByGroupId(groupId).stream()
                    .filter(member -> !member.userId().equals(userId)).toList();
            revoke.add(Tuple.user(PartyGuard.GROUP, groupId.toString(), mine.getRole().guardRelation(), userId));

            if (mine.getRole() == GroupRole.OWNER && others.isEmpty()) {
                for (MapGroupShare share : groupShares.findByGroupId(groupId)) {
                    revoke.add(new Tuple(PartyGuard.MAP, share.mapId().toString(), PartyGuard.VIEWER, PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER));
                }
                // 멤버십·초대 링크·모임에 공유한 지도 기록은 DB 가 함께 지운다
                groups.delete(group);
                groups.flush();
                continue;
            }
            groupMembers.delete(mine);
            groupMembers.flush();
            if (mine.getRole() == GroupRole.OWNER) {
                // others 는 방장 우선·들어온 순으로 정렬돼 있고 방장은 이 사용자이므로 첫 사람이 가장 먼저 들어온 멤버다
                GroupMember next = others.getFirst();
                next.changeRole(GroupRole.OWNER);
                group.changeOwner(next.userId());
                groupMembers.flush();
                // owner 관계가 member 를 포함하므로 새 방장의 member 튜플은 owner 로 바꾼다
                grant.add(Tuple.user(PartyGuard.GROUP, groupId.toString(), PartyGuard.OWNER, next.userId()));
                revoke.add(Tuple.user(PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER, next.userId()));
            }
        }
    }

    /**
     * 이 사용자가 만든 지도. 공유받은 사람이 있거나, 다른 사람이 남긴 기록이 있거나, 멤버가 남아 있는 모임에 공유돼 있으면 남긴다
     * (주인은 탈퇴한 사용자로 보이고 owner 튜플만 거둔다. 남은 사람들은 자기 viewer/editor 권한을 그대로 갖는다). 그렇지 않으면 아무도
     * 볼 수 없으므로 핀·사진과 함께 지운다. 남기는 지도의 '친구 전체 공개' 는 친구 관계가 사라지므로 함께 끈다.
     */
    private void handleOwnedMaps(UUID userId, List<Tuple> revoke, Set<String> photoKeys) {
        for (PartyMap map : maps.findAllByOwnerIdOrderByCreatedAtDesc(userId)) {
            UUID mapId = map.getId();
            List<MapUserShare> shared = userShares.findByMapId(mapId);
            List<MapGroupShare> sharedGroups = groupShares.findByMapId(mapId);
            boolean keep = !shared.isEmpty()
                    || sharedGroups.stream().anyMatch(share -> groupMembers.countByGroupId(share.groupId()) > 0)
                    || maps.hasContentFromOthers(mapId, userId);

            revoke.add(Tuple.user(PartyGuard.MAP, mapId.toString(), PartyGuard.OWNER, userId));
            if (map.getFriendAccess().isPublic()) {
                revoke.add(new Tuple(PartyGuard.MAP, mapId.toString(), map.getFriendAccess().guardRelation(), PartyGuard.FRIENDS, userId.toString(), PartyGuard.FRIEND));
            }
            if (keep) {
                map.changeFriendAccess(FriendAccess.NONE);
                continue;
            }
            // 아무도 볼 수 없는 지도: 공유 튜플과 사진 파일까지 정리하고 지도를 지운다(핀·기록은 DB 가 함께 지운다)
            for (MapUserShare share : shared) {
                revoke.add(Tuple.user(PartyGuard.MAP, mapId.toString(), share.getRole().guardRelation(), share.userId()));
            }
            for (MapGroupShare share : sharedGroups) {
                revoke.add(new Tuple(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.GROUP, share.groupId().toString(), PartyGuard.MEMBER));
            }
            photoKeys.addAll(photos.objectKeysOfMap(mapId));
            maps.delete(map);
        }
        maps.flush();
    }
}
