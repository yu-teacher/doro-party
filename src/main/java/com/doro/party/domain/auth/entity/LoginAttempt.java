package com.doro.party.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 진행 중인 로그인(인가 요청을 보냈고 콜백을 기다리는 상태). state 해시로 찾고, 한 번 쓰면 지운다. */
@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    @Id
    @Column(name = "state_hash", length = 64)
    private String stateHash;

    @Column(name = "code_verifier_enc", nullable = false, columnDefinition = "TEXT")
    private String codeVerifierEnc;

    @Column(name = "return_path", nullable = false, length = 500)
    private String returnPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public LoginAttempt(String stateHash, String codeVerifierEnc, String returnPath, Instant now, Instant expiresAt) {
        this.stateHash = stateHash;
        this.codeVerifierEnc = codeVerifierEnc;
        this.returnPath = returnPath;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
