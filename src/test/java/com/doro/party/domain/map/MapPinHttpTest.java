package com.doro.party.domain.map;

import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 지도·핀 API 를 실제 로그인 세션과 실제 Guard 로 검증한다: 권한, IDOR, 입력 검증, 필터. */
class MapPinHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private static final String HONGDAE_PIN = pinBody("연남 카페", 37.5625, 126.9246, "VISITED", 4, "[\"카페\"]");

    // ------------------------------------------------------------------ 지도

    @Test
    @DisplayName("지도를 만들면 내 목록에 핀 개수와 함께 보이고, 상세·수정·삭제는 주인만 할 수 있다")
    void mapLifecycleForOwner() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "홍대 맛집");
        createPin(owner, mapId, HONGDAE_PIN);

        send(owner, get("/api/v1/maps")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + mapId + "')].pinCount").value(1))
                .andExpect(jsonPath("$.data[?(@.id=='" + mapId + "')].mine").value(true));
        send(owner, get("/api/v1/maps/{id}", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍대 맛집"))
                .andExpect(jsonPath("$.data.ownerId").value(owner.id().toString()));

        send(owner, patch("/api/v1/maps/{id}", mapId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  홍대 놀거리  \",\"description\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍대 놀거리"))
                .andExpect(jsonPath("$.data.description").doesNotExist());

        send(owner, delete("/api/v1/maps/{id}", mapId)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from pins where map_id = ?::uuid", Long.class, mapId))
                .as("지도를 지우면 핀도 함께 지워진다").isZero();
        // 권한 튜플도 지워져서 주인이었던 사람도 더는 접근할 수 없다
        send(owner, get("/api/v1/maps/{id}", mapId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("내 지도 목록에는 다른 사람의 지도가 나오지 않는다")
    void listContainsOnlyMyMaps() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String aliceMap = createMap(alice, "앨리스");
        String bobMap = createMap(bob, "밥");

        String body = send(alice, get("/api/v1/maps")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertThat(ids).contains(aliceMap).doesNotContain(bobMap);
    }

    @Test
    @DisplayName("남의 지도는 보기·수정·삭제·핀 조회·핀 추가가 모두 403 이다 (없는 지도와 구분되지 않는다)")
    void strangerCannotTouchAMap() throws Exception {
        TestUser owner = newUser();
        TestUser stranger = newUser();
        String mapId = createMap(owner, "비공개");
        String pinId = createPin(owner, mapId, HONGDAE_PIN);

        send(stranger, get("/api/v1/maps/{id}", mapId)).andExpect(status().isForbidden());
        send(stranger, put("/api/v1/maps/{id}", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"해킹\"}"))
                .andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/maps/{id}", mapId)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{id}/pins", mapId)).andExpect(status().isForbidden());
        send(stranger, post("/api/v1/maps/{id}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(HONGDAE_PIN))
                .andExpect(status().isForbidden());
        putPin(stranger, mapId, pinId, HONGDAE_PIN).andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/maps/{m}/pins/{p}", mapId, pinId)).andExpect(status().isForbidden());
        // 존재하지 않는 지도도 같은 403 이라 존재 여부가 드러나지 않는다
        send(stranger, get("/api/v1/maps/{id}", UUID.randomUUID())).andExpect(status().isForbidden());

        send(owner, get("/api/v1/maps/{id}", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("비공개"));
    }

    @Test
    @DisplayName("로그인하지 않으면 지도 API 는 401 이다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/maps")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/maps/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지도 입력 검증: 이름이 비었거나 너무 길면 400")
    void mapValidation() throws Exception {
        TestUser user = newUser();
        for (String body : new String[]{"{}", "{\"name\":\"   \"}", "{\"name\":\"" + "가".repeat(101) + "\"}",
                "{\"name\":\"ok\",\"description\":\"" + "가".repeat(501) + "\"}"}) {
            send(user, post("/api/v1/maps").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
    }

    // ------------------------------------------------------------------ 핀

    @Test
    @DisplayName("핀: 만들기·수정·삭제, 상태 기본값은 WISH, 태그는 정리되어 저장된다")
    void pinLifecycle() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "핀 테스트");

        String created = send(owner, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                .content(pinBody("  망원동 맛집 ", 37.556, 126.906, null, null, "[\"#Cafe\",\"cafe\",\" 맛집 \"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WISH"))
                .andExpect(jsonPath("$.data.name").value("망원동 맛집"))
                .andExpect(jsonPath("$.data.createdBy").value(owner.id().toString()))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String pinId = JsonPath.read(created, "$.data.id");
        List<String> tags = JsonPath.read(created, "$.data.tags");
        assertThat(tags).containsExactly("cafe", "맛집");

        putPin(owner, mapId, pinId, pinBody("망원동 맛집", 37.557, 126.907, "VISITED", 5, "[\"맛집\",\"데이트\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VISITED"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.lat").value(37.557))
                .andExpect(jsonPath("$.data.tags[0]").value("데이트"))
                .andExpect(jsonPath("$.data.tags.length()").value(2));
        assertThat(jdbc.queryForList("select tag from pin_tags where pin_id = ?::uuid order by tag", String.class, pinId))
                .containsExactly("데이트", "맛집");

        send(owner, delete("/api/v1/maps/{m}/pins/{p}", mapId, pinId)).andExpect(status().isOk());
        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from pin_tags where pin_id = ?::uuid", Long.class, pinId)).isZero();
    }

    @Test
    @DisplayName("핀 목록은 상태와 태그로 걸러 볼 수 있다")
    void pinFilters() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "필터");
        createPin(owner, mapId, pinBody("가고싶은 술집", 37.5, 127.0, "WISH", null, "[\"술집\"]"));
        createPin(owner, mapId, pinBody("다녀온 술집", 37.51, 127.01, "VISITED", 3, "[\"술집\",\"추천\"]"));
        createPin(owner, mapId, pinBody("다녀온 카페", 37.52, 127.02, "VISITED", 4, "[\"카페\"]"));

        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data.length()").value(3));
        send(owner, get("/api/v1/maps/{m}/pins", mapId).param("status", "VISITED")).andExpect(jsonPath("$.data.length()").value(2));
        send(owner, get("/api/v1/maps/{m}/pins", mapId).param("tag", "#술집")).andExpect(jsonPath("$.data.length()").value(2));
        send(owner, get("/api/v1/maps/{m}/pins", mapId).param("tag", "술집").param("status", "VISITED"))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].name").value("다녀온 술집"));
        send(owner, get("/api/v1/maps/{m}/pins", mapId).param("status", "NOPE")).andExpect(status().isBadRequest());
        send(owner, get("/api/v1/maps/{m}/pins", mapId).param("tag", "bad tag!")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("핀 입력 검증: 좌표 범위, 평점 범위, 이름, 상태, 태그 형식")
    void pinValidation() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "검증");
        String[] invalid = {
                pinBody("", 37.5, 127.0, null, null, null),
                pinBody("ok", 90.1, 127.0, null, null, null),
                pinBody("ok", -90.1, 127.0, null, null, null),
                pinBody("ok", 37.5, 180.1, null, null, null),
                pinBody("ok", 37.5, 127.0, null, 0, null),
                pinBody("ok", 37.5, 127.0, null, 6, null),
                pinBody("ok", 37.5, 127.0, "UNKNOWN", null, null),
                pinBody("ok", 37.5, 127.0, null, null, "[\"공백 있는 태그\"]"),
                pinBody("ok", 37.5, 127.0, null, null, "[\"" + "a".repeat(31) + "\"]"),
                pinBody("ok", 37.5, 127.0, null, null, "[\"<script>\"]"),
                "{\"name\":\"ok\",\"lng\":127.0}",
                "{\"name\":\"" + "가".repeat(101) + "\",\"lat\":37.5,\"lng\":127.0}",
        };
        for (String body : invalid) {
            send(owner, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        // 경계값은 통과한다
        send(owner, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                .content(pinBody("경계", 90.0, -180.0, "VISITED", 1, null))).andExpect(status().isOk());
        send(owner, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                .content(pinBody("경계2", -90.0, 180.0, "VISITED", 5, null))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("IDOR: 내 지도 경로로 다른 사람 지도의 핀을 고치거나 지울 수 없다")
    void pinFromAnotherMapIsNotReachable() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String aliceMap = createMap(alice, "앨리스 지도");
        String bobMap = createMap(bob, "밥 지도");
        String bobPin = createPin(bob, bobMap, HONGDAE_PIN);

        putPin(alice, aliceMap, bobPin, pinBody("탈취", 1.0, 1.0, null, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PIN-404-01"));
        send(alice, delete("/api/v1/maps/{m}/pins/{p}", aliceMap, bobPin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PIN-404-01"));

        send(bob, get("/api/v1/maps/{m}/pins", bobMap)).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("연남 카페"));
    }

    // ------------------------------------------------------------------ 공유 권한(M3 에서 API 가 생기기 전에 규칙만 먼저 검증)

    @Test
    @DisplayName("editor 는 핀을 꽂고 자기 핀만 고칠 수 있고, 주인은 모든 핀을 고치거나 지울 수 있다")
    void editorRules() throws Exception {
        TestUser owner = newUser();
        TestUser editor = newUser();
        String mapId = createMap(owner, "공동 지도");
        grant(mapId, PartyGuard.EDITOR, editor);

        String ownerPin = createPin(owner, mapId, pinBody("주인 핀", 37.5, 127.0, null, null, null));
        String editorPin = createPin(editor, mapId, pinBody("편집자 핀", 37.6, 127.1, null, null, null));
        send(editor, get("/api/v1/maps/{id}", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.mine").value(false));

        putPin(editor, mapId, editorPin, pinBody("편집자 핀 수정", 37.6, 127.1, "VISITED", null, null)).andExpect(status().isOk());
        putPin(editor, mapId, ownerPin, pinBody("남의 핀 수정", 37.5, 127.0, null, null, null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403-01"));
        send(editor, delete("/api/v1/maps/{m}/pins/{p}", mapId, ownerPin)).andExpect(status().isForbidden());
        // editor 는 지도 자체를 고치거나 지울 수 없다
        send(editor, put("/api/v1/maps/{id}", mapId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        send(editor, delete("/api/v1/maps/{id}", mapId)).andExpect(status().isForbidden());

        putPin(owner, mapId, editorPin, pinBody("주인이 수정", 37.6, 127.1, null, null, null)).andExpect(status().isOk());
        send(owner, delete("/api/v1/maps/{m}/pins/{p}", mapId, editorPin)).andExpect(status().isOk());
        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("viewer 는 지도와 핀을 볼 수만 있다")
    void viewerRules() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "보기 전용");
        String pinId = createPin(owner, mapId, HONGDAE_PIN);
        grant(mapId, PartyGuard.VIEWER, viewer);

        send(viewer, get("/api/v1/maps/{id}", mapId)).andExpect(status().isOk());
        send(viewer, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        send(viewer, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON).content(HONGDAE_PIN))
                .andExpect(status().isForbidden());
        putPin(viewer, mapId, pinId, HONGDAE_PIN).andExpect(status().isForbidden());
        send(viewer, delete("/api/v1/maps/{m}/pins/{p}", mapId, pinId)).andExpect(status().isForbidden());
    }
}
