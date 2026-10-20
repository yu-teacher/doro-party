package com.doro.party.domain.push;

import com.doro.party.domain.push.PushTestSupport.TestDevice;
import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 답글 알림: 답한 상대에게는 "내 댓글에 답글", 같은 스레드 참여자와 핀을 꽂은 사람에게는 일반 알림, 그 밖의 사람에게는 보내지 않는다. */
class PushReplyDispatchTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier("pushExecutor") private Executor pushExecutor;

    private static final Duration WAIT = Duration.ofSeconds(10);

    private TestDevice register(TestUser user) throws Exception {
        TestDevice device = new TestDevice();
        send(user, put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(device.subscriptionJson())).andExpect(status().isOk());
        return device;
    }

    private ResultActions comment(TestUser user, String mapId, String pinId, String body, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        return send(user, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"" + parent + "}"));
    }

    private String commentId(TestUser user, String mapId, String pinId, String body, String parentId) throws Exception {
        return JsonPath.read(comment(user, mapId, pinId, body, parentId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private void awaitPushIdle() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) pushExecutor;
        await().atMost(WAIT).until(() -> executor.getThreadPoolExecutor().getQueue().isEmpty() && executor.getActiveCount() == 0);
    }

    private void awaitCount(TestDevice device, int count) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(device.received()).hasSize(count));
    }

    private void setComments(TestUser user, boolean on) throws Exception {
        send(user, put("/api/v1/push/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"comments\":" + on + ",\"friendRequests\":true,\"shares\":true}")).andExpect(status().isOk());
    }

    /** 핀 주인(owner)과 viewer 셋(a, b, c). 각자 기기를 하나씩 등록한다. */
    private record Scene(TestUser owner, TestUser a, TestUser b, TestUser c, String mapId, String pinId, TestDevice ownerPhone, TestDevice aPhone, TestDevice bPhone, TestDevice cPhone) {
    }

    private Scene scene() throws Exception {
        TestUser owner = newUser();
        TestUser a = newUser();
        TestUser b = newUser();
        TestUser c = newUser();
        String mapId = createMap(owner, "답글 알림");
        grant(mapId, PartyGuard.VIEWER, a);
        grant(mapId, PartyGuard.VIEWER, b);
        grant(mapId, PartyGuard.VIEWER, c);
        String pinId = createPin(owner, mapId, pinBody("국밥집", 37.5, 127.0, "WISH", null, null));
        return new Scene(owner, a, b, c, mapId, pinId, register(owner), register(a), register(b), register(c));
    }

    @Test
    @DisplayName("답글을 받은 사람에게는 '내 댓글에 답글' 알림이, 핀 주인에게는 일반 답글 알림이 가고, 눌렀을 때 그 답글로 가는 주소가 담긴다")
    void replyNotifiesRepliedToAndPinOwner() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "질문이에요", null);
        awaitCount(s.ownerPhone(), 1);   // 맨 위 댓글: 핀 주인에게 일반 댓글 알림
        awaitPushIdle();

        String reply = commentId(s.b(), s.mapId(), s.pinId(), "답이에요", root);

        awaitCount(s.aPhone(), 1);
        assertThat(s.aPhone().lastField("title")).isEqualTo("국밥집");
        assertThat(s.aPhone().lastField("body")).contains("님이 내 댓글에 답글").endsWith("답이에요");
        assertThat(s.aPhone().lastField("url")).isEqualTo("./?map=" + s.mapId() + "&pin=" + s.pinId() + "&comment=" + reply);
        assertThat(s.aPhone().lastField("tag")).isEqualTo("reply:" + root);
        awaitCount(s.ownerPhone(), 2);
        assertThat(s.ownerPhone().lastField("body")).contains("(답글)").endsWith("답이에요");
        awaitPushIdle();
        assertThat(s.bPhone().received()).isEmpty();    // 쓴 사람
        assertThat(s.cPhone().received()).isEmpty();    // 이 스레드와 무관한 viewer
    }

    @Test
    @DisplayName("같은 스레드에 글을 남긴 사람에게도 가고, 다른 스레드에만 참여한 사람에게는 가지 않는다")
    void threadParticipantsOnly() throws Exception {
        Scene s = scene();
        String thread1 = commentId(s.a(), s.mapId(), s.pinId(), "첫 스레드", null);
        commentId(s.c(), s.mapId(), s.pinId(), "다른 스레드", null);          // c 는 다른 스레드의 원글 작성자
        awaitPushIdle();
        int cBefore = s.cPhone().received().size();
        commentId(s.b(), s.mapId(), s.pinId(), "첫 스레드 답글", thread1);
        awaitPushIdle();
        int aBefore = s.aPhone().received().size();

        // a 가 자기 스레드에 다시 답글을 달면: b(스레드 참여자)에게 가고, c(다른 스레드)에게는 가지 않는다
        commentId(s.a(), s.mapId(), s.pinId(), "제 스레드에 이어서", thread1);

        awaitPushIdle();
        assertThat(s.bPhone().received()).hasSize(1);
        assertThat(s.bPhone().lastField("body")).contains("(답글)");
        assertThat(s.cPhone().received()).hasSize(cBefore);
        assertThat(s.aPhone().received()).hasSize(aBefore);   // 쓴 사람
    }

    @Test
    @DisplayName("답글의 답글: 답한 상대에게는 직접 알림, 원글 작성자에게는 일반 답글 알림. 한 사람이 두 역할이면 한 번만(직접 알림으로) 간다")
    void replyToReplyNotifiesDirectOnceAndRootAuthor() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "원글", null);
        String firstReply = commentId(s.b(), s.mapId(), s.pinId(), "첫 답글", root);
        awaitPushIdle();
        int aBefore = s.aPhone().received().size();
        int bBefore = s.bPhone().received().size();

        commentId(s.c(), s.mapId(), s.pinId(), "답글에 답글", firstReply);

        awaitPushIdle();
        assertThat(s.bPhone().received()).hasSize(bBefore + 1);
        assertThat(s.bPhone().lastField("body")).contains("님이 내 댓글에 답글");       // 답한 상대 = b
        assertThat(s.aPhone().received()).hasSize(aBefore + 1);
        assertThat(s.aPhone().lastField("body")).contains("(답글)");                   // 원글 작성자 = a (스레드 참여자)
    }

    @Test
    @DisplayName("핀 주인이 답한 상대이기도 하면 한 번만 가고, 그 알림은 '내 댓글에 답글' 이다")
    void pinOwnerWhoIsAlsoRepliedToGetsOneDirectNotification() throws Exception {
        Scene s = scene();
        String root = commentId(s.owner(), s.mapId(), s.pinId(), "주인의 댓글", null);
        awaitPushIdle();
        int before = s.ownerPhone().received().size();

        commentId(s.a(), s.mapId(), s.pinId(), "주인에게 답글", root);

        awaitCount(s.ownerPhone(), before + 1);
        awaitPushIdle();
        assertThat(s.ownerPhone().received()).hasSize(before + 1);
        assertThat(s.ownerPhone().lastField("body")).contains("님이 내 댓글에 답글");
    }

    @Test
    @DisplayName("댓글 알림을 끈 사람에게는 답글 알림도 가지 않는다")
    void respectsPreferences() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "질문", null);
        awaitPushIdle();
        setComments(s.a(), false);
        int before = s.aPhone().received().size();

        commentId(s.b(), s.mapId(), s.pinId(), "답", root);

        awaitPushIdle();
        assertThat(s.aPhone().received()).hasSize(before);
    }

    @Test
    @DisplayName("지도를 더 이상 볼 수 없는 사람에게는 답글 알림도 가지 않는다(권한을 다시 확인한다)")
    void respectsAccessRevocation() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        TestUser other = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "공유 취소");
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        grant(mapId, PartyGuard.VIEWER, other);
        String pinId = createPin(owner, mapId, pinBody("비밀 장소", 37.5, 127.0, "WISH", null, null));
        TestDevice friendPhone = register(friend);
        String root = commentId(friend, mapId, pinId, "질문", null);
        awaitPushIdle();

        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());
        commentId(other, mapId, pinId, "답", root);

        awaitPushIdle();
        assertThat(friendPhone.received()).isEmpty();
    }

    @Test
    @DisplayName("지워진 댓글의 작성자에게는 답글이 달려도(삭제 전에 달린 것이 아니라면) 스레드 알림이 가지 않는다")
    void deletedAuthorGetsNoThreadNotification() throws Exception {
        Scene s = scene();
        String root = commentId(s.a(), s.mapId(), s.pinId(), "곧 지울 댓글", null);
        String reply = commentId(s.b(), s.mapId(), s.pinId(), "답글", root);
        send(s.a(), delete("/api/v1/maps/{m}/pins/{p}/comments/{c}", s.mapId(), s.pinId(), root)).andExpect(status().isOk());
        awaitPushIdle();
        int aBefore = s.aPhone().received().size();

        commentId(s.c(), s.mapId(), s.pinId(), "삭제 뒤의 답글", reply);

        awaitPushIdle();
        assertThat(s.aPhone().received()).hasSize(aBefore);   // a 의 댓글은 지워졌으니 더는 이 스레드의 참여자가 아니다
        assertThat(jdbc.queryForObject("select count(*) from pin_comments where pin_id = ?::uuid", Long.class, s.pinId())).isEqualTo(3);
    }
}
