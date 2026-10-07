package com.doro.party.domain.user;

import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 프로필: 가입 때는 이메일과 무관한 임시 사용자명, 닉네임·사용자명은 본인이 바꾼다. */
class ProfileHttpTest extends PartyHttpTestBase {

    private ResultActions update(TestUser user, String nickname, String username) throws Exception {
        return send(user, patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\",\"username\":\"" + username + "\"}"));
    }

    @Test
    @DisplayName("새 사용자의 사용자명은 이메일에서 만들지 않은 무작위 값이다")
    void initialUsernameDoesNotDeriveFromEmail() throws Exception {
        TestUser user = newUser();

        String body = send(user, get("/api/v1/me")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String username = JsonPath.read(body, "$.data.username");

        assertThat(username).matches("user[a-z0-9]{8}");
        assertThat(user.email()).doesNotContain(username);
        assertThat(body).doesNotContain(user.email());
    }

    @Test
    @DisplayName("닉네임과 사용자명을 바꾸면 내 프로필과 로그인 상태에 반영되고, 사용자명은 정리되어 저장된다")
    void updateProfile() throws Exception {
        TestUser user = newUser();
        String name = "p" + System.nanoTime() % 100000000L;

        update(user, "  파티왕  ", "  @" + name.toUpperCase() + " ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("파티왕")).andExpect(jsonPath("$.data.username").value(name));

        send(user, get("/api/v1/me")).andExpect(jsonPath("$.data.nickname").value("파티왕")).andExpect(jsonPath("$.data.username").value(name));
        mockMvc.perform(get("/api/v1/bff/session").cookie(session(user.cookie())))
                .andExpect(jsonPath("$.data.user.nickname").value("파티왕")).andExpect(jsonPath("$.data.user.username").value(name));
        // 자기 이름을 그대로 다시 저장해도 오류가 아니다
        update(user, "파티왕2", name).andExpect(status().isOk());
        // PUT 도 같다
        send(user, put("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"파티왕3\",\"username\":\"" + name + "\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 쓰는 사용자명은 409, 형식이 틀리거나 예약된 이름과 닉네임 오류는 400")
    void validation() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String taken = "t" + System.nanoTime() % 100000000L;
        update(alice, "앨리스", taken).andExpect(status().isOk());

        update(bob, "밥", taken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER-409-01"));
        update(bob, "밥", taken.toUpperCase()).andExpect(status().isConflict());
        for (String bad : new String[]{"ab", "a".repeat(31), "has space", "한글이름", "dash-name", "admin", "friends", "api"}) {
            update(bob, "밥", bad).andExpect(status().isBadRequest());
        }
        update(bob, "   ", "valid_name_1").andExpect(status().isBadRequest());
        update(bob, "가".repeat(21), "valid_name_1").andExpect(status().isBadRequest());
        update(bob, "밥\\u0007", "valid_name_1").andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"익명\",\"username\":\"anonymous_try\"}")).andExpect(status().isUnauthorized());
    }
}
