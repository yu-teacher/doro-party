package com.doro.party.domain.user.sync;

import com.doro.party.domain.push.PushTestSupport.TestDevice;
import com.doro.party.infra.guard.PartyGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Doro 에서 영구 탈퇴한 사용자의 도로 파티 데이터 정리를 실제 DB·MinIO·Guard 로 검증한다. */
class DeletedAccountAnonymizerTest extends DeletedAccountTestSupport {

    @Autowired private DeletedAccountAnonymizer anonymizer;

    @Test
    @DisplayName("익명화하면 본인 전용 데이터는 지워지고, 공유 지도·핀·댓글·방문 기록은 남아 작성자가 '탈퇴한 사용자'로 보인다")
    void removesPersonalDataAndKeepsSharedContentAnonymized() throws Exception {
        TestUser victim = newUser();
        TestUser friend = newUser();
        TestUser pending = newUser();
        befriend(victim, friend);                       // 초대 링크(victim)도 함께 생긴다
        String friendUsername = jdbc.queryForObject("select username from party_users where id = ?", String.class, pending.id());
        // 대기 중인 친구 요청(보낸 것)
        send(victim, post("/api/v1/friends/requests").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + friendUsername + "\"}")).andExpect(status().isOk());
        String nickBefore = jdbc.queryForObject("select nickname from party_users where id = ?", String.class, victim.id());

