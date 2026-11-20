package com.controller;

import com.dto.user.ChangeEmailRequestDTO;
import com.dto.user.ChangePasswordRequestDTO;
import com.dto.user.DeleteAccountRequestDTO;
import com.dto.user.MeResponseDTO;
import com.dto.user.ProfileDTO;
import com.dto.user.TwoFactorCodeRequestDTO;
import com.dto.user.TwoFactorSetupResponseDTO;
import com.service.TwoFactorService;
import com.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// "/me" = the logged-in user. The id is never taken from the URL or body, only from the verified
// JWT (JwtAuthenticationFilter puts it in as the principal), so nobody can read or edit someone else.
@RestController
@RequestMapping("/me")
public class UserController {

    private final UserService userService;
    private final TwoFactorService twoFactorService;

    public UserController(UserService userService, TwoFactorService twoFactorService) {
        this.userService = userService;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping
    public MeResponseDTO getMe(@AuthenticationPrincipal String userId) {
        return userService.getMe(Long.parseLong(userId));
    }

    @PutMapping
    public MeResponseDTO updateProfile(@AuthenticationPrincipal String userId, @RequestBody @Valid ProfileDTO profile) {
        return userService.updateProfile(Long.parseLong(userId), profile);
    }

    // 204: nothing to return - every session (this one included) has just been ended
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal String userId, @RequestBody @Valid ChangePasswordRequestDTO request) {
        userService.changePassword(Long.parseLong(userId), request);
    }

    // 202: accepted but not done - the email only changes once the link sent to the new address is clicked
    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@AuthenticationPrincipal String userId, @RequestBody @Valid ChangeEmailRequestDTO request) {
        userService.requestEmailChange(Long.parseLong(userId), request);
    }

    // --- Two-factor authentication (TwoFactorService explains the lifecycle) ---

    @PostMapping("/2fa/setup")
    public TwoFactorSetupResponseDTO startTwoFactorSetup(@AuthenticationPrincipal String userId) {
        return twoFactorService.startSetup(Long.parseLong(userId));
    }

    @PostMapping("/2fa/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableTwoFactor(@AuthenticationPrincipal String userId, @RequestBody @Valid TwoFactorCodeRequestDTO request) {
        twoFactorService.enable(Long.parseLong(userId), request.code());
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTwoFactor(@AuthenticationPrincipal String userId, @RequestBody @Valid TwoFactorCodeRequestDTO request) {
        twoFactorService.disable(Long.parseLong(userId), request.code());
    }

    // DELETE with a body: allowed by HTTP and supported by Spring; it carries the re-authentication proof
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal String userId, @RequestBody DeleteAccountRequestDTO request) {
        userService.deleteAccount(Long.parseLong(userId), request);
    }
}
