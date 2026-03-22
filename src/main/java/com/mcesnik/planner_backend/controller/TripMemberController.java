package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.AddTripMemberDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripMemberDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import com.mcesnik.planner_backend.service.TripMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/trips/{tripId}/members")
@RestController
public class TripMemberController {
    private final TripMemberService tripMemberService;

    public TripMemberController(TripMemberService tripMemberService) {
        this.tripMemberService = tripMemberService;
    }

    @PostMapping
    public ResponseEntity<TripMemberResponse> addMember(@PathVariable Long tripId, @RequestBody AddTripMemberDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMemberService.addMember(tripId, dto, getCurrentUser()));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<TripMemberResponse> updateMemberRole(@PathVariable Long tripId, @PathVariable Long userId, @RequestBody UpdateTripMemberDTO dto) {
        return ResponseEntity.ok(tripMemberService.updateMemberRole(tripId, userId, dto, getCurrentUser()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long tripId, @PathVariable Long userId) {
        tripMemberService.removeMember(tripId, userId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
