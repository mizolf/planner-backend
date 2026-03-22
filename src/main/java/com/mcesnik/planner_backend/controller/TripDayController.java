package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateTripDayDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDayDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.TripDayResponse;
import com.mcesnik.planner_backend.service.TripDayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/trips/{tripId}/days")
@RestController
public class TripDayController {
    private final TripDayService tripDayService;

    public TripDayController(TripDayService tripDayService) {
        this.tripDayService = tripDayService;
    }

    @PostMapping
    public ResponseEntity<TripDayResponse> addDay(@PathVariable Long tripId, @RequestBody CreateTripDayDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripDayService.addDay(tripId, dto, getCurrentUser()));
    }

    @PutMapping("/{dayId}")
    public ResponseEntity<TripDayResponse> updateDay(@PathVariable Long tripId, @PathVariable Long dayId, @RequestBody UpdateTripDayDTO dto) {
        return ResponseEntity.ok(tripDayService.updateDay(tripId, dayId, dto, getCurrentUser()));
    }

    @DeleteMapping("/{dayId}")
    public ResponseEntity<Void> deleteDay(@PathVariable Long tripId, @PathVariable Long dayId) {
        tripDayService.deleteDay(tripId, dayId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
