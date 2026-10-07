package com.doro.party.domain.map.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.friend.FriendshipRepository;
import com.doro.party.domain.group.MapGroupShare;
import com.doro.party.domain.group.MapGroupShareRepository;
import com.doro.party.domain.group.PartyGroup;
import com.doro.party.domain.group.PartyGroupRepository;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.dto.MapDtos.MapRole;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.pin.repository.PinRepository;
import com.doro.party.domain.share.FriendAccess;
import com.doro.party.domain.share.MapUserShare;
import com.doro.party.domain.share.MapUserShareRepository;
import com.doro.party.domain.share.ShareRole;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지도 목록을 응답으로 만든다. 내 권한(주인 / 직접 공유받은 editor·viewer / 모임 덕분에 보는 viewer)과 주인 정보, 핀 개수를
 * 지도마다 조회하지 않고 한 번에 모아서 계산한다.
 */
@Component
@RequiredArgsConstructor
public class MapAssembler {

    private final MapUserShareRepository shares;
    private final MapGroupShareRepository groupShares;
    private final PartyGroupRepository groups;
    private final PinRepository pins;
    private final PartyUserRepository users;
    private final FriendshipRepository friendships;

    public List<MapResponse> assemble(List<PartyMap> maps, UUID viewerId) {
        if (maps.isEmpty()) {
            return List.of();
        }
        List<UUID> mapIds = maps.stream().map(PartyMap::getId).toList();
        Map<UUID, Long> counts = pins.countByMapIds(mapIds).stream()
                .collect(Collectors.toMap(PinRepository.PinCount::getMapId, PinRepository.PinCount::getCount));
        Map<UUID, PartyUser> owners = users.findAllById(maps.stream().map(PartyMap::getOwnerId).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, Function.identity()));
        Map<UUID, ShareRole> directRoles = shares.findByUserId(viewerId).stream()
                .collect(Collectors.toMap(MapUserShare::mapId, MapUserShare::getRole));
        Map<UUID, List<String>> viaGroups = viaGroupNames(viewerId);
        Set<UUID> friendIds = friendIdsIfNeeded(maps, viewerId);

        List<MapResponse> result = new ArrayList<>();
        for (PartyMap map : maps) {
            PartyUser owner = owners.get(map.getOwnerId());
            if (owner == null) {
                throw new PartyException(ErrorCode.USER_NOT_FOUND);
            }
            MapRole role = roleOf(map, viewerId, directRoles.get(map.getId()), friendIds.contains(map.getOwnerId()));
            // 모임 이름은 모임 덕분에 보는 경우에만 보여 준다(주인이거나, 직접 공유받았거나, 친구 공개로 보면 비운다)
            boolean viaFriend = map.getFriendAccess().isPublic() && friendIds.contains(map.getOwnerId());
            List<String> via = role == MapRole.OWNER || directRoles.containsKey(map.getId()) || viaFriend ? List.of() : viaGroups.getOrDefault(map.getId(), List.of());
            result.add(MapResponse.from(map, role, owner, via, counts.getOrDefault(map.getId(), 0L)));
        }
        return result;
    }

    public MapResponse assemble(PartyMap map, UUID viewerId) {
        return assemble(List.of(map), viewerId).get(0);
    }

    /**
     * 내 권한: 주인이면 OWNER. 아니면 직접 공유(viewer/editor)와 친구 공개(주인의 친구일 때만) 중 더 높은 권한.
     * 둘 다 아닌데 볼 수 있다면 모임을 통해 보는 것이므로 열람자다.
     */
    private static MapRole roleOf(PartyMap map, UUID viewerId, ShareRole direct, boolean ownerIsMyFriend) {
        if (map.isOwnedBy(viewerId)) {
            return MapRole.OWNER;
        }
        boolean editorByFriend = ownerIsMyFriend && map.getFriendAccess() == FriendAccess.EDITOR;
        return direct == ShareRole.EDITOR || editorByFriend ? MapRole.EDITOR : MapRole.VIEWER;
    }

    /** 친구 공개 권한을 따지려면 내 친구 목록이 필요하다. 남이 만든 공개 지도가 없으면 조회하지 않는다. */
    private Set<UUID> friendIdsIfNeeded(List<PartyMap> maps, UUID viewerId) {
        boolean needed = maps.stream().anyMatch(map -> !map.isOwnedBy(viewerId) && map.getFriendAccess().isPublic());
        if (!needed) {
            return Set.of();
        }
        return friendships.findFriends(viewerId).stream().map(friendship -> friendship.otherThan(viewerId)).collect(Collectors.toSet());
    }

    /** 내가 속한 모임들 중 각 지도를 공유한 모임의 이름(이름순). */
    private Map<UUID, List<String>> viaGroupNames(UUID viewerId) {
        List<MapGroupShare> visible = groupShares.findVisibleTo(viewerId);
        if (visible.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = groups.findAllById(visible.stream().map(MapGroupShare::groupId).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyGroup::getId, PartyGroup::getName));
        Map<UUID, List<String>> byMap = new HashMap<>();
        for (MapGroupShare share : visible) {
            byMap.computeIfAbsent(share.mapId(), key -> new ArrayList<>()).add(names.get(share.groupId()));
        }
        byMap.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return byMap;
    }
}
