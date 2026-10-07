package com.doro.party.domain.overlay;

import com.doro.party.domain.pin.dto.PinDtos.PinResponse;

import java.util.List;
import java.util.UUID;

public final class OverlayDtos {

    private OverlayDtos() {
    }

    /**
     * @param mapIds 실제로 겹친 지도(요청한 것 중 내가 볼 수 있는 것). 볼 수 없거나 없는 지도는 이유를 밝히지 않고 조용히 빠진다
     * @param pins   그 지도들의 핀. 각 핀에 지도 ID 와 작성자 닉네임·색이 들어 있다
     */
    public record OverlayResponse(List<UUID> mapIds, List<PinResponse> pins) {
    }
}
