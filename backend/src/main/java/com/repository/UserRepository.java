package com.repository;

import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSub(String googleSub);

    // Runs on every authenticated request (JwtAuthenticationFilter), so it fetches just this one
    // column by primary key instead of loading the whole User. Empty = the user no longer exists.
    // "new ..." is a JPQL constructor expression: it needs the record's fully qualified name.
    @Query("SELECT new com.repository.UserSessionState(u.passwordChangedAt) FROM User u WHERE u.userId = :userId")
    Optional<UserSessionState> findSessionStateByUserId(Long userId);

    // TOTP replay guard, same idea as RefreshTokenRepository.revokeIfActive: checking lastUsedStep in Java and
    // then saving would let two simultaneous requests with the same code both pass. This single conditional
    // UPDATE lets only one of them move the step forward; the other gets 0 rows = replay, rejected.
    @Modifying
    @Query("UPDATE User u SET u.totpLastUsedStep = :step WHERE u.userId = :userId " +
            "AND (u.totpLastUsedStep IS NULL OR u.totpLastUsedStep < :step)")
    int markTotpStepUsed(Long userId, long step);
}
