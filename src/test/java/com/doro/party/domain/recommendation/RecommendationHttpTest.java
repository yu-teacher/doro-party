package com.doro.party.domain.recommendation;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 모임 추천: 권한은 겹쳐보기와 같고, 점수는 설정된 가중치(기본값)로 계산된다. */
class RecommendationHttpTest extends PartyHttpTestBase {

    private static final double LAT = 37.5563;
    private static final double LNG = 126.9236;

    private String createGroup(TestUser owner, String name) throws Exception {
        String body = send(owner, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private void join(TestUser owner, String groupId, TestUser member) throws Exception {
        String code = JsonPath.read(send(owner, post("/api/v1/groups/{g}/invite", groupId)).andReturn().getResponse().getContentAsString(), "$.data.code");
        send(member, post("/api/v1/groups/invite/{c}/join", code)).andExpect(status().isOk());
    }

    private String pinAt(TestUser user, String mapId, String name, double dLat, String status, Integer rating, String revisit) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"lat\":" + (LAT + dLat) + ",\"lng\":" + LNG + ",\"status\":\"" + status + "\""
                + (rating == null ? "" : ",\"rating\":" + rating) + (revisit == null ? "" : ",\"revisitIntent\":\"" + revisit + "\"") + "}";
        return createPin(user, mapId, body);
    }

    private ResultActions groupReco(TestUser user, String groupId, String query) throws Exception {
        return send(user, get("/api/v1/groups/{g}/recommendations" + query, groupId));
    }

    /** 앨리스·밥·캐럴이 한 모임에 각자 지도를 공유하고, 세 사람이 같은 장소(연남 파스타)를, 앨리스 혼자 다른 장소를 찍은 상황. */
    private record Scene(TestUser alice, TestUser bob, TestUser carol, String groupId, String aliceMap, String bobMap, String carolMap) {
    }

    private Scene scene() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        String groupId = createGroup(alice, "추천 모임");
        join(alice, groupId, bob);
        join(alice, groupId, carol);
        String aliceMap = createMap(alice, "앨리스 지도");
        String bobMap = createMap(bob, "밥 지도");
        String carolMap = createMap(carol, "캐럴 지도");
        for (String[] share : new String[][]{{"alice", aliceMap}, {"bob", bobMap}, {"carol", carolMap}}) {
            TestUser owner = share[0].equals("alice") ? alice : share[0].equals("bob") ? bob : carol;
            send(owner, put("/api/v1/maps/{m}/groups/{g}", share[1], groupId)).andExpect(status().isOk());
        }
        pinAt(alice, aliceMap, "연남 파스타", 0.0000, "WISH", null, null);
        pinAt(bob, bobMap, "연남 파스타", 0.0001, "WISH", null, null);
        pinAt(carol, carolMap, "연남 파스타", 0.0002, "VISITED", 5, "AGAIN");
        pinAt(alice, aliceMap, "한강 치킨", 0.02, "VISITED", 4, null);
        return new Scene(alice, bob, carol, groupId, aliceMap, bobMap, carolMap);
    }

