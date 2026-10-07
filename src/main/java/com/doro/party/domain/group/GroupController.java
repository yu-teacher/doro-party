package com.doro.party.domain.group;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.friend.FriendDtos.InviteLinkResponse;
import com.doro.party.domain.group.GroupDtos.GroupDetail;
import com.doro.party.domain.group.GroupDtos.GroupInvitePreview;
import com.doro.party.domain.group.GroupDtos.GroupNameRequest;
import com.doro.party.domain.group.GroupDtos.GroupSummary;
import com.doro.party.domain.group.GroupDtos.JoinResult;
import com.doro.party.domain.group.GroupDtos.UserIdRequest;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.annotation.DoroGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "8. Groups (모임)", description = "카톡방처럼 사람들을 묶고, 각자 내 지도를 모임에 공유한다")
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupInviteService inviteService;
    private final GroupMapService groupMapService;

    private static DoroUser authenticated(DoroUser user) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    @Operation(summary = "모임 만들기 (로그인)", description = "만든 사람이 방장이 된다")
    @PostMapping
    public ApiResponse<GroupSummary> create(@CurrentDoroUser DoroUser user, @Valid @RequestBody GroupNameRequest request) {
        return ApiResponse.success(groupService.create(authenticated(user), request.name()));
    }

    @Operation(summary = "내가 속한 모임 (로그인)")
    @GetMapping
    public ApiResponse<List<GroupSummary>> list(@CurrentDoroUser DoroUser user) {
        return ApiResponse.success(groupService.list(authenticated(user)));
    }

    @Operation(summary = "모임 상세와 멤버 (Guard: member)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetail> detail(@PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user) {
        return ApiResponse.success(groupService.detail(groupId, user));
    }

    @Operation(summary = "모임 이름 바꾸기 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @RequestMapping(value = "/{groupId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<Void> rename(@PathVariable("groupId") UUID groupId, @Valid @RequestBody GroupNameRequest request) {
        groupService.rename(groupId, request.name());
        return ApiResponse.success();
    }

    @Operation(summary = "모임 삭제 (Guard: owner)", description = "멤버십과 모임에 공유된 지도 기록이 지워진다(지도 자체는 각 주인의 것으로 남는다)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> delete(@PathVariable("groupId") UUID groupId) {
        groupService.delete(groupId);
        return ApiResponse.success();
    }

    @Operation(summary = "모임 나가기 (Guard: member)", description = "내가 이 모임에 공유한 지도도 함께 거둔다. 방장은 나갈 수 없다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @DeleteMapping("/{groupId}/members/me")
    public ApiResponse<Void> leave(@PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user) {
        groupService.leave(groupId, user);
        return ApiResponse.success();
    }

    @Operation(summary = "멤버 내보내기 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> kick(@PathVariable("groupId") UUID groupId, @PathVariable("userId") UUID userId, @CurrentDoroUser DoroUser user) {
        groupService.kick(groupId, user, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "내 친구를 모임에 초대 (Guard: member)", description = "친구만 초대할 수 있다. 이미 멤버면 아무 일도 없다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @PostMapping("/{groupId}/members")
    public ApiResponse<Void> addFriend(@PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user, @Valid @RequestBody UserIdRequest request) {
        groupService.addFriend(groupId, user, request.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "방장 넘기기 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @PutMapping("/{groupId}/owner")
    public ApiResponse<Void> transfer(@PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user, @Valid @RequestBody UserIdRequest request) {
        groupService.transferOwnership(groupId, user, request.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "모임에 공유된 지도 (Guard: member)", description = "멤버라면 이 지도들을 모두 볼 수 있다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @GetMapping("/{groupId}/maps")
    public ApiResponse<List<MapResponse>> maps(@PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user) {
        return ApiResponse.success(groupMapService.mapsOfGroup(groupId, user));
    }

    @Operation(summary = "모임 초대 링크 (Guard: member)", description = "없거나 만료됐으면 data 가 null. 멤버라면 링크를 볼 수 있다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @GetMapping("/{groupId}/invite")
    public ApiResponse<InviteLinkResponse> currentInvite(@PathVariable("groupId") UUID groupId) {
        return ApiResponse.success(inviteService.current(groupId).orElse(null));
    }

    @Operation(summary = "초대 링크 만들기·다시 만들기 (Guard: owner)", description = "이전 링크는 즉시 무효가 된다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @PostMapping("/{groupId}/invite")
    public ApiResponse<InviteLinkResponse> createInvite(@PathVariable("groupId") UUID groupId) {
        return ApiResponse.success(inviteService.create(groupId));
    }

    @Operation(summary = "초대 링크 없애기 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.OWNER)
    @DeleteMapping("/{groupId}/invite")
    public ApiResponse<Void> revokeInvite(@PathVariable("groupId") UUID groupId) {
        inviteService.revoke(groupId);
        return ApiResponse.success();
    }

    @Operation(summary = "초대 링크 미리보기 (로그인)", description = "어떤 모임인지만 보여 준다. 없거나 만료된 코드는 404")
    @GetMapping("/invite/{code}")
    public ApiResponse<GroupInvitePreview> previewInvite(@CurrentDoroUser DoroUser user, @PathVariable("code") String code) {
        return ApiResponse.success(inviteService.preview(authenticated(user), code));
    }

    @Operation(summary = "초대 링크로 모임에 들어가기 (로그인)", description = "이미 멤버여도 오류가 아니다")
    @PostMapping("/invite/{code}/join")
    public ApiResponse<JoinResult> join(@CurrentDoroUser DoroUser user, @PathVariable("code") String code) {
        return ApiResponse.success(inviteService.join(authenticated(user), code));
    }
}
