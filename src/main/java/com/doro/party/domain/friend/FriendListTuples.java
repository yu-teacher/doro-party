package com.doro.party.domain.friend;

import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Guard 의 친구 목록(party_friends:<사용자>#friend@user:<친구>)을 DB 의 친구 관계에 맞춰 쓰고 지운다. DB 의 friendships 가 원본이다.
 *
 * <p>두 사람이 친구가 되면 서로의 목록에 서로를 넣는다. 지도를 "친구 전체에게 공개"하면 주인의 목록 전체가 지도의 viewer/editor 가 되므로,
 * 친구가 늘고 줄 때 지도 쪽 튜플은 건드리지 않는다. 쓰기는 트랜잭션 안에서(실패하면 함께 되돌린다), 삭제는 커밋 뒤에 한다.
 */
@Component
@RequiredArgsConstructor
public class FriendListTuples {

    private final GuardTuples guardTuples;

    /** 두 사람이 친구가 되었다: 서로의 친구 목록에 넣는다. 이미 있어도 안전하다. */
    public void added(UUID a, UUID b) {
        put(a, b);
        put(b, a);
    }

    /** 친구를 끊었다: 서로의 친구 목록에서 뺀다(커밋 뒤). 처음부터 없던 튜플이어도 안전하다. */
    public void removed(UUID a, UUID b) {
        guardTuples.deleteAfterCommit(PartyGuard.FRIENDS, a.toString(), PartyGuard.FRIEND, PartyGuard.USER, b.toString());
        guardTuples.deleteAfterCommit(PartyGuard.FRIENDS, b.toString(), PartyGuard.FRIEND, PartyGuard.USER, a.toString());
    }

    /**
     * 이 사용자의 친구 목록을 DB 기준으로 맞춰 쓴다. 이 기능이 생기기 전에 맺은 친구 관계에는 튜플이 없을 수 있으므로,
     * 지도를 처음 공개할 때 호출해 빠진 것을 채운다(이미 있는 것은 그대로).
     */
    public void ensureAll(UUID owner, Collection<UUID> friendIds) {
        friendIds.forEach(friend -> put(owner, friend));
    }

    private void put(UUID owner, UUID friend) {
        guardTuples.write(PartyGuard.FRIENDS, owner.toString(), PartyGuard.FRIEND, PartyGuard.USER, friend.toString());
    }
}
