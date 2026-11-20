package com.outpick.domain.member.repository;

import com.outpick.domain.member.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now where token.familyId = :familyId and token.revokedAt is null")
    void revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from RefreshToken token where token.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
