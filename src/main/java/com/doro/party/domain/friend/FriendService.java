package com.doro.party.domain.friend;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.friend.FriendDtos.FriendRequestView;
import com.doro.party.domain.friend.FriendDtos.FriendView;
import com.doro.party.domain.friend.FriendDtos.FriendsOverview;
import com.doro.party.domain.friend.FriendDtos.RequestResult;
import com.doro.party.domain.share.ShareService;
import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.domain.user.service.UsernamePolicy;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendships;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final PartyLimits limits;
    private final ShareService shareService;

    @Transactional(readOnly = true)
    public FriendsOverview overview(DoroUser doroUser) {
        UUID me = doroUser.userId();
        List<Friendship> friends = friendships.findFriends(me);
        List<Friendship> incoming = friendships.findIncoming(me);
        List<Friendship> outgoing = friendships.findOutgoing(me);
        Map<UUID, PartyUser> people = loadUsers(me, friends, incoming, outgoing);
        return new FriendsOverview(
                friends.stream().map(f -> new FriendView(summary(people, f.otherThan(me)), f.getAcceptedAt())).toList(),
                incoming.stream().map(f -> new FriendRequestView(f.getId(), summary(people, f.getRequesterId()), f.getCreatedAt())).toList(),
                outgoing.stream().map(f -> new FriendRequestView(f.getId(), summary(people, f.otherThan(me)), f.getCreatedAt())).toList());
    }

    /**
     * 사용자명으로 친구 요청을 보낸다. 상대가 이미 나에게 요청을 보냈다면 서로 원한 것이므로 바로 친구가 된다.
     * 같은 요청을 다시 보내거나 이미 친구여도 오류가 아니다(멱등).
     */
    @Transactional
    public RequestResult requestByUsername(DoroUser doroUser, String rawUsername) {
        PartyUser me = userService.getOrCreateUser(doroUser);
        PartyUser target = users.findByUsername(UsernamePolicy.normalize(rawUsername))
                .orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND, "해당 사용자명의 사용자를 찾을 수 없어요."));
        if (target.getId().equals(me.getId())) {
            throw new PartyException(ErrorCode.CANNOT_FRIEND_SELF);
        }
        UUID[] pair = Friendship.orderedPair(me.getId(), target.getId());

        if (friendships.insertPendingIfAbsent(UUID.randomUUID(), pair[0], pair[1], me.getId()) == 1) {
            // 새 요청이다. 한도는 새로 만들 때만 센다(이미 있는 요청을 다시 보내는 건 막지 않는다)
            if (friendships.countOutgoing(me.getId()) > limits.maxPendingFriendRequests()) {
                throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "보낸 친구 요청은 최대 " + limits.maxPendingFriendRequests() + "개까지 대기할 수 있습니다.");
            }
            log.info("Friend request sent: from={}, to={}", me.getId(), target.getId());
            return new RequestResult("PENDING", UserSummary.from(target));
        }

        Friendship existing = friendships.findByUserLowIdAndUserHighId(pair[0], pair[1])
                .orElseThrow(() -> new PartyException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        if (existing.isPending() && !existing.getRequesterId().equals(me.getId())) {
            requireRoomForFriend(me.getId());
            requireRoomForFriend(target.getId());
            friendships.acceptPending(existing.getId());
            log.info("Friend request matched and accepted: a={}, b={}", me.getId(), target.getId());
            return new RequestResult("ACCEPTED", UserSummary.from(target));
        }
        return new RequestResult(existing.isPending() ? "PENDING" : "ACCEPTED", UserSummary.from(target));
    }

    /** 나에게 온 요청을 수락한다. 내가 받는 사람이 아니면 없는 요청처럼 보인다. */
    @Transactional
    public void accept(DoroUser doroUser, UUID friendshipId) {
        UUID me = doroUser.userId();
        Friendship request = friendships.findById(friendshipId)
                .filter(f -> f.involves(me) && f.isPending() && !f.getRequesterId().equals(me))
                .orElseThrow(() -> new PartyException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        requireRoomForFriend(me);
        requireRoomForFriend(request.getRequesterId());
        if (friendships.acceptPending(friendshipId) == 1) {
            log.info("Friend request accepted: requester={}, accepter={}", request.getRequesterId(), me);
        }
    }

    /** 받은 요청을 거절하거나 보낸 요청을 취소한다. 당사자가 아니면 없는 요청처럼 보인다. */
    @Transactional
    public void removeRequest(DoroUser doroUser, UUID friendshipId) {
        UUID me = doroUser.userId();
        friendships.findById(friendshipId)
                .filter(f -> f.involves(me) && f.isPending())
                .orElseThrow(() -> new PartyException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        friendships.deletePending(friendshipId);
    }

    /** 친구를 끊는다. */
    @Transactional
    public void unfriend(DoroUser doroUser, UUID otherUserId) {
        UUID[] pair = Friendship.orderedPair(doroUser.userId(), otherUserId);
        if (friendships.deleteAccepted(pair[0], pair[1]) == 0) {
            throw new PartyException(ErrorCode.FRIEND_NOT_FOUND);
        }
        // 끊은 쪽이 상대에게 공유해 둔 지도는 함께 회수한다(상대가 나에게 준 공유는 그대로)
        int revoked = shareService.revokeGiven(doroUser.userId(), otherUserId);
        log.info("Unfriended: a={}, b={}, revokedShares={}", doroUser.userId(), otherUserId, revoked);
    }

    /** 초대 링크 등 "바로 친구" 경로. 이미 친구면 그대로 두고, 대기 중인 요청이 있으면 수락으로 바꾼다. */
    @Transactional
    public void connect(UUID me, UUID other) {
        if (me.equals(other)) {
            throw new PartyException(ErrorCode.CANNOT_FRIEND_SELF);
        }
        UUID[] pair = Friendship.orderedPair(me, other);
        if (friendships.areFriends(pair[0], pair[1])) {
            return;
        }
        requireRoomForFriend(me);
        requireRoomForFriend(other);
        friendships.upsertAccepted(UUID.randomUUID(), pair[0], pair[1], me);
        log.info("Friends connected: a={}, b={}", me, other);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID a, UUID b) {
        UUID[] pair = Friendship.orderedPair(a, b);
        return friendships.areFriends(pair[0], pair[1]);
    }

    private void requireRoomForFriend(UUID userId) {
        if (friendships.countFriends(userId) >= limits.maxFriends()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "친구는 최대 " + limits.maxFriends() + "명까지 사귈 수 있습니다.");
        }
    }

    private Map<UUID, PartyUser> loadUsers(UUID me, List<Friendship> friends, List<Friendship> incoming, List<Friendship> outgoing) {
        List<UUID> ids = java.util.stream.Stream.of(friends, incoming, outgoing).flatMap(List::stream)
                .flatMap(f -> java.util.stream.Stream.of(f.getUserLowId(), f.getUserHighId()))
                .filter(id -> !id.equals(me)).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(PartyUser::getId, Function.identity()));
    }

    private static UserSummary summary(Map<UUID, PartyUser> people, UUID id) {
        PartyUser user = people.get(id);
        if (user == null) {
            throw new PartyException(ErrorCode.USER_NOT_FOUND);
        }
        return UserSummary.from(user);
    }
}
