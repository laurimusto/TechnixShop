package com.service;

import com.dto.user.ChangeEmailRequestDTO;
import com.dto.user.ChangePasswordRequestDTO;
import com.dto.user.DeleteAccountRequestDTO;
import com.dto.user.MeResponseDTO;
import com.dto.user.ProfileDTO;
import com.dto.user.ProfileMapper;
import com.entity.EmailChangeToken;
import com.entity.User;
import com.exception.EmailAlreadyExistsException;
import com.exception.InvalidEmailChangeTokenException;
import com.exception.InvalidEmailException;
import com.exception.InvalidPasswordException;
import com.exception.PasswordMismatchException;
import com.exception.PasswordReuseException;
import com.exception.ResourceNotFoundException;
import com.repository.EmailChangeTokenRepository;
import com.repository.RefreshTokenRepository;
import com.security.GoogleTokenVerifier;
import com.security.TokenHashing;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class UserService {
    // how long the "confirm your new email" link works
    private static final Duration EMAIL_CHANGE_LIFETIME = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder, GoogleTokenVerifier googleTokenVerifier,
                       EmailChangeTokenRepository emailChangeTokenRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.emailChangeTokenRepository = emailChangeTokenRepository;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public MeResponseDTO getMe(Long userId) {
        return ProfileMapper.toMeResponse(findUser(userId));
    }

    // @Transactional: the loaded User is "managed", so the changes are saved on commit without calling save()
    @Transactional
    public MeResponseDTO updateProfile(Long userId, ProfileDTO profile) {
        User user = findUser(userId);
        ProfileMapper.applyTo(profile, user);
        return ProfileMapper.toMeResponse(user);
    }

    // @Transactional: new hash, passwordChangedAt and the refresh-token revocation commit together or not at all.
    // Setting passwordChangedAt makes JwtAuthenticationFilter reject every older access token, so together
    // with the revoked refresh tokens this logs out every device - including the current one.
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequestDTO request) {
        User user = findUser(userId);
        if (user.getPassword() == null) {
            throw new InvalidPasswordException("Your account signs in with Google, so it has no password to change.");
        }
        if (!request.newPassword().equals(request.repeatPassword())) {
            throw new PasswordMismatchException("Passwords do not match.");
        }
        if (!bCryptPasswordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect.");
        }
        if (bCryptPasswordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new PasswordReuseException("New password must be different from the current one.");
        }

        user.setPassword(bCryptPasswordEncoder.encode(request.newPassword()));
        // JWT iat has whole-second precision; truncating avoids rejecting a token issued in this same second
        user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        refreshTokenRepository.revokeAllByUser(user);
    }

    // Hard delete (GDPR right to erasure). User has no child collections in JPA, so Hibernate deletes just
    // the users row and Postgres' ON DELETE CASCADE removes the refresh and reset tokens in the same transaction.
    // When orders exist (Project 2) this must become anonymisation: invoices have to be kept by law.
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequestDTO request) {
        User user = findUser(userId);
        if (user.getPassword() != null) {
            if (request.password() == null || !bCryptPasswordEncoder.matches(request.password(), user.getPassword())) {
                throw new InvalidPasswordException("Password is incorrect.");
            }
        } else {
            // Google-only account: the fresh Google sign-in must belong to this exact account (same Google id)
            boolean sameGoogleAccount = request.googleIdToken() != null
                    && googleTokenVerifier.verify(request.googleIdToken())
                    .map(google -> google.sub().equals(user.getGoogleSub()))
                    .orElse(false);
            if (!sameGoogleAccount) {
                throw new InvalidPasswordException("Google sign-in did not confirm this account.");
            }
        }
        userRepository.delete(user);
    }

    // Step 1 of an email change: nothing changes yet. A link goes to the NEW address (proves the user owns it)
    // and a heads-up goes to the OLD one (so a hijacked session can't quietly move the account away).
    // @Transactional: if sending the mail fails, the pending request is rolled back too.
    @Transactional
    public void requestEmailChange(Long userId, ChangeEmailRequestDTO request) {
        User user = findUser(userId);
        if (user.getPassword() == null) {
            // Google-only accounts: the email belongs to the Google account
            throw new InvalidPasswordException("Your account signs in with Google, so its email comes from Google.");
        }
        if (request.password() == null || !bCryptPasswordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidPasswordException("Password is incorrect.");
        }
        String newEmail = request.newEmail().trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new InvalidEmailException("That is already your email.");
        }
        // same answer /register gives, so this reveals nothing new - and it needs the password anyway
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }

        emailChangeTokenRepository.deleteByUser(user); // only the newest request is valid

        String rawToken = TokenHashing.generateRawToken();
        EmailChangeToken token = new EmailChangeToken();
        token.setTokenHash(TokenHashing.sha256(rawToken)); // the database never sees the link itself
        token.setUser(user);
        token.setNewEmail(newEmail);
        token.setExpiryDate(new Date(System.currentTimeMillis() + EMAIL_CHANGE_LIFETIME.toMillis()));
        emailChangeTokenRepository.save(token);

        emailService.sendEmailChangeConfirmation(newEmail, rawToken);
        emailService.sendEmailChangeNotice(user.getEmail(), newEmail);
    }

    // Step 2: the link was clicked. The token alone says who the user is, so this works logged in or not.
    // Sessions stay valid: they are tied to the user id, not to the email.
    @Transactional
    public void confirmEmailChange(String rawToken) {
        EmailChangeToken token = emailChangeTokenRepository.findByTokenHash(TokenHashing.sha256(rawToken))
                .filter(t -> t.getExpiryDate().after(new Date()))
                .orElseThrow(() -> new InvalidEmailChangeTokenException("This confirmation link is invalid or has expired."));

        // someone may have registered this address since the link was sent
        if (userRepository.existsByEmailIgnoreCase(token.getNewEmail())) {
            emailChangeTokenRepository.delete(token);
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }
        token.getUser().setEmail(token.getNewEmail());
        emailChangeTokenRepository.delete(token); // single use
    }

    // the id comes from a valid JWT, but the account may have been deleted since it was issued
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
