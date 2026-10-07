package com.doro.party.domain.pin.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PinPrivateNoteRepository extends JpaRepository<PinPrivateNote, PinPrivateNote.Key> {

    /** 이 지도의 핀 중 내가 사적 메모를 남긴 것. 다른 사람의 메모는 조건상 절대 나오지 않는다. */
    @Query("select n from PinPrivateNote n where n.id.userId = :userId "
            + "and n.id.pinId in (select p.id from Pin p where p.mapId = :mapId) order by n.updatedAt desc")
    List<PinPrivateNote> findMine(@Param("mapId") UUID mapId, @Param("userId") UUID userId);

    /** 같은 사용자가 동시에 처음 저장해도 PK 충돌로 실패하지 않도록 한 문장으로 만들거나 덮어쓴다. */
    @Modifying
    @Query(value = "insert into pin_private_notes (pin_id, user_id, body, updated_at) values (:pinId, :userId, :body, now()) "
            + "on conflict (pin_id, user_id) do update set body = excluded.body, updated_at = now()", nativeQuery = true)
    int upsert(@Param("pinId") UUID pinId, @Param("userId") UUID userId, @Param("body") String body);

    @Modifying
    @Query("delete from PinPrivateNote n where n.id.pinId = :pinId and n.id.userId = :userId")
    int deleteMine(@Param("pinId") UUID pinId, @Param("userId") UUID userId);
}
