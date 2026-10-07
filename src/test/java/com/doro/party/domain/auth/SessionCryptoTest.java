package com.doro.party.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCryptoTest {

    private static String key(int bytes, int fill) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final SessionCrypto crypto = new SessionCrypto(key(32, 7));

    @Test
    @DisplayName("암호화한 값은 평문을 담지 않고 복호화하면 원래 값이 된다")
    void roundTrip() {
        String stored = crypto.encrypt("refresh-token-value");

        assertThat(stored).startsWith("v1:").doesNotContain("refresh-token-value");
        assertThat(crypto.decrypt(stored)).isEqualTo("refresh-token-value");
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문이 된다 (IV 가 무작위)")
    void ciphertextsDiffer() {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    @DisplayName("변조된 암호문과 다른 키로는 복호화되지 않는다")
    void tamperingAndWrongKeyAreRejected() {
        String stored = crypto.encrypt("secret");
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SessionCrypto(key(32, 9)).decrypt(stored)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("plain-text")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키는 base64 32바이트여야 한다 (기동 시 바로 실패)")
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new SessionCrypto(key(16, 1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("32바이트");
        assertThatThrownBy(() -> new SessionCrypto("not base64 !!!")).isInstanceOf(IllegalStateException.class);
    }
}
