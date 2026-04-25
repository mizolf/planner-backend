package com.mcesnik.planner_backend.mapper;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.model.TripEvent;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.ActivityFeedItemResponse;
import com.mcesnik.planner_backend.responses.DashboardActivityFeedItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityFeedMapper {

    private static final TypeReference<List<FieldChange>> FIELD_CHANGE_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ActivityFeedItemResponse toResponse(TripEvent event) {
        User actor = event.getActor();
        return ActivityFeedItemResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .entityName(event.getEntityName())
                .actorId(actor != null ? actor.getId() : null)
                .actorName(event.getActorName())
                .createdAt(event.getCreatedAt())
                .changes(deserializeChanges(event.getChanges()))
                .build();
    }

    public DashboardActivityFeedItemResponse toDashboardResponse(TripEvent event) {
        User actor = event.getActor();
        return DashboardActivityFeedItemResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .entityName(event.getEntityName())
                .actorId(actor != null ? actor.getId() : null)
                .actorName(event.getActorName())
                .createdAt(event.getCreatedAt())
                .changes(deserializeChanges(event.getChanges()))
                .tripId(event.getTrip().getId())
                .tripName(event.getTrip().getName())
                .build();
    }

    private List<FieldChange> deserializeChanges(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FIELD_CHANGE_LIST);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize field changes for trip event", e);
        }
    }
}
