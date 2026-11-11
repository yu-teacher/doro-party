package com.doro.party.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 친구·모임 초대 코드 암호화. 초대 링크를 다시 보여 주려고 코드를 AES-256-GCM 으로 암호화해 DB 에 둔다(찾기는 해시로만 한다).
 * 형식: {@code v1:} + base64(IV 12바이트 + 암호문+태그 16바이트). 키는 base64 32바이트다.
 *
 * <p>예전에 Doro SDK 의 BFF 암호화(BffCrypto)로 저장한 값과 형식이 같아서, 같은 키면 이미 저장된 초대 코드도 그대로 복호화된다.
 * 키는 {@code party.invite.encryption-key}(환경변수 PARTY_INVITE_KEY, 없으면 PARTY_SESSION_KEY)로만 받는다.
 * 키를 지우거나 바꾸면 저장된 초대 링크를 다시 보여 주지 못한다(새로 만들면 된다).
 */
@Component
public class InviteCodeCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /** @throws IllegalStateException 키가 없거나 base64 32바이트가 아니면(기동 시 바로 실패한다) */
    public InviteCodeCrypto(@Value("${party.invite.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("초대 코드 암호화 키(party.invite.encryption-key, 환경변수 PARTY_INVITE_KEY 또는 PARTY_SESSION_KEY)가 필요하다. "
                    + "생성: openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("초대 코드 암호화 키는 base64 여야 한다", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("초대 코드 암호화 키는 32바이트(base64)여야 한다 (현재 " + raw.length + "바이트). "
                    + "생성: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다", e);
        }
    }

    /** @throws IllegalStateException 형식이 다르거나 키가 맞지 않거나 변조된 경우 */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("알 수 없는 암호문 형식");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) {
                throw new IllegalStateException("암호문이 너무 짧다");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("복호화에 실패했습니다 (키가 바뀌었거나 데이터가 변조됨)", e);
        }
    }
}
