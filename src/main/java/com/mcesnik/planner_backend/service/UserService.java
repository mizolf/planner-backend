package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.ChangePasswordDTO;
import com.mcesnik.planner_backend.exception.InvalidPasswordException;
import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.UserRepository;
import com.mcesnik.planner_backend.responses.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordDTO dto) {
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse updatePreferences(User user, Set<Interest> interests) {
        user.setPreferredInterests(interests != null ? interests : new HashSet<>());
        userRepository.save(user);
        return UserResponse.from(user);
    }
}