        // victim 의 공유 지도(친구에게 공유 + 친구 전체 공개)
        String shared = createMap(victim, "함께 보는 지도");
        String pinShared = pin(victim, shared, "내가 꽂은 핀");
        shareMap(victim, shared, friend, "EDITOR").andExpect(status().isOk());
        friendAccess(victim, shared, "VIEWER");
        String sharedPhoto = upload(victim, shared, pinShared);
        String sharedPhotoKey = objectKeyOf(sharedPhoto);
        note(victim, shared, pinShared, "나만 보는 메모");
        visit(victim, shared, pinShared);
        comment(victim, shared, pinShared, "내 댓글");
        markRead(victim, shared, pinShared);
        // 알림 받던 기기와 알림 설정(친구의 기기는 그대로 남아야 한다)
        TestDevice victimPhone = new TestDevice();
        TestDevice friendPhone = new TestDevice();
        send(victim, put("/api/v1/push/subscription").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(victimPhone.subscriptionJson())).andExpect(status().isOk());
        send(friend, put("/api/v1/push/subscription").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(friendPhone.subscriptionJson())).andExpect(status().isOk());
        send(victim, put("/api/v1/push/preferences").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"comments\":false,\"friendRequests\":true,\"shares\":true}")).andExpect(status().isOk());

        // 친구의 지도에 victim 이 참여
        String friendMap = createMap(friend, "친구 지도");
        shareMap(friend, friendMap, victim, "EDITOR").andExpect(status().isOk());
        String pinF = pin(victim, friendMap, "친구 지도에 내가 꽂은 핀");
        String friendPhotoKey = objectKeyOf(upload(victim, friendMap, pinF));
        note(victim, friendMap, pinF, "친구 지도의 내 메모");
        comment(victim, friendMap, pinF, "친구 지도 댓글");

        // 아무와도 공유하지 않은 지도
        String solo = createMap(victim, "혼자 쓰는 지도");
        String soloPin = pin(victim, solo, "혼자 핀");
        String soloPhotoKey = objectKeyOf(upload(victim, solo, soloPin));

        assertThat(anonymizer.anonymize(victim.id(), Instant.now())).isTrue();

        // 프로필 익명화
        Map<String, Object> row = partyUser(victim.id());
        assertThat((String) row.get("username")).isEqualTo("deleted-" + victim.id().toString().replace("-", ""));
        assertThat(row.get("nickname")).isEqualTo("탈퇴한 사용자").isNotEqualTo(nickBefore);
        assertThat(row.get("deleted_at")).isNotNull();
        // 본인 전용 데이터 삭제
        assertThat(count("select count(*) from pin_private_notes where user_id = ?", victim.id())).isZero();
        assertThat(count("select count(*) from pin_comment_reads where user_id = ?", victim.id())).isZero();
        assertThat(count("select count(*) from push_subscriptions where user_id = ?", victim.id())).as("푸시 구독").isZero();
        assertThat(count("select count(*) from push_preferences where user_id = ?", victim.id())).as("알림 설정").isZero();
        assertThat(count("select count(*) from push_subscriptions where user_id = ?", friend.id())).as("친구의 구독은 그대로").isEqualTo(1);
        assertThat(count("select count(*) from friend_invites where owner_id = ?", victim.id())).isZero();
        assertThat(count("select count(*) from friendships where user_low_id = ? or user_high_id = ?", victim.id(), victim.id())).isZero();
        assertThat(count("select count(*) from auth_sessions where user_id = ?", victim.id())).as("로그인 세션").isZero();
        assertThat(count("select count(*) from map_user_shares where user_id = ?", victim.id())).isZero();
        // 사진: 어느 지도에 올렸든 파일과 기록 삭제
        assertThat(count("select count(*) from pin_photos where uploaded_by = ?", victim.id())).isZero();
        assertThat(stored(sharedPhotoKey)).isFalse();
        assertThat(stored(friendPhotoKey)).isFalse();
        assertThat(stored(soloPhotoKey)).isFalse();
        // 아무도 볼 수 없던 지도는 내용과 함께 삭제
        assertThat(count("select count(*) from party_maps where id = ?::uuid", solo)).isZero();
        assertThat(count("select count(*) from pins where id = ?::uuid", soloPin)).isZero();
        // 공유 지도·핀·댓글·방문 기록은 남는다(작성자 참조는 익명화된 행)
        assertThat(count("select count(*) from party_maps where id = ?::uuid and owner_id = ?", shared, victim.id())).isEqualTo(1);
        assertThat(count("select count(*) from party_maps where id = ?::uuid and friend_access = 'NONE'", shared)).as("친구 공개는 꺼진다").isEqualTo(1);
        assertThat(count("select count(*) from pins where created_by = ?", victim.id())).isEqualTo(2);
        assertThat(count("select count(*) from pin_comments where user_id = ?", victim.id())).isEqualTo(2);
        assertThat(count("select count(*) from visit_logs where user_id = ?", victim.id())).isEqualTo(1);
        assertThat(count("select count(*) from map_user_shares where map_id = ?::uuid and user_id = ?", shared, friend.id())).as("공유받은 사람의 공유는 그대로").isEqualTo(1);

        // Guard: 탈퇴한 사용자를 가리키는 튜플은 없고, 남은 사람의 접근은 그대로다
        assertThat(has(PartyGuard.MAP, shared, PartyGuard.OWNER, victim.id())).isFalse();
        assertThat(has(PartyGuard.MAP, shared, PartyGuard.VIEWER, victim.id())).isFalse();
        assertThat(has(PartyGuard.MAP, friendMap, PartyGuard.EDITOR, victim.id())).isFalse();
        assertThat(has(PartyGuard.FRIENDS, friend.id().toString(), PartyGuard.FRIEND, victim.id())).isFalse();
        assertThat(has(PartyGuard.FRIENDS, victim.id().toString(), PartyGuard.FRIEND, friend.id())).isFalse();
        assertThat(has(PartyGuard.MAP, shared, PartyGuard.EDITOR, friend.id())).isTrue();
        assertThat(has(PartyGuard.MAP, friendMap, PartyGuard.OWNER, friend.id())).isTrue();
        assertThat(has(PartyGuard.MAP, solo, PartyGuard.OWNER, victim.id())).isFalse();

        // 남은 지도에서 작성자는 익명화된 이름으로 보인다
        send(friend, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/maps/{m}/pins/{p}/comments", friendMap, pinF))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].authorNickname").value("탈퇴한 사용자"));
    }

    @Test
    @DisplayName("이미 처리했거나 도로 파티 사용자가 아닌 ID 에는 다시 호출해도 false 이고 아무것도 바꾸지 않는다")
    void isIdempotent() throws Exception {
        TestUser user = newUser();
        createMap(user, "지도");

        assertThat(anonymizer.anonymize(user.id(), Instant.now())).isTrue();
        Object firstDeletedAt = partyUser(user.id()).get("deleted_at");
        assertThat(anonymizer.anonymize(user.id(), Instant.now().plusSeconds(60))).isFalse();

        assertThat(partyUser(user.id()).get("deleted_at")).isEqualTo(firstDeletedAt);
        assertThat(anonymizer.anonymize(UUID.randomUUID(), Instant.now())).as("도로 파티를 쓴 적 없는 사용자").isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 데이터와 지도, Guard 권한에는 손대지 않는다")
    void leavesOtherUsersAlone() throws Exception {
        TestUser victim = newUser();
        TestUser other = newUser();
        String otherMap = createMap(other, "남의 지도");
        String otherPin = pin(other, otherMap, "남의 핀");
        String otherKey = objectKeyOf(upload(other, otherMap, otherPin));
        note(other, otherMap, otherPin, "남의 메모");
        createMap(victim, "내 지도");

        anonymizer.anonymize(victim.id(), Instant.now());

        assertThat(stored(otherKey)).isTrue();
        assertThat(count("select count(*) from pin_private_notes where user_id = ?", other.id())).isEqualTo(1);
        assertThat(has(PartyGuard.MAP, otherMap, PartyGuard.OWNER, other.id())).isTrue();
        assertThat(partyUser(other.id()).get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("모임: 다른 멤버가 있으면 방장을 가장 먼저 들어온 멤버에게 넘기고, 혼자인 모임은 지도 공유와 함께 사라진다")
    void groupsAreHandedOverOrRemoved() throws Exception {
        TestUser victim = newUser();
        TestUser first = newUser();
        TestUser second = newUser();
        String group = createGroup(victim, "내가 방장");
        joinGroup(victim, group, first);
        joinGroup(victim, group, second);
        String groupMap = createMap(victim, "모임 지도");
        shareToGroup(victim, groupMap, group);

        String soloGroup = createGroup(victim, "혼자 모임");
        String soloGroupMap = createMap(victim, "혼자 모임 지도");
        shareToGroup(victim, soloGroupMap, soloGroup);

        // 다른 사람이 방장인 모임에 멤버로 참여
        String othersGroup = createGroup(first, "남의 모임");
        joinGroup(first, othersGroup, victim);

        assertThat(anonymizer.anonymize(victim.id(), Instant.now())).isTrue();

        assertThat(jdbc.queryForObject("select owner_id from party_groups where id = ?::uuid", UUID.class, group)).isEqualTo(first.id());
        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid and user_id = ? and role = 'OWNER'", group, first.id())).isEqualTo(1);
        assertThat(count("select count(*) from party_group_members where user_id = ?", victim.id())).isZero();
        assertThat(has(PartyGuard.GROUP, group, PartyGuard.OWNER, first.id())).isTrue();
        assertThat(has(PartyGuard.GROUP, group, PartyGuard.MEMBER, first.id())).isTrue();
        assertThat(has(PartyGuard.GROUP, group, PartyGuard.MEMBER, second.id())).isTrue();
        assertThat(has(PartyGuard.GROUP, group, PartyGuard.OWNER, victim.id())).isFalse();
        assertThat(has(PartyGuard.GROUP, group, PartyGuard.MEMBER, victim.id())).isFalse();
        // 멤버가 있는 모임에 공유된 지도는 남고, 멤버들은 계속 볼 수 있다
        assertThat(count("select count(*) from party_maps where id = ?::uuid", groupMap)).isEqualTo(1);
        assertThat(has(PartyGuard.MAP, groupMap, PartyGuard.VIEWER, second.id())).isTrue();
        assertThat(has(PartyGuard.MAP, groupMap, PartyGuard.OWNER, victim.id())).isFalse();
        // 혼자였던 모임과 그 지도는 삭제
        assertThat(count("select count(*) from party_groups where id = ?::uuid", soloGroup)).isZero();
        assertThat(count("select count(*) from party_maps where id = ?::uuid", soloGroupMap)).isZero();
        assertThat(has(PartyGuard.GROUP, soloGroup, PartyGuard.OWNER, victim.id())).isFalse();
        // 남의 모임에서는 그냥 빠진다
        assertThat(count("select count(*) from party_groups where id = ?::uuid", othersGroup)).isEqualTo(1);
        assertThat(has(PartyGuard.GROUP, othersGroup, PartyGuard.MEMBER, victim.id())).isFalse();
        assertThat(has(PartyGuard.GROUP, othersGroup, PartyGuard.OWNER, first.id())).isTrue();
    }

    @Test
    @DisplayName("다른 사람이 핀을 남긴 지도는 공유받은 사람이 없어도(친구 공개로 참여했어도) 지우지 않고 남긴다")
    void keepsMapsWithOthersContributions() throws Exception {
        TestUser victim = newUser();
        TestUser friend = newUser();
        befriend(victim, friend);
        String map = createMap(victim, "친구가 참여한 지도");
        friendAccess(victim, map, "EDITOR");
        String friendPin = pin(friend, map, "친구가 꽂은 핀");
        String friendKey = objectKeyOf(upload(friend, map, friendPin));

        anonymizer.anonymize(victim.id(), Instant.now());

        assertThat(count("select count(*) from party_maps where id = ?::uuid", map)).isEqualTo(1);
        assertThat(count("select count(*) from pins where id = ?::uuid", friendPin)).isEqualTo(1);
        assertThat(stored(friendKey)).as("남이 올린 사진은 그대로").isTrue();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, victim.id())).isFalse();
        // 친구 관계가 사라지므로 친구 전체 공개는 꺼진다
        assertThat(has(PartyGuard.MAP, map, PartyGuard.EDITOR, friend.id())).isFalse();
    }

    @Test
    @DisplayName("지도를 공유받은 적 없는 사용자의 지도에 쓴 모든 Guard 튜플이 지도와 함께 정리된다")
    void deletedMapLeavesNoTuples() throws Exception {
        TestUser victim = newUser();
        TestUser friend = newUser();
        befriend(victim, friend);
        String map = createMap(victim, "곧 사라질 지도");
        shareMap(victim, map, friend, "VIEWER").andExpect(status().isOk());
        send(victim, delete("/api/v1/maps/{m}/shares/{u}", map, friend.id())).andExpect(status().isOk());
        friendAccess(victim, map, "VIEWER");

        anonymizer.anonymize(victim.id(), Instant.now());

        assertThat(count("select count(*) from party_maps where id = ?::uuid", map)).isZero();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, victim.id())).isFalse();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.VIEWER, friend.id())).isFalse();
    }
}
