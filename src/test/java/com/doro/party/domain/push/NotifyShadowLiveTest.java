package com.doro.party.domain.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제로 떠 있는 알림 서비스(doro-notify)에 붙어 SHADOW 비교를 확인한다. {@code NOTIFY_LIVE_URL}·{@code NOTIFY_LIVE_TOKEN}·사용자 ID 세 개가 있을 때만 돈다
 * (알림 서비스 DB 에 사전에 구독·설정을 넣어 둔다: 사용자 A=구독 1개, B=구독 1개와 댓글 알림 끔, C=구독 없음).
 */
@EnabledIfEnvironmentVariable(named = "NOTIFY_LIVE_URL", matches = ".+")
class NotifyShadowLiveTest {

    private static final PushMessage MESSAGE = new PushMessage("핀 이름", "닉: 댓글", "./?map=m1&pin=p1&comment=c1", "comment:p1");

    private NotifyShadow shadow() {
        return new NotifyShadow(new NotifyProperties(NotifyProperties.Mode.SHADOW, System.getenv("NOTIFY_LIVE_URL"), System.getenv("NOTIFY_LIVE_TOKEN"), "/party",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 20));
    }

    private static UUID id(String name) {
        return UUID.fromString(System.getenv(name));
    }

    private static void awaitDone(NotifyShadow shadow, long expected) {
        await().atMost(Duration.ofSeconds(10)).until(() -> shadow.matchedCount() + shadow.mismatchedCount() + shadow.failedCount() == expected);
    }

    @Test
    void realServiceAgreesWithPartyDecisions() {
        NotifyShadow shadow = shadow();
        // 앱이 뜰 때처럼 알림 종류를 먼저 등록한다(같은 1스레드 풀이라 아래 비교보다 먼저 끝난다)
        shadow.registerTypes();
        // A: 파티도 기기 1개에 보냈다 → 알림 서비스도 1개에 보낼 것으로 판정해야 한다
        shadow.compare("comment", id("NOTIFY_LIVE_USER_A"), MESSAGE, true, new PushSender.Summary(1, 1));
        // B: 댓글 알림을 꺼 둔 사용자 → 파티는 보내지 않았고(기기 0) 알림 서비스도 '끔'이어야 한다
        shadow.compare("comment", id("NOTIFY_LIVE_USER_B"), MESSAGE, false, new PushSender.Summary(0, 0));
        // C: 기기가 없는 사용자 → 파티도 0, 알림 서비스도 '기기 없음'
        shadow.compare("comment", id("NOTIFY_LIVE_USER_C"), MESSAGE, true, new PushSender.Summary(0, 0));
        awaitDone(shadow, 3);
        assertThat(shadow.failedCount()).isZero();
        assertThat(shadow.mismatchedCount()).isZero();
        assertThat(shadow.matchedCount()).isEqualTo(3);

        // 시험이 틀릴 수 있는지 확인: 파티는 기기 0개라고 하는데 알림 서비스에는 구독이 있는 사용자 → 불일치로 잡혀야 한다
        shadow.compare("comment", id("NOTIFY_LIVE_USER_A"), MESSAGE, true, new PushSender.Summary(0, 0));
        awaitDone(shadow, 4);
        assertThat(shadow.mismatchedCount()).isEqualTo(1);
    }
}
