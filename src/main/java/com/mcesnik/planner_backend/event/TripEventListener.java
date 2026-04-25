package com.mcesnik.planner_backend.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.mcesnik.planner_backend.model.TripEvent;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.TripEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TripEventListener {

    private final TripEventRepository repository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(TripEventRecorded event) {
        User actor = event.actor();
        TripEvent tripEvent = TripEvent.builder()
                .trip(event.trip())
                .actor(actor)
                .actorName(actor != null ? actor.getFullName() : null)
                .eventType(event.eventType())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .entityName(event.entityName())
                .changes(serializeChanges(event))
                .build();

        repository.save(tripEvent);
    }

    private String serializeChanges(TripEventRecorded event) {
        if (event.changes() == null || event.changes().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(event.changes());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize field changes for trip event", e);
        }
    }
}
