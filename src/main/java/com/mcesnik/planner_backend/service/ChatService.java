package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.ChatMessageRequestDTO;
import com.mcesnik.planner_backend.exception.ForbiddenException;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.mapper.ChatMessageMapper;
import com.mcesnik.planner_backend.model.ChatMessage;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ChatMessageRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.ChatMessagePageResponse;
import com.mcesnik.planner_backend.responses.ChatMessageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserTripRepository userTripRepository;
    private final TripAuthorizationService authorizationService;
    private final ChatMessageMapper chatMessageMapper;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       UserTripRepository userTripRepository,
                       TripAuthorizationService authorizationService,
                       ChatMessageMapper chatMessageMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.userTripRepository = userTripRepository;
        this.authorizationService = authorizationService;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponse getMessages(Long tripId, Long before, int limit, User currentUser) {
        authorizationService.validateMembership(tripId, currentUser);

        // Fetch one extra row so we can tell whether older messages still exist.
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<ChatMessage> rows = (before == null)
                ? chatMessageRepository.findByTripIdOrderByIdDesc(tripId, pageable)
                : chatMessageRepository.findByTripIdAndIdLessThanOrderByIdDesc(tripId, before, pageable);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        // Rows come newest -> oldest; the client renders oldest -> newest.
        Collections.reverse(rows);

        List<ChatMessageResponse> content = rows.stream()
                .map(chatMessageMapper::toResponse)
                .toList();

        return ChatMessagePageResponse.builder()
                .content(content)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long tripId, ChatMessageRequestDTO dto, User currentUser) {
        UserTrip membership = authorizationService.validateMembership(tripId, currentUser);

        ChatMessage message = ChatMessage.builder()
                .trip(membership.getTrip())
                .sender(currentUser)
                .content(dto.getContent())
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        return chatMessageMapper.toResponse(saved);
    }

    @Transactional
    public ChatMessageResponse editMessage(Long tripId, Long messageId, ChatMessageRequestDTO dto, User currentUser) {
        authorizationService.validateMembership(tripId, currentUser);
        ChatMessage message = findOwnMessageOrThrow(tripId, messageId, currentUser);

        message.setContent(dto.getContent());
        message.setEdited(true);

        ChatMessage saved = chatMessageRepository.save(message);
        return chatMessageMapper.toResponse(saved);
    }

    @Transactional
    public void deleteMessage(Long tripId, Long messageId, User currentUser) {
        authorizationService.validateMembership(tripId, currentUser);
        ChatMessage message = findOwnMessageOrThrow(tripId, messageId, currentUser);
        chatMessageRepository.delete(message);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long tripId, User currentUser) {
        UserTrip membership = authorizationService.validateMembership(tripId, currentUser);
        long lastRead = membership.getLastReadMessageId() == null ? 0L : membership.getLastReadMessageId();
        return chatMessageRepository.countByTripIdAndIdGreaterThanAndSenderIdNot(tripId, lastRead, currentUser.getId());
    }

    @Transactional
    public void markRead(Long tripId, User currentUser) {
        UserTrip membership = authorizationService.validateMembership(tripId, currentUser);
        chatMessageRepository.findTopByTripIdOrderByIdDesc(tripId)
                .ifPresent(latest -> membership.setLastReadMessageId(latest.getId()));
        userTripRepository.save(membership);
    }

    private ChatMessage findOwnMessageOrThrow(Long tripId, Long messageId, User currentUser) {
        ChatMessage message = chatMessageRepository.findByIdAndTripId(messageId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You can only modify your own messages");
        }
        return message;
    }
}
