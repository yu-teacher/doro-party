package com.doro.party.domain.recommendation;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.recommendation.RecommendationDtos.RecommendationResponse;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.annotation.DoroGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Tag(name = "11. Recommendations (모임 추천)", description = "여러 사람이 찍은 장소를 점수로 순위 매기기. 점수 내역을 함께 준다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private static final int MAX_MIN_PEOPLE = 50;
    private static final int MAX_LIMIT = 100;

    private final RecommendationService recommendationService;

    @Operation(summary = "고른 지도들의 추천 장소 (로그인)",
            description = "내가 볼 수 있는 지도만 쓴다. excludeAuthors 에 든 사람의 핀은 빼고 계산한다. minPeople 명 이상이 찍은 장소만")
    @GetMapping("/overlay/recommendations")
    public ApiResponse<RecommendationResponse> forMaps(
            @CurrentDoroUser DoroUser user,
            @RequestParam(name = "mapIds") List<UUID> mapIds,
            @RequestParam(name = "excludeAuthors", required = false) List<UUID> excludeAuthors,
            @RequestParam(name = "minPeople", defaultValue = "1") @Min(1) @Max(MAX_MIN_PEOPLE) int minPeople,
            @RequestParam(name = "limit", required = false) @Min(1) @Max(MAX_LIMIT) Integer limit
    ) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(recommendationService.forMaps(mapIds, toSet(excludeAuthors), minPeople, limit, user));
    }

    @Operation(summary = "모임의 추천 장소 (Guard: member)", description = "모임에 공유된 지도들의 핀으로 \"몇 명이 찍은 곳\" 을 점수로 순위 매긴다")
    @DoroGuard(namespace = PartyGuard.GROUP, object = "#groupId", relation = PartyGuard.MEMBER)
    @GetMapping("/groups/{groupId}/recommendations")
    public ApiResponse<RecommendationResponse> forGroup(
            @PathVariable("groupId") UUID groupId,
            @CurrentDoroUser DoroUser user,
            @RequestParam(name = "excludeAuthors", required = false) List<UUID> excludeAuthors,
            @RequestParam(name = "minPeople", defaultValue = "1") @Min(1) @Max(MAX_MIN_PEOPLE) int minPeople,
            @RequestParam(name = "limit", required = false) @Min(1) @Max(MAX_LIMIT) Integer limit
    ) {
        return ApiResponse.success(recommendationService.forGroup(groupId, toSet(excludeAuthors), minPeople, limit, user));
    }

    private static java.util.Set<UUID> toSet(List<UUID> ids) {
        return ids == null ? java.util.Set.of() : new HashSet<>(ids);
    }
}
