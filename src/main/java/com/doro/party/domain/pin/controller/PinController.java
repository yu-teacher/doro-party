package com.doro.party.domain.pin.controller;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.dto.PinDtos.PinRequest;
import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.service.PinService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 핀은 항상 지도 아래 경로로만 접근한다. 지도에 대한 권한이 곧 핀에 대한 권한이다. */
@Tag(name = "2. Pins (핀)", description = "지도 위에 꽂는 핀")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/pins")
@RequiredArgsConstructor
public class PinController {

    private final PinService pinService;

    @Operation(summary = "핀 꽂기 (Guard: editor 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @PostMapping
    public ApiResponse<PinResponse> create(
            @PathVariable("mapId") UUID mapId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody PinRequest request
    ) {
        return ApiResponse.success(pinService.create(mapId, doroUser, request));
    }

    @Operation(summary = "지도의 핀 목록 (Guard: viewer 이상)", description = "status(WISH/VISITED), tag 로 걸러 볼 수 있다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping
    public ApiResponse<List<PinResponse>> list(
            @PathVariable("mapId") UUID mapId,
            @RequestParam(name = "status", required = false) PinStatus status,
            @RequestParam(name = "tag", required = false) String tag
    ) {
        return ApiResponse.success(pinService.list(mapId, status, tag));
    }

    @Operation(summary = "핀 수정 (Guard: editor 이상, 내가 꽂은 핀 또는 지도 주인)", description = "전체 교체")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @RequestMapping(value = "/{pinId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<PinResponse> update(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody PinRequest request
    ) {
        return ApiResponse.success(pinService.update(mapId, pinId, doroUser, request));
    }

    @Operation(summary = "핀 삭제 (Guard: editor 이상, 내가 꽂은 핀 또는 지도 주인)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @DeleteMapping("/{pinId}")
    public ApiResponse<Void> delete(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser
    ) {
        pinService.delete(mapId, pinId, doroUser);
        return ApiResponse.success();
    }
}
