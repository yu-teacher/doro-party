package com.doro.party.domain.friend;

import com.doro.party.domain.friend.FriendDtos.FriendMaps;
import com.doro.party.domain.friend.FriendDtos.FriendMapsOverview;
import com.doro.party.domain.map.dto.MapDtos.MapResponse;
import com.doro.party.domain.map.entity.PartyMap;
import com.doro.party.domain.map.repository.PartyMapRepository;
import com.doro.party.domain.map.service.MapAssembler;
import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 친구 지도 둘러보기: 내 친구들이 "친구 전체에게 공개" 해 둔 지도를 친구별로 모아 준다. 목록은 DB(친구 관계, 지도의 공개 범위)로 만들고,
 * 실제 열람 권한은 지도를 열 때 Guard 가 판정한다. 공개한 지도가 없는 친구는 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FriendMapService {

    private final FriendshipRepository friendships;
    private final PartyMapRepository maps;
    private final PartyUserRepository users;
    private final MapAssembler assembler;

    @Transactional(readOnly = true)
    public FriendMapsOverview browse(UUID me) {
        List<UUID> friendIds = friendships.findFriends(me).stream().map(friendship -> friendship.otherThan(me)).toList();
        if (friendIds.isEmpty()) {
            return new FriendMapsOverview(List.of());
        }
        List<PartyMap> visible = maps.findFriendVisible(friendIds);
        Map<UUID, MapResponse> responses = assembler.assemble(visible, me).stream().collect(Collectors.toMap(MapResponse::id, Function.identity()));
        Map<UUID, PartyUser> people = users.findAllById(friendIds).stream().collect(Collectors.toMap(PartyUser::getId, Function.identity()));

        Map<UUID, List<MapResponse>> byOwner = new LinkedHashMap<>();
        for (PartyMap map : visible) {
            byOwner.computeIfAbsent(map.getOwnerId(), key -> new java.util.ArrayList<>()).add(responses.get(map.getId()));
        }
        List<FriendMaps> friends = byOwner.entrySet().stream()
                .map(entry -> new FriendMaps(UserSummary.from(people.get(entry.getKey())), entry.getValue()))
                .sorted(Comparator.comparing((FriendMaps item) -> item.friend().nickname(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return new FriendMapsOverview(friends);
    }
}
