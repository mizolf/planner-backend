package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.ChatMessage;
import com.mcesnik.planner_backend.responses.ChatMessageResponse;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageResponse toResponse(ChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSender().getFullName())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .edited(m.isEdited())
                .build();
    }
}
