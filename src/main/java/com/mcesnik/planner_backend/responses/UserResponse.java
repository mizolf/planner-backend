package com.mcesnik.planner_backend.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.User;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;

    // Omitted from /search, which never sets it; included by /me (empty set -> []).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<Interest> preferredInterests;

    public static UserResponse from(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .preferredInterests(u.getPreferredInterests())
                .build();
    }
}
