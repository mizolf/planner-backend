package com.mcesnik.planner_backend.model;

import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "trip_events", indexes = {
        @Index(name = "idx_trip_events_trip_id_created_at", columnList = "trip_id, created_at DESC")
})
public class TripEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User actor;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TripEventType eventType;

    @Column(name = "entity_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TripEventEntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String changes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
