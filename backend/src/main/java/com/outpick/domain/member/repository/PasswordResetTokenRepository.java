package com.outpick.domain.member.repository;

import com.outpick.domain.member.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByMemberIdAndCreatedAtAfter(Long memberId, LocalDateTime after);

    @Modifying
    @Query("delete from PasswordResetToken token where token.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
