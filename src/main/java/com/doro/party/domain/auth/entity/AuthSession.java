package com.doro.party.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** BFF 로그인 세션. 쿠키에는 세션 식별자 원문만 있고 여기에는 그 해시와 암호화된 토큰이 있다. */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "session_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String sessionHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "access_token_enc", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEnc;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_token_enc", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public AuthSession(String sessionHash, UUID userId, String accessTokenEnc, Instant accessExpiresAt,
                       String refreshTokenEnc, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.sessionHash = sessionHash;
        this.userId = userId;
        this.accessTokenEnc = accessTokenEnc;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshTokenEnc = refreshTokenEnc;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 토큰 갱신 결과를 반영한다 (리프레시 토큰은 회전되므로 새 값으로 바꿔야 한다). */
    public void replaceTokens(String accessTokenEnc, Instant accessExpiresAt, String refreshTokenEnc) {
        this.accessTokenEnc = accessTokenEnc;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
    }
}
