package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterResponseDTO {
    private Long id;
    private String fullName;
    private String email;

    public RegisterResponseDTO(User user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
    }
}
