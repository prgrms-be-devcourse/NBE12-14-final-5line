package com.outpick.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "refresh_token")
@Getter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {}

    public RefreshToken(Member member, String tokenHash, String familyId, LocalDateTime now) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.authVersion = member.getAuthVersion();
        this.expiresAt = now.plusDays(7);
    }

    public boolean usableAt(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt)
                && authVersion == member.getAuthVersion() && "ACTIVE".equals(member.getStatus());
    }

    public void revoke(LocalDateTime now) { revokedAt = now; }
}
