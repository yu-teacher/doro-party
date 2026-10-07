package com.doro.party.domain.auth;

import com.doro.party.domain.auth.repository.AuthSessionRepository;
import com.doro.party.domain.auth.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 만료된 BFF 세션과 끝나지 않은 로그인 시도를 주기적으로 지운다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BffCleanup {

    private final AuthSessionRepository sessions;
    private final LoginAttemptRepository attempts;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();
        int sessionCount = sessions.deleteExpired(now);
        int attemptCount = attempts.deleteExpired(now);
        if (sessionCount > 0 || attemptCount > 0) {
            log.info("Purged expired BFF rows: sessions={}, loginAttempts={}", sessionCount, attemptCount);
        }
    }
}
