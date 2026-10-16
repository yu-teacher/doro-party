package com.doro.party.infra.guard;

import com.doro.party.support.PartyHttpTestBase;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import com.hunnit_beasts.doro.sdk.exception.DoroGuardWriteFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guard 튜플 삭제 아웃박스: 커밋은 됐는데 Guard 삭제가 실패해도 권한 회수가 사라지지 않고, Guard 가 돌아오면 재시도기가 지운다.
 * 실제 Guard 로 튜플을 쓰고 확인하며, 장애는 {@link DoroGuardClient} 스파이로 흉내 낸다.
 */
class GuardDeleteOutboxTest extends PartyHttpTestBase {

    @Autowired private GuardDeleteOutboxRetrier retrier;
    @Autowired private GuardOutboxProperties properties;
    @Autowired private GuardTupleDeleteOutboxRepository outboxRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private DoroGuardClient guard;

    private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        reset(guard);
        jdbc.update("delete from guard_tuple_delete_outbox");
        tx = new TransactionTemplate(txManager);
    }

    @AfterEach
    void heal() {
        reset(guard);
    }

    private void guardDown() {
        doThrow(new DoroGuardWriteFailedException("Guard down", null))
                .when(guard).deleteTupleOrThrow(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private void guardUp() {
        reset(guard);
    }

    private List<Map<String, Object>> rows(String objectId) {
        return jdbc.queryForList("select * from guard_tuple_delete_outbox where object_id = ?", objectId);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private boolean isViewer(String map, String user) {
        return guard.check(PartyGuard.MAP, map, PartyGuard.VIEWER, user);
    }

    /** 먼 미래를 기준 시각으로 줘서 대기 시간과 상관없이 모든 행을 시도하게 한다. */
    private GuardDeleteOutboxRetrier.Result retryAll() {
        return retrier.processDue(Instant.now().plus(Duration.ofDays(1)));
    }

    @Test
    @DisplayName("커밋 뒤 Guard 삭제가 실패해도 요청은 성공하고, 아웃박스 행이 남으며, Guard 가 돌아오면 재시도기가 지우고 행을 치운다")
    void failedDeleteIsQueuedAndRetried() {
        String map = id(), user = id();
        guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        assertThat(isViewer(map, user)).isTrue();

        guardDown();
        tx.executeWithoutResult(s -> guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user));

        assertThat(isViewer(map, user)).as("Guard 가 죽어 있어 아직 남아 있다").isTrue();
        List<Map<String, Object>> queued = rows(map);
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0)).containsEntry("relation", PartyGuard.VIEWER).containsEntry("subject_id", user);
        assertThat(queued.get(0).get("attempts")).isEqualTo(1);
        assertThat((String) queued.get(0).get("last_error")).contains("Guard down");

        guardUp();
        assertThat(retryAll().succeeded()).isGreaterThanOrEqualTo(1);

        assertThat(isViewer(map, user)).as("재시도로 권한이 사라진다").isFalse();
        assertThat(rows(map)).isEmpty();
    }

    @Test
    @DisplayName("삭제가 바로 성공하면 아웃박스 행이 남지 않는다")
    void successfulDeleteLeavesNoRow() {
        String map = id(), user = id();
        guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);

        tx.executeWithoutResult(s -> guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user));

        assertThat(isViewer(map, user)).isFalse();
        assertThat(rows(map)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 아웃박스 행도 없고 Guard 삭제 시도도 없다")
    void rolledBackTransactionLeavesNothing() {
        String map = id(), user = id();
        guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);

        tx.executeWithoutResult(s -> {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
            s.setRollbackOnly();
        });

        assertThat(rows(map)).isEmpty();
        assertThat(isViewer(map, user)).as("롤백됐으니 권한은 그대로").isTrue();
        verify(guard, never()).deleteTupleOrThrow(anyString(), org.mockito.ArgumentMatchers.eq(map), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("재시도가 실패할 때마다 시도 횟수가 늘고 대기 시간이 두 배로 늘다가 max-backoff 에서 멈춘다")
    void backoffGrowsAndIsCapped() {
        String map = id(), user = id();
        guardDown();
        guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user); // 트랜잭션 없음 → 실패 시 기록(1회)
        assertThat(rows(map)).hasSize(1);

        Duration max = properties.maxBackoff();
        // PostgreSQL 의 timestamptz 는 마이크로초까지만 저장한다. Linux 의 Instant.now() 는 나노초라 그대로 쓰면 저장 뒤 값이 미세하게 달라져 비교가 어긋난다.
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        for (int attempts = 2; attempts <= 13; attempts++) {
            now = now.plus(Duration.ofDays(1));
            retrier.processDue(now);
            GuardTupleDeleteOutbox row = outboxRepository.findAll().stream().filter(r -> r.getObjectId().equals(map)).findFirst().orElseThrow();
            Duration expected = Duration.ofSeconds(10).multipliedBy(1L << (attempts - 1));
            if (expected.compareTo(max) > 0) {
                expected = max;
            }
            assertThat(row.getAttempts()).isEqualTo(attempts);
            assertThat(Duration.between(now, row.getNextAttemptAt())).isEqualTo(expected);
        }
        assertThat(Duration.ofSeconds(10).multipliedBy(1L << 12)).isGreaterThan(max);
    }

    @Test
    @DisplayName("대기 시간이 아직 안 지난 행은 건드리지 않는다")
    void notDueRowIsSkipped() {
        String map = id(), user = id();
        guardDown();
        guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        guardUp();

        retrier.processDue(Instant.now());

        assertThat(rows(map)).as("방금 실패해 10초 대기 중").hasSize(1);
    }

    @Test
    @DisplayName("쓴 뒤 롤백됐는데 고아 튜플 정리까지 실패하면 아웃박스에 남기고, 재시도기가 지운다")
    void orphanCleanupFailureIsQueued() {
        String map = id(), user = id();
        guardDown();

        tx.executeWithoutResult(s -> {
            guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
            s.setRollbackOnly();
        });

        assertThat(isViewer(map, user)).as("정리 실패로 고아 튜플이 남았다").isTrue();
        assertThat(rows(map)).hasSize(1);

        guardUp();
        retryAll();

        assertThat(isViewer(map, user)).isFalse();
        assertThat(rows(map)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 없이 지우다 실패하면 아웃박스에 남기고, 재시도기가 지운다")
    void noTransactionPathQueuesOnFailure() {
        String map = id(), user = id();
        guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        guardDown();

        guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);

        assertThat(rows(map)).hasSize(1);
        guardUp();
        retryAll();
        assertThat(isViewer(map, user)).isFalse();
        assertThat(rows(map)).isEmpty();
    }

    @Test
    @DisplayName("이미 없는 튜플을 지우는 행은 오류 없이 처리되어 사라진다(멱등)")
    void deletingAnAbsentTupleIsFine() {
        String map = id(), user = id();
        guardDown();
        guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        guardUp();

        retryAll();

        assertThat(rows(map)).isEmpty();
    }

    @Test
    @DisplayName("공유 끊기: Guard 가 죽어 있어도 요청은 성공하고, 복구 뒤 재시도로 접근이 막힌다")
    void revokeShareWhileGuardIsDown() throws Exception {
        TestUser owner = newUser();
        TestUser friend = newUser();
        befriend(owner, friend);
        String mapId = createMap(owner, "공유 지도");
        shareMap(owner, mapId, friend, "EDITOR").andExpect(status().isOk());
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk());

        guardDown();
        send(owner, delete("/api/v1/maps/{m}/shares/{u}", mapId, friend.id())).andExpect(status().isOk());

        assertThat(rows(mapId)).as("회수할 튜플이 아웃박스에 남는다").hasSize(1);
        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isOk()); // Guard 장애 중엔 옛 권한이 남아 있다

        guardUp();
        retryAll();

        send(friend, get("/api/v1/maps/{m}", mapId)).andExpect(status().isForbidden());
        assertThat(rows(mapId)).isEmpty();
    }

    @Test
    @DisplayName("설정이 꺼져 있으면 재시도기는 아무것도 하지 않는다")
    void disabledDoesNothing() {
        String map = id(), user = id();
        guardTuples.write(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        guardDown();
        guardTuples.deleteAfterCommit(PartyGuard.MAP, map, PartyGuard.VIEWER, PartyGuard.USER, user);
        guardUp();

        GuardDeleteOutboxRetrier off = new GuardDeleteOutboxRetrier(outboxRepository, guard,
                new GuardOutboxProperties(false, Duration.ofSeconds(30), 100, Duration.ofHours(1)), txManager);
        off.run();
        assertThat(off.processDue(Instant.now().plus(Duration.ofDays(1)))).isEqualTo(new GuardDeleteOutboxRetrier.Result(0, 0));

        assertThat(rows(map)).hasSize(1);
        assertThat(isViewer(map, user)).isTrue();
    }

    @Test
    @DisplayName("한 번에 batch-size 개까지만, 오래된 행부터 처리한다")
    void batchIsBoundedAndOldestFirst() {
        guardDown();
        String first = id();
        for (int i = 0; i < 3; i++) {
            guardTuples.deleteAfterCommit(PartyGuard.MAP, i == 0 ? first : id(), PartyGuard.VIEWER, PartyGuard.USER, id());
        }
        guardUp();

        GuardDeleteOutboxRetrier small = new GuardDeleteOutboxRetrier(outboxRepository, guard,
                new GuardOutboxProperties(true, Duration.ofSeconds(30), 1, Duration.ofHours(1)), txManager);
        assertThat(small.processDue(Instant.now().plus(Duration.ofDays(1))).succeeded()).isEqualTo(1);

        assertThat(rows(first)).as("가장 오래된 행이 먼저").isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from guard_tuple_delete_outbox", Integer.class)).isEqualTo(2);
    }
}
