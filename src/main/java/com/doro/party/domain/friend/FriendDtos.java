package com.doro.party.domain.friend;

import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FriendDtos {

    private FriendDtos() {
    }

    public record FriendRequestBody(@NotBlank @Size(max = 60) String username) {
    }

    public record FriendView(UserSummary user, Instant since) {
    }

    /** 받은 요청이면 user 는 요청을 보낸 사람, 보낸 요청이면 받는 사람이다. */
    public record FriendRequestView(UUID id, UserSummary user, Instant requestedAt) {
    }

    public record FriendsOverview(List<FriendView> friends, List<FriendRequestView> incoming, List<FriendRequestView> outgoing) {
    }

    /** status 는 PENDING(상대의 수락을 기다림) 또는 ACCEPTED(이미 친구이거나 서로 요청해서 바로 연결됨). */
    public record RequestResult(String status, UserSummary user) {
    }

    public record InviteLinkResponse(String code, Instant expiresAt) {
    }

    public record InvitePreview(UserSummary inviter, boolean self, boolean alreadyFriends) {
    }

    public record AcceptResult(UserSummary friend) {
    }

    /** 친구 한 명이 공개한 지도들 */
    public record FriendMaps(UserSummary friend, List<MapResponse> maps) {
    }

    /** 친구 지도 둘러보기: 친구 전체에게 공개된 지도를 친구별로 */
    public record FriendMapsOverview(List<FriendMaps> friends) {
    }
}
