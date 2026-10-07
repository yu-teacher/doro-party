package com.doro.party.domain.map.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.map.dto.MapDtos.MapRequest;
import com.doro.party.domain.map.dto.MapDtos.MapRole;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.pin.photo.PinPhotoRepository;
import com.doro.party.domain.share.MapUserShare;
import com.doro.party.domain.share.MapUserShareRepository;
import com.doro.party.domain.share.ShareRole;
import com.doro.party.domain.share.ShareService;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.infra.storage.StorageCleanup;
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
    private final PinPhotoRepository photos;
    private final MapUserShareRepository shares;
    private final ShareService shareService;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final GuardTuples guardTuples;
    private final StorageCleanup storageCleanup;
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
        return MapResponse.from(saved, MapRole.OWNER, user, 0);
    }

    /** 내가 볼 수 있는 지도: 내가 만든 지도와 친구가 공유한 지도. 권한(role)과 주인 정보를 함께 준다. */
    @Transactional(readOnly = true)
    public List<MapResponse> listAccessible(DoroUser doroUser) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        List<PartyMap> owned = maps.findAllByOwnerIdOrderByCreatedAtDesc(user.getId());
        List<MapUserShare> sharedWithMe = shares.findByUserId(user.getId());
        Map<UUID, ShareRole> sharedRoles = sharedWithMe.stream().collect(Collectors.toMap(MapUserShare::mapId, MapUserShare::getRole));
        List<PartyMap> shared = maps.findAllById(sharedRoles.keySet()).stream()
                .sorted(java.util.Comparator.comparing(PartyMap::getName)).toList();
        List<PartyMap> all = new java.util.ArrayList<>(owned);
        all.addAll(shared);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = pins.countByMapIds(all.stream().map(PartyMap::getId).toList()).stream()
                .collect(Collectors.toMap(PinRepository.PinCount::getMapId, PinRepository.PinCount::getCount));
        Map<UUID, PartyUser> owners = users.findAllById(all.stream().map(PartyMap::getOwnerId).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, Function.identity()));
        return all.stream()
                .map(map -> MapResponse.from(map, map.isOwnedBy(user.getId()) ? MapRole.OWNER : toRole(sharedRoles.get(map.getId())),
                        owners.get(map.getOwnerId()), counts.getOrDefault(map.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MapResponse get(UUID mapId, DoroUser doroUser) {
        PartyMap map = find(mapId);
        return respond(map, doroUser.userId());
    }

    @Transactional
    public MapResponse update(UUID mapId, DoroUser doroUser, MapRequest request) {
        PartyMap map = find(mapId);
        map.update(request.name().strip(), blankToNull(request.description()));
        return respond(map, doroUser.userId());
    }

    @Transactional
    public void delete(UUID mapId) {
        PartyMap map = find(mapId);
        List<String> photoKeys = photos.objectKeysOfMap(mapId);
        // 공유받은 사람들의 권한(Guard)도 지도와 함께 정리한다(공유 행은 지도와 함께 DB 가 지운다)
        shareService.releaseGuardForMap(mapId);
        maps.delete(map);
        // 지도와 핀·사진 기록은 DB 가 함께 지운다. 스토리지의 사진 파일은 커밋 뒤에 지운다.
        storageCleanup.deleteAfterCommit(photoKeys);
        // 커밋이 확정된 뒤에 권한을 지운다. 공유 튜플(editor/viewer)은 공유 기능(M3)에서 함께 정리한다.
        guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), PartyGuard.OWNER, PartyGuard.USER, map.getOwnerId().toString());
        log.info("Map deleted: mapId={}, photos={}", mapId, photoKeys.size());
    }

    private MapResponse respond(PartyMap map, UUID viewerId) {
        PartyUser owner = users.findById(map.getOwnerId()).orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        MapRole role;
        if (map.isOwnedBy(viewerId)) {
            role = MapRole.OWNER;
        } else {
            // 직접 공유가 없는데 볼 수 있다면 다른 경로(모임)로 보는 것이므로 열람자다
            role = shares.findById(MapUserShare.Key.of(map.getId(), viewerId)).map(share -> toRole(share.getRole())).orElse(MapRole.VIEWER);
        }
        return MapResponse.from(map, role, owner, pins.countByMapId(map.getId()));
    }

    private static MapRole toRole(ShareRole role) {
        return role == ShareRole.EDITOR ? MapRole.EDITOR : MapRole.VIEWER;
    }

    private PartyMap find(UUID mapId) {
        return maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
