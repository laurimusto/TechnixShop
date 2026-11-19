package com.repository;

import com.entity.RefreshToken;
import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUser(User user);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.familyId = :familyId")
    void revokeAllByFamilyId(UUID familyId);

    // Atomic check-and-set: the database lets only one concurrent caller flip revoked from
    // false to true. Returns 1 for the caller that claimed the token, 0 for everyone else.
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.tokenId = :tokenId AND r.revoked = false")
    int revokeIfActive(Long tokenId);

    // One UPDATE for every token the user has - used after a password reset to end all sessions.
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllByUser(User user);

}
