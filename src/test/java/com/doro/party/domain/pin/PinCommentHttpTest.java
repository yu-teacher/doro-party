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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 핀 댓글: 지도를 볼 수 있는 사람이면 누구나 쓰고, 쓴 사람만 고치며, 새 댓글은 핀을 꽂은 사람과 댓글 참여자에게만 알린다. */
class PinCommentHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private ResultActions comment(TestUser user, String mapId, String pinId, String body) throws Exception {
        return send(user, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"));
    }

    private String commentId(TestUser user, String mapId, String pinId, String body) throws Exception {
        String response = comment(user, mapId, pinId, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private ResultActions list(TestUser user, String mapId, String pinId) throws Exception {
        return send(user, get("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId));
    }

    private ResultActions markRead(TestUser user, String mapId, String pinId, String upTo) throws Exception {
        return send(user, post("/api/v1/maps/{m}/pins/{p}/comments/read", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"upTo\":\"" + upTo + "\"}"));
    }

    private List<Integer> unreadCounts(TestUser user) throws Exception {
        String body = send(user, get("/api/v1/comments/unread")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].unread");
    }

    private String lastCommentTime(TestUser user, String mapId, String pinId) throws Exception {
        String body = list(user, mapId, pinId).andReturn().getResponse().getContentAsString();
        List<String> times = JsonPath.read(body, "$.data[*].createdAt");
        return times.get(times.size() - 1);
    }

    // ------------------------------------------------------------------ 쓰기와 읽기

    @Test
    @DisplayName("지도를 볼 수 있는 viewer 도 댓글을 남기고, 오래된 순으로 작성자 이름과 함께 보이며, 핀 목록에 댓글 수가 실린다")
    void viewerCanComment() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "맛집");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("국밥집", 37.5, 127.0, "WISH", null, null));

        comment(viewer, mapId, pinId, "여기 웨이팅 길어요").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(viewer.id().toString()))
                .andExpect(jsonPath("$.data.body").value("여기 웨이팅 길어요"))
                .andExpect(jsonPath("$.data.editedAt").doesNotExist());
        comment(owner, mapId, pinId, "  평일에 가볼게요  ").andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value("평일에 가볼게요"));

