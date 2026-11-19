package com.controller;

import com.dto.user.MeResponseDTO;
import com.dto.user.ProfileDTO;
import com.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// "/me" = the logged-in user. The id is never taken from the URL or body, only from the verified
// JWT (JwtAuthenticationFilter puts it in as the principal), so nobody can read or edit someone else.
@RestController
@RequestMapping("/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponseDTO getMe(@AuthenticationPrincipal String userId) {
        return userService.getMe(Long.parseLong(userId));
    }

    @PutMapping
    public MeResponseDTO updateProfile(@AuthenticationPrincipal String userId, @RequestBody @Valid ProfileDTO profile) {
        return userService.updateProfile(Long.parseLong(userId), profile);
    }
}
