package com.doro.party.domain.pin.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.dto.PinDtos.PinRequest;
import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 핀 CRUD. 지도에 대한 접근(보기/편집)은 컨트롤러의 {@code @DoroGuard} 가 판정하고,
 * 여기서는 핀이 그 지도의 것인지(IDOR 방지)와 핀 단위 규칙(내가 꽂은 핀 또는 지도 주인만 수정·삭제)을 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PinService {

    private final PinRepository pins;
    private final PartyMapRepository maps;
    private final PartyUserService userService;
    private final PartyLimits limits;

    @Transactional
    public PinResponse create(UUID mapId, DoroUser doroUser, PinRequest request) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        // 같은 지도에 동시에 핀을 추가해도 개수 상한을 넘지 못하게 지도 행으로 직렬화한다.
        maps.findByIdForUpdate(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        if (pins.countByMapId(mapId) >= limits.maxPinsPerMap()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "지도 하나에는 핀을 최대 " + limits.maxPinsPerMap() + "개까지 꽂을 수 있습니다.");
        }
        Set<String> tags = TagNormalizer.normalize(request.tags(), limits.maxTagsPerPin());

        Pin saved = pins.save(Pin.builder()
                .mapId(mapId)
                .createdBy(user.getId())
                .lat(request.lat())
                .lng(request.lng())
                .name(request.name().strip())
                .sharedMemo(blankToNull(request.sharedMemo()))
                .status(request.status() == null ? PinStatus.WISH : request.status())
                .rating(request.rating())
                .tags(tags)
                .build());
        log.info("Pin created: pinId={}, mapId={}, createdBy={}", saved.getId(), mapId, user.getId());
        return PinResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PinResponse> list(UUID mapId, PinStatus status, String tag) {
        return pins.search(mapId, status, TagNormalizer.normalizeOne(tag)).stream().map(PinResponse::from).toList();
    }

    @Transactional
    public PinResponse update(UUID mapId, UUID pinId, DoroUser doroUser, PinRequest request) {
        Pin pin = findModifiable(mapId, pinId, doroUser);
        Set<String> tags = TagNormalizer.normalize(request.tags(), limits.maxTagsPerPin());
        pin.update(request.lat(), request.lng(), request.name().strip(), blankToNull(request.sharedMemo()),
                request.status() == null ? pin.getStatus() : request.status(), request.rating(), tags);
        return PinResponse.from(pin);
    }

    @Transactional
    public void delete(UUID mapId, UUID pinId, DoroUser doroUser) {
        Pin pin = findModifiable(mapId, pinId, doroUser);
        pins.delete(pin);
        log.info("Pin deleted: pinId={}, mapId={}", pinId, mapId);
    }

    /** 이 지도의 핀이어야 하고, 내가 꽂은 핀이거나 내가 지도 주인이어야 고칠 수 있다. */
    private Pin findModifiable(UUID mapId, UUID pinId, DoroUser doroUser) {
        Pin pin = pins.findByIdAndMapId(pinId, mapId).orElseThrow(() -> new PartyException(ErrorCode.PIN_NOT_FOUND));
        PartyMap map = maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        UUID userId = doroUser.userId();
        if (!pin.getCreatedBy().equals(userId) && !map.isOwnedBy(userId)) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
        return pin;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
