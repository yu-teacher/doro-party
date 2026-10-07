package com.doro.party.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 로그인 후 돌아갈 경로는 사이트 안의 경로만 허용한다 (오픈 리다이렉트 방지). */
class ReturnPathTest {

    @ParameterizedTest(name = "허용: {0}")
    @ValueSource(strings = {"/", "/maps/42", "/friends?tab=requests", "/overlay?maps=a%20b", "/groups/new"})
    @DisplayName("사이트 안의 경로는 그대로 쓴다")
    void sitePathsAreKept(String path) {
        assertThat(BffAuthService.safeReturnPath(path)).isEqualTo(path);
    }

    @ParameterizedTest(name = "차단: {0}")
    @ValueSource(strings = {"//evil.example", "https://evil.example", "http://evil.example/x", "javascript:alert(1)", "/\\evil.example",
            "evil.example", "/ok\nSet-Cookie: a=b", "/ok\r\nX: y", "/redirect?to=https://evil.example"})
    @DisplayName("외부 주소, 프로토콜 상대 주소, 개행·역슬래시가 섞인 값은 /로 바꾼다")
    void externalTargetsFallBackToRoot(String path) {
        assertThat(BffAuthService.safeReturnPath(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("비어 있으면 /")
    void blankFallsBackToRoot(String path) {
        assertThat(BffAuthService.safeReturnPath(path)).isEqualTo("/");
    }

    @Test
    @DisplayName("너무 긴 경로는 /")
    void tooLongFallsBackToRoot() {
        assertThat(BffAuthService.safeReturnPath("/" + "a".repeat(600))).isEqualTo("/");
    }
}
