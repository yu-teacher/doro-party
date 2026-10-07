package com.doro.party.domain.map;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 개수 상한(지도·핀·태그)은 동시 요청에서도 지켜져야 한다. 작은 상한으로 따로 띄운 컨텍스트에서 검증한다. */
@TestPropertySource(properties = {
        "party.limits.max-maps-per-user=3",
        "party.limits.max-pins-per-map=3",
        "party.limits.max-tags-per-pin=2",
        "party.limits.max-visits-per-pin=3",
        "party.limits.max-photos-per-pin=2",
        "party.limits.max-photo-bytes=2048",
        "party.limits.max-shares-per-map=2",
        "party.limits.max-pending-friend-requests=2",
        "party.limits.max-members-per-group=2",
        "party.limits.max-groups-per-user=2",
        "party.limits.max-groups-per-map=1",
})
class LimitsHttpTest extends PartyHttpTestBase {

    private static final int PARALLEL_REQUESTS = 12;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired private JdbcTemplate jdbc;

    private int statusOf(TestUser user, String url, String body) throws Exception {
        return mockMvc.perform(user.sign(post(url).contentType(MediaType.APPLICATION_JSON).content(body)))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> parallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(call));
            }
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("지도 개수 상한: 동시에 여러 개를 만들어도 상한 이상 만들어지지 않는다")
    void mapLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            String name = "{\"name\":\"지도" + i + "\"}";
            calls.add(() -> statusOf(user, "/api/v1/maps", name));
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(PARALLEL_REQUESTS - 3);
    }

    @Test
    @DisplayName("핀 개수 상한: 동시에 여러 개를 꽂아도 상한 이상 저장되지 않는다")
    void pinLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        String mapId = JsonPath.read(mockMvc.perform(user.sign(post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"상한\"}"))).andReturn().getResponse().getContentAsString(), "$.data.id");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            String body = "{\"name\":\"핀" + i + "\",\"lat\":37.5,\"lng\":127.0}";
            calls.add(() -> statusOf(user, "/api/v1/maps/" + mapId + "/pins", body));
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(PARALLEL_REQUESTS - 3);
    }

    @Test
    @DisplayName("태그 개수 상한을 넘으면 400(LIMIT-400-01)")
    void tagLimit() throws Exception {
        TestUser user = newUser();
        String mapId = JsonPath.read(mockMvc.perform(user.sign(post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"태그\"}"))).andReturn().getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(user.sign(post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"lat\":1,\"lng\":1,\"tags\":[\"a\",\"b\",\"c\"]}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        mockMvc.perform(user.sign(post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"lat\":1,\"lng\":1,\"tags\":[\"a\",\"#A\"]}")))
                .andExpect(status().isOk());
    }

    private String newPin(TestUser user) throws Exception {
        String mapId = createMap(user, "상한");
        return mapId + "/" + createPin(user, mapId, pinBody("핀", 37.5, 127.0, null, null, null));
    }

    @Test
    @DisplayName("방문 기록 개수 상한: 동시에 여러 개를 남겨도 상한 이상 저장되지 않는다")
    void visitLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        String[] ids = newPin(user).split("/");
        String url = "/api/v1/maps/" + ids[0] + "/pins/" + ids[1] + "/visits";
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            calls.add(() -> statusOf(user, url, "{\"visitedOn\":\"" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) + "\"}"));
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(PARALLEL_REQUESTS - 3);
        assertThat(jdbc.queryForObject("select count(*) from visit_logs where pin_id = ?::uuid", Long.class, ids[1])).isEqualTo(3L);
    }

    @Test
    @DisplayName("사진 개수 상한: 동시에 올려도 상한 이상 저장되지 않고, 거절된 사진 파일은 스토리지에 올라가지 않는다")
    void photoLimitHoldsUnderConcurrency() throws Exception {
        TestUser user = newUser();
        String[] ids = newPin(user).split("/");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> mockMvc.perform(user.sign(multipart("/api/v1/maps/{m}/pins/{p}/photos", ids[0], ids[1])
                    .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(2);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from pin_photos where pin_id = ?::uuid", Long.class, ids[1])).isEqualTo(2L);
    }

    @Test
    @DisplayName("사진 크기 상한을 넘으면 413(UPLOAD-413-01)")
    void photoSizeLimit() throws Exception {
        TestUser user = newUser();
        String[] ids = newPin(user).split("/");
        byte[] tooBig = new byte[3000];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

        mockMvc.perform(user.sign(multipart("/api/v1/maps/{m}/pins/{p}/photos", ids[0], ids[1])
                        .file(new MockMultipartFile("file", "big.png", "image/png", tooBig))))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD-413-01"));
    }

    @Test
    @DisplayName("공유 인원 상한: 상한에 이르면 새로 공유할 수 없지만, 이미 공유한 사람의 권한 변경은 된다")
    void shareLimit() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "공유 상한");
        List<TestUser> friends = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestUser friend = newUser();
            befriend(owner, friend);
            friends.add(friend);
        }

        shareMap(owner, mapId, friends.get(0), "VIEWER").andExpect(status().isOk());
        shareMap(owner, mapId, friends.get(1), "VIEWER").andExpect(status().isOk());
        shareMap(owner, mapId, friends.get(2), "VIEWER").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        shareMap(owner, mapId, friends.get(0), "EDITOR").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from map_user_shares where map_id = ?::uuid", Long.class, mapId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("대기 중인 친구 요청 상한: 넘으면 400 이고 보낸 요청은 남지 않는다")
    void pendingFriendRequestLimit() throws Exception {
        TestUser sender = newUser();
        List<TestUser> targets = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            targets.add(newUser());
        }
        for (int i = 0; i < 2; i++) {
            String name = JsonPath.read(mockMvc.perform(targets.get(i).sign(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me")))
                    .andReturn().getResponse().getContentAsString(), "$.data.username");
            mockMvc.perform(sender.sign(post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + name + "\"}")))
                    .andExpect(status().isOk());
        }
        String third = JsonPath.read(mockMvc.perform(targets.get(2).sign(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me")))
                .andReturn().getResponse().getContentAsString(), "$.data.username");

        mockMvc.perform(sender.sign(post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + third + "\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        assertThat(jdbc.queryForObject("select count(*) from friendships where requester_id = ?::uuid", Long.class, sender.id().toString())).isEqualTo(2L);
    }

    private String newGroup(TestUser owner, String name) throws Exception {
        String body = mockMvc.perform(owner.sign(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String groupInvite(TestUser owner, String groupId) throws Exception {
        String body = mockMvc.perform(owner.sign(post("/api/v1/groups/{g}/invite", groupId))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.code");
    }

    @Test
    @DisplayName("한 사람이 속할 수 있는 모임 수 상한: 만들기와 링크 가입 모두 같은 상한을 센다")
    void groupsPerUserLimit() throws Exception {
        TestUser user = newUser();
        TestUser other = newUser();
        newGroup(user, "하나");
        newGroup(user, "둘");

        mockMvc.perform(user.sign(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"셋\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        String othersGroup = newGroup(other, "남의 모임");
        mockMvc.perform(user.sign(post("/api/v1/groups/invite/{c}/join", groupInvite(other, othersGroup))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
        assertThat(jdbc.queryForObject("select count(*) from party_group_members where user_id = ?::uuid", Long.class, user.id().toString())).isEqualTo(2L);
    }

    @Test
    @DisplayName("모임 멤버 수 상한: 동시에 여러 명이 들어와도 상한을 넘지 못한다")
    void groupMemberLimitHoldsUnderConcurrency() throws Exception {
        TestUser owner = newUser();
        String groupId = newGroup(owner, "정원");
        String code = groupInvite(owner, groupId);
        List<TestUser> guests = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            guests.add(newUser());
        }
        List<Callable<Integer>> calls = new ArrayList<>();
        for (TestUser guest : guests) {
            calls.add(() -> mockMvc.perform(guest.sign(post("/api/v1/groups/invite/{c}/join", code))).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = parallel(calls);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 400).count()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from party_group_members where group_id = ?::uuid", Long.class, groupId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("지도 하나를 공유할 수 있는 모임 수 상한")
    void groupsPerMapLimit() throws Exception {
        TestUser owner = newUser();
        String first = newGroup(owner, "첫째");
        String second = newGroup(owner, "둘째");
        String mapId = createMap(owner, "여러 모임");

        mockMvc.perform(owner.sign(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/groups/{g}", mapId, first))).andExpect(status().isOk());
        mockMvc.perform(owner.sign(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/groups/{g}", mapId, first))).andExpect(status().isOk());
        mockMvc.perform(owner.sign(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/maps/{m}/groups/{g}", mapId, second)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
    }
}
