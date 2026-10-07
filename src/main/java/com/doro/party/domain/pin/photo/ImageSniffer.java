package com.doro.party.domain.pin.photo;

import java.util.Optional;

/**
 * 사진 형식 판별. 클라이언트가 보낸 확장자와 Content-Type 은 믿지 않고 파일의 첫 바이트로 판단한다.
 * JPEG·PNG·WebP 만 받는다. SVG 는 스크립트를 담을 수 있고 GIF 는 필요하지 않아 받지 않는다.
 */
public final class ImageSniffer {

    /** 판별된 형식: 서버가 저장하고 내려줄 때 쓰는 Content-Type 과 확장자. */
    public record Detected(String contentType, String extension) {
    }

    /** 판별에 필요한 최소 바이트 수(WebP 의 RIFF....WEBP 헤더가 12바이트). */
    public static final int HEADER_BYTES = 12;

    private ImageSniffer() {
    }

    public static Optional<Detected> detect(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return Optional.of(new Detected("image/jpeg", "jpg"));
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return Optional.of(new Detected("image/png", "png"));
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return Optional.of(new Detected("image/webp", "webp"));
        }
        return Optional.empty();
    }
}
