package com.doro.party.support;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

/**
 * 시험용 액세스 토큰 발급기. 운영에서는 Doro IAM 이 발급하고 doro-bff 가 게이트웨이를 거쳐 Bearer 로 붙여 주는 토큰을
 * 같은 모양(RS256, sub·email·sid·role·cid)으로 만든다. 공개 키는 시험 기반 클래스가 JWKS 제공자에 등록한다.
 */
public final class TestTokens {

    public static final String KID = "test-iam-kid";

    private final KeyPair keyPair;

    public TestTokens() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            keyPair = gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    /** 한 시간 동안 유효한 액세스 토큰. */
    public String accessToken(UUID userId, String email, String clientId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject(userId.toString())
                .claim("email", email)
                .claim("sid", UUID.randomUUID().toString())
                .claim("role", "USER")
                .claim("cid", clientId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
