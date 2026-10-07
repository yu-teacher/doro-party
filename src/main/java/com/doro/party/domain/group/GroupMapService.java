package com.doro.party.domain.group;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.group.GroupDtos.MapGroupView;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.map.service.MapAssembler;
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

/**
 * 내 지도를 모임에 공유한다. 지도의 주인은 그대로이고, 모임의 모든 멤버가 그 지도의 viewer 가 된다
 * ({@code party_map:M#viewer@party_group:G#member}). 멤버가 들고 나도 이 튜플은 그대로이고 접근이 따라 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMapService {

    private final MapGroupShareRepository groupShares;
    private final GroupMemberRepository members;
    private final PartyGroupRepository groups;
    private final PartyMapRepository maps;
    private final MapAssembler assembler;
    private final GuardTuples guardTuples;
    private final PartyLimits limits;

    /** 내 지도를 내가 속한 모임에 공유한다. 이미 공유했다면 아무것도 하지 않는다. */
    @Transactional
    public MapGroupView share(UUID mapId, UUID groupId, DoroUser owner) {
        PartyMap map = maps.findByIdForUpdate(mapId).orElseThrow(() -> new PartyException(ErrorCode.MAP_NOT_FOUND));
        PartyGroup group = groups.findById(groupId).orElseThrow(() -> new PartyException(ErrorCode.GROUP_NOT_FOUND));
        if (!map.isOwnedBy(owner.userId())) {
            throw new PartyException(ErrorCode.ACCESS_DENIED);
        }
        if (!members.existsById(GroupMember.Key.of(groupId, owner.userId()))) {
            // 내가 속하지 않은 모임은 없는 것처럼 보인다
            throw new PartyException(ErrorCode.GROUP_NOT_FOUND);
        }
        MapGroupShare existing = groupShares.findById(MapGroupShare.Key.of(mapId, groupId)).orElse(null);
        if (existing != null) {
            return new MapGroupView(groupId, group.getName(), existing.getCreatedAt());
        }
        if (groupShares.countByMapId(mapId) >= limits.maxGroupsPerMap()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "지도 하나는 최대 " + limits.maxGroupsPerMap() + "개의 모임에 공유할 수 있습니다.");
        }
        MapGroupShare saved = groupShares.saveAndFlush(new MapGroupShare(mapId, groupId, owner.userId()));
        guardTuples.write(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER);
        log.info("Map shared with group: mapId={}, groupId={}, by={}", mapId, groupId, owner.userId());
        return new MapGroupView(groupId, group.getName(), saved.getCreatedAt());
    }

    /** 모임에서 내 지도를 거둔다(지도 주인만). */
    @Transactional
    public void unshare(UUID mapId, UUID groupId) {
        MapGroupShare share = groupShares.findById(MapGroupShare.Key.of(mapId, groupId))
                .orElseThrow(() -> new PartyException(ErrorCode.SHARE_NOT_FOUND));
        groupShares.delete(share);
        guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.GROUP, groupId.toString(), PartyGuard.MEMBER);
        log.info("Map unshared from group: mapId={}, groupId={}", mapId, groupId);
    }

    /** 이 지도를 공유해 둔 모임들(지도 주인용). */
    @Transactional(readOnly = true)
    public List<MapGroupView> groupsOfMap(UUID mapId) {
        List<MapGroupShare> rows = groupShares.findByMapId(mapId);
        Map<UUID, PartyGroup> byId = groups.findAllById(rows.stream().map(MapGroupShare::groupId).toList()).stream()
                .collect(Collectors.toMap(PartyGroup::getId, Function.identity()));
        return rows.stream().map(row -> new MapGroupView(row.groupId(), byId.get(row.groupId()).getName(), row.getCreatedAt())).toList();
    }

    /** 이 모임에 공유된 지도들. 모임의 멤버라면 누구나 볼 수 있다(각 지도에서의 내 권한과 주인 정보 포함). */
    @Transactional(readOnly = true)
    public List<MapResponse> mapsOfGroup(UUID groupId, DoroUser viewer) {
        List<UUID> mapIds = groupShares.findByGroupId(groupId).stream().map(MapGroupShare::mapId).toList();
        List<PartyMap> found = maps.findAllById(mapIds).stream().sorted(java.util.Comparator.comparing(PartyMap::getName)).toList();
        return assembler.assemble(found, viewer.userId());
    }

    /** 지도를 지울 때: 모임에 준 보기 권한(Guard)을 커밋 뒤에 지운다(공유 행은 지도와 함께 DB 가 지운다). */
    public void releaseGuardForMap(UUID mapId) {
        for (MapGroupShare share : groupShares.findByMapId(mapId)) {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, mapId.toString(), PartyGuard.VIEWER, PartyGuard.GROUP, share.groupId().toString(), PartyGuard.MEMBER);
        }
    }
}
