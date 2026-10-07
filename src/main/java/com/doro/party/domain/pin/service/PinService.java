package com.doro.party.domain.pin.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.dto.PinDtos.PinRequest;
import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.photo.PinPhotoRepository;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.storage.StorageCleanup;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 핀 CRUD. 접근 규칙은 {@link PinAccess} 와 컨트롤러의 {@code @DoroGuard} 가 맡는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PinService {

    private final PinRepository pins;
    private final PartyMapRepository maps;
    private final PinPhotoRepository photos;
    private final PinAccess access;
    private final PinAssembler assembler;
    private final PartyUserService userService;
    private final StorageCleanup storageCleanup;
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
                .revisitIntent(request.revisitIntent())
                .tags(tags)
                .build());
        log.info("Pin created: pinId={}, mapId={}, createdBy={}", saved.getId(), mapId, user.getId());
        return assembler.assemble(saved);
    }

    @Transactional(readOnly = true)
    public List<PinResponse> list(UUID mapId, PinStatus status, String tag) {
        return assembler.assemble(pins.search(mapId, status, TagNormalizer.normalizeOne(tag)));
    }

    @Transactional
    public PinResponse update(UUID mapId, UUID pinId, DoroUser doroUser, PinRequest request) {
        Pin pin = access.requirePin(mapId, pinId);
        access.requirePinModifier(pin, doroUser.userId());
        Set<String> tags = TagNormalizer.normalize(request.tags(), limits.maxTagsPerPin());
        pin.update(request.lat(), request.lng(), request.name().strip(), blankToNull(request.sharedMemo()),
                request.status() == null ? pin.getStatus() : request.status(), request.rating(), request.revisitIntent(), tags);
        return assembler.assemble(pin);
    }

    @Transactional
    public void delete(UUID mapId, UUID pinId, DoroUser doroUser) {
        Pin pin = access.requirePin(mapId, pinId);
        access.requirePinModifier(pin, doroUser.userId());
        List<String> photoKeys = photos.objectKeysOfPin(pinId);
        pins.delete(pin);
        // 핀이 지워지면(DB 가 사진 기록도 함께 지운다) 커밋 뒤에 스토리지의 파일도 지운다
        storageCleanup.deleteAfterCommit(photoKeys);
        log.info("Pin deleted: pinId={}, mapId={}, photos={}", pinId, mapId, photoKeys.size());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
