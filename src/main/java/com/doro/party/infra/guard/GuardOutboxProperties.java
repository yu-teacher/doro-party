package com.doro.party.infra.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Guard 삭제 아웃박스 재시도 설정(application.yaml 의 party.guard-outbox.*). */
@ConfigurationProperties(prefix = "party.guard-outbox")
public record GuardOutboxProperties(boolean enabled, Duration interval, int batchSize, Duration maxBackoff) {

    public GuardOutboxProperties {
        if (interval == null) interval = Duration.ofSeconds(30);
        if (batchSize <= 0) batchSize = 100;
        if (maxBackoff == null) maxBackoff = Duration.ofHours(1);
    }
}
