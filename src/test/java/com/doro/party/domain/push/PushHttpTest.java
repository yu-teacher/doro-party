package com.doro.party.domain.push;

import com.doro.party.domain.push.PushTestSupport.TestDevice;
import com.doro.party.support.PartyHttpTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 푸시 구독 등록·해제, 알림 종류 설정, 시험 알림. */
class PushHttpTest extends PartyHttpTestBase {

    @Autowired private JdbcTemplate jdbc;

    private ResultActions subscribe(TestUser user, String json) throws Exception {
        return send(user, put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions unsubscribe(TestUser user, String endpoint) throws Exception {
        return send(user, delete("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"));
    }

    private long rows(String endpoint) {
        return jdbc.queryForObject("select count(*) from push_subscriptions where endpoint = ?", Long.class, endpoint);
    }

    private String owner(String endpoint) {
        return jdbc.queryForObject("select user_id::text from push_subscriptions where endpoint = ?", String.class, endpoint);
    }

    // ------------------------------------------------------------------ 설정

    @Test
    @DisplayName("푸시 설정: 켜져 있고 공개 키를 돌려준다. 로그인하지 않으면 401")
    void config() throws Exception {
        TestUser user = newUser();
        send(user, get("/api/v1/push/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.publicKey").value(PushTestSupport.KEYS.publicKey));
        mockMvc.perform(get("/api/v1/push/config")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(new TestDevice().subscriptionJson())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/push/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/push/test")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 구독

    @Test
    @DisplayName("구독을 등록하면 저장되고, 같은 기기를 다시 등록해도 하나뿐이다")
    void subscribeIsIdempotent() throws Exception {
        TestUser user = newUser();
        TestDevice device = new TestDevice();

        subscribe(user, device.subscriptionJson()).andExpect(status().isOk());
        subscribe(user, device.subscriptionJson()).andExpect(status().isOk());

        assertThat(rows(device.endpoint)).isEqualTo(1);
        assertThat(owner(device.endpoint)).isEqualTo(user.id().toString());
    }

    @Test
    @DisplayName("허용되지 않은 구독 주소는 400: 허용 목록 밖의 도메인, IP 주소(내부망·메타데이터), 계정 정보가 든 주소")
    void rejectsDangerousEndpoints() throws Exception {
        TestUser user = newUser();
        TestDevice device = new TestDevice();
        String keys = device.subscriptionJson().substring(device.subscriptionJson().indexOf("\"keys\""));
        for (String endpoint : List.of(
                "https://evil.example/push/1",
                "http://169.254.169.254/latest/meta-data/",
                "http://127.0.0.1:8081/internal",
                "http://[::1]:9090/x",
                "http://localhost@evil.example/push/1",
                "http://user:pw@localhost/push/1",
                "ftp://localhost/push/1",
                "http://auth-api:8080/internal/v1/deleted-users")) {
            subscribe(user, "{\"endpoint\":\"" + endpoint + "\"," + keys).andExpect(status().isBadRequest());
            assertThat(rows(endpoint)).isZero();
        }
    }

    @Test
    @DisplayName("키가 잘못되면 400: 곡선 위의 점이 아닌 공개 키, 길이가 틀린 auth, 값 누락")
    void rejectsBadKeys() throws Exception {
        TestUser user = newUser();
        TestDevice device = new TestDevice();
        String good = device.subscriptionJson();
        String p256dh = good.replaceAll(".*\"p256dh\":\"([^\"]+)\".*", "$1");
        String auth = good.replaceAll(".*\"auth\":\"([^\"]+)\".*", "$1");
        String offCurve = p256dh.substring(0, p256dh.length() - 2) + (p256dh.endsWith("AA") ? "BB" : "AA");

        subscribe(user, "{\"endpoint\":\"" + device.endpoint + "\",\"keys\":{\"p256dh\":\"" + offCurve + "\",\"auth\":\"" + auth + "\"}}").andExpect(status().isBadRequest());
        subscribe(user, "{\"endpoint\":\"" + device.endpoint + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"AAAA\"}}").andExpect(status().isBadRequest());
        subscribe(user, "{\"endpoint\":\"" + device.endpoint + "\",\"keys\":{\"p256dh\":\"!!!\",\"auth\":\"" + auth + "\"}}").andExpect(status().isBadRequest());
        subscribe(user, "{\"endpoint\":\"" + device.endpoint + "\"}").andExpect(status().isBadRequest());
        subscribe(user, "{\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth + "\"}}").andExpect(status().isBadRequest());
        assertThat(rows(device.endpoint)).isZero();
    }

    @Test
    @DisplayName("같은 기기를 다른 계정으로 로그인해 등록하면 그 계정으로 옮겨진다(이전 계정에는 더는 알림이 가지 않는다)")
    void deviceMovesToNewAccount() throws Exception {
        TestUser first = newUser();
        TestUser second = newUser();
        TestDevice device = new TestDevice();
        subscribe(first, device.subscriptionJson()).andExpect(status().isOk());

        subscribe(second, device.subscriptionJson()).andExpect(status().isOk());

        assertThat(rows(device.endpoint)).isEqualTo(1);
        assertThat(owner(device.endpoint)).isEqualTo(second.id().toString());
    }

    @Test
    @DisplayName("해제: 내 구독만 지우고, 이미 없어도 성공이며, 남의 구독은 지워지지 않는다")
    void unsubscribeOnlyOwn() throws Exception {
        TestUser owner = newUser();
        TestUser other = newUser();
        TestDevice device = new TestDevice();
        subscribe(owner, device.subscriptionJson()).andExpect(status().isOk());

        unsubscribe(other, device.endpoint).andExpect(status().isOk());
        assertThat(rows(device.endpoint)).isEqualTo(1);

        unsubscribe(owner, device.endpoint).andExpect(status().isOk());
        assertThat(rows(device.endpoint)).isZero();
        unsubscribe(owner, device.endpoint).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기기는 사용자마다 최대 10대: 넘으면 가장 오래된 것부터 지우고 방금 등록한 기기는 남는다")
    void limitsDevicesPerUser() throws Exception {
        TestUser user = newUser();
        TestDevice[] devices = new TestDevice[12];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = new TestDevice();
            subscribe(user, devices[i].subscriptionJson()).andExpect(status().isOk());
            Thread.sleep(5);  // 등록 시각 순서를 구분한다
        }

        long count = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?::uuid", Long.class, user.id().toString());
        assertThat(count).isEqualTo(10);
        assertThat(rows(devices[0].endpoint)).isZero();
        assertThat(rows(devices[1].endpoint)).isZero();
        assertThat(rows(devices[11].endpoint)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 알림 종류

    @Test
    @DisplayName("알림 종류는 처음에 모두 켜져 있고, 바꾸면 저장되며, 사람마다 따로다")
    void preferences() throws Exception {
        TestUser user = newUser();
        TestUser other = newUser();
        send(user, get("/api/v1/push/preferences")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments").value(true)).andExpect(jsonPath("$.data.friendRequests").value(true)).andExpect(jsonPath("$.data.shares").value(true));

        send(user, put("/api/v1/push/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"comments\":false,\"friendRequests\":true,\"shares\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.comments").value(false));

        send(user, get("/api/v1/push/preferences")).andExpect(jsonPath("$.data.comments").value(false)).andExpect(jsonPath("$.data.shares").value(false)).andExpect(jsonPath("$.data.friendRequests").value(true));
        send(other, get("/api/v1/push/preferences")).andExpect(jsonPath("$.data.comments").value(true));
    }

    // ------------------------------------------------------------------ 시험 알림

    @Test
    @DisplayName("시험 알림: 내 기기마다 암호화된 알림이 가고, 받는 쪽이 복호화하면 내용이 보인다. 기기가 없으면 0")
    void testNotification() throws Exception {
        TestUser user = newUser();
        send(user, post("/api/v1/push/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data.devices").value(0)).andExpect(jsonPath("$.data.delivered").value(0));

        TestDevice phone = new TestDevice();
        TestDevice laptop = new TestDevice();
        subscribe(user, phone.subscriptionJson()).andExpect(status().isOk());
        subscribe(user, laptop.subscriptionJson()).andExpect(status().isOk());

        send(user, post("/api/v1/push/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data.devices").value(2)).andExpect(jsonPath("$.data.delivered").value(2));

        assertThat(phone.received()).hasSize(1);
        assertThat(phone.lastField("title")).isEqualTo("알림이 켜졌어요");
        assertThat(laptop.received()).hasSize(1);
        var request = phone.received().get(0);
        assertThat(request.headers().get("authorization")).startsWith("vapid t=").endsWith(", k=" + PushTestSupport.KEYS.publicKey);
        assertThat(request.headers().get("content-encoding")).isEqualTo("aes128gcm");
        assertThat(request.headers().get("ttl")).isEqualTo("86400");
        // 본문에 평문이 그대로 실려 가지 않는다
        assertThat(new String(request.body(), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("알림이 켜졌어요");
    }
}
