package com.doro.party.domain.group;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.group.GroupDtos.MapGroupView;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.annotation.DoroGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "9. Group maps (모임에 지도 공유)", description = "내 지도를 모임에 공유하면 모임 멤버 모두가 볼 수 있다")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/groups")
@RequiredArgsConstructor
public class GroupMapController {

    private final GroupMapService groupMapService;

    @Operation(summary = "이 지도를 공유해 둔 모임들 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @GetMapping
    public ApiResponse<List<MapGroupView>> list(@PathVariable("mapId") UUID mapId) {
        return ApiResponse.success(groupMapService.groupsOfMap(mapId));
    }

    @Operation(summary = "내 지도를 모임에 공유 (Guard: owner)", description = "내가 속한 모임에만 공유할 수 있다. 모임 멤버는 모두 이 지도의 viewer 가 된다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @PutMapping("/{groupId}")
    public ApiResponse<MapGroupView> share(@PathVariable("mapId") UUID mapId, @PathVariable("groupId") UUID groupId, @CurrentDoroUser DoroUser user) {
        return ApiResponse.success(groupMapService.share(mapId, groupId, user));
    }

    @Operation(summary = "모임에서 내 지도 거두기 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> unshare(@PathVariable("mapId") UUID mapId, @PathVariable("groupId") UUID groupId) {
        groupMapService.unshare(mapId, groupId);
        return ApiResponse.success();
    }
}
