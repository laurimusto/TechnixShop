package com.repository;

import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSub(String googleSub);

    // Runs on every authenticated request (JwtAuthenticationFilter), so it fetches just this one
    // column by primary key instead of loading the whole User. Empty = user not found or never reset.
    @Query("SELECT u.passwordChangedAt FROM User u WHERE u.userId = :userId")
    Optional<Instant> findPasswordChangedAtByUserId(Long userId);
}