        list(owner, mapId, pinId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].body").value("여기 웨이팅 길어요"))
                .andExpect(jsonPath("$.data[0].authorNickname").isNotEmpty())
                .andExpect(jsonPath("$.data[1].body").value("평일에 가볼게요"));
        send(viewer, get("/api/v1/maps/{m}/pins", mapId)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].commentCount").value(2));
    }

    @Test
    @DisplayName("지도를 볼 수 없는 사람은 댓글을 읽지도 쓰지도 못한다(403)")
    void strangerIsDenied() throws Exception {
        TestUser owner = newUser();
        TestUser stranger = newUser();
        String mapId = createMap(owner, "비공개");
        String pinId = createPin(owner, mapId, pinBody("아지트", 37.5, 127.0, "WISH", null, null));
        String id = commentId(owner, mapId, pinId, "비밀");

        comment(stranger, mapId, pinId, "나도 끼워줘").andExpect(status().isForbidden());
        list(stranger, mapId, pinId).andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, pinId, id)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from pin_comments where pin_id = ?::uuid", Long.class, pinId)).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 댓글, 500자 초과는 400, 앞뒤 공백은 잘라 저장한다")
    void validation() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "검증");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));

        comment(owner, mapId, pinId, "   ").andExpect(status().isBadRequest());
        comment(owner, mapId, pinId, "가".repeat(501)).andExpect(status().isBadRequest());
        comment(owner, mapId, pinId, "가".repeat(500)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("핀마다 댓글 개수 상한을 넘으면 400")
    void perPinLimit() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "상한");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        jdbc.update("insert into pin_comments (id, pin_id, user_id, body, created_at) select gen_random_uuid(), ?::uuid, ?::uuid, 'x', now() from generate_series(1, 500)",
                pinId, owner.id().toString());

        comment(owner, mapId, pinId, "501번째").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT-400-01"));
    }

    @Test
    @DisplayName("다른 지도의 핀 ID 로는 댓글에 접근할 수 없다(IDOR): 내 다른 지도 경로로 불러도 404")
    void pinMustBelongToTheMap() throws Exception {
        TestUser owner = newUser();
        String mapA = createMap(owner, "A");
        String mapB = createMap(owner, "B");
        String pinInA = createPin(owner, mapA, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        String id = commentId(owner, mapA, pinInA, "A 의 댓글");

        comment(owner, mapB, pinInA, "B 경로로").andExpect(status().isNotFound());
        list(owner, mapB, pinInA).andExpect(status().isNotFound());
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapB, pinInA, id)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 고치기와 지우기

    @Test
    @DisplayName("고치기는 쓴 사람만: 지도 주인이어도 남의 댓글은 못 고치고, 고치면 editedAt 이 생긴다")
    void onlyAuthorEdits() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "수정");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        String id = commentId(viewer, mapId, pinId, "처음");

        send(owner, patch("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, pinId, id).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"주인이 바꿈\"}"))
                .andExpect(status().isForbidden());
        send(viewer, patch("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, pinId, id).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"수정\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value("수정")).andExpect(jsonPath("$.data.editedAt").isNotEmpty());
        list(owner, mapId, pinId).andExpect(jsonPath("$.data[0].body").value("수정"));
    }

    @Test
    @DisplayName("지우기는 쓴 사람, 핀을 꽂은 사람, 지도 주인만. 다른 viewer 는 403")
    void whoCanDelete() throws Exception {
        TestUser owner = newUser();
        TestUser editor = newUser();
        TestUser writer = newUser();
        TestUser bystander = newUser();
        String mapId = createMap(owner, "삭제");
        grant(mapId, PartyGuard.EDITOR, editor);
        grant(mapId, PartyGuard.VIEWER, writer);
        grant(mapId, PartyGuard.VIEWER, bystander);
        String editorPin = createPin(editor, mapId, pinBody("편집자 핀", 37.5, 127.0, "WISH", null, null));

        String byWriter = commentId(writer, mapId, editorPin, "내 댓글");
        send(bystander, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, editorPin, byWriter)).andExpect(status().isForbidden());
        send(writer, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, editorPin, byWriter)).andExpect(status().isOk());

        String second = commentId(writer, mapId, editorPin, "핀 주인이 지움");
        send(editor, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, editorPin, second)).andExpect(status().isOk());

        String third = commentId(writer, mapId, editorPin, "지도 주인이 지움");
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, editorPin, third)).andExpect(status().isOk());

        list(owner, mapId, editorPin).andExpect(jsonPath("$.data.length()").value(0));
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, editorPin, third)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("핀이나 지도를 지우면 댓글과 읽음 표시도 함께 지워진다")
    void cascade() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "연쇄");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        commentId(viewer, mapId, pinId, "곧 사라짐");
        assertThat(jdbc.queryForObject("select count(*) from pin_comment_reads", Long.class)).isGreaterThan(0);

        send(owner, delete("/api/v1/maps/{m}/pins/{p}", mapId, pinId)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from pin_comments where pin_id = ?::uuid", Long.class, pinId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pin_comment_reads where pin_id = ?::uuid", Long.class, pinId)).isZero();
    }

    // ------------------------------------------------------------------ 새 댓글(읽지 않음)

    @Test
    @DisplayName("핀을 꽂은 사람에게 남의 댓글이 새 댓글로 알려지고, 내가 쓴 댓글은 세지 않으며, 읽음 표시하면 사라진다")
    void unreadForPinCreator() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "알림");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("국밥집", 37.5, 127.0, "WISH", null, null));

        commentId(owner, mapId, pinId, "내가 쓴 건 안 센다");
        assertThat(unreadCounts(owner)).isEmpty();

        commentId(viewer, mapId, pinId, "첫 댓글");
        commentId(viewer, mapId, pinId, "두 번째");
        send(owner, get("/api/v1/comments/unread")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pinId").value(pinId))
                .andExpect(jsonPath("$.data[0].mapId").value(mapId))
                .andExpect(jsonPath("$.data[0].pinName").value("국밥집"))
                .andExpect(jsonPath("$.data[0].unread").value(2));

        markRead(owner, mapId, pinId, lastCommentTime(owner, mapId, pinId)).andExpect(status().isOk());
        assertThat(unreadCounts(owner)).isEmpty();

        commentId(viewer, mapId, pinId, "읽은 뒤의 댓글");
        assertThat(unreadCounts(owner)).containsExactly(1);
    }

    @Test
    @DisplayName("읽음 표시가 없어도 내가 쓴 댓글은 새 댓글로 세지 않는다")
    void ownCommentsNeverCountEvenWithoutReadMark() throws Exception {
        TestUser owner = newUser();
        String mapId = createMap(owner, "내 댓글");
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        jdbc.update("insert into pin_comments (id, pin_id, user_id, body, created_at) values (gen_random_uuid(), ?::uuid, ?::uuid, '읽음 표시 없이 들어간 내 댓글', now())",
                pinId, owner.id().toString());

        assertThat(unreadCounts(owner)).isEmpty();
    }

    @Test
    @DisplayName("읽음 표시는 앞으로만 간다: 오래된 시각으로 다시 보내도 이미 읽은 댓글이 새 댓글로 되돌아오지 않는다")
    void readMarkNeverMovesBack() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "순서");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        commentId(viewer, mapId, pinId, "하나");
        markRead(owner, mapId, pinId, lastCommentTime(owner, mapId, pinId)).andExpect(status().isOk());

        markRead(owner, mapId, pinId, Instant.parse("2020-01-01T00:00:00Z").toString()).andExpect(status().isOk());

        assertThat(unreadCounts(owner)).isEmpty();
    }

    @Test
    @DisplayName("댓글을 남긴 사람에게는 답글이 새 댓글로 알려지지만, 댓글도 안 달고 핀도 안 꽂은 viewer 에게는 알리지 않는다")
    void unreadForParticipantsOnly() throws Exception {
        TestUser owner = newUser();
        TestUser asker = newUser();
        TestUser lurker = newUser();
        String mapId = createMap(owner, "참여자");
        grant(mapId, PartyGuard.VIEWER, asker);
        grant(mapId, PartyGuard.VIEWER, lurker);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));

        commentId(asker, mapId, pinId, "질문 있어요");
        assertThat(unreadCounts(asker)).isEmpty();
        commentId(owner, mapId, pinId, "답변이에요");

        assertThat(unreadCounts(asker)).containsExactly(1);
        assertThat(unreadCounts(lurker)).isEmpty();
    }

    @Test
    @DisplayName("지도를 더 이상 볼 수 없게 되면 그 핀의 새 댓글은 목록에서 빠지고 다시 공유하면 돌아온다")
    void unreadHidesPinsOfMapsNoLongerVisible() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "공유 취소");
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        commentId(friend, mapId, pinId, "질문");
        commentId(owner, mapId, pinId, "답변");
        assertThat(unreadCounts(friend)).containsExactly(1);

        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());
        assertThat(unreadCounts(friend)).isEmpty();
        list(friend, mapId, pinId).andExpect(status().isForbidden());

        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        assertThat(unreadCounts(friend)).containsExactly(1);
    }

    @Test
    @DisplayName("로그인하지 않으면 새 댓글 목록은 401")
    void unreadRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/comments/unread")).andExpect(status().isUnauthorized());
    }
}