    @Test
    @DisplayName("모임 멤버가 추천을 보면 3명이 찍은 장소가 1등이고 점수 내역이 합쳐서 점수가 된다")
    void groupRecommendation() throws Exception {
        Scene scene = scene();

        String body = groupReco(scene.bob(), scene.groupId(), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapIds.length()").value(3)).andExpect(jsonPath("$.data.places.length()").value(2))
                .andExpect(jsonPath("$.data.places[0].rank").value(1)).andExpect(jsonPath("$.data.places[0].name").value("연남 파스타"))
                .andExpect(jsonPath("$.data.places[0].people").value(3)).andExpect(jsonPath("$.data.places[0].pinCount").value(3))
                .andExpect(jsonPath("$.data.places[0].score").value(50))
                .andExpect(jsonPath("$.data.places[1].name").value("한강 치킨")).andExpect(jsonPath("$.data.places[1].score").value(14))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<?>) JsonPath.read(body, "$.data.places[0].authors")).hasSize(3);
        assertThat((List<?>) JsonPath.read(body, "$.data.places[0].pinIds")).hasSize(3);
        assertThat((List<?>) JsonPath.read(body, "$.data.places[0].mapIds")).hasSize(3);
        Integer ratingPoints = JsonPath.read(body, "$.data.places[0].breakdown.ratingPoints");
        Integer wishPoints = JsonPath.read(body, "$.data.places[0].breakdown.wishPoints");
        assertThat(ratingPoints).isEqualTo(4);
        assertThat(wishPoints).isEqualTo(8);
        assertThat(body).doesNotContain(scene.alice().email()).doesNotContain("private");
    }

    @Test
    @DisplayName("특정 작성자를 빼고 다시 계산하면 순위와 점수가 바뀐다")
    void excludingAnAuthor() throws Exception {
        Scene scene = scene();

        // 캐럴(다녀왔고 5점, 또 가고싶어요)이 빠지면 연남 파스타는 앨리스·밥의 "가고 싶어요" 2명: 20 + 8 = 28
        groupReco(scene.alice(), scene.groupId(), "?excludeAuthors=" + scene.carol().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places[0].name").value("연남 파스타")).andExpect(jsonPath("$.data.places[0].people").value(2))
                .andExpect(jsonPath("$.data.places[0].score").value(28));
        // 앨리스·밥이 모두 빠지면 캐럴 혼자라 한강 치킨(앨리스)도 사라진다
        groupReco(scene.carol(), scene.groupId(), "?excludeAuthors=" + scene.alice().id() + "," + scene.bob().id()).andExpect(jsonPath("$.data.places.length()").value(1))
                .andExpect(jsonPath("$.data.places[0].people").value(1));
    }

    @Test
    @DisplayName("minPeople 과 limit 로 거르고, 모든 사람이 빠지면 빈 결과")
    void filters() throws Exception {
        Scene scene = scene();

        groupReco(scene.alice(), scene.groupId(), "?minPeople=2").andExpect(jsonPath("$.data.places.length()").value(1))
                .andExpect(jsonPath("$.data.places[0].name").value("연남 파스타"));
        groupReco(scene.alice(), scene.groupId(), "?limit=1").andExpect(jsonPath("$.data.places.length()").value(1));
        groupReco(scene.alice(), scene.groupId(), "?minPeople=4").andExpect(jsonPath("$.data.places.length()").value(0));
        groupReco(scene.alice(), scene.groupId(), "?excludeAuthors=" + scene.alice().id() + "," + scene.bob().id() + "," + scene.carol().id())
                .andExpect(jsonPath("$.data.places.length()").value(0));
    }

    @Test
    @DisplayName("멤버가 아니면 모임 추천을 볼 수 없고(403), 비로그인은 401")
    void groupAccess() throws Exception {
        Scene scene = scene();
        TestUser stranger = newUser();

        groupReco(stranger, scene.groupId(), "").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/groups/{g}/recommendations", scene.groupId())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("겹쳐보기 추천은 내가 볼 수 있는 지도만 쓴다(볼 수 없는 지도의 핀은 점수에 들어가지 않는다)")
    void overlayRecommendationRespectsAccess() throws Exception {
        Scene scene = scene();
        TestUser outsider = newUser();
        String outsiderMap = createMap(outsider, "남의 비공개 지도");
        pinAt(outsider, outsiderMap, "연남 파스타", 0.0003, "WISH", null, null);
        String maps = scene.aliceMap() + "," + scene.bobMap() + "," + scene.carolMap() + "," + outsiderMap;

        send(scene.alice(), get("/api/v1/overlay/recommendations").param("mapIds", maps)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapIds.length()").value(3))
                .andExpect(jsonPath("$.data.places[0].people").value(3)).andExpect(jsonPath("$.data.places[0].score").value(50));
        // 남의 비공개 지도만 고르면 아무것도 추천되지 않고, 존재 여부도 드러나지 않는다
        send(scene.alice(), get("/api/v1/overlay/recommendations").param("mapIds", outsiderMap)).andExpect(jsonPath("$.data.places.length()").value(0))
                .andExpect(jsonPath("$.data.mapIds.length()").value(0));
    }

    @Test
    @DisplayName("공유를 거두거나 모임에서 나가면 그 사람의 핀은 추천에서 빠진다")
    void accessChangesAreReflected() throws Exception {
        Scene scene = scene();
        send(scene.carol(), org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/groups/{g}/members/me", scene.groupId())).andExpect(status().isOk());

        groupReco(scene.alice(), scene.groupId(), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places[0].people").value(2)).andExpect(jsonPath("$.data.mapIds.length()").value(2));
    }

    @Test
    @DisplayName("잘못된 파라미터는 400: minPeople·limit 범위, 잘못된 UUID, mapIds 누락")
    void invalidParameters() throws Exception {
        Scene scene = scene();

        groupReco(scene.alice(), scene.groupId(), "?minPeople=0").andExpect(status().isBadRequest());
        groupReco(scene.alice(), scene.groupId(), "?minPeople=51").andExpect(status().isBadRequest());
        groupReco(scene.alice(), scene.groupId(), "?limit=0").andExpect(status().isBadRequest());
        groupReco(scene.alice(), scene.groupId(), "?limit=101").andExpect(status().isBadRequest());
        groupReco(scene.alice(), scene.groupId(), "?excludeAuthors=not-a-uuid").andExpect(status().isBadRequest());
        send(scene.alice(), get("/api/v1/overlay/recommendations")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/overlay/recommendations").param("mapIds", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }
}
