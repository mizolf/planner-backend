package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // First page: newest messages of a trip (Pageable supplies the limit).
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByTripIdOrderByIdDesc(Long tripId, Pageable pageable);

    // Older page: messages of a trip with id below the cursor.
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByTripIdAndIdLessThanOrderByIdDesc(Long tripId, Long beforeId, Pageable pageable);

    // Single message scoped to its trip (edit/delete lookup).
    @EntityGraph(attributePaths = "sender")
    Optional<ChatMessage> findByIdAndTripId(Long id, Long tripId);

    // Unread count: messages newer than lastRead that the caller did not send.
    long countByTripIdAndIdGreaterThanAndSenderIdNot(Long tripId, Long lastReadId, Long senderId);

    // Newest message of a trip (for mark-read).
    Optional<ChatMessage> findTopByTripIdOrderByIdDesc(Long tripId);
}
