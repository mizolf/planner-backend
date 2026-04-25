package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.DashboardActivityFeedItemResponse;
import com.mcesnik.planner_backend.service.ActivityFeedService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activity-feed")
public class DashboardActivityFeedController {

    private final ActivityFeedService activityFeedService;

    public DashboardActivityFeedController(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    @GetMapping
    public ResponseEntity<Page<DashboardActivityFeedItemResponse>> getDashboardFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(activityFeedService.getDashboardFeed(getCurrentUser(), pageable));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
