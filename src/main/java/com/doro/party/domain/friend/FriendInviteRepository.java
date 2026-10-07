package com.doro.party.domain.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, UUID> {

    Optional<FriendInvite> findByCodeHash(String codeHash);

    /** 사용자마다 링크는 하나다. 다시 만들면 이전 링크는 바로 무효가 된다(동시에 눌러도 한 행만 남는다). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into friend_invites (owner_id, code_hash, code_enc, created_at, expires_at) "
            + "values (:ownerId, :hash, :enc, now(), :expiresAt) "
            + "on conflict (owner_id) do update set code_hash = excluded.code_hash, code_enc = excluded.code_enc, "
            + "created_at = now(), expires_at = excluded.expires_at", nativeQuery = true)
    int upsert(@Param("ownerId") UUID ownerId, @Param("hash") String hash, @Param("enc") String enc, @Param("expiresAt") Instant expiresAt);
}
