package com.doro.party.domain.pin.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PinPhotoRepository extends JpaRepository<PinPhoto, UUID> {

    List<PinPhoto> findAllByPinIdOrderByCreatedAtAsc(UUID pinId);

    Optional<PinPhoto> findByIdAndPinId(UUID id, UUID pinId);

    long countByPinId(UUID pinId);

    interface PhotoCount {
        UUID getPinId();

        long getCount();
    }

    @Query("select ph.pinId as pinId, count(ph) as count from PinPhoto ph where ph.pinId in :pinIds group by ph.pinId")
    List<PhotoCount> countsByPinIds(@Param("pinIds") Collection<UUID> pinIds);

    /** 지우기 전에 스토리지에서도 지워야 할 오브젝트 키를 모은다(핀 하나 / 지도 전체). */
    @Query("select ph.objectKey from PinPhoto ph where ph.pinId = :pinId")
    List<String> objectKeysOfPin(@Param("pinId") UUID pinId);

    @Query("select ph.objectKey from PinPhoto ph where ph.pinId in (select p.id from Pin p where p.mapId = :mapId)")
    List<String> objectKeysOfMap(@Param("mapId") UUID mapId);

    /** 탈퇴 정리: 이 사용자가 올린 사진의 스토리지 키(어느 지도의 핀이든). */
    @Query("select ph.objectKey from PinPhoto ph where ph.uploadedBy = :userId")
    List<String> objectKeysUploadedBy(@Param("userId") UUID userId);

    /** 탈퇴 정리: 이 사용자가 올린 사진 기록. 파일을 먼저 지운 뒤에 부른다. */
    @Modifying
    @Query("delete from PinPhoto ph where ph.uploadedBy = :userId")
    int deleteAllUploadedBy(@Param("userId") UUID userId);
}
