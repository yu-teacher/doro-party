package com.doro.party.domain.user.repository;

import com.doro.party.domain.user.entity.PartyUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PartyUserRepository extends JpaRepository<PartyUser, UUID> {

    Optional<PartyUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 사용자별 지도 개수 상한처럼 "개수를 세고 만드는" 작업을 같은 사용자에 대해 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from PartyUser u where u.id = :id")
    Optional<PartyUser> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 같은 사용자의 첫 요청이 동시에 여러 개 들어와도 PK 충돌로 실패하지 않도록, 이미 있으면 아무것도 하지 않는다.
     */
    @Modifying
    @Query(value = "insert into party_users (id, username, nickname, color) "
            + "values (:id, :username, :nickname, :color) on conflict (id) do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("username") String username,
                       @Param("nickname") String nickname, @Param("color") String color);
}
