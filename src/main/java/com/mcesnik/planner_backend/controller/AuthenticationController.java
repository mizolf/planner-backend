package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.LoginUserDTO;
import com.mcesnik.planner_backend.DTO.RegisterUserDTO;
import com.mcesnik.planner_backend.DTO.ResendVerificationDTO;
import com.mcesnik.planner_backend.DTO.VerifiedUserDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.LoginResponse;
import com.mcesnik.planner_backend.responses.RegisterResponseDTO;
import com.mcesnik.planner_backend.service.AuthenticationService;
import com.mcesnik.planner_backend.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/signup")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterUserDTO registerUserDTO) {
        User registeredUser = authenticationService.signUp(registerUserDTO);
        return ResponseEntity.ok(new RegisterResponseDTO(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginUserDTO loginUserDTO) {
        User authenticatedUser = authenticationService.authenticate(loginUserDTO);
        String jwtToken = jwtService.generateToken(authenticatedUser);
        LoginResponse loginResponse = new LoginResponse(jwtToken, jwtService.getExpirationTime());
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verify(@Valid @RequestBody VerifiedUserDTO verifiedUserDTO) {
        authenticationService.verifyUser(verifiedUserDTO);
        return ResponseEntity.ok("Account verified successfully");
    }

    @PostMapping("/resend")
    public ResponseEntity<String> resendVerificationCode(@Valid @RequestBody ResendVerificationDTO resendVerificationDTO) {
        authenticationService.resendVerificationCode(resendVerificationDTO);
        return ResponseEntity.ok("Verification code sent");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authenticationService.logout(token);
        return ResponseEntity.ok("Logged out successfully");
    }
}
