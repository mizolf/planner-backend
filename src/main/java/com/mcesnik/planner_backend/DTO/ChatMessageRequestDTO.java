package com.mcesnik.planner_backend.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequestDTO {

    @NotBlank(message = "Content is required")
    @Size(max = 2000, message = "Content must be at most 2000 characters")
    private String content;
}
