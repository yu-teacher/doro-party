package com.doro.party.domain.group;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 모임: 만들기·링크/친구 초대·방장과 멤버, 지도 공유, 그리고 "멤버십이 곧 권한" 이 실제 Guard 로 지켜지는지. */
class GroupHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private String createGroup(TestUser owner, String name) throws Exception {
        String body = send(owner, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String inviteCode(TestUser owner, String groupId) throws Exception {
        String body = send(owner, post("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.code");
    }

    private ResultActions join(TestUser user, String code) throws Exception {
        return send(user, post("/api/v1/groups/invite/{c}/join", code));
    }

    /** 방장이 링크를 만들고 사용자가 그 링크로 들어온다. */
    private void joinGroup(TestUser owner, String groupId, TestUser... users) throws Exception {
        String code = inviteCode(owner, groupId);
        for (TestUser user : users) {
            join(user, code).andExpect(status().isOk());
        }
    }

    private String newMapWithPin(TestUser owner, String name) throws Exception {
        String mapId = createMap(owner, name);
        createPin(owner, mapId, pinBody("주인의 핀", 37.5, 127.0, null, null, null));
        return mapId;
    }

    private ResultActions shareToGroup(TestUser owner, String mapId, String groupId) throws Exception {
        return send(owner, put("/api/v1/maps/{m}/groups/{g}", mapId, groupId));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ------------------------------------------------------------------ 만들기와 조회

    @Test
    @DisplayName("모임을 만들면 방장이 되고, 내 모임 목록과 상세(멤버의 닉네임·색)에 나온다. 사용자명·이메일은 드러나지 않는다")
    void createListAndDetail() throws Exception {
        TestUser owner = newUser();
        String groupId = createGroup(owner, "  홍대 모임  ");

        send(owner, get("/api/v1/groups")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + groupId + "')].name").value("홍대 모임"))
                .andExpect(jsonPath("$.data[?(@.id=='" + groupId + "')].myRole").value("OWNER"))
                .andExpect(jsonPath("$.data[?(@.id=='" + groupId + "')].memberCount").value(1));
        String detail = send(owner, get("/api/v1/groups/{g}", groupId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1)).andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("username").doesNotContain(owner.email());
    }

    @Test
    @DisplayName("멤버가 아니면 모임을 볼 수 없고(403), 로그인하지 않으면 401, 이름 검증은 400")
    void accessAndValidation() throws Exception {
        TestUser owner = newUser();
        TestUser stranger = newUser();
        String groupId = createGroup(owner, "비밀 모임");

        send(stranger, get("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/groups/{g}/maps", groupId)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
        assertThat(send(stranger, get("/api/v1/groups")).andReturn().getResponse().getContentAsString()).doesNotContain(groupId);
        mockMvc.perform(get("/api/v1/groups")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/groups/{g}", groupId)).andExpect(status().isUnauthorized());
        for (String name : new String[]{"", "   ", "가".repeat(31)}) {
            send(owner, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")).andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ 초대 링크

    @Test
    @DisplayName("링크로 들어오면 멤버가 되고, 이미 멤버여도 오류가 아니며, 미리보기는 모임 이름과 인원을 보여 준다")
    void joinViaLink() throws Exception {
        TestUser owner = newUser();
        TestUser guest = newUser();
        String groupId = createGroup(owner, "링크 모임");
        String code = inviteCode(owner, groupId);

        send(guest, get("/api/v1/groups/invite/{c}", code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("링크 모임")).andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.alreadyMember").value(false));
        join(guest, code).andExpect(status().isOk()).andExpect(jsonPath("$.data.groupId").value(groupId));
        join(guest, code).andExpect(status().isOk());

        send(guest, get("/api/v1/groups/{g}", groupId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("MEMBER")).andExpect(jsonPath("$.data.members.length()").value(2));
        send(guest, get("/api/v1/groups/invite/{c}", code)).andExpect(jsonPath("$.data.alreadyMember").value(true));
        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid", groupId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("멤버는 링크를 볼 수 있지만 만들거나 없애는 건 방장만, 다시 만들면 이전 링크는 즉시 무효, 만료·엉뚱한 코드는 404")
    void inviteLinkRules() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser guest = newUser();
        String groupId = createGroup(owner, "링크 규칙");
        String first = inviteCode(owner, groupId);
        join(member, first).andExpect(status().isOk());

        send(member, get("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value(first));
        send(member, post("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isForbidden());
        send(member, delete("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isForbidden());

        String second = inviteCode(owner, groupId);
        assertThat(second).isNotEqualTo(first);
        join(guest, first).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITE-404-01"));

        jdbc.update("update group_invites set expires_at = now() - interval '1 minute' where group_id = ?::uuid", groupId);
        join(guest, second).andExpect(status().isNotFound());
        send(owner, get("/api/v1/groups/{g}/invite", groupId)).andExpect(jsonPath("$.data").doesNotExist());
        join(guest, "made-up-code").andExpect(status().isNotFound());
        join(guest, "x".repeat(200)).andExpect(status().isNotFound());
        send(guest, get("/api/v1/groups/invite/{c}", "made-up-code")).andExpect(status().isNotFound());

        String third = inviteCode(owner, groupId);
        send(owner, delete("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isOk());
        join(guest, third).andExpect(status().isNotFound());
        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid and user_id = ?::uuid", groupId, guest.id().toString())).isZero();
        mockMvc.perform(post("/api/v1/groups/invite/{c}/join", first)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("멤버가 자기 친구를 모임에 초대할 수 있고, 친구가 아닌 사람은 초대할 수 없다")
    void inviteAFriend() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser memberFriend = newUser();
        TestUser stranger = newUser();
        String groupId = createGroup(owner, "친구 초대");
        joinGroup(owner, groupId, member);
        befriend(member, memberFriend);

        send(member, post("/api/v1/groups/{g}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberFriend.id() + "\"}")).andExpect(status().isOk());
        send(member, post("/api/v1/groups/{g}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberFriend.id() + "\"}")).andExpect(status().isOk());
        send(member, post("/api/v1/groups/{g}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + stranger.id() + "\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRIEND-400-02"));
        send(member, post("/api/v1/groups/{g}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + member.id() + "\"}")).andExpect(status().isBadRequest());
        send(stranger, post("/api/v1/groups/{g}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberFriend.id() + "\"}")).andExpect(status().isForbidden());

        send(memberFriend, get("/api/v1/groups/{g}", groupId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.members.length()").value(3));
    }

    // ------------------------------------------------------------------ 지도 공유

    @Test
    @DisplayName("지도를 모임에 공유하면 멤버가 보기만 할 수 있고, 내 지도 목록에는 모임 이름과 함께 나온다")
    void shareMapWithGroup() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser outsider = newUser();
        String groupId = createGroup(owner, "공유 모임");
        joinGroup(owner, groupId, member);
        String mapId = newMapWithPin(owner, "모임에 공유");
        send(member, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());

        shareToGroup(owner, mapId, groupId).andExpect(status().isOk()).andExpect(jsonPath("$.data.groupName").value("공유 모임"));

        send(member, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER")).andExpect(jsonPath("$.data.viaGroups[0]").value("공유 모임"))
                .andExpect(jsonPath("$.data.mine").value(false)).andExpect(jsonPath("$.data.ownerId").value(owner.id().toString()));
        send(member, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        send(member, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinBody("침입", 37.6, 127.1, null, null, null)))
                .andExpect(status().isForbidden());
        send(member, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(member, get("/api/v1/maps/{m}/shares", mapId)).andExpect(status().isForbidden());
        send(member, get("/api/v1/maps/{m}/groups", mapId)).andExpect(status().isForbidden());
        // 보기만 가능해도 나만 보는 메모는 남길 수 있다
        String pinId = JsonPath.read(send(member, get("/api/v1/maps/{m}/pins", mapId)).andReturn().getResponse().getContentAsString(), "$.data[0].id");
        send(member, put("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"모임원의 비밀\"}"))
                .andExpect(status().isOk());

        String list = send(member, get("/api/v1/maps")).andReturn().getResponse().getContentAsString();
        List<String> via = JsonPath.read(list, "$.data[?(@.id=='" + mapId + "')].viaGroups[0]");
        assertThat(via).containsExactly("공유 모임");
        send(member, get("/api/v1/groups/{g}/maps", groupId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(mapId));
        send(owner, get("/api/v1/maps/{m}/groups", mapId)).andExpect(jsonPath("$.data[0].groupId").value(groupId));
        send(outsider, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(outsider, get("/api/v1/groups/{g}/maps", groupId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("내 지도만, 내가 속한 모임에만 공유할 수 있고, 같은 공유를 반복해도 오류가 아니다")
    void sharingRules() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser other = newUser();
        String groupId = createGroup(owner, "규칙 모임");
        joinGroup(owner, groupId, member);
        String ownerMap = newMapWithPin(owner, "주인 지도");
        String memberMap = newMapWithPin(member, "멤버 지도");
        String otherGroup = createGroup(other, "남의 모임");

        shareToGroup(member, ownerMap, groupId).andExpect(status().isForbidden());   // 남의 지도
        shareToGroup(owner, ownerMap, otherGroup).andExpect(status().isNotFound());   // 내가 속하지 않은 모임
        shareToGroup(owner, ownerMap, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        shareToGroup(owner, ownerMap, groupId).andExpect(status().isOk());
        shareToGroup(owner, ownerMap, groupId).andExpect(status().isOk());
        shareToGroup(member, memberMap, groupId).andExpect(status().isOk());
        assertThat(count("select count(*) from map_group_shares where group_id = ?::uuid", groupId)).isEqualTo(2L);
        send(owner, get("/api/v1/groups/{g}", groupId)).andExpect(jsonPath("$.data.mapCount").value(2));

        // 지도를 거두는 건 지도 주인만
        send(member, delete("/api/v1/maps/{m}/groups/{g}", ownerMap, groupId)).andExpect(status().isForbidden());
        send(owner, delete("/api/v1/maps/{m}/groups/{g}", ownerMap, groupId)).andExpect(status().isOk());
        send(owner, delete("/api/v1/maps/{m}/groups/{g}", ownerMap, groupId)).andExpect(status().isNotFound());
        send(member, get("/api/v1/maps/{m}", ownerMap)).andExpect(status().isForbidden());
        send(owner, get("/api/v1/maps/{m}", memberMap)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("직접 공유(editor)와 모임 공유가 겹치면 직접 공유의 권한이 이긴다")
    void directShareWinsOverGroup() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        String groupId = createGroup(owner, "겹치는 공유");
        joinGroup(owner, groupId, member);
        befriend(owner, member);
        String mapId = newMapWithPin(owner, "겹침");
        shareToGroup(owner, mapId, groupId).andExpect(status().isOk());
        shareMap(owner, mapId, member, "EDITOR").andExpect(status().isOk());

        send(member, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.role").value("EDITOR")).andExpect(jsonPath("$.data.viaGroups.length()").value(0));
        send(member, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinBody("편집자의 핀", 37.6, 127.1, null, null, null)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 멤버십이 곧 권한

    @Test
    @DisplayName("공유 뒤에 들어온 멤버도 지도를 바로 볼 수 있고, 나가면 곧바로 볼 수 없다(지도 쪽 튜플은 그대로)")
    void membershipIsPermission() throws Exception {
        TestUser owner = newUser();
        TestUser early = newUser();
        TestUser late = newUser();
        String groupId = createGroup(owner, "멤버십 모임");
        joinGroup(owner, groupId, early);
        String mapId = newMapWithPin(owner, "공유 지도");
        shareToGroup(owner, mapId, groupId).andExpect(status().isOk());
        send(late, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());

        join(late, inviteCode(owner, groupId)).andExpect(status().isOk());
        send(late, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
        assertThat(count("select count(*) from map_group_shares where map_id = ?::uuid", mapId)).isEqualTo(1L);

        send(early, delete("/api/v1/groups/{g}/members/me", groupId)).andExpect(status().isOk());
        send(early, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(early, get("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
        assertThat(send(early, get("/api/v1/maps")).andReturn().getResponse().getContentAsString()).doesNotContain(mapId);
        send(late, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("멤버가 나가면 그 사람이 모임에 공유한 지도도 거두어진다(남은 멤버는 더 못 보고, 지도는 주인의 것으로 남는다)")
    void leavingWithdrawsMySharedMaps() throws Exception {
        TestUser owner = newUser();
        TestUser leaver = newUser();
        TestUser stayer = newUser();
        String groupId = createGroup(owner, "탈퇴 모임");
        joinGroup(owner, groupId, leaver, stayer);
        String leaversMap = newMapWithPin(leaver, "나가는 사람의 지도");
        String ownersMap = newMapWithPin(owner, "방장의 지도");
        shareToGroup(leaver, leaversMap, groupId).andExpect(status().isOk());
        shareToGroup(owner, ownersMap, groupId).andExpect(status().isOk());
        send(stayer, get("/api/v1/maps/{m}", leaversMap)).andExpect(status().isOk());

        send(leaver, delete("/api/v1/groups/{g}/members/me", groupId)).andExpect(status().isOk());

        send(stayer, get("/api/v1/maps/{m}", leaversMap)).andExpect(status().isForbidden());
        send(stayer, get("/api/v1/maps/{m}", ownersMap)).andExpect(status().isOk());
        send(leaver, get("/api/v1/maps/{m}", leaversMap)).andExpect(status().isOk());
        assertThat(count("select count(*) from map_group_shares where map_id = ?::uuid", leaversMap)).isZero();
        assertThat(count("select count(*) from map_group_shares where map_id = ?::uuid", ownersMap)).isEqualTo(1L);
    }

    @Test
    @DisplayName("방장은 멤버를 내보낼 수 있고(자기 자신·비멤버는 안 됨), 내보낸 사람의 접근과 공유가 사라진다. 멤버는 못 한다")
    void kickingAMember() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser other = newUser();
        String groupId = createGroup(owner, "내보내기");
        joinGroup(owner, groupId, member, other);
        String ownersMap = newMapWithPin(owner, "방장 지도");
        String membersMap = newMapWithPin(member, "멤버 지도");
        shareToGroup(owner, ownersMap, groupId).andExpect(status().isOk());
        shareToGroup(member, membersMap, groupId).andExpect(status().isOk());

        send(other, delete("/api/v1/groups/{g}/members/{u}", groupId, member.id())).andExpect(status().isForbidden());
        send(owner, delete("/api/v1/groups/{g}/members/{u}", groupId, owner.id())).andExpect(status().isBadRequest());
        send(owner, delete("/api/v1/groups/{g}/members/{u}", groupId, UUID.randomUUID())).andExpect(status().isNotFound());

        send(owner, delete("/api/v1/groups/{g}/members/{u}", groupId, member.id())).andExpect(status().isOk());

        send(member, get("/api/v1/maps/{m}", ownersMap)).andExpect(status().isForbidden());
        send(other, get("/api/v1/maps/{m}", membersMap)).andExpect(status().isForbidden());
        send(member, get("/api/v1/maps/{m}", membersMap)).andExpect(status().isOk());
        send(other, get("/api/v1/maps/{m}", ownersMap)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 방장

    @Test
    @DisplayName("방장은 나갈 수 없고, 방장을 넘기면 이전 방장은 나갈 수 있으며 새 방장이 관리한다. 방장은 항상 한 명이다")
    void ownershipTransfer() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        TestUser outsider = newUser();
        String groupId = createGroup(owner, "방장 교체");
        joinGroup(owner, groupId, member);

        send(owner, delete("/api/v1/groups/{g}/members/me", groupId)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GROUP-400-01"));
        send(member, put("/api/v1/groups/{g}/owner", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + member.id() + "\"}")).andExpect(status().isForbidden());
        send(owner, put("/api/v1/groups/{g}/owner", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + outsider.id() + "\"}")).andExpect(status().isNotFound());
        send(owner, put("/api/v1/groups/{g}/owner", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + owner.id() + "\"}")).andExpect(status().isBadRequest());

        send(owner, put("/api/v1/groups/{g}/owner", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + member.id() + "\"}")).andExpect(status().isOk());

        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid and role = 'OWNER'", groupId)).isEqualTo(1L);
        send(member, get("/api/v1/groups/{g}", groupId)).andExpect(jsonPath("$.data.myRole").value("OWNER"));
        send(owner, get("/api/v1/groups/{g}", groupId)).andExpect(jsonPath("$.data.myRole").value("MEMBER"));
        // 권한도 따라 바뀐다: 옛 방장은 관리할 수 없고, 새 방장은 관리할 수 있다
        send(owner, patch("/api/v1/groups/{g}", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"해킹\"}")).andExpect(status().isForbidden());
        send(owner, post("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isForbidden());
        send(member, patch("/api/v1/groups/{g}", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\"}")).andExpect(status().isOk());
        send(member, get("/api/v1/groups/{g}", groupId)).andExpect(jsonPath("$.data.name").value("새 이름"));
        // 이제 옛 방장은 나갈 수 있다
        send(owner, delete("/api/v1/groups/{g}/members/me", groupId)).andExpect(status().isOk());
        send(owner, get("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이름 변경은 방장만 할 수 있다")
    void renameIsOwnerOnly() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        String groupId = createGroup(owner, "원래 이름");
        joinGroup(owner, groupId, member);

        send(member, patch("/api/v1/groups/{g}", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}")).andExpect(status().isForbidden());
        send(owner, patch("/api/v1/groups/{g}", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  바뀐 이름 \"}")).andExpect(status().isOk());
        send(member, get("/api/v1/groups/{g}", groupId)).andExpect(jsonPath("$.data.name").value("바뀐 이름"));
        send(owner, patch("/api/v1/groups/{g}", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("모임을 지우면 멤버의 접근이 모두 사라지고 지도는 각 주인의 것으로 남는다. 방장만 지울 수 있다")
    void deletingAGroup() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        String groupId = createGroup(owner, "지울 모임");
        joinGroup(owner, groupId, member);
        String ownersMap = newMapWithPin(owner, "방장 지도");
        String membersMap = newMapWithPin(member, "멤버 지도");
        shareToGroup(owner, ownersMap, groupId).andExpect(status().isOk());
        shareToGroup(member, membersMap, groupId).andExpect(status().isOk());

        send(member, delete("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
        send(owner, delete("/api/v1/groups/{g}", groupId)).andExpect(status().isOk());

        send(owner, get("/api/v1/maps/{m}", membersMap)).andExpect(status().isForbidden());
        send(member, get("/api/v1/maps/{m}", ownersMap)).andExpect(status().isForbidden());
        send(owner, get("/api/v1/maps/{m}", ownersMap)).andExpect(status().isOk());
        send(member, get("/api/v1/maps/{m}", membersMap)).andExpect(status().isOk());
        send(member, get("/api/v1/groups/{g}", groupId)).andExpect(status().isForbidden());
        assertThat(count("select count(*) from party_groups where id = ?::uuid", groupId)).isZero();
        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid", groupId)).isZero();
        assertThat(count("select count(*) from map_group_shares where group_id = ?::uuid", groupId)).isZero();
    }

    @Test
    @DisplayName("지도를 지우면 모임에서도 접근이 사라지고 모임은 그대로 남는다")
    void deletingAMapKeepsTheGroup() throws Exception {
        TestUser owner = newUser();
        TestUser member = newUser();
        String groupId = createGroup(owner, "지도 삭제");
        joinGroup(owner, groupId, member);
        String mapId = newMapWithPin(owner, "곧 삭제");
        shareToGroup(owner, mapId, groupId).andExpect(status().isOk());

        send(owner, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());

        send(member, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(member, get("/api/v1/groups/{g}/maps", groupId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        send(member, get("/api/v1/groups/{g}", groupId)).andExpect(status().isOk());
        assertThat(count("select count(*) from map_group_shares where map_id = ?::uuid", mapId)).isZero();
    }

    @Test
    @DisplayName("같은 사람이 같은 링크로 동시에 여러 번 들어와도 오류 없이 멤버 한 명으로 남는다")
    void concurrentJoins() throws Exception {
        TestUser owner = newUser();
        TestUser guest = newUser();
        String groupId = createGroup(owner, "동시 가입");
        String code = inviteCode(owner, groupId);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Callable<Integer> call = () -> join(guest, code).andReturn().getResponse().getStatus();
                futures.add(pool.submit(call));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("select count(*) from party_group_members where group_id = ?::uuid and user_id = ?::uuid", groupId, guest.id().toString())).isEqualTo(1L);
        send(guest, get("/api/v1/groups/{g}", groupId)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 방장도 남의 지도는 공유된 것만 본다

    @Test
    @DisplayName("방장도 멤버가 공유하지 않은 지도에는 접근할 수 없고, 공유된 지도는 보기만 할 수 있다(수정·핀 변경·공유 설정 모두 막힘)")
    void groupOwnerCannotReachUnsharedMapsOfMembers() throws Exception {
        TestUser leader = newUser();
        TestUser member = newUser();
        TestUser other = newUser();
        String groupId = createGroup(leader, "방장 확인");
        joinGroup(leader, groupId, member, other);
        String otherGroupId = createGroup(member, "방장이 없는 모임");

        String privateMap = newMapWithPin(member, "멤버의 비공개 지도");
        String sharedMap = newMapWithPin(member, "모임에 공유한 지도");
        String sharedElsewhere = newMapWithPin(member, "다른 모임에만 공유한 지도");
        shareToGroup(member, sharedMap, groupId).andExpect(status().isOk());
        shareToGroup(member, sharedElsewhere, otherGroupId).andExpect(status().isOk());
        String privatePin = JsonPath.read(send(member, get("/api/v1/maps/{m}/pins", privateMap)).andReturn().getResponse().getContentAsString(), "$.data[0].id");
        send(member, put("/api/v1/maps/{m}/pins/{p}/private-note", privateMap, privatePin).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"멤버만 아는 메모\"}"))
                .andExpect(status().isOk());

        // 공유하지 않은 지도: 읽기·쓰기·부가 정보 모두 막힌다
        send(leader, get("/api/v1/maps/{m}", privateMap)).andExpect(status().isForbidden());
        send(leader, get("/api/v1/maps/{m}/pins", privateMap)).andExpect(status().isForbidden());
        send(leader, get("/api/v1/maps/{m}/private-notes", privateMap)).andExpect(status().isForbidden());
        send(leader, get("/api/v1/maps/{m}/members", privateMap)).andExpect(status().isForbidden());
        send(leader, post("/api/v1/maps/{m}/pins", privateMap).contentType(MediaType.APPLICATION_JSON).content(pinBody("침입", 37.6, 127.1, null, null, null)))
                .andExpect(status().isForbidden());
        send(leader, put("/api/v1/maps/{m}/friend-access", privateMap).contentType(MediaType.APPLICATION_JSON).content("{\"access\":\"VIEWER\"}")).andExpect(status().isForbidden());
        send(leader, delete("/api/v1/maps/{m}", privateMap)).andExpect(status().isForbidden());
        shareToGroup(leader, privateMap, groupId).andExpect(status().isForbidden());

        // 목록·모임 화면·겹쳐보기·추천에도 나오지 않는다
        String mine = send(leader, get("/api/v1/maps")).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(mine, "$.data[*].id")).containsExactly(sharedMap);
        String groupMaps = send(leader, get("/api/v1/groups/{g}/maps", groupId)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(groupMaps, "$.data[*].id")).containsExactly(sharedMap);
        String overlay = send(leader, get("/api/v1/overlay/pins").param("mapIds", privateMap + "," + sharedElsewhere + "," + sharedMap))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(overlay, "$.data.mapIds")).containsExactly(sharedMap);
        assertThat((List<String>) JsonPath.read(overlay, "$.data.pins[*].mapId")).containsOnly(sharedMap);
        String recommendations = send(leader, get("/api/v1/groups/{g}/recommendations", groupId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(recommendations, "$.data.mapIds")).containsExactly(sharedMap);
        String overlayRecommendations = send(leader, get("/api/v1/overlay/recommendations").param("mapIds", privateMap + "," + sharedMap))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(overlayRecommendations, "$.data.mapIds")).containsExactly(sharedMap);

        // 방장이 속하지 않은 모임에만 공유한 지도도 볼 수 없다
        send(leader, get("/api/v1/maps/{m}", sharedElsewhere)).andExpect(status().isForbidden());
        // 공유한 지도는 방장도 다른 멤버처럼 보기만 한다
        send(leader, get("/api/v1/maps/{m}", sharedMap)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
        send(leader, delete("/api/v1/maps/{m}", sharedMap)).andExpect(status().isForbidden());
        // 모임에 공유된 지도는 방장도 고칠 수 없다: 지도 정보, 핀 추가·수정·삭제, 공유 설정 모두 막힌다
        String sharedPin = JsonPath.read(send(leader, get("/api/v1/maps/{m}/pins", sharedMap)).andReturn().getResponse().getContentAsString(), "$.data[0].id");
        send(leader, put("/api/v1/maps/{m}", sharedMap).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"방장이 바꿈\"}")).andExpect(status().isForbidden());
        send(leader, post("/api/v1/maps/{m}/pins", sharedMap).contentType(MediaType.APPLICATION_JSON).content(pinBody("방장의 핀", 37.6, 127.1, null, null, null)))
                .andExpect(status().isForbidden());
        putPin(leader, sharedMap, sharedPin, pinBody("방장이 고침", 37.6, 127.1, null, null, null)).andExpect(status().isForbidden());
        send(leader, delete("/api/v1/maps/{m}/pins/{p}", sharedMap, sharedPin)).andExpect(status().isForbidden());
        send(leader, put("/api/v1/maps/{m}/friend-access", sharedMap).contentType(MediaType.APPLICATION_JSON).content("{\"access\":\"EDITOR\"}")).andExpect(status().isForbidden());
        send(leader, delete("/api/v1/maps/{m}/groups/{g}", sharedMap, groupId)).andExpect(status().isForbidden());
        send(member, get("/api/v1/maps/{m}", sharedMap)).andExpect(jsonPath("$.data.name").value("모임에 공유한 지도"));

        // 방장을 넘겨도 새 방장 역시 공유되지 않은 지도는 볼 수 없고, 이전 방장은 멤버로서 공유된 지도를 계속 본다
        send(leader, put("/api/v1/groups/{g}/owner", groupId).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + other.id() + "\"}")).andExpect(status().isOk());
        send(other, get("/api/v1/maps/{m}", privateMap)).andExpect(status().isForbidden());
        send(other, get("/api/v1/maps/{m}", sharedMap)).andExpect(status().isOk());
        send(leader, get("/api/v1/maps/{m}", privateMap)).andExpect(status().isForbidden());
        send(leader, get("/api/v1/maps/{m}", sharedMap)).andExpect(status().isOk());

        // 지도 주인은 자기 지도를 그대로 쓴다
        send(member, get("/api/v1/maps/{m}/private-notes", privateMap)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }
}
