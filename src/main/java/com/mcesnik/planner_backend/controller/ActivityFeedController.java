package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.ActivityFeedItemResponse;
import com.mcesnik.planner_backend.service.ActivityFeedService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trips/{tripId}/activity-feed")
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    public ActivityFeedController(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    @GetMapping
    public ResponseEntity<Page<ActivityFeedItemResponse>> getFeed(
            @PathVariable Long tripId,
            @RequestParam(required = false) TripEventEntityType entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(activityFeedService.getFeed(tripId, getCurrentUser(), entityType, pageable));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
