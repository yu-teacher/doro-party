package com.doro.party.domain.share;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지도를 친구 전체에게 공개: 보기/편집 범위, 앞으로 생길 친구와 이미 있던 친구 모두에게 적용, 친구를 끊으면 자동 회수,
 * 직접 공유와의 관계, 친구 지도 둘러보기. 실제 Guard(친구 목록 userset)로 검증한다.
 */
class FriendAccessHttpTest extends PartyHttpTestBase {

    private ResultActions setAccess(TestUser user, String mapId, String access) throws Exception {
        return send(user, put("/api/v1/maps/{m}/friend-access", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"access\":\"" + access + "\"}"));
    }

    private String pinPayload(String name) {
        return pinBody(name, 37.6, 127.1, null, null, null);
    }

    private String usernameOf(TestUser user) throws Exception {
        String body = send(user, get("/api/v1/me")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.username");
    }

    /** 사용자명 요청 -> 수락 경로로 친구가 된다(초대 링크 경로는 befriend). */
    private void befriendByRequest(TestUser requester, TestUser accepter) throws Exception {
        send(requester, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usernameOf(accepter) + "\"}"))
                .andExpect(status().isOk());
        String body = send(accepter, get("/api/v1/friends")).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(body, "$.data.incoming[0].id");
        send(accepter, post("/api/v1/friends/requests/{id}/accept", requestId)).andExpect(status().isOk());
    }

    private String mapWithPin(TestUser owner, String name) throws Exception {
        String mapId = createMap(owner, name);
        createPin(owner, mapId, pinBody("주인의 핀", 37.5, 127.0, null, null, null));
        return mapId;
    }

    // ------------------------------------------------------------------ 보기 / 편집

