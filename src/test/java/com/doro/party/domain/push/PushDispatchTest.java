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

/** 어떤 일이 있을 때 누구에게 푸시가 가는지(그리고 누구에게 가지 않는지). 받은 메시지는 기기 역할이 직접 복호화해서 확인한다. */
class PushDispatchTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier("pushExecutor") private Executor pushExecutor;

    private static final Duration WAIT = Duration.ofSeconds(10);

    private TestDevice register(TestUser user) throws Exception {
        TestDevice device = new TestDevice();
        send(user, put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(device.subscriptionJson())).andExpect(status().isOk());
        return device;
    }

    private ResultActions comment(TestUser user, String mapId, String pinId, String body) throws Exception {
        return send(user, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"));
    }

    /** 이미 시작된 푸시 작업이 모두 끝날 때까지 기다린다("가지 않았다" 를 확인하기 전에 쓴다). */
    private void awaitPushIdle() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) pushExecutor;
        await().atMost(WAIT).until(() -> executor.getThreadPoolExecutor().getQueue().isEmpty() && executor.getActiveCount() == 0);
    }

    private void awaitCount(TestDevice device, int count) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(device.received()).hasSize(count));
    }

    private void setPreferences(TestUser user, boolean comments, boolean friendRequests, boolean shares) throws Exception {
        send(user, put("/api/v1/push/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comments\":" + comments + ",\"friendRequests\":" + friendRequests + ",\"shares\":" + shares + "}")).andExpect(status().isOk());
    }

    private String subscriptionCount(TestDevice device) {
        return jdbc.queryForObject("select count(*) from push_subscriptions where endpoint = ?", String.class, device.endpoint);
    }

    // ------------------------------------------------------------------ 댓글

    @Test
    @DisplayName("댓글: 핀을 꽂은 사람에게 가고, 제목은 핀 이름, 본문은 '작성자: 내용', 눌렀을 때 그 핀으로 가는 주소와 합치기용 태그가 담긴다")
    void commentNotifiesPinCreator() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "맛집");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("골목 국밥집", 37.5, 127.0, "WISH", null, null));
        TestDevice ownerPhone = register(owner);

        comment(viewer, mapId, pinId, "여기 웨이팅 길어요").andExpect(status().isOk());

        awaitCount(ownerPhone, 1);
        assertThat(ownerPhone.lastField("title")).isEqualTo("골목 국밥집");
        assertThat(ownerPhone.lastField("body")).endsWith(": 여기 웨이팅 길어요");
        assertThat(ownerPhone.lastField("url")).startsWith("./?map=" + mapId + "&pin=" + pinId + "&comment=");   // 눌렀을 때 그 댓글로 간다
        assertThat(ownerPhone.lastField("tag")).isEqualTo("comment:" + pinId);
        assertThat(ownerPhone.received().get(0).headers().get("topic")).hasSize(32);
        assertThat(ownerPhone.received().get(0).headers().get("urgency")).isEqualTo("high");
    }

    @Test
    @DisplayName("댓글: 쓴 사람 자신, 핀과 무관한 보기 전용 사람에게는 가지 않고, 전에 댓글을 남긴 참여자에게는 간다")
    void commentRecipientsAreCreatorAndParticipantsOnly() throws Exception {
        TestUser owner = newUser();
        TestUser asker = newUser();
        TestUser lurker = newUser();
        String mapId = createMap(owner, "참여자");
        grant(mapId, PartyGuard.VIEWER, asker);
        grant(mapId, PartyGuard.VIEWER, lurker);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice ownerPhone = register(owner);
        TestDevice askerPhone = register(asker);
        TestDevice lurkerPhone = register(lurker);

        comment(asker, mapId, pinId, "질문 있어요").andExpect(status().isOk());
        awaitCount(ownerPhone, 1);
        awaitPushIdle();
        assertThat(askerPhone.received()).isEmpty();   // 내가 쓴 댓글은 알리지 않는다
        assertThat(lurkerPhone.received()).isEmpty();

        comment(owner, mapId, pinId, "답변이에요").andExpect(status().isOk());
        awaitCount(askerPhone, 1);
        awaitPushIdle();
        assertThat(ownerPhone.received()).hasSize(1);   // 쓴 사람(주인)은 제외
        assertThat(lurkerPhone.received()).isEmpty();    // 핀에 참여하지 않은 viewer 에게는 가지 않는다
    }

    @Test
    @DisplayName("댓글: 알림 종류에서 댓글을 끈 사람에게는 가지 않는다")
    void commentRespectsPreferences() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "설정");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice ownerPhone = register(owner);
        setPreferences(owner, false, true, true);

        comment(viewer, mapId, pinId, "안녕").andExpect(status().isOk());
        awaitPushIdle();
        assertThat(ownerPhone.received()).isEmpty();

        setPreferences(owner, true, true, true);
        comment(viewer, mapId, pinId, "다시").andExpect(status().isOk());
        awaitCount(ownerPhone, 1);
    }

    @Test
    @DisplayName("댓글: 지도를 더 이상 볼 수 없게 된 사람에게는 핀 이름과 댓글이 가지 않는다(권한을 다시 확인한다)")
    void commentSkipsPeopleWhoLostAccess() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "공유 취소");
        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());
        String pinId = createPin(owner, mapId, pinBody("비밀 장소", 37.5, 127.0, "WISH", null, null));
        TestDevice friendPhone = register(friend);
        comment(friend, mapId, pinId, "질문").andExpect(status().isOk());
        awaitPushIdle();

        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());
        comment(owner, mapId, pinId, "답변").andExpect(status().isOk());
        awaitPushIdle();

        assertThat(friendPhone.received()).isEmpty();
    }

    @Test
    @DisplayName("댓글: 같은 사람의 여러 기기에 모두 가고, 긴 댓글과 줄바꿈은 알림 길이로 줄어든다")
    void commentGoesToEveryDeviceAndIsTrimmed() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "여러 기기");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice phone = register(owner);
        TestDevice tablet = register(owner);

        comment(viewer, mapId, pinId, "가".repeat(400)).andExpect(status().isOk());

        awaitCount(phone, 1);
        awaitCount(tablet, 1);
        String body = phone.lastField("body");
        assertThat(body.codePointCount(0, body.length())).isLessThanOrEqualTo(PushMessage.BODY_MAX);
        assertThat(body).endsWith("…");
    }

    // ------------------------------------------------------------------ 친구와 공유

    @Test
    @DisplayName("친구 요청을 받은 사람에게 가고, 수락하면 요청한 사람에게 간다. 종류를 끄면 가지 않는다")
    void friendRequestAndAccept() throws Exception {
        TestUser asker = newUser();
        TestUser target = newUser();
        TestDevice askerPhone = register(asker);
        TestDevice targetPhone = register(target);
        String targetUsername = jdbc.queryForObject("select username from party_users where id = ?::uuid", String.class, target.id().toString());

        send(asker, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + targetUsername + "\"}")).andExpect(status().isOk());

        awaitCount(targetPhone, 1);
        assertThat(targetPhone.lastField("title")).isEqualTo("친구 요청");
        assertThat(targetPhone.lastField("url")).isEqualTo("./friends");
        awaitPushIdle();
        assertThat(askerPhone.received()).isEmpty();

        String requestId = jdbc.queryForObject("select id::text from friendships where status = 'PENDING' and requester_id = ?::uuid", String.class, asker.id().toString());
        send(target, post("/api/v1/friends/requests/{id}/accept", requestId)).andExpect(status().isOk());

        awaitCount(askerPhone, 1);
        assertThat(askerPhone.lastField("title")).isEqualTo("친구가 됐어요");
        awaitPushIdle();
        assertThat(targetPhone.received()).hasSize(1);
    }

    @Test
    @DisplayName("친구 요청 알림을 끈 사람에게는 가지 않는다")
    void friendRequestRespectsPreferences() throws Exception {
        TestUser asker = newUser();
        TestUser target = newUser();
        TestDevice targetPhone = register(target);
        setPreferences(target, true, false, true);
        String targetUsername = jdbc.queryForObject("select username from party_users where id = ?::uuid", String.class, target.id().toString());

        send(asker, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + targetUsername + "\"}")).andExpect(status().isOk());

        awaitPushIdle();
        assertThat(targetPhone.received()).isEmpty();
    }

    @Test
    @DisplayName("지도를 공유받은 사람에게 가고(제목과 지도 이름 포함), 권한만 바꿀 때는 다시 가지 않는다")
    void shareNotifiesRecipientOnce() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "함께 볼 지도");
        TestDevice friendPhone = register(friend);
        TestDevice ownerPhone = register(owner);

        shareMap(owner, mapId, friend, "VIEWER").andExpect(status().isOk());

        awaitCount(friendPhone, 1);
        assertThat(friendPhone.lastField("title")).isEqualTo("지도가 공유됐어요");
        assertThat(friendPhone.lastField("body")).contains("함께 볼 지도");
        assertThat(friendPhone.lastField("url")).isEqualTo("./?map=" + mapId);

        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());
        awaitPushIdle();
        assertThat(friendPhone.received()).hasSize(1);
        assertThat(ownerPhone.received()).isEmpty();
    }

    // ------------------------------------------------------------------ 푸시 서비스의 응답

    @Test
    @DisplayName("푸시 서비스가 410(구독 만료)을 주면 그 구독을 지운다")
    void goneSubscriptionIsRemoved() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "만료");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice phone = register(owner);
        PushTestSupport.SERVICE.respondWith(phone.name, 410);

        comment(viewer, mapId, pinId, "안녕").andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(subscriptionCount(phone)).isEqualTo("0"));
    }

    @Test
    @DisplayName("거절(403)이 연속 5번 쌓이면 지우고, 그 전에는 남긴다. 일시적 오류(500)는 몇 번이어도 지우지 않는다")
    void rejectionsRemoveSubscriptionButTransientErrorsDoNot() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "거절");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice rejecting = register(owner);
        PushTestSupport.SERVICE.respondWith(rejecting.name, 403);

        for (int i = 1; i <= 4; i++) {
            comment(viewer, mapId, pinId, "댓글 " + i).andExpect(status().isOk());
            awaitCount(rejecting, i);
            awaitPushIdle();
            assertThat(subscriptionCount(rejecting)).isEqualTo("1");
        }
        comment(viewer, mapId, pinId, "댓글 5").andExpect(status().isOk());
        await().atMost(WAIT).untilAsserted(() -> assertThat(subscriptionCount(rejecting)).isEqualTo("0"));

        TestUser owner2 = newUser();
        String mapId2 = createMap(owner2, "일시 오류");
        grant(mapId2, PartyGuard.VIEWER, viewer);
        String pinId2 = createPin(owner2, mapId2, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice flaky = register(owner2);
        PushTestSupport.SERVICE.respondWith(flaky.name, 500);
        for (int i = 1; i <= 6; i++) {
            comment(viewer, mapId2, pinId2, "댓글 " + i).andExpect(status().isOk());
            awaitCount(flaky, i);
            awaitPushIdle();
        }
        assertThat(subscriptionCount(flaky)).isEqualTo("1");
    }

    @Test
    @DisplayName("전달에 성공하면 거절 횟수가 0 으로 돌아간다(가끔의 거절로 지워지지 않는다)")
    void successResetsRejectionCount() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "복구");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice phone = register(owner);

        PushTestSupport.SERVICE.respondWith(phone.name, 403);
        for (int i = 1; i <= 3; i++) {
            comment(viewer, mapId, pinId, "거절 " + i).andExpect(status().isOk());
            awaitCount(phone, i);
            awaitPushIdle();
        }
        PushTestSupport.SERVICE.respondWith(phone.name, 201);
        comment(viewer, mapId, pinId, "성공").andExpect(status().isOk());
        awaitCount(phone, 4);
        awaitPushIdle();

        String failures = jdbc.queryForObject("select failure_count::text from push_subscriptions where endpoint = ?", String.class, phone.endpoint);
        assertThat(failures).isEqualTo("0");
    }

    @Test
    @DisplayName("저장된 구독 주소도 보낼 때 다시 검증한다: 허용 목록 밖(IP 주소)으로는 접속하지 않고 거절로 센다")
    void storedEndpointsAreRevalidatedBeforeSending() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "재검증");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        // 옛 데이터나 허용 목록 변경으로 정책에 맞지 않게 저장된 구독(API 를 거치지 않고 직접 넣는다)
        TestDevice device = new TestDevice();
        String json = device.subscriptionJson();
        String sneaky = device.endpoint.replace("localhost", "127.0.0.1");
        jdbc.update("insert into push_subscriptions (id, user_id, endpoint, p256dh, auth, created_at, failure_count) values (gen_random_uuid(), ?::uuid, ?, ?, ?, now(), 0)",
                owner.id().toString(), sneaky, JsonPath.<String>read(json, "$.keys.p256dh"), JsonPath.<String>read(json, "$.keys.auth"));

        comment(viewer, mapId, pinId, "안녕").andExpect(status().isOk());
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select failure_count from push_subscriptions where endpoint = ?", Integer.class, sneaky)).isEqualTo(1));
        awaitPushIdle();

        assertThat(device.received()).isEmpty();   // 푸시 서비스에는 아무 요청도 가지 않았다
    }

    @Test
    @DisplayName("구독이 없거나 푸시를 못 보내도 댓글 저장은 성공한다(알림은 부가 기능)")
    void failuresNeverBreakTheRequest() throws Exception {
        TestUser owner = newUser();
        TestUser viewer = newUser();
        String mapId = createMap(owner, "부가");
        grant(mapId, PartyGuard.VIEWER, viewer);
        String pinId = createPin(owner, mapId, pinBody("핀", 37.5, 127.0, "WISH", null, null));
        TestDevice phone = register(owner);
        PushTestSupport.SERVICE.respondWith(phone.name, 500);

        String body = comment(viewer, mapId, pinId, "저장은 된다").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.data.body")).isEqualTo("저장은 된다");
        awaitPushIdle();
    }
}
