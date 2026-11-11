package com.doro.party.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InviteCodeCryptoTest {

    /** 시험용 고정 키: 바이트 i*7+3 (i=0..31) 의 base64. */
    private static final String FIXED_KEY = "AwoRGB8mLTQ7QklQV15lbHN6gYiPlp2kq7K5wMfO1dw=";
    private static final String FIXED_PLAIN = "Xk3_party-invite-code-0123456789abcdef";
    /**
     * 위 키와 평문으로 Doro SDK(sdk-v0.1.0) 의 BffCrypto.encrypt 가 만든 실제 암호문. 운영 DB 에 이미 저장된 초대 코드가
     * 이 클래스로 바뀐 뒤에도 그대로 복호화되는지를 이 값으로 고정해 확인한다(SDK 에서 BffCrypto 가 사라져도 시험이 남는다).
     */
    private static final String BFF_CRYPTO_CIPHERTEXT =
            "v1:mb4t02gyy49T658clhHk5hsXBSB6TRZ65WXsIqEpqWslDs8r5RIKYJrnnemDIxTJIsvSdzw8Ir3WFLeUAUvXmJf9";

    private static String key(int bytes, int fill) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final InviteCodeCrypto crypto = new InviteCodeCrypto(key(32, 7));

    @Test
    @DisplayName("예전 SDK BffCrypto 로 저장한 암호문을 같은 키로 복호화한다(운영 DB 의 초대 코드 호환)")
    void decryptsCiphertextFromSdkBffCrypto() {
        InviteCodeCrypto fixed = new InviteCodeCrypto(FIXED_KEY);

        assertThat(fixed.decrypt(BFF_CRYPTO_CIPHERTEXT)).isEqualTo(FIXED_PLAIN);
        // 키 앞뒤 공백은 SDK 처럼 무시한다(.env 값 끝의 공백·줄바꿈)
        assertThat(new InviteCodeCrypto(" " + FIXED_KEY + "\n").decrypt(BFF_CRYPTO_CIPHERTEXT)).isEqualTo(FIXED_PLAIN);
    }

    @Test
    @DisplayName("새로 만든 암호문도 같은 형식(v1: + base64(IV 12 + 암호문 + 태그 16))이다")
    void producesSameFormat() {
        InviteCodeCrypto fixed = new InviteCodeCrypto(FIXED_KEY);
        String stored = fixed.encrypt(FIXED_PLAIN);

        assertThat(stored).startsWith("v1:").hasSameSizeAs(BFF_CRYPTO_CIPHERTEXT);
        byte[] all = Base64.getDecoder().decode(stored.substring(3));
        assertThat(all).hasSize(12 + FIXED_PLAIN.length() + 16);
        assertThat(fixed.decrypt(stored)).isEqualTo(FIXED_PLAIN);
    }

    @Test
    @DisplayName("암호화한 값은 평문을 담지 않고 복호화하면 원래 값이 된다")
    void roundTrip() {
        String stored = crypto.encrypt("invite-code-value");

        assertThat(stored).startsWith("v1:").doesNotContain("invite-code-value");
        assertThat(crypto.decrypt(stored)).isEqualTo("invite-code-value");
        assertThat(crypto.decrypt(crypto.encrypt("한글 코드"))).isEqualTo("한글 코드");
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문이 된다 (IV 가 무작위)")
    void ciphertextsDiffer() {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    @DisplayName("변조된 암호문, 다른 키, 형식이 다른 값은 복호화되지 않는다")
    void tamperingAndWrongKeyAreRejected() {
        String stored = crypto.encrypt("secret");
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InviteCodeCrypto(key(32, 9)).decrypt(stored)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InviteCodeCrypto(key(32, 9)).decrypt(BFF_CRYPTO_CIPHERTEXT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("plain-text")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("v1:AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("v1:not base64!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 없거나 base64 32바이트가 아니면 만들 수 없다 (기동 시 바로 실패)")
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new InviteCodeCrypto(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("PARTY_SESSION_KEY");
        assertThatThrownBy(() -> new InviteCodeCrypto("  ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InviteCodeCrypto(key(16, 1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("32바이트");
        assertThatThrownBy(() -> new InviteCodeCrypto("not base64 !!!")).isInstanceOf(IllegalStateException.class);
    }
}
