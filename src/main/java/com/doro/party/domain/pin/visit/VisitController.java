package com.doro.party.domain.pin.visit;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.visit.VisitDtos.VisitRequest;
import com.doro.party.domain.pin.visit.VisitDtos.VisitResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "3. Visits (방문 기록)", description = "핀을 다녀온 날짜와 한 줄 후기의 타임라인")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/pins/{pinId}/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @Operation(summary = "방문 기록 남기기 (Guard: editor 이상)", description = "핀을 만든 사람이 남기면 가고 싶던 곳이 다녀온 곳으로 바뀐다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @PostMapping
    public ApiResponse<VisitResponse> add(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody VisitRequest request
    ) {
        return ApiResponse.success(visitService.add(mapId, pinId, doroUser, request));
    }

    @Operation(summary = "방문 기록 타임라인 (Guard: viewer 이상)", description = "최근 방문 순")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping
    public ApiResponse<List<VisitResponse>> list(@PathVariable("mapId") UUID mapId, @PathVariable("pinId") UUID pinId) {
        return ApiResponse.success(visitService.list(mapId, pinId));
    }

    @Operation(summary = "방문 기록 삭제 (Guard: editor 이상, 내가 남긴 기록 또는 지도 주인)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @DeleteMapping("/{visitId}")
    public ApiResponse<Void> delete(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @PathVariable("visitId") UUID visitId,
            @CurrentDoroUser DoroUser doroUser
    ) {
        visitService.delete(mapId, pinId, visitId, doroUser);
        return ApiResponse.success();
    }
}
