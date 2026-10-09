package com.doro.party.domain.pin.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PinCommentRepository extends JpaRepository<PinComment, UUID> {

    List<PinComment> findAllByPinIdOrderByCreatedAtAscIdAsc(UUID pinId);

    Optional<PinComment> findByIdAndPinId(UUID id, UUID pinId);

    long countByPinId(UUID pinId);

    interface CommentCount {
        UUID getPinId();

        long getCount();
    }

    @Query("select c.pinId as pinId, count(c) as count from PinComment c where c.pinId in :pinIds group by c.pinId")
    List<CommentCount> countsByPinIds(@Param("pinIds") Collection<UUID> pinIds);

    /** 읽은 시각은 앞으로만 간다(오래된 요청이 나중에 도착해도 되돌리지 않는다). */
    @Modifying
    @Query(value = "INSERT INTO pin_comment_reads (pin_id, user_id, last_read_at) VALUES (:pinId, :userId, :upTo) "
            + "ON CONFLICT (pin_id, user_id) DO UPDATE SET last_read_at = GREATEST(pin_comment_reads.last_read_at, EXCLUDED.last_read_at)",
            nativeQuery = true)
    void markRead(@Param("pinId") UUID pinId, @Param("userId") UUID userId, @Param("upTo") Instant upTo);

    interface UnreadRow {
        UUID getPinId();

        UUID getMapId();

        String getPinName();

        long getUnread();
    }

    /**
     * 내가 만든 핀이거나 내가 댓글을 남긴 핀에서, 내가 읽은 시각 이후에 남이 쓴 댓글 수. 가장 최근 댓글이 있는 핀부터.
     * 지도를 더 이상 볼 수 없게 된 핀은 서비스가 Guard 로 걸러 낸다.
     */
    @Query(value = """
            SELECT c.pin_id AS pinId, p.map_id AS mapId, p.name AS pinName, count(*) AS unread
            FROM pin_comments c
            JOIN pins p ON p.id = c.pin_id
            LEFT JOIN pin_comment_reads r ON r.pin_id = c.pin_id AND r.user_id = :userId
            WHERE c.user_id <> :userId
              AND (p.created_by = :userId OR EXISTS (SELECT 1 FROM pin_comments mine WHERE mine.pin_id = c.pin_id AND mine.user_id = :userId))
              AND (r.last_read_at IS NULL OR c.created_at > r.last_read_at)
            GROUP BY c.pin_id, p.map_id, p.name
            ORDER BY max(c.created_at) DESC, c.pin_id
            LIMIT :limit
            """, nativeQuery = true)
    List<UnreadRow> findUnread(@Param("userId") UUID userId, @Param("limit") int limit);
}