    @Test
    @DisplayName("친구 전체에게 VIEWER 로 공개하면 친구는 보기만 한다. 공개 전에는 접근할 수 없다")
    void viewerAccess() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = mapWithPin(owner, "맛집");

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());

        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk()).andExpect(jsonPath("$.data.friendAccess").value("VIEWER"));

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER")).andExpect(jsonPath("$.data.mine").value(false))
                .andExpect(jsonPath("$.data.viaGroups", empty())).andExpect(jsonPath("$.data.friendAccess").value("VIEWER"));
        send(friend, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("침입"))).andExpect(status().isForbidden());
        send(friend, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("EDITOR 로 공개하면 친구는 핀을 꽂을 수 있고 role 은 EDITOR 이다. 지도 삭제·이름 변경은 여전히 주인만")
    void editorAccess() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = mapWithPin(owner, "같이 쓰는 지도");

        setAccess(owner, mapId, "EDITOR").andExpect(status().isOk());

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.role").value("EDITOR"));
        createPin(friend, mapId, pinPayload("친구가 꽂은 핀"));
        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data.length()").value(2));
        send(friend, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(friend, put("/api/v1/maps/{m}", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("범위를 VIEWER -> EDITOR -> VIEWER -> NONE 으로 바꾸면 권한이 그대로 따라간다")
    void changingScope() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = mapWithPin(owner, "범위 변경");

        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("a"))).andExpect(status().isForbidden());

        setAccess(owner, mapId, "EDITOR").andExpect(status().isOk());
        createPin(friend, mapId, pinPayload("b"));

        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("c"))).andExpect(status().isForbidden());

        setAccess(owner, mapId, "NONE").andExpect(status().isOk()).andExpect(jsonPath("$.data.friendAccess").value("NONE"));
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(friend, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("같은 범위를 반복해서 보내도 안전하다")
    void idempotent() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "멱등");

        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());
        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
        setAccess(owner, mapId, "NONE").andExpect(status().isOk());
        setAccess(owner, mapId, "NONE").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 누구에게 적용되나

    @Test
    @DisplayName("친구가 아닌 사람과 친구의 친구는 볼 수 없다")
    void onlyDirectFriends() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser friendOfFriend = newUser();
        TestUser stranger = newUser();
        befriend(owner, friend);
        befriend(friend, friendOfFriend);
        String mapId = mapWithPin(owner, "내 지도");
        setAccess(owner, mapId, "EDITOR").andExpect(status().isOk());

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
        send(friendOfFriend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개하기 전에 이미 친구였던 사람도, 공개한 뒤에 친구가 된 사람도 (초대 링크·요청 수락·맞요청 모두) 자동으로 볼 수 있다")
    void appliesToExistingAndFutureFriends() throws Exception {
        TestUser owner = newUser();
        TestUser before = newUser();
        befriend(owner, before);
        String mapId = mapWithPin(owner, "자동 적용");
        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());

        TestUser viaInvite = newUser();
        TestUser viaRequest = newUser();
        TestUser viaMutual = newUser();
        TestUser stillStranger = newUser();
        befriend(owner, viaInvite);
        befriendByRequest(viaRequest, owner);
        // 서로 먼저 요청해서 바로 친구가 되는 경로
        send(owner, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usernameOf(viaMutual) + "\"}")).andExpect(status().isOk());
        send(viaMutual, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + usernameOf(owner) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        for (TestUser friend : new TestUser[]{before, viaInvite, viaRequest, viaMutual}) {
            send(friend, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk());
        }
        send(stillStranger, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("친구를 끊으면 양쪽이 서로에게 공개한 지도가 모두 자동으로 보이지 않게 된다")
    void unfriendRevokes() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        befriend(alice, bob);
        String aliceMap = mapWithPin(alice, "앨리스 지도");
        String bobMap = mapWithPin(bob, "밥 지도");
        setAccess(alice, aliceMap, "VIEWER").andExpect(status().isOk());
        setAccess(bob, bobMap, "EDITOR").andExpect(status().isOk());
        send(bob, get("/api/v1/maps/{m}", aliceMap)).andExpect(status().isOk());
        send(alice, get("/api/v1/maps/{m}", bobMap)).andExpect(status().isOk());

        send(alice, delete("/api/v1/friends/{u}", bob.id())).andExpect(status().isOk());

        send(bob, get("/api/v1/maps/{m}", aliceMap)).andExpect(status().isForbidden());
        send(alice, get("/api/v1/maps/{m}", bobMap)).andExpect(status().isForbidden());
        // 공개 설정 자체는 남아 있어서 다시 친구가 되면 곧바로 다시 보인다
        befriend(alice, bob);
        send(bob, get("/api/v1/maps/{m}", aliceMap)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 권한

    @Test
    @DisplayName("공개 범위는 주인만 바꿀 수 있다(편집 권한이 있는 친구도, 낯선 사람도, 로그인하지 않은 사람도 안 된다)")
    void onlyOwnerCanChange() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser stranger = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "주인만");
        setAccess(owner, mapId, "EDITOR").andExpect(status().isOk());

        setAccess(friend, mapId, "NONE").andExpect(status().isForbidden());
        setAccess(stranger, mapId, "VIEWER").andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/maps/{m}/friend-access", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"access\":\"NONE\"}")).andExpect(status().isUnauthorized());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 범위 값이나 빈 본문은 400")
    void invalidBody() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "검증");

        setAccess(owner, mapId, "EVERYONE").andExpect(status().isBadRequest());
        send(owner, put("/api/v1/maps/{m}/friend-access", mapId).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        send(owner, put("/api/v1/maps/{m}/friend-access", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"access\":null}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("새 지도의 기본값은 비공개(NONE)다")
    void defaultIsPrivate() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "기본");
        send(owner, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.friendAccess").value("NONE"));
    }

    // ------------------------------------------------------------------ 직접 공유와의 관계

    @Test
    @DisplayName("직접 공유와 친구 공개가 함께 있으면 더 높은 권한이 적용되고, 한쪽을 거두어도 다른 쪽은 유지된다")
    void combinesWithDirectShare() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "함께");

        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        setAccess(owner, mapId, "EDITOR").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.role").value("EDITOR"));

        // 친구 공개를 거두면 직접 공유(VIEWER)만 남는다
        setAccess(owner, mapId, "NONE").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("x"))).andExpect(status().isForbidden());

        // 반대로 직접 공유를 EDITOR 로 두고 친구 공개를 VIEWER 로 하면 EDITOR 이고, 직접 공유를 거두면 VIEWER 로 내려간다
        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());
        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.role").value("EDITOR"));
        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
    }

    @Test
    @DisplayName("친구 공개 지도는 내 지도 목록(GET /maps)에 섞이지 않는다 — 둘러보기 화면에서만 찾는다")
    void notInMyMapList() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "공개 지도");
        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());

        send(friend, get("/api/v1/maps")).andExpect(status().isOk()).andExpect(jsonPath("$.data", empty()));
        send(owner, get("/api/v1/maps")).andExpect(jsonPath("$.data[0].friendAccess").value("VIEWER"));
    }

    // ------------------------------------------------------------------ 겹쳐보기·추천에도 적용

    @Test
    @DisplayName("겹쳐보기에는 친구가 공개한 지도의 핀이 들어가고, 권한 없는 사람이 같은 지도 ID 를 보내면 조용히 빠진다")
    void overlayHonorsFriendAccess() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser stranger = newUser();
        befriend(owner, friend);
        String mapId = mapWithPin(owner, "겹쳐볼 지도");
        setAccess(owner, mapId, "VIEWER").andExpect(status().isOk());

        send(friend, get("/api/v1/overlay/pins").param("mapIds", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pins.length()").value(1));
        send(stranger, get("/api/v1/overlay/pins").param("mapIds", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pins.length()").value(0));
    }

    // ------------------------------------------------------------------ 친구 지도 둘러보기

    @Test
    @DisplayName("친구 지도 둘러보기는 내 친구가 공개한 지도만 친구별로 모아 주고, 내 권한(role)을 함께 준다")
    void browseFriendMaps() throws Exception {
        TestUser me = newUser();
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser notFriend = newUser();
        TestUser noPublicMaps = newUser();
        befriend(me, alice);
        befriend(me, bob);
        befriend(me, noPublicMaps);

        String aliceViewer = mapWithPin(alice, "가 앨리스 보기");
        String aliceEditor = createMap(alice, "나 앨리스 편집");
        createMap(alice, "비공개");
        String bobMap = createMap(bob, "밥 지도");
        String strangerMap = createMap(notFriend, "남의 지도");
        String myOwn = createMap(me, "내 공개 지도");
        createMap(noPublicMaps, "안 보임");
        setAccess(alice, aliceViewer, "VIEWER").andExpect(status().isOk());
        setAccess(alice, aliceEditor, "EDITOR").andExpect(status().isOk());
        setAccess(bob, bobMap, "VIEWER").andExpect(status().isOk());
        setAccess(notFriend, strangerMap, "VIEWER").andExpect(status().isOk());
        setAccess(me, myOwn, "VIEWER").andExpect(status().isOk());

        String body = send(me, get("/api/v1/friends/maps")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friends.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        // 친구는 닉네임 순, 지도는 이름 순. 공개하지 않은 지도·친구가 아닌 사람·내 지도·공개한 지도가 없는 친구는 나오지 않는다
        java.util.List<String> allNames = JsonPath.read(body, "$.data.friends[*].maps[*].name");
        org.assertj.core.api.Assertions.assertThat(allNames).containsExactlyInAnyOrder("가 앨리스 보기", "나 앨리스 편집", "밥 지도");
        java.util.List<String> aliceNames = JsonPath.read(body, "$.data.friends[?(@.friend.id=='" + alice.id() + "')].maps[*].name");
        org.assertj.core.api.Assertions.assertThat(aliceNames).containsExactly("가 앨리스 보기", "나 앨리스 편집");
        java.util.List<String> roles = JsonPath.read(body, "$.data.friends[?(@.friend.id=='" + alice.id() + "')].maps[*].role");
        org.assertj.core.api.Assertions.assertThat(roles).containsExactly("VIEWER", "EDITOR");
        java.util.List<Integer> pinCounts = JsonPath.read(body, "$.data.friends[?(@.friend.id=='" + alice.id() + "')].maps[*].pinCount");
        org.assertj.core.api.Assertions.assertThat(pinCounts).containsExactly(1, 0);
    }

    @Test
    @DisplayName("친구가 없거나 공개된 지도가 없으면 둘러보기는 빈 목록이고, 비로그인은 401")
    void browseEmpty() throws Exception {
        TestUser loner = newUser();
        send(loner, get("/api/v1/friends/maps")).andExpect(status().isOk()).andExpect(jsonPath("$.data.friends", empty()));
        mockMvc.perform(get("/api/v1/friends/maps")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("둘러보기에서 친구를 끊거나 공개를 거두면 그 지도가 목록에서 사라진다")
    void browseFollowsChanges() throws Exception {
        TestUser me = newUser();
        TestUser friend = newUser();
        befriend(me, friend);
        String mapId = createMap(friend, "사라질 지도");
        setAccess(friend, mapId, "VIEWER").andExpect(status().isOk());
        send(me, get("/api/v1/friends/maps")).andExpect(jsonPath("$.data.friends[0].maps[*].name", contains("사라질 지도")));

        setAccess(friend, mapId, "NONE").andExpect(status().isOk());
        send(me, get("/api/v1/friends/maps")).andExpect(jsonPath("$.data.friends", empty()));

        setAccess(friend, mapId, "VIEWER").andExpect(status().isOk());
        send(me, delete("/api/v1/friends/{u}", friend.id())).andExpect(status().isOk());
        send(me, get("/api/v1/friends/maps")).andExpect(jsonPath("$.data.friends", empty()));
    }

    // ------------------------------------------------------------------ 지도 삭제

    @Test
    @DisplayName("공개한 지도를 지워도 문제없고, 같은 친구들이 다른 공개 지도는 계속 볼 수 있다")
    void deletingPublicMap() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String doomed = createMap(owner, "지울 지도");
        String kept = createMap(owner, "남길 지도");
        setAccess(owner, doomed, "EDITOR").andExpect(status().isOk());
        setAccess(owner, kept, "VIEWER").andExpect(status().isOk());

        send(owner, delete("/api/v1/maps/{m}", doomed)).andExpect(status().isOk());

        // 권한 튜플이 함께 지워져서 Guard 가 먼저 막는다(지워진 지도의 존재 여부를 알려 주지 않는다)
        send(friend, get("/api/v1/maps/{m}", doomed)).andExpect(status().isForbidden());
        send(friend, get("/api/v1/maps/{m}", kept)).andExpect(status().isOk());
    }
}
