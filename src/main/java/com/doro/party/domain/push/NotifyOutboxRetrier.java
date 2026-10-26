package com.doro.party.domain.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 아웃박스에 쌓인 알림을 알림 서비스에 보낸다(CENTRAL). 행마다 짧은 트랜잭션으로 행을 잠그고 보내므로 인스턴스가 여럿이어도 안전하고,
 * 알림 서비스의 멱등 키 덕분에 같은 행을 두 번 보내도 알림은 한 번만 간다.
 *
 * <p>실패 처리: 연결 실패·시간 초과·429·5xx·인증 오류(401/403: 토큰 설정 문제)는 지수 백오프로 다시 시도하되 만료 시각까지만 한다. 그 밖의 4xx(요청 자체가 잘못됨)는
 * 다시 보내도 같은 결과라서 버린다. 만료되면 버린다(낡은 알림이 늦게 가지 않게).
 */
@Slf4j
@Component
public class NotifyOutboxRetrier {

    static final Duration BASE_BACKOFF = Duration.ofSeconds(10);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    static final int ALERT_ATTEMPTS = 10;
    static final int BATCH = 20;

    public record Result(int sent, int retry, int dropped) {
    }

    private final NotifyOutbox outbox;
    private final NotifyClient client;
    private final NotifyProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NotifyOutboxRetrier(NotifyOutbox outbox, NotifyClient client, NotifyProperties properties, Clock clock, PlatformTransactionManager txManager) {
        this.outbox = outbox;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${party.notify.outbox-interval:PT5S}", initialDelayString = "${party.notify.outbox-interval:PT5S}")
    public void run() {
        if (!properties.central()) {
            return;
        }
        try {
            processDue();
        } catch (RuntimeException e) {
            log.warn("Notify outbox run failed: {}", e.getClass().getSimpleName());
        }
    }

    /** 시도할 때가 된 행을 처리한다. CENTRAL 이 아니면 아무것도 하지 않는다. */
    public Result processDue() {
        if (!properties.central()) {
            return new Result(0, 0, 0);
        }
        Instant now = clock.instant();
        List<UUID> ids = outbox.findDueIds(now, BATCH);
        int sent = 0;
        int retry = 0;
        int dropped = 0;
        for (UUID id : ids) {
            String outcome = tx.execute(status -> attempt(id, now));
            if (outcome == null) {
                continue;
            }
            switch (outcome) {
                case "SENT" -> sent++;
                case "RETRY" -> retry++;
                case "DROPPED" -> dropped++;
                default -> {
                }
            }
        }
        if (retry > 0 || dropped > 0) {
            log.warn("Notify outbox: sent={}, will retry={}, dropped={}", sent, retry, dropped);
        }
        return new Result(sent, retry, dropped);
    }

    private String attempt(UUID id, Instant now) {
        NotifyOutbox.Row row = outbox.lock(id).orElse(null);
        if (row == null) {
            return "SKIPPED";
        }
        if (!row.expiresAt().isAfter(now)) {
            outbox.delete(id);
            log.warn("Notify outbox: expired before it could be sent, type={}, attempts={}", row.type(), row.attempts());
            return "DROPPED";
        }
        String error;
        try {
            NotifyClient.Reply reply = client.send(row.id(), row.recipientId(), row.type(), row.title(), row.body(), row.tag(), row.url(), row.expiresAt());
            if (reply.ok()) {
                outbox.delete(id);
                return "SENT";
            }
            int status = reply.status();
            boolean transientFailure = status == 401 || status == 403 || status == 408 || status == 429 || status >= 500;
            if (!transientFailure) {
                // 요청이 잘못됐다(예: 등록되지 않은 종류·주소 범위): 다시 보내도 같은 결과다
                outbox.delete(id);
                log.error("Notify outbox: request rejected, dropping. type={}, status={}, code={}", row.type(), status,
                        reply.body() == null ? "" : reply.body().path("code").asText(""));
                return "DROPPED";
            }
            error = "status " + status;
        } catch (NotifyClient.UnreachableException e) {
            error = e.getMessage();
        }
        int attempts = row.attempts() + 1;
        outbox.reschedule(id, attempts, now.plus(backoff(attempts)), error);
        if (attempts == ALERT_ATTEMPTS) {
            log.error("Notify outbox: {} attempts failed, type={}, last={}", attempts, row.type(), error);
        }
        return "RETRY";
    }

    static Duration backoff(int attempts) {
        long seconds = BASE_BACKOFF.getSeconds() << Math.min(Math.max(attempts - 1, 0), 10);
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.getSeconds()));
    }
}
