package com.doro.party.infra.guard;

import com.doro.party.support.PartyHttpTestBase;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모임 설계의 전제: "모임의 멤버는 지도의 viewer" 를 {@code party_map:M#viewer@party_group:G#member} 한 줄로 표현하고,
 * 멤버가 들고 나면 지도 쪽 튜플을 고치지 않아도 접근이 따라 바뀐다. 실제 Guard 로 확인한다.
 */
class GroupUsersetGuardTest extends PartyHttpTestBase {

    @Autowired private GuardTuples tuples;
    @Autowired private DoroGuardClient guard;

    @Test
    @DisplayName("모임에 지도를 공유하면 멤버만 viewer 가 되고, 멤버가 나가면 접근이 사라진다")
    void membershipBecomesPermission() {
        String map = UUID.randomUUID().toString();
        String group = UUID.randomUUID().toString();
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        String outsider = UUID.randomUUID().toString();

        tuples.write(PartyGuard.MAP, map, PartyGuard.OWNER, PartyGuard.USER, owner);
        tuples.write(PartyGuard.GROUP, group, PartyGuard.MEMBER, PartyGuard.USER, member);
        tuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.GROUP, group, PartyGuard.MEMBER);

        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, member)).as("모임 멤버는 viewer").isTrue();
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.EDITOR, member)).as("모임이 주는 건 보기까지").isFalse();
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, outsider)).as("멤버가 아니면 접근 불가").isFalse();
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, owner)).isTrue();

        // 새 멤버가 들어오면 지도 쪽 튜플은 그대로인데도 볼 수 있다
        tuples.write(PartyGuard.GROUP, group, PartyGuard.MEMBER, PartyGuard.USER, outsider);
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, outsider)).isTrue();

        // 멤버가 나가면 곧바로 사라진다
        tuples.deleteAfterCommit(PartyGuard.GROUP, group, PartyGuard.MEMBER, PartyGuard.USER, member);
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, member)).isFalse();
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, outsider)).isTrue();

        // 모임에서 지도 공유를 끊으면 남은 멤버도 접근할 수 없다
        tuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.GROUP, group, PartyGuard.MEMBER);
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, outsider)).isFalse();
    }

    @Test
    @DisplayName("모임 방장(owner)도 멤버로 취급된다")
    void ownerIsAMember() {
        String map = UUID.randomUUID().toString();
        String group = UUID.randomUUID().toString();
        String groupOwner = UUID.randomUUID().toString();
        tuples.write(PartyGuard.GROUP, group, PartyGuard.OWNER, PartyGuard.USER, groupOwner);
        tuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.GROUP, group, PartyGuard.MEMBER);

        assertThat(guard.check(PartyGuard.GROUP, group, PartyGuard.MEMBER, groupOwner)).isTrue();
        assertThat(guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, groupOwner)).isTrue();
    }
}
