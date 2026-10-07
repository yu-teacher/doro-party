package com.doro.party.domain.pin.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.repository.PinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 핀에 대한 공통 접근 규칙. 지도에 대한 접근(보기/편집)은 컨트롤러의 {@code @DoroGuard} 가 판정하고,
 * 여기서는 핀이 그 지도의 것인지(IDOR 방지)와 핀 단위 규칙(내가 만든 것 또는 지도 주인만 고칠 수 있다)을 확인한다.
 */
@Component
@RequiredArgsConstructor
public class PinAccess {

    private final PinRepository pins;
    private final PartyMapRepository maps;

    /** 이 지도의 핀이어야 한다. 다른 지도의 핀 ID 로 접근하면 없는 것으로 본다. */
    public Pin requirePin(UUID mapId, UUID pinId) {
        return pins.findByIdAndMapId(pinId, mapId).orElseThrow(() -> new PartyException(ErrorCode.PIN_NOT_FOUND));
    }

    /** {@link #requirePin} + 핀 행을 잠근다. 방문 기록·사진처럼 핀마다 개수 상한을 검사할 때 쓴다. */
    public Pin requirePinForUpdate(UUID mapId, UUID pinId) {
        return pins.findByIdAndMapIdForUpdate(pinId, mapId).orElseThrow(() -> new PartyException(ErrorCode.PIN_NOT_FOUND));
    }

    public boolean isMapOwner(UUID mapId, UUID userId) {
        return maps.existsByIdAndOwnerId(mapId, userId);
    }

    /** 핀을 고치거나 지우려면 내가 꽂은 핀이거나 내가 지도 주인이어야 한다. */
    public void requirePinModifier(Pin pin, UUID userId) {
        if (!pin.getCreatedBy().equals(userId) && !isMapOwner(pin.getMapId(), userId)) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 내가 만든 기록(방문·사진)을 지우려면 내가 만든 것이거나 내가 지도 주인이어야 한다. */
    public void requireRecordModifier(UUID mapId, UUID recordAuthorId, UUID userId) {
        if (!recordAuthorId.equals(userId) && !isMapOwner(mapId, userId)) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
    }
}
