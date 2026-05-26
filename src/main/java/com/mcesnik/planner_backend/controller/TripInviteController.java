package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateInviteDTO;
import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import com.mcesnik.planner_backend.service.TripInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TripInviteController {

    private final TripInviteService inviteService;

    public TripInviteController(TripInviteService inviteService) {
        this.inviteService = inviteService;
    }

    // ───────────────────── Owner-facing ─────────────────────

    @PostMapping("/trips/{tripId}/invites")
    public ResponseEntity<TripInviteResponse> create(
            @PathVariable Long tripId,
            @Valid @RequestBody CreateInviteDTO dto) {
        TripInviteResponse response = inviteService.createInvite(tripId, dto, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/trips/{tripId}/invites")
    public ResponseEntity<List<TripInviteResponse>> listForTrip(
            @PathVariable Long tripId,
            @RequestParam(required = false) InviteStatus status) {
        return ResponseEntity.ok(inviteService.listForTrip(tripId, status, getCurrentUser()));
    }

    @DeleteMapping("/trips/{tripId}/invites/{inviteId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long tripId,
            @PathVariable Long inviteId) {
        inviteService.cancelInvite(tripId, inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    // ───────────────────── Invitee-facing ─────────────────────

    @GetMapping("/me/invites")
    public ResponseEntity<List<MyInviteResponse>> listMine() {
        return ResponseEntity.ok(inviteService.listMyInvites(getCurrentUser()));
    }

    @PostMapping("/me/invites/{inviteId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long inviteId) {
        inviteService.acceptInvite(inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/invites/{inviteId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long inviteId) {
        inviteService.declineInvite(inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
