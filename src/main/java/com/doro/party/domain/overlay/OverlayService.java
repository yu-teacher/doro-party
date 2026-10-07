package com.doro.party.domain.overlay;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.overlay.OverlayDtos.OverlayResponse;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.pin.service.PinAssembler;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 겹쳐보기: 고른 지도 여러 개의 핀을 한 번에 돌려준다. 권한은 지도마다 Guard 로 판정하고(서비스 코드에 권한 규칙을 따로 두지 않는다),
 * 볼 수 없는 지도는 조회하지 않는다. 한 번의 응답이 커지지 않도록 지도 수와 핀 총 개수를 제한한다.
 */
@Service
@RequiredArgsConstructor
public class OverlayService {

    private final DoroGuardClient guard;
    private final PinRepository pins;
    private final PinAssembler assembler;
    private final PartyLimits limits;

    @Transactional(readOnly = true)
    public OverlayResponse overlay(List<UUID> requested, DoroUser user) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(requested);
        if (unique.size() > limits.maxOverlayMaps()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "한 번에 겹쳐볼 수 있는 지도는 최대 " + limits.maxOverlayMaps() + "개입니다.");
        }
        // Guard 가 응답하지 못하면(checkOrThrow) 거부로 오해하지 않고 503 으로 전파한다
        List<UUID> viewable = unique.stream()
                .filter(mapId -> guard.checkOrThrow(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.USER, user.userId().toString(), null))
                .toList();
        if (viewable.isEmpty()) {
            return new OverlayResponse(List.of(), List.of());
        }
        if (pins.countByMapIdIn(viewable) > limits.maxOverlayPins()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "선택한 지도의 핀이 너무 많아 한 번에 겹칠 수 없습니다. 지도를 줄여 보세요.");
        }
        List<Pin> found = pins.findAllByMapIdInOrderByCreatedAtAscIdAsc(viewable);
        return new OverlayResponse(viewable, assembler.assemble(found));
    }
}
