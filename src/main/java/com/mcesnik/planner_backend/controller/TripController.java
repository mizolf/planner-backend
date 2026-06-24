package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CloneTripDTO;
import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripVisibilityDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.PublicTripDetailResponse;
import com.mcesnik.planner_backend.responses.PublicTripSummaryResponse;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import com.mcesnik.planner_backend.service.PublicTripService;
import com.mcesnik.planner_backend.service.TripService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final PublicTripService publicTripService;

    public TripController(TripService tripService, PublicTripService publicTripService) {
        this.tripService = tripService;
        this.publicTripService = publicTripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@Valid @RequestBody CreateTripDTO dto) {
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
    public ResponseEntity<TripResponse> updateTrip(@PathVariable Long tripId, @Valid @RequestBody UpdateTripDTO dto) {
        return ResponseEntity.ok(tripService.updateTrip(tripId, dto, getCurrentUser()));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long tripId) {
        tripService.deleteTrip(tripId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{tripId}/visibility")
    public ResponseEntity<TripResponse> updateVisibility(@PathVariable Long tripId,
                                                         @Valid @RequestBody UpdateTripVisibilityDTO dto) {
        return ResponseEntity.ok(tripService.changeVisibility(tripId, dto, getCurrentUser()));
    }

    @PostMapping("/{tripId}/clone")
    public ResponseEntity<TripResponse> cloneTrip(@PathVariable Long tripId,
                                                  @Valid @RequestBody CloneTripDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.cloneTrip(tripId, dto, getCurrentUser()));
    }

    @GetMapping("/public")
    public ResponseEntity<Page<PublicTripSummaryResponse>> getPublicTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ResponseEntity.ok(publicTripService.listPublic(search, pageable));
    }

    @GetMapping("/public/{tripId}")
    public ResponseEntity<PublicTripDetailResponse> getPublicTripDetail(@PathVariable Long tripId) {
        return ResponseEntity.ok(publicTripService.getPublicDetail(tripId));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
