package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.TripInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TripInviteRepository extends JpaRepository<TripInvite, Long> {

    Optional<TripInvite> findByToken(String token);

    Optional<TripInvite> findByTripIdAndEmailAndStatus(Long tripId, String email, InviteStatus status);

    List<TripInvite> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<TripInvite> findByTripIdAndStatusOrderByCreatedAtDesc(Long tripId, InviteStatus status);

    List<TripInvite> findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, InviteStatus status);

    List<TripInvite> findAllByStatusAndExpiresAtBefore(InviteStatus status, Instant cutoff);
}
