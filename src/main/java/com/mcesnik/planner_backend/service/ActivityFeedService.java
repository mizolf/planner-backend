package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.mapper.ActivityFeedMapper;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.TripEvent;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.TripEventRepository;
import com.mcesnik.planner_backend.responses.ActivityFeedItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class ActivityFeedService {

    private static final Logger log = LoggerFactory.getLogger(ActivityFeedService.class);

    private final TripEventRepository repository;
    private final ActivityFeedMapper mapper;
    private final TripAuthorizationService authorizationService;

    @Value("${activity.feed.retention-months:3}")
    private int retentionMonths;

    public ActivityFeedService(TripEventRepository repository, ActivityFeedMapper mapper, TripAuthorizationService authorizationService) {
        this.repository = repository;
        this.mapper = mapper;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public Page<ActivityFeedItemResponse> getFeed(Long tripId, User currentUser, TripEventEntityType entityType, Pageable pageable) {
        UserTrip membership = authorizationService.validateMembership(tripId, currentUser);
        boolean isViewer = membership.getRole() == TripRole.VIEWER;

        if (isViewer && entityType == TripEventEntityType.MEMBER) {
            throw new RuntimeException("You do not have permission to view member events");
        }

        Page<TripEvent> events;
        if (entityType != null) {
            events = repository.findByTripIdAndEntityType(tripId, entityType, pageable);
        } else if (isViewer) {
            events = repository.findByTripIdAndEntityTypeNot(tripId, TripEventEntityType.MEMBER, pageable);
        } else {
            events = repository.findByTripId(tripId, pageable);
        }

        return events.map(mapper::toResponse);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldEvents() {
        Instant threshold = Instant.now().atOffset(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        long deleted = repository.deleteByCreatedAtBefore(threshold);
        log.info("Purged {} trip events older than {}", deleted, threshold);
    }
}
