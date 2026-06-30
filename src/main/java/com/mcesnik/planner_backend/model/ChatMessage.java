package com.mcesnik.planner_backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_trip_id_id", columnList = "trip_id, id DESC")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private boolean edited;

    @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;
}
