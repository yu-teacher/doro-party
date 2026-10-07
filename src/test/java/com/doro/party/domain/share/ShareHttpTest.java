package com.doro.party.domain.share;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 친구에게 지도 공유: 권한 부여·변경·회수, 내가 볼 수 있는 지도, 핀 작성자, 친구 해제와 지도 삭제 시 정리. 실제 Guard 로 검증한다. */
class ShareHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private String newMapWithPin(TestUser owner, String name) throws Exception {
        String mapId = createMap(owner, name);
        createPin(owner, mapId, pinBody("주인의 핀", 37.5, 127.0, null, null, null));
        return mapId;
    }

    private String pinPayload(String name) {
        return pinBody(name, 37.6, 127.1, null, null, null);
    }

    // ------------------------------------------------------------------ 공유와 권한

    @Test
    @DisplayName("VIEWER 로 공유하면 친구는 지도와 핀을 볼 수 있지만 핀을 꽂거나 지도를 고칠 수 없고, 사적 메모는 쓸 수 있다")
    void viewerShare() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "공유 지도");

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER")).andExpect(jsonPath("$.data.mine").value(false))
                .andExpect(jsonPath("$.data.ownerId").value(owner.id().toString()));
        send(friend, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("침입"))).andExpect(status().isForbidden());
        send(friend, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(friend, put_("/api/v1/maps/{m}", mapId, "{\"name\":\"바꿈\"}")).andExpect(status().isForbidden());

        // 보기만 가능해도 나만 보는 메모는 남길 수 있고, 주인에게는 보이지 않는다
        String pinId = JsonPath.read(send(friend, get("/api/v1/maps/{m}/pins", mapId)).andReturn().getResponse().getContentAsString(), "$.data[0].id");
        send(friend, put_("/api/v1/maps/{m}/pins/" + pinId + "/private-note", mapId, "{\"body\":\"열람자의 비밀\"}")).andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}/private-notes", mapId)).andExpect(jsonPath("$.data.length()").value(1));
        assertThat(send(owner, get("/api/v1/maps/{m}/private-notes", mapId)).andReturn().getResponse().getContentAsString()).doesNotContain("열람자의 비밀");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder put_(String url, Object var, String json) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url, var).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @DisplayName("EDITOR 로 공유하면 친구가 핀을 꽂을 수 있고 작성자가 표시되며, 남의 핀은 못 고치고 주인은 모두 고칠 수 있다")
    void editorShare() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "공동 지도");
        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());
        String ownerPin = JsonPath.read(send(owner, get("/api/v1/maps/{m}/pins", mapId)).andReturn().getResponse().getContentAsString(), "$.data[0].id");

        String friendPin = createPin(friend, mapId, pinPayload("친구의 핀"));

        // 작성자 표시: 핀마다 만든 사람의 닉네임과 색이 실린다
        String pinsAsOwner = send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> authors = JsonPath.read(pinsAsOwner, "$.data[?(@.id=='" + friendPin + "')].createdBy");
        assertThat(authors).containsExactly(friend.id().toString());
        List<String> colors = JsonPath.read(pinsAsOwner, "$.data[*].authorColor");
        assertThat(colors).hasSize(2).allMatch(color -> color.matches("#[0-9A-Fa-f]{6}"));
        List<String> nicknames = JsonPath.read(pinsAsOwner, "$.data[*].authorNickname");
        assertThat(nicknames).hasSize(2).doesNotContainNull();

        putPin(friend, mapId, friendPin, pinPayload("친구 핀 수정")).andExpect(status().isOk());
        putPin(friend, mapId, ownerPin, pinPayload("남의 핀")).andExpect(status().isForbidden());
        send(friend, delete("/api/v1/maps/{m}/pins/{p}", mapId, ownerPin)).andExpect(status().isForbidden());
        putPin(owner, mapId, friendPin, pinPayload("주인이 수정")).andExpect(status().isOk());
        // 편집자도 지도 자체는 못 지우고 공유를 관리하지 못한다
        send(friend, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(friend, get("/api/v1/maps/{m}/shares", mapId)).andExpect(status().isForbidden());
        shareMap(friend, mapId, owner, "VIEWER").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공유받은 지도는 내 지도 목록에 권한·주인 정보와 함께 나오고, 목록에 없던 사람에게는 나오지 않는다")
    void accessibleMapList() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser stranger = newUser();
        befriend(owner, friend);
        String shared = newMapWithPin(owner, "공유됨");
        String notShared = newMapWithPin(owner, "공유 안 함");
        String friendsOwn = createMap(friend, "친구 자신의 지도");
        shareMap(owner, shared, friend, "EDITOR").andExpect(status().isOk());

        String body = send(friend, get("/api/v1/maps")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertThat(ids).contains(shared, friendsOwn).doesNotContain(notShared);
        List<String> roles = JsonPath.read(body, "$.data[?(@.id=='" + shared + "')].role");
        List<String> ownRoles = JsonPath.read(body, "$.data[?(@.id=='" + friendsOwn + "')].role");
        List<String> ownerNicknames = JsonPath.read(body, "$.data[?(@.id=='" + shared + "')].ownerNickname");
        List<Integer> pinCounts = JsonPath.read(body, "$.data[?(@.id=='" + shared + "')].pinCount");
        assertThat(roles).containsExactly("EDITOR");
        assertThat(ownRoles).containsExactly("OWNER");
        assertThat(ownerNicknames).hasSize(1).doesNotContainNull();
        assertThat(pinCounts).containsExactly(1);

        String strangerBody = send(stranger, get("/api/v1/maps")).andReturn().getResponse().getContentAsString();
        assertThat(strangerBody).doesNotContain(shared).doesNotContain(notShared);
    }

    @Test
    @DisplayName("권한을 바꾸면 곧바로 반영된다: VIEWER -> EDITOR 면 핀을 꽂을 수 있고, 다시 VIEWER 로 낮추면 못 꽂는다")
    void roleChangeTakesEffectImmediately() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "역할 변경");
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("1"))).andExpect(status().isForbidden());

        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("EDITOR"));
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("2"))).andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(jsonPath("$.data.role").value("EDITOR"));

        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("3"))).andExpect(status().isForbidden());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("친구가 아닌 사람, 자기 자신, 없는 사용자에게는 공유할 수 없다")
    void sharingRules() throws Exception {
        TestUser owner = newUser();
        TestUser stranger = newUser();
        String mapId = newMapWithPin(owner, "규칙");

        shareMap(owner, mapId, stranger, "VIEWER").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRIEND-400-02"));
        shareMap(owner, mapId, owner, "VIEWER").andExpect(status().isBadRequest());
        send(owner, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/shares/{u}", mapId, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isNotFound());
        TestUser friend = newUser();
        befriend(owner, friend);
        send(owner, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}")).andExpect(status().isBadRequest());
        send(owner, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        // 친구가 아닌 사람이 남의 지도를 공유 대상으로 지정하는 것도 막힌다
        shareMap(stranger, mapId, owner, "VIEWER").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isZero();
    }

    @Test
    @DisplayName("공유 목록은 주인만 보고, 같이 보는 사람 목록(닉네임·색)은 지도를 볼 수 있는 사람이 보며 사용자명은 드러나지 않는다")
    void sharesAndMembers() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        TestUser editor = newUser();
        TestUser stranger = newUser();
        befriend(owner, viewer);
        befriend(owner, editor);
        String mapId = newMapWithPin(owner, "구성원");
        shareMap(owner, mapId, viewer, "VIEWER").andExpect(status().isOk());
        shareMap(owner, mapId, editor, "EDITOR").andExpect(status().isOk());

        send(owner, get("/api/v1/maps/{m}/shares", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        send(viewer, get("/api/v1/maps/{m}/shares", mapId)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{m}/shares", mapId)).andExpect(status().isForbidden());

        String members = send(viewer, get("/api/v1/maps/{m}/members", mapId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> roles = JsonPath.read(members, "$.data[*].role");
        assertThat(roles).containsExactly("OWNER", "VIEWER", "EDITOR");
        assertThat(members).doesNotContain("username").doesNotContain(owner.email());
        send(stranger, get("/api/v1/maps/{m}/members", mapId)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 회수

    @Test
    @DisplayName("주인이 공유를 끊으면 친구는 즉시 접근할 수 없고, 목록에서도 사라진다")
    void ownerRevokes() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "회수");
        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());

        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("x"))).andExpect(status().isForbidden());
        assertThat(send(friend, get("/api/v1/maps")).andReturn().getResponse().getContentAsString()).doesNotContain(mapId);
        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHARE-404-01"));
    }

    @Test
    @DisplayName("공유받은 사람은 스스로 나갈 수 있지만, 다른 사람의 공유를 끊을 수는 없다")
    void leavingASharedMap() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser other = newUser();
        befriend(owner, friend);
        befriend(owner, other);
        String mapId = newMapWithPin(owner, "나가기");
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        shareMap(owner, mapId, other, "VIEWER").andExpect(status().isOk());

        send(friend, delete("/api/v1/maps/{m}/shares/{u}", mapId, other.id())).andExpect(status().isForbidden());
        send(friend, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        send(other, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("친구를 끊으면 내가 그 친구에게 준 공유만 회수되고, 친구가 나에게 준 공유는 그대로다")
    void unfriendRevokesOnlyWhatIGave() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        befriend(alice, bob);
        String aliceMap = newMapWithPin(alice, "앨리스 지도");
        String aliceSecondMap = newMapWithPin(alice, "앨리스 두번째");
        String bobMap = newMapWithPin(bob, "밥 지도");
        shareMap(alice, aliceMap, bob, "VIEWER").andExpect(status().isOk());
        shareMap(alice, aliceSecondMap, bob, "EDITOR").andExpect(status().isOk());
        shareMap(bob, bobMap, alice, "VIEWER").andExpect(status().isOk());

        send(alice, delete("/api/v1/friends/{u}", bob.id())).andExpect(status().isOk());

        send(bob, get("/api/v1/maps/{m}", aliceMap)).andExpect(status().isForbidden());
        send(bob, get("/api/v1/maps/{m}", aliceSecondMap)).andExpect(status().isForbidden());
        send(alice, get("/api/v1/maps/{m}", bobMap)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id in (?::uuid, ?::uuid)", Long.class, aliceMap, aliceSecondMap)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, bobMap)).isEqualTo(1L);
        // 친구가 아니게 됐으므로 다시 공유할 수 없다
        shareMap(alice, aliceMap, bob, "VIEWER").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지도를 지우면 공유받은 사람의 접근도 함께 사라진다")
    void deletingTheMapRemovesAccess() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "삭제");
        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());

        send(owner, delete("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        assertThat(send(friend, get("/api/v1/maps")).andReturn().getResponse().getContentAsString()).doesNotContain(mapId);
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isZero();
    }

    @Test
    @DisplayName("같은 공유 요청이 동시에 여러 번 와도 오류 없이 하나만 남는다")
    void concurrentShares() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = newMapWithPin(owner, "동시 공유");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String role = i % 2 == 0 ? "VIEWER" : "EDITOR";
                Callable<Integer> call = () -> shareMap(owner, mapId, friend, role).andReturn().getResponse().getStatus();
                futures.add(pool.submit(call));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isEqualTo(1L);
        // DB 의 역할과 Guard 의 실제 권한이 어긋나지 않는다
        String role = jdbc.queryForObject("select role from map_user_shares where map_id = ?::uuid", String.class, mapId);
        int createStatus = send(friend, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(pinPayload("확인")))
                .andReturn().getResponse().getStatus();
        assertThat(createStatus).isEqualTo("EDITOR".equals(role) ? 200 : 403);
    }

    @Test
    @DisplayName("비로그인은 공유 API 에 401, 로그인 안 한 요청은 목록에도 접근할 수 없다")
    void anonymousIsRejected() throws Exception {
        UUID someMap = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/maps/{m}/shares", someMap)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/maps/{m}/members", someMap)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/maps/{m}/shares/{u}", someMap, UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/maps")).andExpect(status().isUnauthorized());
    }
}
