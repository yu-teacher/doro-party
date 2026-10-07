package com.doro.party.domain.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    Optional<Friendship> findByUserLowIdAndUserHighId(UUID low, UUID high);

    @Query("select f from Friendship f where f.status = com.doro.party.domain.friend.Friendship.Status.ACCEPTED "
            + "and (f.userLowId = :userId or f.userHighId = :userId) order by f.acceptedAt desc")
    List<Friendship> findFriends(@Param("userId") UUID userId);

    /** 나에게 온 요청(내가 보낸 것이 아닌 대기 중인 요청). */
    @Query("select f from Friendship f where f.status = com.doro.party.domain.friend.Friendship.Status.PENDING "
            + "and f.requesterId <> :userId and (f.userLowId = :userId or f.userHighId = :userId) order by f.createdAt desc")
    List<Friendship> findIncoming(@Param("userId") UUID userId);

    @Query("select f from Friendship f where f.status = com.doro.party.domain.friend.Friendship.Status.PENDING "
            + "and f.requesterId = :userId order by f.createdAt desc")
    List<Friendship> findOutgoing(@Param("userId") UUID userId);

    @Query("select count(f) from Friendship f where f.status = com.doro.party.domain.friend.Friendship.Status.ACCEPTED "
            + "and (f.userLowId = :userId or f.userHighId = :userId)")
    long countFriends(@Param("userId") UUID userId);

    @Query("select count(f) from Friendship f where f.status = com.doro.party.domain.friend.Friendship.Status.PENDING and f.requesterId = :userId")
    long countOutgoing(@Param("userId") UUID userId);

    @Query("select count(f) > 0 from Friendship f where f.userLowId = :low and f.userHighId = :high "
            + "and f.status = com.doro.party.domain.friend.Friendship.Status.ACCEPTED")
    boolean areFriends(@Param("low") UUID low, @Param("high") UUID high);

    /** 같은 쌍의 요청이 동시에 들어와도 한 행만 만들어진다(이미 있으면 아무것도 하지 않는다). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into friendships (id, user_low_id, user_high_id, requester_id, status, created_at) "
            + "values (:id, :low, :high, :requester, 'PENDING', now()) on conflict (user_low_id, user_high_id) do nothing", nativeQuery = true)
    int insertPendingIfAbsent(@Param("id") UUID id, @Param("low") UUID low, @Param("high") UUID high, @Param("requester") UUID requester);

    /** 초대 링크로 바로 친구가 된다. 이미 대기 중이면 수락으로 바꾸고, 이미 친구면 그대로 둔다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into friendships (id, user_low_id, user_high_id, requester_id, status, created_at, accepted_at) "
            + "values (:id, :low, :high, :requester, 'ACCEPTED', now(), now()) "
            + "on conflict (user_low_id, user_high_id) do update set status = 'ACCEPTED', accepted_at = coalesce(friendships.accepted_at, now())", nativeQuery = true)
    int upsertAccepted(@Param("id") UUID id, @Param("low") UUID low, @Param("high") UUID high, @Param("requester") UUID requester);

    /** 대기 중인 요청만 수락한다. 이미 수락됐거나 지워졌다면 0. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update friendships set status = 'ACCEPTED', accepted_at = now() where id = :id and status = 'PENDING'", nativeQuery = true)
    int acceptPending(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from friendships where id = :id and status = 'PENDING'", nativeQuery = true)
    int deletePending(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from friendships where user_low_id = :low and user_high_id = :high and status = 'ACCEPTED'", nativeQuery = true)
    int deleteAccepted(@Param("low") UUID low, @Param("high") UUID high);
}
