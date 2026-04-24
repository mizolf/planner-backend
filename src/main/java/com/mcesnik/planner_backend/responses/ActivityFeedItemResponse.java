package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFeedItemResponse {
    private Long id;
    private TripEventType eventType;
    private TripEventEntityType entityType;
    private Long entityId;
    private String entityName;
    private Long actorId;
    private String actorName;
    private Instant createdAt;
    private List<FieldChange> changes;
}
