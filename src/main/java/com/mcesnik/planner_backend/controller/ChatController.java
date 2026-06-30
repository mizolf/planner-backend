package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.ChatMessageRequestDTO;
import com.mcesnik.planner_backend.model.Enums.ChatEventType;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.ChatEvent;
import com.mcesnik.planner_backend.responses.ChatMessagePageResponse;
import com.mcesnik.planner_backend.responses.ChatMessageResponse;
import com.mcesnik.planner_backend.responses.UnreadCountResponse;
import com.mcesnik.planner_backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trips/{tripId}/messages")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<ChatMessagePageResponse> getMessages(
            @PathVariable Long tripId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(chatService.getMessages(tripId, before, limit, getCurrentUser()));
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long tripId,
            @Valid @RequestBody ChatMessageRequestDTO dto) {
        ChatMessageResponse response = chatService.sendMessage(tripId, dto, getCurrentUser());
        broadcast(tripId, ChatEventType.CREATED, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @PathVariable Long tripId,
            @PathVariable Long messageId,
            @Valid @RequestBody ChatMessageRequestDTO dto) {
        ChatMessageResponse response = chatService.editMessage(tripId, messageId, dto, getCurrentUser());
        broadcast(tripId, ChatEventType.UPDATED, response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long tripId,
            @PathVariable Long messageId) {
        chatService.deleteMessage(tripId, messageId, getCurrentUser());
        broadcast(tripId, ChatEventType.DELETED, ChatMessageResponse.builder().id(messageId).build());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(@PathVariable Long tripId) {
        long count = chatService.getUnreadCount(tripId, getCurrentUser());
        return ResponseEntity.ok(UnreadCountResponse.builder().count(count).build());
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(@PathVariable Long tripId) {
        chatService.markRead(tripId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private void broadcast(Long tripId, ChatEventType type, ChatMessageResponse message) {
        messagingTemplate.convertAndSend("/topic/trips/" + tripId, new ChatEvent(type, message));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
