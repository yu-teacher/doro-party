package com.doro.party.domain.overlay;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.overlay.OverlayDtos.OverlayResponse;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "10. Overlay (겹쳐보기)", description = "여러 지도의 핀을 한 번에")
@RestController
@RequestMapping("/api/v1/overlay")
@RequiredArgsConstructor
public class OverlayController {

    private final OverlayService overlayService;

    @Operation(summary = "선택한 지도들의 핀 (로그인)", description = "내가 볼 수 있는 지도만 포함된다. 볼 수 없거나 없는 지도는 조용히 빠진다")
    @GetMapping("/pins")
    public ApiResponse<OverlayResponse> pins(@CurrentDoroUser DoroUser user, @RequestParam(name = "mapIds") List<UUID> mapIds) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(overlayService.overlay(mapIds, user));
    }
}
