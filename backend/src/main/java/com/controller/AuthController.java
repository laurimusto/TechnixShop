package com.controller;


import com.dto.user.LoginRequestDTO;
import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public LoginResponseDTO register(@RequestBody @Valid RegisterRequestDTO registerRequestDTO) {
        return authService.createUser(registerRequestDTO);
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody @Valid LoginRequestDTO loginRequestDTO) {
        return authService.login(loginRequestDTO.email(), loginRequestDTO.password());
    }

}
