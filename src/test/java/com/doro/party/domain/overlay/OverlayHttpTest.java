package com.doro.party.domain.overlay;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 겹쳐보기: 여러 지도의 핀을 한 번에. 권한은 지도마다 Guard 가 판정한다. */
class OverlayHttpTest extends PartyHttpTestBase {

    private ResultActions overlay(TestUser user, String... mapIds) throws Exception {
        return send(user, get("/api/v1/overlay/pins").param("mapIds", String.join(",", mapIds)));
    }

    private String mapWithPins(TestUser owner, String name, String... pinNames) throws Exception {
        String mapId = createMap(owner, name);
        double lat = 37.5;
        for (String pinName : pinNames) {
            createPin(owner, mapId, pinBody(pinName, lat, 127.0, "VISITED", 4, null));
            lat += 0.001;
        }
        return mapId;
    }

    private String createGroup(TestUser owner, String name) throws Exception {
        String body = send(owner, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private void joinGroup(TestUser owner, String groupId, TestUser member) throws Exception {
        String code = JsonPath.read(send(owner, post("/api/v1/groups/{g}/invite", groupId)).andReturn().getResponse().getContentAsString(), "$.data.code");
        send(member, post("/api/v1/groups/invite/{c}/join", code)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("내 지도, 직접 공유받은 지도, 모임으로 보이는 지도를 한 번에 겹치면 핀마다 작성자 닉네임·색이 실린다")
    void overlaysEveryKindOfAccessibleMap() throws Exception {
        TestUser me = newUser();
        TestUser friend = newUser();
        TestUser groupMate = newUser();
        befriend(friend, me);
        String mine = mapWithPins(me, "내 지도", "내 핀 1", "내 핀 2");
        String friends = mapWithPins(friend, "친구 지도", "친구 핀");
        String groupMates = mapWithPins(groupMate, "모임원 지도", "모임원 핀 1", "모임원 핀 2", "모임원 핀 3");
        shareMap(friend, friends, me, "VIEWER").andExpect(status().isOk());
        String groupId = createGroup(groupMate, "모임");
        joinGroup(groupMate, groupId, me);
        send(groupMate, put("/api/v1/maps/{m}/groups/{g}", groupMates, groupId)).andExpect(status().isOk());

        String body = overlay(me, mine, friends, groupMates).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapIds.length()").value(3)).andExpect(jsonPath("$.data.pins.length()").value(6))
                .andReturn().getResponse().getContentAsString();

        List<String> authors = JsonPath.read(body, "$.data.pins[*].createdBy");
        assertThat(authors).containsOnly(me.id().toString(), friend.id().toString(), groupMate.id().toString());
        List<String> colors = JsonPath.read(body, "$.data.pins[*].authorColor");
        assertThat(colors).hasSize(6).allMatch(color -> color.matches("#[0-9A-Fa-f]{6}"));
        List<String> nicknames = JsonPath.read(body, "$.data.pins[*].authorNickname");
        assertThat(nicknames).hasSize(6).doesNotContainNull();
        List<String> pinMaps = JsonPath.read(body, "$.data.pins[?(@.createdBy=='" + friend.id() + "')].mapId");
        assertThat(pinMaps).containsExactly(friends);
        assertThat(body).doesNotContain("private").doesNotContain(me.email());
    }

    @Test
    @DisplayName("볼 수 없는 지도와 없는 지도는 이유를 밝히지 않고 조용히 빠진다")
    void inaccessibleMapsAreSilentlyDropped() throws Exception {
        TestUser me = newUser();
        TestUser stranger = newUser();
        String mine = mapWithPins(me, "내 지도", "내 핀");
        String secret = mapWithPins(stranger, "남의 비공개 지도", "비밀 핀");

        String body = overlay(me, mine, secret, UUID.randomUUID().toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapIds.length()").value(1)).andExpect(jsonPath("$.data.pins.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("비밀 핀").doesNotContain(secret);
        // 존재하는 비공개 지도와 없는 지도의 응답이 같다(존재 여부를 알 수 없다)
        String onlySecret = overlay(me, secret).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String onlyMissing = overlay(me, UUID.randomUUID().toString()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(onlySecret.replaceAll("\"timestamp\":\"[^\"]*\"", "")).isEqualTo(onlyMissing.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
    }

    @Test
    @DisplayName("공유를 끊거나 모임에서 나가면 다음 요청부터 그 지도의 핀이 겹쳐지지 않는다")
    void accessChangesApplyImmediately() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        befriend(owner, viewer);
        String mapId = mapWithPins(owner, "공유 지도", "핀 A", "핀 B");
        shareMap(owner, mapId, viewer, "VIEWER").andExpect(status().isOk());
        overlay(viewer, mapId).andExpect(jsonPath("$.data.pins.length()").value(2));

        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, viewer.id())).andExpect(status().isOk());

        overlay(viewer, mapId).andExpect(jsonPath("$.data.pins.length()").value(0)).andExpect(jsonPath("$.data.mapIds.length()").value(0));
    }

    @Test
    @DisplayName("같은 지도를 여러 번 넣어도 한 번만 겹치고, 지도를 안 고르면 빈 결과, 잘못된 ID 는 400, 비로그인은 401")
    void inputHandling() throws Exception {
        TestUser me = newUser();
        String mapId = mapWithPins(me, "내 지도", "핀");

        overlay(me, mapId, mapId, mapId).andExpect(jsonPath("$.data.mapIds.length()").value(1)).andExpect(jsonPath("$.data.pins.length()").value(1));
        send(me, get("/api/v1/overlay/pins").param("mapIds", "")).andExpect(status().isOk()).andExpect(jsonPath("$.data.pins.length()").value(0));
        send(me, get("/api/v1/overlay/pins").param("mapIds", "not-a-uuid")).andExpect(status().isBadRequest());
        send(me, get("/api/v1/overlay/pins")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/overlay/pins").param("mapIds", mapId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("겹치는 사람들의 사적 메모·사진 파일은 응답에 없고, 방문 횟수 같은 공유 정보만 있다")
    void noPrivateDataInOverlay() throws Exception {
        TestUser me = newUser();
        String mapId = createMap(me, "비공개 확인");
        String pinId = createPin(me, mapId, pinBody("핀", 37.5, 127.0, "VISITED", 5, null));
        String secret = "나만아는비밀" + UUID.randomUUID().toString().substring(0, 8);
        send(me, put("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + secret + "\"}"))
                .andExpect(status().isOk());

        String body = overlay(me, mapId).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(secret).contains("visitCount").contains("photoCount");
    }
}
