package com.doro.party.domain.pin;

import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 핀의 개인 기록: 재방문 의사, 방문 기록 타임라인, 사적 메모. */
class PinRecordsHttpTest extends PartyHttpTestBase {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired private JdbcTemplate jdbc;

    private static LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }

    private static String visitBody(LocalDate date, String note) {
        return "{\"visitedOn\":\"" + date + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
    }

    private ResultActions addVisit(TestUser user, String mapId, String pinId, String body) throws Exception {
        return send(user, post("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String visit(TestUser user, String mapId, String pinId, LocalDate date, String note) throws Exception {
        String body = addVisit(user, mapId, pinId, visitBody(date, note)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private ResultActions putNote(TestUser user, String mapId, String pinId, String body) throws Exception {
        return send(user, put("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}"));
    }

    // ------------------------------------------------------------------ 재방문 의사

    @Test
    @DisplayName("재방문 의사(AGAIN/ONCE)를 저장하고 바꿀 수 있고, 모르는 값은 400")
    void revisitIntent() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "재방문");
        String body = send(owner, post("/api/v1/maps/{m}/pins", mapId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"파스타집\",\"lat\":37.5,\"lng\":127.0,\"status\":\"VISITED\",\"revisitIntent\":\"AGAIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revisitIntent").value("AGAIN")).andReturn().getResponse().getContentAsString();
        String pinId = JsonPath.read(body, "$.data.id");

        putPin(owner, mapId, pinId, "{\"name\":\"파스타집\",\"lat\":37.5,\"lng\":127.0,\"status\":\"VISITED\",\"revisitIntent\":\"ONCE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revisitIntent").value("ONCE"));
        putPin(owner, mapId, pinId, "{\"name\":\"파스타집\",\"lat\":37.5,\"lng\":127.0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revisitIntent").doesNotExist());
        putPin(owner, mapId, pinId, "{\"name\":\"파스타집\",\"lat\":37.5,\"lng\":127.0,\"revisitIntent\":\"MAYBE\"}")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 방문 기록

    @Test
    @DisplayName("방문 기록: 쌓이고 최근 순으로 나오며, 핀 목록에는 방문 횟수와 마지막 방문일이 실린다")
    void visitTimeline() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "방문");
        String pinId = createPin(owner, mapId, pinBody("단골집", 37.5, 127.0, "VISITED", null, null));

        visit(owner, mapId, pinId, today().minusDays(30), "처음 갔다");
        visit(owner, mapId, pinId, today().minusDays(2), "또 갔다");
        visit(owner, mapId, pinId, today(), null);

        send(owner, get("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].visitedOn").value(today().toString()))
                .andExpect(jsonPath("$.data[2].note").value("처음 갔다"))
                .andExpect(jsonPath("$.data[0].userId").value(owner.id().toString()));
        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data[0].visitCount").value(3))
                .andExpect(jsonPath("$.data[0].lastVisitedOn").value(today().toString()));
    }

    @Test
    @DisplayName("핀을 만든 사람이 가고 싶던 곳에 방문 기록을 남기면 다녀온 곳으로 바뀐다")
    void visitMarksWishPinAsVisited() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "상태");
        String pinId = createPin(owner, mapId, pinBody("가고싶던곳", 37.5, 127.0, "WISH", null, null));

        visit(owner, mapId, pinId, today(), "드디어");

        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data[0].status").value("VISITED"));
    }

    @Test
    @DisplayName("남의 핀에 내 방문을 기록해도 그 핀의 상태는 바뀌지 않는다")
    void visitByAnotherEditorKeepsStatus() throws Exception {
        TestUser owner = newUser();
        TestUser editor = newUser();
        String mapId = createMap(owner, "공동");
        grant(mapId, PartyGuard.EDITOR, editor);
        String pinId = createPin(owner, mapId, pinBody("주인의 위시", 37.5, 127.0, "WISH", null, null));

        visit(editor, mapId, pinId, today(), "나도 다녀옴");

        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data[0].status").value("WISH"))
                .andExpect(jsonPath("$.data[0].visitCount").value(1));
    }

    @Test
    @DisplayName("방문 기록 입력 검증: 미래 날짜, 날짜 누락·형식 오류, 너무 긴 후기는 400")
    void visitValidation() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "검증");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));

        addVisit(owner, mapId, pinId, visitBody(today().plusDays(2), null)).andExpect(status().isBadRequest());
        addVisit(owner, mapId, pinId, "{}").andExpect(status().isBadRequest());
        addVisit(owner, mapId, pinId, "{\"visitedOn\":\"어제\"}").andExpect(status().isBadRequest());
        addVisit(owner, mapId, pinId, "{\"visitedOn\":\"2026-13-40\"}").andExpect(status().isBadRequest());
        addVisit(owner, mapId, pinId, visitBody(today(), "가".repeat(501))).andExpect(status().isBadRequest());
        // 오늘은 허용된다(서비스 시간대 기준)
        addVisit(owner, mapId, pinId, visitBody(today(), "가".repeat(500))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("방문 기록 권한: viewer 는 보기만, 기록은 내가 남긴 것만(주인은 모두) 지울 수 있다")
    void visitPermissions() throws Exception {
        TestUser owner = newUser();
        TestUser editor = newUser();
        TestUser viewer = newUser();
        TestUser stranger = newUser();
        String mapId = createMap(owner, "권한");
        grant(mapId, PartyGuard.EDITOR, editor);
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));
        String ownerVisit = visit(owner, mapId, pinId, today(), "주인");
        String editorVisit = visit(editor, mapId, pinId, today(), "편집자");

        send(viewer, get("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        addVisit(viewer, mapId, pinId, visitBody(today(), null)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId)).andExpect(status().isForbidden());
        addVisit(stranger, mapId, pinId, visitBody(today(), null)).andExpect(status().isForbidden());

        send(editor, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", mapId, pinId, ownerVisit)).andExpect(status().isForbidden());
        send(viewer, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", mapId, pinId, editorVisit)).andExpect(status().isForbidden());
        send(editor, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", mapId, pinId, editorVisit)).andExpect(status().isOk());
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", mapId, pinId, ownerVisit)).andExpect(status().isOk());
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", mapId, pinId, ownerVisit))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VISIT-404-01"));
    }

    @Test
    @DisplayName("IDOR: 내 지도 경로로 다른 지도 핀의 방문 기록을 읽거나 남기거나 지울 수 없다")
    void visitIdor() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String aliceMap = createMap(alice, "앨리스");
        String bobMap = createMap(bob, "밥");
        String bobPin = createPin(bob, bobMap, pinBody("밥의 핀", 37.5, 127.0, null, null, null));
        String bobVisit = visit(bob, bobMap, bobPin, today(), "밥 방문");

        send(alice, get("/api/v1/maps/{m}/pins/{p}/visits", aliceMap, bobPin)).andExpect(status().isNotFound());
        addVisit(alice, aliceMap, bobPin, visitBody(today(), null)).andExpect(status().isNotFound());
        send(alice, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", aliceMap, bobPin, bobVisit)).andExpect(status().isNotFound());
        // 같은 지도 안이라도 다른 핀의 방문 기록 ID 로는 지울 수 없다
        String alicePin = createPin(alice, aliceMap, pinBody("앨리스 핀", 37.5, 127.0, null, null, null));
        send(alice, delete("/api/v1/maps/{m}/pins/{p}/visits/{v}", aliceMap, alicePin, bobVisit)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from visit_logs where id = ?::uuid", Long.class, bobVisit)).isEqualTo(1L);
    }

    @Test
    @DisplayName("핀이나 지도를 지우면 방문 기록도 함께 지워진다")
    void visitsAreDeletedWithPin() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "정리");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));
        visit(owner, mapId, pinId, today(), "기록");

        send(owner, delete("/api/v1/maps/{m}/pins/{p}", mapId, pinId)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from visit_logs where pin_id = ?::uuid", Long.class, pinId)).isZero();
    }

    // ------------------------------------------------------------------ 사적 메모

    @Test
    @DisplayName("사적 메모: 저장하면 내 목록에 나오고 다시 저장하면 덮어쓰며, 삭제할 수 있다")
    void privateNoteLifecycle() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "메모");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));

        putNote(owner, mapId, pinId, "  사장님이 불친절  ").andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value("사장님이 불친절"));
        putNote(owner, mapId, pinId, "다시 생각해보니 괜찮았음").andExpect(status().isOk());

        send(owner, get("/api/v1/maps/{m}/private-notes", mapId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].pinId").value(pinId))
                .andExpect(jsonPath("$.data[0].body").value("다시 생각해보니 괜찮았음"));
        assertThat(jdbc.queryForObject("select count(*) from pin_private_notes where pin_id = ?::uuid", Long.class, pinId)).isEqualTo(1L);

        send(owner, delete("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId)).andExpect(status().isOk());
        send(owner, get("/api/v1/maps/{m}/private-notes", mapId)).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("사적 메모는 쓴 사람에게만 보이고, 핀 응답 어디에도 실리지 않는다")
    void privateNoteIsNeverShared() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "비밀");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));
        String ownerSecret = "주인만아는비밀" + UUID.randomUUID().toString().substring(0, 8);
        String viewerSecret = "열람자만아는비밀" + UUID.randomUUID().toString().substring(0, 8);

        putNote(owner, mapId, pinId, ownerSecret).andExpect(status().isOk());
        putNote(viewer, mapId, pinId, viewerSecret).andExpect(status().isOk());

        // 각자 자기 메모만 본다
        String ownerView = send(owner, get("/api/v1/maps/{m}/private-notes", mapId)).andReturn().getResponse().getContentAsString();
        String viewerView = send(viewer, get("/api/v1/maps/{m}/private-notes", mapId)).andReturn().getResponse().getContentAsString();
        assertThat(ownerView).contains(ownerSecret).doesNotContain(viewerSecret);
        assertThat(viewerView).contains(viewerSecret).doesNotContain(ownerSecret);

        // 공유되는 응답(핀 목록, 방문 기록)에는 어떤 사적 메모도 없다
        for (TestUser reader : List.of(owner, viewer)) {
            String pins = send(reader, get("/api/v1/maps/{m}/pins", mapId)).andReturn().getResponse().getContentAsString();
            String visits = send(reader, get("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId)).andReturn().getResponse().getContentAsString();
            assertThat(pins + visits).doesNotContain(ownerSecret).doesNotContain(viewerSecret);
        }

        // 한 사람의 삭제는 다른 사람의 메모에 영향을 주지 않는다
        send(viewer, delete("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId)).andExpect(status().isOk());
        assertThat(send(owner, get("/api/v1/maps/{m}/private-notes", mapId)).andReturn().getResponse().getContentAsString()).contains(ownerSecret);
    }

    @Test
    @DisplayName("사적 메모 입력 검증과 접근 제한: 빈 메모·긴 메모 400, 지도를 볼 수 없는 사람은 403, 다른 지도 핀은 404")
    void privateNoteValidationAndAccess() throws Exception {
        TestUser owner = newUser();
        TestUser stranger = newUser();
        String mapId = createMap(owner, "접근");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));

        putNote(owner, mapId, pinId, "   ").andExpect(status().isBadRequest());
        putNote(owner, mapId, pinId, "가".repeat(2001)).andExpect(status().isBadRequest());
        putNote(owner, mapId, pinId, "가".repeat(2000)).andExpect(status().isOk());

        putNote(stranger, mapId, pinId, "침입").andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{m}/private-notes", mapId)).andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId)).andExpect(status().isForbidden());

        String strangerMap = createMap(stranger, "남의 지도");
        putNote(stranger, strangerMap, pinId, "다른 지도 경로로").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/maps/{m}/private-notes", mapId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 사용자가 처음 저장하는 요청이 동시에 와도 오류 없이 하나만 남는다")
    void privateNoteConcurrentFirstSave() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "동시");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, null, null, null));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String text = "메모" + i;
                Callable<Integer> call = () -> putNote(owner, mapId, pinId, text).andReturn().getResponse().getStatus();
                futures.add(pool.submit(call));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from pin_private_notes where pin_id = ?::uuid", Long.class, pinId)).isEqualTo(1L);
    }
}
