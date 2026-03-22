package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import com.mcesnik.planner_backend.service.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/trips")
@RestController
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@RequestBody CreateTripDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.createTrip(dto, getCurrentUser()));
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> getTrips() {
        return ResponseEntity.ok(tripService.getTripsForUser(getCurrentUser()));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripDetailResponse> getTripDetail(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.getTripDetail(tripId, getCurrentUser()));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<TripResponse> updateTrip(@PathVariable Long tripId, @RequestBody UpdateTripDTO dto) {
        return ResponseEntity.ok(tripService.updateTrip(tripId, dto, getCurrentUser()));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long tripId) {
        tripService.deleteTrip(tripId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
