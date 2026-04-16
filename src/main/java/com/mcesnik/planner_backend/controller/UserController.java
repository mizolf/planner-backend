package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.UserResponse;
import com.mcesnik.planner_backend.service.UserService;
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
    public ResponseEntity<User> authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(currentUser);
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
}
