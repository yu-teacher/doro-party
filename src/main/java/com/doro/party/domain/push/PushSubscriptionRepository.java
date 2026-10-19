package com.doro.party.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    /** 같은 기기를 다른 계정으로 다시 로그인해 등록하면 그 계정의 구독으로 옮긴다(동시에 두 번 불러도 안전하다). */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth, user_agent, created_at, failure_count) "
            + "VALUES (:id, :userId, :endpoint, :p256dh, :auth, :userAgent, :now, 0) "
            + "ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth, "
            + "user_agent = EXCLUDED.user_agent, failure_count = 0", nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("userId") UUID userId, @Param("endpoint") String endpoint, @Param("p256dh") String p256dh,
                @Param("auth") String auth, @Param("userAgent") String userAgent, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.userId = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") UUID userId);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.id = :id")
    int deleteBySubscriptionId(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("update PushSubscription s set s.lastSuccessAt = :now, s.failureCount = 0 where s.id = :id")
    void recordSuccess(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update PushSubscription s set s.failureCount = s.failureCount + 1 where s.id = :id")
    void recordRejection(@Param("id") UUID id);

    /** 거절 응답이 이만큼 연속된 구독을 지운다. */
    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.id = :id and s.failureCount >= :threshold")
    int deleteIfRejectedTooOften(@Param("id") UUID id, @Param("threshold") int threshold);
}
