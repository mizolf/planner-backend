package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.ChatEventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatEvent {
    private ChatEventType type;
    private ChatMessageResponse message;
}
