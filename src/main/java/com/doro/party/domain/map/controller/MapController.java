package com.doro.party.domain.map.controller;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.map.dto.MapDtos.MapRequest;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.service.MapService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "1. Maps (지도)", description = "주제별 개인 지도")
@RestController
@RequestMapping("/api/v1/maps")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    @Operation(summary = "지도 만들기 (로그인)", description = "내 지도를 만들고 owner 관계를 Guard 에 기록한다")
    @PostMapping
    public ApiResponse<MapResponse> create(@CurrentDoroUser DoroUser doroUser, @Valid @RequestBody MapRequest request) {
        return ApiResponse.success(mapService.create(doroUser, request));
    }

    @Operation(summary = "내가 볼 수 있는 지도 목록 (로그인)", description = "내가 만든 지도와 친구가 공유한 지도. 각 지도에서 내 권한(role)과 주인 정보를 함께 준다")
    @GetMapping
    public ApiResponse<List<MapResponse>> listAccessible(@CurrentDoroUser DoroUser doroUser) {
        return ApiResponse.success(mapService.listAccessible(doroUser));
    }

    @Operation(summary = "지도 상세 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping("/{mapId}")
    public ApiResponse<MapResponse> get(@PathVariable("mapId") UUID mapId, @CurrentDoroUser DoroUser doroUser) {
        return ApiResponse.success(mapService.get(mapId, doroUser));
    }

    @Operation(summary = "지도 이름·설명 수정 (Guard: owner)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @RequestMapping(value = "/{mapId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<MapResponse> update(
            @PathVariable("mapId") UUID mapId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody MapRequest request
    ) {
        return ApiResponse.success(mapService.update(mapId, doroUser, request));
    }

    @Operation(summary = "지도 삭제 (Guard: owner)", description = "지도와 그 안의 핀이 모두 삭제된다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.OWNER)
    @DeleteMapping("/{mapId}")
    public ApiResponse<Void> delete(@PathVariable("mapId") UUID mapId) {
        mapService.delete(mapId);
        return ApiResponse.success();
    }
}
