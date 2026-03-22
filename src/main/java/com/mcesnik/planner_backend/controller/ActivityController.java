package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateActivityDTO;
import com.mcesnik.planner_backend.DTO.UpdateActivityDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.ActivityResponse;
import com.mcesnik.planner_backend.service.ActivityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/trips/{tripId}/days/{dayId}/activities")
@RestController
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> addActivity(@PathVariable Long tripId, @PathVariable Long dayId, @RequestBody CreateActivityDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.addActivity(tripId, dayId, dto, getCurrentUser()));
    }

    @PutMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> updateActivity(@PathVariable Long tripId, @PathVariable Long dayId, @PathVariable Long activityId, @RequestBody UpdateActivityDTO dto) {
        return ResponseEntity.ok(activityService.updateActivity(tripId, dayId, activityId, dto, getCurrentUser()));
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long tripId, @PathVariable Long dayId, @PathVariable Long activityId) {
        activityService.deleteActivity(tripId, dayId, activityId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
