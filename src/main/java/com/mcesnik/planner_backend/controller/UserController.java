package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.ChangePasswordDTO;
import com.mcesnik.planner_backend.DTO.UpdatePreferencesDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.UserResponse;
import com.mcesnik.planner_backend.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/users")
@RestController
@Validated
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> authenticatedUser() {
        return ResponseEntity.ok(UserResponse.from(currentUser()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(currentUser(), dto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserResponse> updatePreferences(@Valid @RequestBody UpdatePreferencesDTO dto) {
        return ResponseEntity.ok(userService.updatePreferences(currentUser(), dto.getInterests()));
    }

    @GetMapping("/search")
    public ResponseEntity<UserResponse> searchByEmail(
            @RequestParam @Email(message = "Invalid email format") String email) {
        return userService.findByEmail(email)
                .map(user -> ResponseEntity.ok(UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
