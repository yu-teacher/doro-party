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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 대댓글(한 단계): 구조, 삭제된 댓글 표시, 권한, 읽지 않음. */
class PinCommentReplyHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private ResultActions comment(TestUser user, String mapId, String pinId, String body, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        return send(user, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"" + parent + "}"));
    }

    private String commentId(TestUser user, String mapId, String pinId, String body, String parentId) throws Exception {
        String response = comment(user, mapId, pinId, body, parentId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private ResultActions list(TestUser user, String mapId, String pinId) throws Exception {
        return send(user, get("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId));
    }

    private ResultActions remove(TestUser user, String mapId, String pinId, String id) throws Exception {
        return send(user, delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", mapId, pinId, id));
    }

    private long rows(String pinId) {
        return jdbc.queryForObject("select count(*) from pin_comments where pin_id = ?::uuid", Long.class, pinId);
    }

    private List<Integer> unreadCounts(TestUser user) throws Exception {
        String body = send(user, get("/api/v1/comments/unread")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].unread");
    }

    /** 주인(owner)의 핀과 두 viewer(a, b). */
    private record Scene(TestUser owner, TestUser a, TestUser b, String mapId, String pinId) {
    }

    private Scene scene() throws Exception {
        TestUser owner = newUser();
        TestUser a = newUser();
        TestUser b = newUser();
        String mapId = createMap(owner, "답글");
        grant(mapId, PartyGuard.VIEWER, a);
        grant(mapId, PartyGuard.VIEWER, b);
        String pinId = createPin(owner, mapId, pinBody("국밥집", 37.5, 127.0, "WISH", null, null));
        return new Scene(owner, a, b, mapId, pinId);
    }

    // ------------------------------------------------------------------ 구조

    @Test
    @DisplayName("답글을 달면 어느 댓글에 속하는지와 누구에게 답한 글인지가 기록되고, 목록에 함께 온다")
    void replyRecordsParentAndReplyTo() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "질문이에요", null);

        comment(s.b(), s.mapId(), s.pinId(), "답이에요", root).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(root))
                .andExpect(jsonPath("$.data.replyToNickname").isNotEmpty())
                .andExpect(jsonPath("$.data.deleted").value(false));

        list(s.owner(), s.mapId(), s.pinId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.data[0].replyToNickname").doesNotExist())
                .andExpect(jsonPath("$.data[1].parentId").value(root))
                .andExpect(jsonPath("$.data[1].body").value("답이에요"));
    }

    @Test
    @DisplayName("답글의 답글은 같은 맨 위 댓글에 붙고(한 단계), 답한 상대는 그 답글의 작성자다")
    void replyToReplyStaysOneLevel() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "질문", null);
        String firstReply = commentId(s.b(), s.mapId(), s.pinId(), "첫 답글", root);

        String response = comment(s.owner(), s.mapId(), s.pinId(), "답글에 답글", firstReply).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(response, "$.data.parentId")).isEqualTo(root);   // 맨 위 댓글에 붙는다
        String bNickname = jdbc.queryForObject("select nickname from party_users where id = ?::uuid", String.class, s.b().id().toString());
        assertThat((String) JsonPath.read(response, "$.data.replyToNickname")).isEqualTo(bNickname);
        // 이 핀의 답글은 모두 맨 위 댓글 하나에만 붙어 있다(답글 아래에 또 답글이 매달리지 않는다)
        assertThat(jdbc.queryForObject("select count(*) from pin_comments where pin_id = ?::uuid and parent_id is not null and parent_id <> ?::uuid", Long.class, s.pinId(), root)).isZero();
    }

    @Test
    @DisplayName("없는 댓글, 다른 핀의 댓글에는 답글을 달 수 없다(404). 지워진 댓글에도 달 수 없다(400)")
    void invalidParents() throws Exception {
        Scene s = scene();
        String otherPin = createPin(s.owner(), s.mapId(), pinBody("다른 핀", 37.6, 127.1, "WISH", null, null));
        String onOtherPin = commentId(s.a(), s.mapId(), otherPin, "다른 핀의 댓글", null);

        comment(s.b(), s.mapId(), s.pinId(), "엉뚱한 부모", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        comment(s.b(), s.mapId(), s.pinId(), "다른 핀의 댓글에 답글", onOtherPin).andExpect(status().isNotFound());
        assertThat(rows(s.pinId())).isZero();

        String root = commentId(s.a(), s.mapId(), s.pinId(), "곧 지워질 댓글", null);
        commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());     // 답글이 있어 "삭제된 댓글" 로 남는다
        comment(s.owner(), s.mapId(), s.pinId(), "삭제된 댓글에 답글", root).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("핀 목록의 댓글 수는 답글까지 센다")
    void commentCountIncludesReplies() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "하나", null);
        commentId(s.b(), s.mapId(), s.pinId(), "둘", root);
        commentId(s.owner(), s.mapId(), s.pinId(), "셋", root);

        send(s.owner(), get("/api/v1/maps/{m}/pins", s.mapId())).andExpect(jsonPath("$.data[0].commentCount").value(3));
    }

    // ------------------------------------------------------------------ 삭제

    @Test
    @DisplayName("답글이 없는 댓글은 그냥 지워진다")
    void deletingCommentWithoutRepliesRemovesIt() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "혼자 있는 댓글", null);

        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());

        assertThat(rows(s.pinId())).isZero();
    }

    @Test
    @DisplayName("답글이 달린 댓글을 지우면 '삭제된 댓글' 로 남고(내용·작성자 숨김) 답글은 그대로 보인다. 댓글 수에서는 빠진다")
    void deletingCommentWithRepliesLeavesTombstone() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "비밀 내용", null);
        commentId(s.b(), s.mapId(), s.pinId(), "남아야 할 답글", root);

        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());

        list(s.b(), s.mapId(), s.pinId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].deleted").value(true))
                .andExpect(jsonPath("$.data[0].body").value(""))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].authorNickname").value(""))
                .andExpect(jsonPath("$.data[1].body").value("남아야 할 답글"))
                .andExpect(jsonPath("$.data[1].parentId").value(root));
        assertThat(jdbc.queryForObject("select body from pin_comments where id = ?::uuid", String.class, root)).isEmpty();   // 내용은 DB 에서도 비운다
        send(s.owner(), get("/api/v1/maps/{m}/pins", s.mapId())).andExpect(jsonPath("$.data[0].commentCount").value(1));
    }

    @Test
    @DisplayName("지워진 댓글은 다시 지우거나 고칠 수 없다(404), 남이 지울 수도 없다")
    void tombstoneIsFinal() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "내용", null);
        commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());

        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isNotFound());
        remove(s.owner(), s.mapId(), s.pinId(), root).andExpect(status().isNotFound());
        send(s.a(), patch("/api/v1/maps/{m}/pins/{p}/comments/{c}", s.mapId(), s.pinId(), root).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"되살리기\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select body from pin_comments where id = ?::uuid", String.class, root)).isEmpty();
    }

    @Test
    @DisplayName("답글만 지우면 답글만 사라지고, 지워진 맨 위 댓글에 마지막 답글이 사라지면 그 댓글도 함께 정리된다")
    void deletingLastReplyCleansUpTombstone() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "원글", null);
        String r1 = commentId(s.b(), s.mapId(), s.pinId(), "답글 1", root);
        String r2 = commentId(s.owner(), s.mapId(), s.pinId(), "답글 2", root);

        remove(s.b(), s.mapId(), s.pinId(), r1).andExpect(status().isOk());
        assertThat(rows(s.pinId())).isEqualTo(2);                       // 원글과 답글 2 가 남는다

        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());   // 답글 2 가 있어 "삭제된 댓글" 로 남는다
        assertThat(rows(s.pinId())).isEqualTo(2);

        remove(s.owner(), s.mapId(), s.pinId(), r2).andExpect(status().isOk());  // 마지막 답글: 지워진 원글도 함께 정리
        assertThat(rows(s.pinId())).isZero();
    }

    @Test
    @DisplayName("지우는 권한은 그대로: 쓴 사람, 핀을 꽂은 사람, 지도 주인. 다른 viewer 는 403")
    void deletePermissionsUnchanged() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "원글", null);
        String reply = commentId(s.b(), s.mapId(), s.pinId(), "답글", root);

        remove(s.a(), s.mapId(), s.pinId(), reply).andExpect(status().isForbidden());   // 남의 답글을 지울 수 없다(원글 작성자여도)
        remove(s.owner(), s.mapId(), s.pinId(), reply).andExpect(status().isOk());       // 핀 주인은 지울 수 있다
    }

    @Test
    @DisplayName("핀을 지우면 답글과 삭제 표시된 댓글까지 모두 지워진다")
    void pinDeletionCascades() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "원글", null);
        commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());

        send(s.owner(), delete("/api/v1/maps/{m}/pins/{p}", s.mapId(), s.pinId())).andExpect(status().isOk());

        assertThat(rows(s.pinId())).isZero();
    }

    // ------------------------------------------------------------------ 읽지 않음

    @Test
    @DisplayName("내 댓글에 남이 답글을 달면 새 댓글로 세고, 지워진 댓글은 세지 않는다")
    void unreadCountsRepliesButNotTombstones() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "질문", null);
        assertThat(unreadCounts(s.a())).isEmpty();

        String reply = commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        assertThat(unreadCounts(s.a())).containsExactly(1);

        remove(s.b(), s.mapId(), s.pinId(), reply).andExpect(status().isOk());       // 답글을 지우면 더는 새 댓글이 아니다
        assertThat(unreadCounts(s.a())).isEmpty();
    }

    @Test
    @DisplayName("남이 쓴 댓글이 '삭제된 댓글' 로 남은 뒤에는 새 댓글로 세지 않는다(내용이 없어진 것을 알릴 이유가 없다)")
    void unreadIgnoresTombstonesOfOthers() throws Exception {
        Scene s = scene();
        commentId(s.a(), s.mapId(), s.pinId(), "a 의 댓글", null);                       // a 는 이 핀의 참여자
        String others = commentId(s.b(), s.mapId(), s.pinId(), "b 의 댓글", null);
        commentId(s.owner(), s.mapId(), s.pinId(), "b 의 댓글에 답글", others);
        assertThat(unreadCounts(s.a())).containsExactly(2);                              // b 의 댓글과 그 답글

        remove(s.b(), s.mapId(), s.pinId(), others).andExpect(status().isOk());          // 답글이 있어 삭제 표시로 남는다

        assertThat(unreadCounts(s.a())).containsExactly(1);                              // 지워진 b 의 댓글은 빠지고 답글만 남는다
    }

    @Test
    @DisplayName("지워진 댓글만 남긴 사람은 그 핀의 참여자로 세지 않는다(새 댓글 알림 대상에서 빠진다)")
    void deletedAuthorIsNotAParticipant() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "내 댓글", null);
        commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        remove(s.a(), s.mapId(), s.pinId(), root).andExpect(status().isOk());          // a 의 댓글은 삭제 표시만 남는다

        commentId(s.owner(), s.mapId(), s.pinId(), "주인의 새 댓글", null);

        assertThat(unreadCounts(s.a())).isEmpty();   // a 는 더 이상 이 핀의 참여자가 아니다
    }
}
