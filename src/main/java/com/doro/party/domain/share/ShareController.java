package com.doro.party.domain.share;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.share.ShareDtos.MemberView;
import com.doro.party.domain.share.ShareDtos.ShareRequest;
import com.doro.party.domain.share.ShareDtos.ShareView;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "7. Shares (지도 공유)", description = "친구에게 지도를 viewer/editor 로 공유")
@RestController
@RequestMapping("/api/v1/maps/{mapId}")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @Operation(summary = "이 지도를 공유받은 사람 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @GetMapping("/shares")
    public ApiResponse<List<ShareView>> list(@PathVariable("mapId") UUID mapId) {
        return ApiResponse.success(shareService.list(mapId));
    }

    @Operation(summary = "친구에게 공유 / 권한 변경 (Guard: owner)", description = "친구에게만 공유할 수 있다. viewer(보기) 또는 editor(핀 추가·수정)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @PutMapping("/shares/{userId}")
    public ApiResponse<ShareView> share(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("userId") UUID userId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody ShareRequest request
    ) {
        return ApiResponse.success(shareService.share(mapId, doroUser, userId, request.role()));
    }

    @Operation(summary = "공유 끊기 (Guard: viewer 이상, 주인 또는 공유받은 본인)", description = "주인이 끊거나, 공유받은 사람이 스스로 나간다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @DeleteMapping("/shares/{userId}")
    public ApiResponse<Void> revoke(@PathVariable("mapId") UUID mapId, @PathVariable("userId") UUID userId, @CurrentDoroUser DoroUser doroUser) {
        shareService.revoke(mapId, doroUser, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "이 지도를 같이 보는 사람 (Guard: viewer 이상)", description = "주인과 공유받은 사람의 닉네임·색")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping("/members")
    public ApiResponse<List<MemberView>> members(@PathVariable("mapId") UUID mapId) {
        return ApiResponse.success(shareService.members(mapId));
    }
}
