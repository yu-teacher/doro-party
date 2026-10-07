package com.doro.party.domain.group;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.friend.Friendship;
import com.doro.party.domain.friend.FriendshipRepository;
import com.doro.party.domain.group.GroupDtos.GroupDetail;
import com.doro.party.domain.group.GroupDtos.GroupMemberView;
import com.doro.party.domain.group.GroupDtos.GroupSummary;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모임. DB 의 멤버 행이 원본이고 Guard 의 {@code party_group} 튜플은 그에 맞춰 쓰고 지운다.
 * 모임의 멤버가 곧 그 모임에 공유된 지도의 viewer 이므로(party_map#viewer@party_group#member), 멤버십 변경만으로 접근이 따라 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final PartyGroupRepository groups;
    private final GroupMemberRepository members;
    private final MapGroupShareRepository groupShares;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final FriendshipRepository friendships;
    private final GuardTuples guardTuples;
    private final PartyLimits limits;

    @Transactional
    public GroupSummary create(DoroUser doroUser, String rawName) {
        PartyUser me = userService.getOrCreateUser(doroUser);
        // 같은 사용자의 동시 생성·가입이 모임 개수 상한을 함께 넘지 못하게 사용자 행으로 직렬화한다
        users.findByIdForUpdate(me.getId());
        requireRoomForGroup(me.getId());
        PartyGroup group = groups.saveAndFlush(new PartyGroup(rawName.strip(), me.getId()));
        GroupMember owner = members.saveAndFlush(new GroupMember(group.getId(), me.getId(), GroupRole.OWNER));
        guardTuples.write(PartyGuard.GROUP, group.getId().toString(), GroupRole.OWNER.guardRelation(), PartyGuard.USER, me.getId().toString());
        log.info("Group created: groupId={}, ownerId={}", group.getId(), me.getId());
        return new GroupSummary(group.getId(), group.getName(), owner.getRole(), 1, 0, me.getNickname(), group.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(DoroUser doroUser) {
        List<GroupMember> mine = members.findByUserId(doroUser.userId());
        if (mine.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = mine.stream().map(GroupMember::groupId).toList();
        Map<UUID, PartyGroup> byId = groups.findAllById(ids).stream().collect(Collectors.toMap(PartyGroup::getId, Function.identity()));
        Map<UUID, Long> memberCounts = members.countsByGroupIds(ids).stream().collect(Collectors.toMap(GroupMemberRepository.GroupCount::getGroupId, GroupMemberRepository.GroupCount::getCount));
        Map<UUID, Long> mapCounts = groupShares.countsByGroupIds(ids).stream().collect(Collectors.toMap(MapGroupShareRepository.GroupMapCount::getGroupId, MapGroupShareRepository.GroupMapCount::getCount));
        Map<UUID, PartyUser> owners = users.findAllById(byId.values().stream().map(PartyGroup::getOwnerId).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, Function.identity()));
        return mine.stream()
                .map(member -> {
                    PartyGroup group = byId.get(member.groupId());
                    return new GroupSummary(group.getId(), group.getName(), member.getRole(), memberCounts.getOrDefault(group.getId(), 0L).intValue(),
                            mapCounts.getOrDefault(group.getId(), 0L).intValue(), owners.get(group.getOwnerId()).getNickname(), group.getCreatedAt());
                })
                .sorted(java.util.Comparator.comparing(GroupSummary::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail detail(UUID groupId, DoroUser doroUser) {
        PartyGroup group = find(groupId);
        List<GroupMember> rows = members.findByGroupId(groupId);
        Map<UUID, PartyUser> people = users.findAllById(rows.stream().map(GroupMember::userId).toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, Function.identity()));
        GroupRole myRole = rows.stream().filter(row -> row.userId().equals(doroUser.userId())).map(GroupMember::getRole).findFirst()
                .orElseThrow(() -> new PartyException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        List<GroupMemberView> views = rows.stream()
                .map(row -> new GroupMemberView(row.userId(), people.get(row.userId()).getNickname(), people.get(row.userId()).getColor(), row.getRole(), row.getJoinedAt()))
                .toList();
        return new GroupDetail(group.getId(), group.getName(), myRole, views, groupShares.findByGroupId(groupId).size(), group.getCreatedAt());
    }

    @Transactional
    public void rename(UUID groupId, String rawName) {
        find(groupId).rename(rawName.strip());
    }

    /** 모임을 지운다. 멤버십과 모임에 공유된 지도 기록은 DB 가 함께 지우고, 그 Guard 튜플은 커밋된 뒤에 지운다. */
    @Transactional
    public void delete(UUID groupId) {
        PartyGroup group = find(groupId);
        for (GroupMember member : members.findByGroupId(groupId)) {
            guardTuples.deleteAfterCommit(PartyGuard.GROUP, groupId.toString(), member.getRole().guardRelation(), PartyGuard.USER, member.userId().toString());
        }
        for (MapGroupShare share : groupShares.findByGroupId(groupId)) {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, share.mapId().toString(), PartyGuard.VIEWER, PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER);
        }
        groups.delete(group);
        log.info("Group deleted: groupId={}", groupId);
    }

    /** 모임을 나간다. 방장은 나갈 수 없다(방장을 넘기거나 모임을 지워야 한다). */
    @Transactional
    public void leave(UUID groupId, DoroUser doroUser) {
        GroupMember me = members.findById(GroupMember.Key.of(groupId, doroUser.userId()))
                .orElseThrow(() -> new PartyException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (me.getRole() == GroupRole.OWNER) {
            throw new PartyException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        removeMembership(me);
        log.info("Left group: groupId={}, userId={}", groupId, doroUser.userId());
    }

    /** 방장이 멤버를 내보낸다. */
    @Transactional
    public void kick(UUID groupId, DoroUser owner, UUID targetId) {
        if (targetId.equals(owner.userId())) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "자기 자신은 내보낼 수 없습니다. 나가려면 '나가기'를 쓰세요.");
        }
        GroupMember target = members.findById(GroupMember.Key.of(groupId, targetId))
                .orElseThrow(() -> new PartyException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        removeMembership(target);
        log.info("Member removed from group: groupId={}, userId={}, by={}", groupId, targetId, owner.userId());
    }

    /** 방장을 다른 멤버에게 넘긴다. 넘긴 방장은 일반 멤버가 되어 나갈 수 있다. */
    @Transactional
    public void transferOwnership(UUID groupId, DoroUser owner, UUID newOwnerId) {
        PartyGroup group = groups.findByIdForUpdate(groupId).orElseThrow(() -> new PartyException(ErrorCode.GROUP_NOT_FOUND));
        if (newOwnerId.equals(owner.userId())) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "이미 방장입니다.");
        }
        GroupMember current = members.findById(GroupMember.Key.of(groupId, owner.userId()))
                .filter(member -> member.getRole() == GroupRole.OWNER)
                .orElseThrow(() -> new PartyException(ErrorCode.ACCESS_DENIED));
        GroupMember next = members.findById(GroupMember.Key.of(groupId, newOwnerId))
                .orElseThrow(() -> new PartyException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        // DB: 방장은 한 명이라는 유니크 제약을 지키려고 먼저 내려놓고 flush 한 뒤 올린다
        current.changeRole(GroupRole.MEMBER);
        members.saveAndFlush(current);
        next.changeRole(GroupRole.OWNER);
        members.saveAndFlush(next);
        group.changeOwner(newOwnerId);
        // Guard: 새 권한을 먼저 쓰고 이전 권한은 커밋된 뒤에 지운다
        guardTuples.write(PartyGuard.GROUP, groupId.toString(), PartyGuard.OWNER, PartyGuard.USER, newOwnerId.toString());
        guardTuples.write(PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER, PartyGuard.USER, owner.userId().toString());
        guardTuples.deleteAfterCommit(PartyGuard.GROUP, groupId.toString(), PartyGuard.OWNER, PartyGuard.USER, owner.userId().toString());
        guardTuples.deleteAfterCommit(PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER, PartyGuard.USER, newOwnerId.toString());
        log.info("Group ownership transferred: groupId={}, from={}, to={}", groupId, owner.userId(), newOwnerId);
    }

    /** 멤버가 자기 친구를 모임에 초대한다(친구에게만). 이미 멤버면 아무것도 하지 않는다. */
    @Transactional
    public void addFriend(UUID groupId, DoroUser inviter, UUID targetId) {
        users.findById(targetId).orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        UUID[] pair = Friendship.orderedPair(inviter.userId(), targetId);
        if (targetId.equals(inviter.userId()) || !friendships.areFriends(pair[0], pair[1])) {
            throw new PartyException(ErrorCode.NOT_FRIENDS, "내 친구만 모임에 초대할 수 있습니다.");
        }
        addMember(groupId, targetId);
    }

    /** 멤버를 추가한다(초대 링크·친구 초대 공통). 멤버 수·모임 개수 상한을 지키고, 이미 멤버면 그대로 둔다. */
    @Transactional
    public PartyGroup addMember(UUID groupId, UUID userId) {
        PartyGroup group = groups.findByIdForUpdate(groupId).orElseThrow(() -> new PartyException(ErrorCode.GROUP_NOT_FOUND));
        if (members.existsById(GroupMember.Key.of(groupId, userId))) {
            return group;
        }
        users.findByIdForUpdate(userId);
        if (members.countByGroupId(groupId) >= limits.maxMembersPerGroup()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "모임은 최대 " + limits.maxMembersPerGroup() + "명까지 들어올 수 있습니다.");
        }
        requireRoomForGroup(userId);
        members.saveAndFlush(new GroupMember(groupId, userId, GroupRole.MEMBER));
        guardTuples.write(PartyGuard.GROUP, groupId.toString(), GroupRole.MEMBER.guardRelation(), PartyGuard.USER, userId.toString());
        log.info("Member joined group: groupId={}, userId={}", groupId, userId);
        return group;
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID groupId, UUID userId) {
        return members.existsById(GroupMember.Key.of(groupId, userId));
    }

    /** 멤버십을 끝낸다: 이 사람이 모임에 공유한 지도(와 그 Guard 튜플)와 멤버 튜플을 함께 정리한다. */
    private void removeMembership(GroupMember member) {
        UUID groupId = member.groupId();
        UUID userId = member.userId();
        for (MapGroupShare share : groupShares.findByGroupIdAndSharedBy(groupId, userId)) {
            groupShares.delete(share);
            guardTuples.deleteAfterCommit(PartyGuard.MAP, share.mapId().toString(), PartyGuard.VIEWER, PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER);
        }
        members.delete(member);
        guardTuples.deleteAfterCommit(PartyGuard.GROUP, groupId.toString(), member.getRole().guardRelation(), PartyGuard.USER, userId.toString());
    }

    private void requireRoomForGroup(UUID userId) {
        if (members.countByUserId(userId) >= limits.maxGroupsPerUser()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "모임은 최대 " + limits.maxGroupsPerUser() + "개까지 들어갈 수 있습니다.");
        }
    }

    private PartyGroup find(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> new PartyException(ErrorCode.GROUP_NOT_FOUND));
    }
}
