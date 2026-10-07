package com.doro.party.domain.pin.photo;

import java.time.Instant;
import java.util.UUID;

public final class PhotoDtos {

    private PhotoDtos() {
    }

    /** 사진 메타데이터. 내용은 {@code .../photos/{id}/content} 로 권한을 확인한 뒤 내려준다. */
    public record PhotoResponse(UUID id, UUID pinId, UUID uploadedBy, String contentType, long sizeBytes, Instant createdAt) {
        public static PhotoResponse from(PinPhoto photo) {
            return new PhotoResponse(photo.getId(), photo.getPinId(), photo.getUploadedBy(), photo.getContentType(),
                    photo.getSizeBytes(), photo.getCreatedAt());
        }
    }

    /** 내려줄 사진: 열린 스트림은 호출자가 닫는다. */
    public record PhotoContent(String contentType, long sizeBytes, java.io.InputStream stream) {
    }
}
