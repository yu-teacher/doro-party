package com.doro.party.domain.user.repository;

import com.doro.party.domain.user.entity.PartyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PartyUserRepository extends JpaRepository<PartyUser, UUID> {

    Optional<PartyUser> findByUsername(String username);

    boolean existsByUsername(String username);

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
