package com.doro.party.domain.recommendation;

import com.doro.party.domain.group.MapGroupShare;
import com.doro.party.domain.group.MapGroupShareRepository;
import com.doro.party.domain.overlay.OverlayDtos.OverlayResponse;
import com.doro.party.domain.overlay.OverlayService;
import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.recommendation.RecommendationDtos.RecommendationResponse;
import com.doro.party.domain.recommendation.RecommendationProperties.Weights;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 모임 추천. 어떤 핀을 쓸 수 있는지(권한)는 겹쳐보기와 같은 규칙(지도마다 Guard)으로 정하고, 계산은 {@link RecommendationEngine} 이 한다.
 * 특정 작성자를 빼고 다시 계산할 수 있어 "A 가 빠지고 가면 어디?" 를 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final OverlayService overlayService;
    private final MapGroupShareRepository groupShares;
    private final Weights weights;

    /** 고른 지도들(내가 볼 수 있는 것만)의 핀으로 추천한다. */
    public RecommendationResponse forMaps(List<UUID> requestedMapIds, Set<UUID> excludedAuthors, int minPeople, Integer limit, DoroUser user) {
        OverlayResponse overlay = overlayService.overlay(requestedMapIds, user);
        List<PinResponse> pins = overlay.pins().stream().filter(pin -> !excludedAuthors.contains(pin.createdBy())).toList();
        int effectiveLimit = limit == null ? weights.maxResults() : Math.min(limit, weights.maxResults());
        return new RecommendationResponse(overlay.mapIds(), RecommendationEngine.recommend(pins, weights, minPeople, effectiveLimit));
    }

    /** 이 모임에 공유된 지도들의 핀으로 추천한다(모임 멤버라면 모두 볼 수 있는 지도들). */
    public RecommendationResponse forGroup(UUID groupId, Set<UUID> excludedAuthors, int minPeople, Integer limit, DoroUser user) {
        List<UUID> mapIds = groupShares.findByGroupId(groupId).stream().map(MapGroupShare::mapId).toList();
        return forMaps(mapIds, excludedAuthors, minPeople, limit, user);
    }
}
