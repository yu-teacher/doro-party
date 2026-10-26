package com.doro.party.domain.push;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 알림 서비스로 아직 보내지 못한 알림. 보낼 때까지(또는 만료될 때까지) 다시 시도한다. */
@Repository
@RequiredArgsConstructor
public class NotifyOutbox {

    /** 다시 보내야 하는 알림 한 건(내용 포함). */
    public record Row(UUID id, UUID recipientId, String type, String title, String body, String tag, String url, Instant expiresAt, int attempts) {
    }

    private final JdbcTemplate jdbc;

    public void enqueue(UUID recipientId, String type, PushMessage message, Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO notify_outbox (id, recipient_id, type, title, body, tag, url, created_at, expires_at, attempts, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
                UUID.randomUUID(), recipientId, type, limit(message.title(), 300), limit(message.body(), 2000), limit(message.tag(), 200), limit(message.url(), 500),
                Timestamp.from(now), Timestamp.from(expiresAt), Timestamp.from(now));
    }

    /** 시도할 때가 된 행 id 를 오래된 순으로. */
    public List<UUID> findDueIds(Instant now, int limit) {
        return jdbc.query("SELECT id FROM notify_outbox WHERE next_attempt_at <= ? ORDER BY next_attempt_at LIMIT ?",
                (rs, n) -> rs.getObject("id", UUID.class), Timestamp.from(now), limit);
    }

    /** 행을 잠그고 가져온다(다른 인스턴스가 잡은 행은 건너뛴다). 이미 처리됐으면 빈 값. 트랜잭션 안에서 불러야 한다. */
    public java.util.Optional<Row> lock(UUID id) {
        return jdbc.query("""
                SELECT id, recipient_id, type, title, body, tag, url, expires_at, attempts FROM notify_outbox WHERE id = ? FOR UPDATE SKIP LOCKED""",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("recipient_id", UUID.class), rs.getString("type"), rs.getString("title"),
                        rs.getString("body"), rs.getString("tag"), rs.getString("url"), rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts")), id)
                .stream().findFirst();
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM notify_outbox WHERE id = ?", id);
    }

    public void reschedule(UUID id, int attempts, Instant next, String error) {
        jdbc.update("UPDATE notify_outbox SET attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?", attempts, Timestamp.from(next), limit(error, 500), id);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM notify_outbox", Long.class);
        return n == null ? 0 : n;
    }

    /** 가장 오래 기다린 행의 대기 시간(초). 없으면 null. 운영 점검용. */
    public Long oldestWaitingSeconds(Instant now) {
        Timestamp oldest = jdbc.queryForObject("SELECT min(created_at) FROM notify_outbox", Timestamp.class);
        return oldest == null ? null : Math.max(0, now.getEpochSecond() - oldest.toInstant().getEpochSecond());
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
