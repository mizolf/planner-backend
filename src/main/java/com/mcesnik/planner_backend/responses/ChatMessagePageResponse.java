package com.mcesnik.planner_backend.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePageResponse {
    private List<ChatMessageResponse> content;
    private boolean hasMore;
}
