package com.doro.party.domain.map.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.map.dto.MapDtos.MapRequest;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {

    private final PartyMapRepository maps;
    private final PinRepository pins;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final GuardTuples guardTuples;
    private final PartyLimits limits;

    @Transactional
    public MapResponse create(DoroUser doroUser, MapRequest request) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        // 같은 사용자의 동시 생성 요청이 개수 상한을 함께 넘지 못하게 사용자 행으로 직렬화한다.
        users.findByIdForUpdate(user.getId());
        if (maps.countByOwnerId(user.getId()) >= limits.maxMapsPerUser()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "지도는 최대 " + limits.maxMapsPerUser() + "개까지 만들 수 있습니다.");
        }

        PartyMap saved = maps.save(PartyMap.builder()
                .ownerId(user.getId())
                .name(request.name().strip())
                .description(blankToNull(request.description()))
                .build());

        // party_map:<id>#owner@user:<userId>. 쓰기에 실패하면 예외로 DB 도 되돌린다(주인 없는 지도가 생기지 않게).
        guardTuples.write(PartyGuard.MAP, saved.getId().toString(), PartyGuard.OWNER, PartyGuard.USER, user.getId().toString());
        log.info("Map created: mapId={}, ownerId={}", saved.getId(), user.getId());
        return MapResponse.from(saved, user.getId(), 0);
    }

    @Transactional(readOnly = true)
    public List<MapResponse> listMine(DoroUser doroUser) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        List<PartyMap> mine = maps.findAllByOwnerIdOrderByCreatedAtDesc(user.getId());
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = pins.countByMapIds(mine.stream().map(PartyMap::getId).toList()).stream()
                .collect(Collectors.toMap(PinRepository.PinCount::getMapId, PinRepository.PinCount::getCount));
        return mine.stream()
                .map(map -> MapResponse.from(map, user.getId(), counts.getOrDefault(map.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MapResponse get(UUID mapId, DoroUser doroUser) {
        PartyMap map = find(mapId);
        return MapResponse.from(map, doroUser.userId(), pins.countByMapId(mapId));
    }

    @Transactional
    public MapResponse update(UUID mapId, DoroUser doroUser, MapRequest request) {
        PartyMap map = find(mapId);
        map.update(request.name().strip(), blankToNull(request.description()));
        return MapResponse.from(map, doroUser.userId(), pins.countByMapId(mapId));
    }

    @Transactional
    public void delete(UUID mapId) {
        PartyMap map = find(mapId);
        maps.delete(map);
        // 커밋이 확정된 뒤에 권한을 지운다. 공유 튜플(editor/viewer)은 공유 기능(M3)에서 함께 정리한다.
        guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), PartyGuard.OWNER, PartyGuard.USER, map.getOwnerId().toString());
        log.info("Map deleted: mapId={}", mapId);
    }

    private PartyMap find(UUID mapId) {
        return maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
