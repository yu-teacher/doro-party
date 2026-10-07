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
import com.doro.party.domain.friend.FriendListTuples;
import com.doro.party.domain.friend.FriendshipRepository;
import com.doro.party.domain.group.GroupMapService;
import com.doro.party.domain.group.MapGroupShareRepository;
import com.doro.party.domain.share.FriendAccess;
import com.doro.party.domain.share.MapUserShareRepository;
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
    private final MapAssembler assembler;
    private final MapGroupShareRepository groupShares;
    private final GroupMapService groupMapService;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final GuardTuples guardTuples;
    private final StorageCleanup storageCleanup;
    private final PartyLimits limits;
    private final FriendshipRepository friendships;
    private final FriendListTuples friendList;

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
        return MapResponse.from(saved, MapRole.OWNER, user, List.of(), 0);
    }

    /** 내가 볼 수 있는 지도: 내가 만든 지도, 친구가 직접 공유한 지도, 내가 속한 모임에 공유된 지도. 권한(role)과 주인 정보를 함께 준다. */
    @Transactional(readOnly = true)
    public List<MapResponse> listAccessible(DoroUser doroUser) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        Map<UUID, PartyMap> visible = new java.util.LinkedHashMap<>();
        maps.findAllByOwnerIdOrderByCreatedAtDesc(user.getId()).forEach(map -> visible.put(map.getId(), map));

        java.util.Set<UUID> sharedIds = new java.util.LinkedHashSet<>();
        shares.findByUserId(user.getId()).forEach(share -> sharedIds.add(share.mapId()));
        groupShares.findVisibleTo(user.getId()).forEach(share -> sharedIds.add(share.mapId()));
        sharedIds.removeAll(visible.keySet());
        maps.findAllById(sharedIds).stream()
                .sorted(java.util.Comparator.comparing(PartyMap::getName))
                .forEach(map -> visible.put(map.getId(), map));
        return assembler.assemble(new java.util.ArrayList<>(visible.values()), user.getId());
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
        groupMapService.releaseGuardForMap(mapId);
        if (map.getFriendAccess().isPublic()) {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), map.getFriendAccess().guardRelation(), PartyGuard.FRIENDS,
                    map.getOwnerId().toString(), PartyGuard.FRIEND);
        }
        maps.delete(map);
        // 지도와 핀·사진 기록은 DB 가 함께 지운다. 스토리지의 사진 파일은 커밋 뒤에 지운다.
        storageCleanup.deleteAfterCommit(photoKeys);
        // 커밋이 확정된 뒤에 권한을 지운다. 공유 튜플(editor/viewer)은 공유 기능(M3)에서 함께 정리한다.
        guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), PartyGuard.OWNER, PartyGuard.USER, map.getOwnerId().toString());
        log.info("Map deleted: mapId={}, photos={}", mapId, photoKeys.size());
    }

    /**
     * 지도를 친구 전체에게 공개하는 범위를 바꾼다(주인만). 지금 친구와 앞으로 생길 친구 모두에게 적용되고, 친구를 끊으면 자동으로 보이지 않게 된다.
     * DB 의 friend_access 가 원본이고 Guard 에는 주인의 친구 목록을 지도의 viewer/editor 로 거는 튜플 하나가 쓰인다.
     * 같은 요청을 반복해도 안전하다.
     */
    @Transactional
    public MapResponse setFriendAccess(UUID mapId, DoroUser doroUser, FriendAccess access) {
        // 같은 지도에 대한 동시 변경을 직렬화한다(튜플 쓰기·삭제가 엇갈리지 않게)
        PartyMap map = maps.findByIdForUpdate(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        FriendAccess previous = map.getFriendAccess();
        if (previous != access) {
            String ownerId = map.getOwnerId().toString();
            if (access.isPublic()) {
                // 이 기능이 생기기 전에 맺은 친구에게도 적용되도록 주인의 친구 목록을 Guard 에 맞춰 둔다(이미 있는 것은 그대로)
                friendList.ensureAll(map.getOwnerId(), friendships.findFriends(map.getOwnerId()).stream()
                        .map(friendship -> friendship.otherThan(map.getOwnerId())).toList());
                // 새 권한을 먼저 쓰고 이전 권한은 커밋된 뒤에 지운다(잠깐이라도 접근이 사라지는 일이 없게)
                guardTuples.write(PartyGuard.MAP, mapId.toString(), access.guardRelation(), PartyGuard.FRIENDS, ownerId, PartyGuard.FRIEND);
            }
            if (previous.isPublic()) {
                guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), previous.guardRelation(), PartyGuard.FRIENDS, ownerId, PartyGuard.FRIEND);
            }
            map.changeFriendAccess(access);
            log.info("Map friend access changed: mapId={}, {} -> {}", mapId, previous, access);
        }
        return respond(map, doroUser.userId());
    }

    private MapResponse respond(PartyMap map, UUID viewerId) {
        return assembler.assemble(map, viewerId);
    }

    private PartyMap find(UUID mapId) {
        return maps.findById(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
