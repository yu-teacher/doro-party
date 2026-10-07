package com.doro.party.domain.friend;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.friend.FriendDtos.AcceptResult;
import com.doro.party.domain.friend.FriendDtos.FriendMapsOverview;
import com.doro.party.domain.friend.FriendDtos.FriendRequestBody;
import com.doro.party.domain.friend.FriendDtos.FriendsOverview;
import com.doro.party.domain.friend.FriendDtos.InviteLinkResponse;
import com.doro.party.domain.friend.FriendDtos.InvitePreview;
import com.doro.party.domain.friend.FriendDtos.RequestResult;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 친구는 지도가 아니라 "내 사람" 에 대한 기능이라 Guard 대신 로그인 사용자 기준으로 처리한다(상대 ID 로 남의 데이터에 닿는 경로가 없다). */
@Tag(name = "6. Friends (친구)", description = "초대 링크, 사용자명 요청, 승인, 해제")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final FriendInviteService inviteService;
    private final FriendMapService friendMapService;

    private static DoroUser authenticated(DoroUser user) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    @Operation(summary = "친구, 받은 요청, 보낸 요청 (로그인)")
    @GetMapping
    public ApiResponse<FriendsOverview> overview(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(friendService.overview(authenticated(user)));
    }

    @Operation(summary = "친구 지도 둘러보기 (로그인)", description = "친구들이 친구 전체에게 공개해 둔 지도를 친구별로. 내 권한(role)이 함께 온다")
    @GetMapping("/maps")
    public ApiResponse<FriendMapsOverview> friendMaps(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(friendMapService.browse(authenticated(user).userId()));
    }

    @Operation(summary = "사용자명으로 친구 요청 (로그인)", description = "상대가 이미 나에게 요청했다면 바로 친구가 된다")
    @PostMapping("/requests")
    public ApiResponse<RequestResult> request(@CurrentDoroUser DoroUser user, @Valid @RequestBody FriendRequestBody body) {
        return ApiResponse.success(friendService.requestByUsername(authenticated(user), body.username()));
    }

    @Operation(summary = "받은 요청 수락 (로그인)")
    @PostMapping("/requests/{id}/accept")
    public ApiResponse<Void> accept(@CurrentDoroUser DoroUser user, @PathVariable("id") UUID id) {
        friendService.accept(authenticated(user), id);
        return ApiResponse.success();
    }

    @Operation(summary = "받은 요청 거절 / 보낸 요청 취소 (로그인)")
    @DeleteMapping("/requests/{id}")
    public ApiResponse<Void> removeRequest(@CurrentDoroUser DoroUser user, @PathVariable("id") UUID id) {
        friendService.removeRequest(authenticated(user), id);
        return ApiResponse.success();
    }

    @Operation(summary = "친구 끊기 (로그인)")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unfriend(@CurrentDoroUser DoroUser user, @PathVariable("userId") UUID userId) {
        friendService.unfriend(authenticated(user), userId);
        return ApiResponse.success();
    }

    @Operation(summary = "내 초대 링크 (로그인)", description = "없거나 만료됐으면 data 가 null")
    @GetMapping("/invite")
    public ApiResponse<InviteLinkResponse> currentInvite(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(inviteService.current(authenticated(user)).orElse(null));
    }

    @Operation(summary = "초대 링크 만들기·다시 만들기 (로그인)", description = "이전 링크는 즉시 무효가 된다")
    @PostMapping("/invite")
    public ApiResponse<InviteLinkResponse> createInvite(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(inviteService.create(authenticated(user)));
    }

    @Operation(summary = "초대 링크 없애기 (로그인)")
    @DeleteMapping("/invite")
    public ApiResponse<Void> revokeInvite(@CurrentDoroUser DoroUser user) {
        inviteService.revoke(authenticated(user));
        return ApiResponse.success();
    }

    @Operation(summary = "초대 링크 미리보기 (로그인)", description = "누구의 초대인지만 보여 준다. 없거나 만료된 코드는 404")
    @GetMapping("/invite/{code}")
    public ApiResponse<InvitePreview> previewInvite(@CurrentDoroUser DoroUser user, @PathVariable("code") String code) {
        return ApiResponse.success(inviteService.preview(authenticated(user), code));
    }

    @Operation(summary = "초대 링크 수락 (로그인)", description = "바로 친구가 된다. 이미 친구여도 오류가 아니다")
    @PostMapping("/invite/{code}/accept")
    public ApiResponse<AcceptResult> acceptInvite(@CurrentDoroUser DoroUser user, @PathVariable("code") String code) {
        return ApiResponse.success(inviteService.accept(authenticated(user), code));
    }
}
