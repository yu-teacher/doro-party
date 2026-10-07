package com.doro.party.domain.pin.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 핀에 붙은 사진. 파일은 비공개 스토리지에 있고, 여기에는 키와 서버가 검증한 형식·크기만 둔다. */
@Entity
@Table(name = "pin_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pin_id", nullable = false, updatable = false)
    private UUID pinId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "object_key", nullable = false, updatable = false, length = 200)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private PinPhoto(UUID pinId, UUID uploadedBy, String objectKey, String contentType, long sizeBytes) {
        this.pinId = pinId;
        this.uploadedBy = uploadedBy;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
}
