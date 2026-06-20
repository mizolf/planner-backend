package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.service.ai.ItineraryGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/trips")
@RestController
public class ItineraryController {

    private final ItineraryGenerationService itineraryGenerationService;

    public ItineraryController(ItineraryGenerationService itineraryGenerationService) {
        this.itineraryGenerationService = itineraryGenerationService;
    }

    @PostMapping("/{tripId}/generate-itinerary")
    public ResponseEntity<TripDetailResponse> generateItinerary(@PathVariable Long tripId){
        return ResponseEntity.ok(itineraryGenerationService.generateItinerary(tripId, getCurrentUser()));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
