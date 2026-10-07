package com.doro.party.domain.friend;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

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

/** 친구: 사용자명 요청·승인, 초대 링크, 해제. 실제 로그인 세션과 DB 로 검증한다. */
class FriendHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private String usernameOf(TestUser user) throws Exception {
        String body = send(user, get("/api/v1/me")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.username");
    }

    private ResultActions request(TestUser from, String username) throws Exception {
        return send(from, post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\"}"));
    }

    private ResultActions overview(TestUser user) throws Exception {
        return send(user, get("/api/v1/friends")).andExpect(status().isOk());
    }

    private String incomingRequestId(TestUser user) throws Exception {
        String body = overview(user).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.incoming[0].id");
    }

    private void makeFriends(TestUser a, TestUser b) throws Exception {
        request(a, usernameOf(b)).andExpect(status().isOk());
        send(b, post("/api/v1/friends/requests/{id}/accept", incomingRequestId(b))).andExpect(status().isOk());
    }

    private long friendshipRows(TestUser a, TestUser b) {
        return jdbc.queryForObject("select count(*) from friendships where (user_low_id = ?::uuid and user_high_id = ?::uuid) or (user_low_id = ?::uuid and user_high_id = ?::uuid)",
                Long.class, a.id().toString(), b.id().toString(), b.id().toString(), a.id().toString());
    }

    // ------------------------------------------------------------------ 사용자명 요청

    @Test
    @DisplayName("사용자명으로 요청하면 상대의 받은 요청에 보이고, 수락하면 서로의 친구 목록에 나타난다")
    void requestAndAccept() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();

        request(alice, usernameOf(bob)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.user.username").value(usernameOf(bob)));
        overview(alice).andExpect(jsonPath("$.data.outgoing.length()").value(1)).andExpect(jsonPath("$.data.friends.length()").value(0));
        overview(bob).andExpect(jsonPath("$.data.incoming.length()").value(1))
                .andExpect(jsonPath("$.data.incoming[0].user.username").value(usernameOf(alice)));

        send(bob, post("/api/v1/friends/requests/{id}/accept", incomingRequestId(bob))).andExpect(status().isOk());

        overview(alice).andExpect(jsonPath("$.data.friends[0].user.username").value(usernameOf(bob)))
                .andExpect(jsonPath("$.data.outgoing.length()").value(0));
        overview(bob).andExpect(jsonPath("$.data.friends[0].user.username").value(usernameOf(alice)))
                .andExpect(jsonPath("$.data.incoming.length()").value(0));
    }

    @Test
    @DisplayName("사용자명은 대소문자·공백·@ 를 정리해 찾고, 없는 이름과 자기 자신은 거절한다")
    void usernameLookup() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();

        request(alice, "  @" + usernameOf(bob).toUpperCase() + " ").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        request(alice, "no_such_user_" + UUID.randomUUID().toString().substring(0, 6)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER-404-01"));
        request(alice, usernameOf(alice)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRIEND-400-01"));
        request(alice, "").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 요청을 다시 보내거나 이미 친구여도 오류 없이 한 쌍만 남는다")
    void requestsAreIdempotent() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();

        request(alice, usernameOf(bob)).andExpect(jsonPath("$.data.status").value("PENDING"));
        request(alice, usernameOf(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(friendshipRows(alice, bob)).isEqualTo(1);

        send(bob, post("/api/v1/friends/requests/{id}/accept", incomingRequestId(bob))).andExpect(status().isOk());
        request(alice, usernameOf(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        request(bob, usernameOf(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        assertThat(friendshipRows(alice, bob)).isEqualTo(1);
    }

    @Test
    @DisplayName("상대가 이미 나에게 요청했다면, 내가 요청하는 순간 서로 원한 것이므로 바로 친구가 된다")
    void mutualRequestsConnectImmediately() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();

        request(alice, usernameOf(bob)).andExpect(jsonPath("$.data.status").value("PENDING"));
        request(bob, usernameOf(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        overview(alice).andExpect(jsonPath("$.data.friends.length()").value(1)).andExpect(jsonPath("$.data.outgoing.length()").value(0));
        assertThat(friendshipRows(alice, bob)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 사람이 동시에 서로에게 요청해도 오류 없이 친구 관계가 하나만 생긴다")
    void simultaneousMutualRequests() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String aliceName = usernameOf(alice);
        String bobName = usernameOf(bob);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<Integer> fromAlice = () -> request(alice, bobName).andReturn().getResponse().getStatus();
                Callable<Integer> fromBob = () -> request(bob, aliceName).andReturn().getResponse().getStatus();
                futures.add(pool.submit(fromAlice));
                futures.add(pool.submit(fromBob));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(friendshipRows(alice, bob)).isEqualTo(1);
        overview(alice).andExpect(jsonPath("$.data.friends.length()").value(1));
    }

    @Test
    @DisplayName("받는 사람만 수락·거절할 수 있고, 보낸 사람은 취소할 수 있다. 남에게는 없는 요청처럼 보인다")
    void onlyTheAddresseeCanAccept() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser mallory = newUser();
        request(alice, usernameOf(bob)).andExpect(status().isOk());
        String requestId = incomingRequestId(bob);

        send(alice, post("/api/v1/friends/requests/{id}/accept", requestId)).andExpect(status().isNotFound());
        send(mallory, post("/api/v1/friends/requests/{id}/accept", requestId)).andExpect(status().isNotFound());
        send(mallory, delete("/api/v1/friends/requests/{id}", requestId)).andExpect(status().isNotFound());
        send(bob, post("/api/v1/friends/requests/{id}/accept", UUID.randomUUID())).andExpect(status().isNotFound());
        overview(bob).andExpect(jsonPath("$.data.incoming.length()").value(1));

        // 보낸 사람은 취소, 받은 사람은 거절
        send(alice, delete("/api/v1/friends/requests/{id}", requestId)).andExpect(status().isOk());
        overview(bob).andExpect(jsonPath("$.data.incoming.length()").value(0));
        request(alice, usernameOf(bob)).andExpect(status().isOk());
        send(bob, delete("/api/v1/friends/requests/{id}", incomingRequestId(bob))).andExpect(status().isOk());
        overview(alice).andExpect(jsonPath("$.data.outgoing.length()").value(0));
    }

    @Test
    @DisplayName("친구를 끊으면 양쪽에서 사라지고, 친구가 아닌 사람은 끊을 수 없으며, 다시 요청할 수 있다")
    void unfriend() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser stranger = newUser();
        makeFriends(alice, bob);

        send(stranger, delete("/api/v1/friends/{id}", bob.id())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIEND-404-02"));
        send(alice, delete("/api/v1/friends/{id}", bob.id())).andExpect(status().isOk());

        overview(alice).andExpect(jsonPath("$.data.friends.length()").value(0));
        overview(bob).andExpect(jsonPath("$.data.friends.length()").value(0));
        send(alice, delete("/api/v1/friends/{id}", bob.id())).andExpect(status().isNotFound());
        request(alice, usernameOf(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("친구 목록과 요청에는 이메일 같은 계정 정보가 없다")
    void noAccountInformationLeaks() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        makeFriends(alice, bob);

        String body = overview(alice).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(alice.email()).doesNotContain(bob.email()).doesNotContain("@doro.local");
    }

    // ------------------------------------------------------------------ 초대 링크

    private String createInvite(TestUser owner) throws Exception {
        String body = send(owner, post("/api/v1/friends/invite")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.code");
    }

    @Test
    @DisplayName("초대 링크를 만들면 다시 열어도 같은 링크이고, 코드는 DB 에 해시와 암호문으로만 있다")
    void inviteLinkLifecycle() throws Exception {
        TestUser owner = newUser();
        send(owner, get("/api/v1/friends/invite")).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

        String code = createInvite(owner);

        send(owner, get("/api/v1/friends/invite")).andExpect(jsonPath("$.data.code").value(code)).andExpect(jsonPath("$.data.expiresAt").exists());
        assertThat(code).hasSizeGreaterThanOrEqualTo(30).matches("[A-Za-z0-9_-]+");
        String stored = jdbc.queryForObject("select code_hash || ' ' || code_enc from friend_invites where owner_id = ?::uuid", String.class, owner.id().toString());
        assertThat(stored).doesNotContain(code);

        send(owner, delete("/api/v1/friends/invite")).andExpect(status().isOk());
        send(owner, get("/api/v1/friends/invite")).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("링크를 열어 수락하면 바로 친구가 되고, 이미 친구여도 오류가 아니다")
    void acceptingAnInviteMakesFriendsImmediately() throws Exception {
        TestUser owner = newUser();
        TestUser guest = newUser();
        String code = createInvite(owner);

        send(guest, get("/api/v1/friends/invite/{c}", code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviter.username").value(usernameOf(owner)))
                .andExpect(jsonPath("$.data.self").value(false)).andExpect(jsonPath("$.data.alreadyFriends").value(false));
        send(guest, post("/api/v1/friends/invite/{c}/accept", code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friend.username").value(usernameOf(owner)));

        overview(owner).andExpect(jsonPath("$.data.friends[0].user.username").value(usernameOf(guest)));
        overview(guest).andExpect(jsonPath("$.data.friends[0].user.username").value(usernameOf(owner)));
        send(guest, post("/api/v1/friends/invite/{c}/accept", code)).andExpect(status().isOk());
        send(guest, get("/api/v1/friends/invite/{c}", code)).andExpect(jsonPath("$.data.alreadyFriends").value(true));
        assertThat(friendshipRows(owner, guest)).isEqualTo(1);
    }

    @Test
    @DisplayName("링크로 수락하면 대기 중이던 요청도 친구로 바뀐다")
    void inviteResolvesAPendingRequest() throws Exception {
        TestUser owner = newUser();
        TestUser guest = newUser();
        request(guest, usernameOf(owner)).andExpect(status().isOk());

        send(guest, post("/api/v1/friends/invite/{c}/accept", createInvite(owner))).andExpect(status().isOk());

        overview(guest).andExpect(jsonPath("$.data.friends.length()").value(1)).andExpect(jsonPath("$.data.outgoing.length()").value(0));
        assertThat(friendshipRows(owner, guest)).isEqualTo(1);
    }

    @Test
    @DisplayName("자기 링크는 수락할 수 없고, 다시 만들면 이전 링크는 즉시 무효, 만료된 링크와 엉뚱한 코드는 모두 같은 404")
    void invalidInvites() throws Exception {
        TestUser owner = newUser();
        TestUser guest = newUser();
        String first = createInvite(owner);

        send(owner, post("/api/v1/friends/invite/{c}/accept", first)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRIEND-400-01"));
        send(owner, get("/api/v1/friends/invite/{c}", first)).andExpect(jsonPath("$.data.self").value(true));

        String second = createInvite(owner);
        assertThat(second).isNotEqualTo(first);
        send(guest, post("/api/v1/friends/invite/{c}/accept", first)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITE-404-01"));
        send(guest, get("/api/v1/friends/invite/{c}", first)).andExpect(status().isNotFound());

        jdbc.update("update friend_invites set expires_at = now() - interval '1 minute' where owner_id = ?::uuid", owner.id().toString());
        send(guest, post("/api/v1/friends/invite/{c}/accept", second)).andExpect(status().isNotFound());
        send(owner, get("/api/v1/friends/invite")).andExpect(jsonPath("$.data").doesNotExist());
        send(guest, post("/api/v1/friends/invite/{c}/accept", "totally-made-up-code")).andExpect(status().isNotFound());
        send(guest, post("/api/v1/friends/invite/{c}/accept", "x".repeat(200))).andExpect(status().isNotFound());
        assertThat(friendshipRows(owner, guest)).isZero();
    }

    @Test
    @DisplayName("로그인하지 않으면 친구 API 는 모두 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/friends")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/friends/invite")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/friends/invite/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/friends/invite/abc/accept")).andExpect(status().isUnauthorized());
    }
}
