package com.doro.party.domain.push;

import java.nio.charset.StandardCharsets;

/**
 * 알림 하나의 내용. {@code url} 은 앱 범위(/party/) 기준의 상대 주소(예: {@code ./?map=...&pin=...})이고,
 * {@code tag} 가 같은 알림은 서로 바꿔치기된다(같은 핀의 댓글이 연달아 와도 하나만 남는다).
 */
public record PushMessage(String title, String body, String url, String tag) {

    static final int TITLE_MAX = 60;
    static final int BODY_MAX = 140;

    /** 암호화 전의 JSON 본문. 한도(4KB)를 넘지 않게 본문을 줄인다. */
    public byte[] toJson() {
        String json = build(clip(title, TITLE_MAX), clip(body, BODY_MAX));
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > WebPushEncryptor.MAX_PLAINTEXT_BYTES) {
            bytes = build(clip(title, TITLE_MAX), "").getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    private String build(String clippedTitle, String clippedBody) {
        return "{\"title\":" + quote(clippedTitle) + ",\"body\":" + quote(clippedBody) + ",\"url\":" + quote(url) + ",\"tag\":" + quote(tag) + "}";
    }

    /** 글자 수 기준으로 자르되 서로게이트 쌍(이모지)을 반으로 자르지 않는다. */
    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").strip();
        if (compact.codePointCount(0, compact.length()) <= max) {
            return compact;
        }
        int end = compact.offsetByCodePoints(0, max - 1);
        return compact.substring(0, end) + "…";
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
