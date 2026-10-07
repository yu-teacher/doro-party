package com.doro.party.domain.pin.photo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSnifferTest {

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("첫 바이트로 JPEG, PNG, WebP 를 판별한다")
    void detectsSupportedTypes() {
        assertThat(ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains(new ImageSniffer.Detected("image/jpeg", "jpg"));
        assertThat(ImageSniffer.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(new ImageSniffer.Detected("image/png", "png"));
        assertThat(ImageSniffer.detect(ascii("RIFF\0\0\0\0WEBP"))).contains(new ImageSniffer.Detected("image/webp", "webp"));
    }

    @Test
    @DisplayName("GIF, SVG, HTML, 텍스트, 너무 짧은 입력, 헤더만 비슷한 파일은 받지 않는다")
    void rejectsEverythingElse() {
        assertThat(ImageSniffer.detect(ascii("GIF89a"))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("<html></html>"))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("plain text"))).isEmpty();
        assertThat(ImageSniffer.detect(new byte[0])).isEmpty();
        assertThat(ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        // RIFF 이지만 WebP 가 아닌 것(예: WAV)
        assertThat(ImageSniffer.detect(ascii("RIFF\0\0\0\0WAVE"))).isEmpty();
    }
}
