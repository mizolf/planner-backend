package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.UserTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTripRepository extends JpaRepository<UserTrip, Long> {
    List<UserTrip> findByUserId(Long userId);
    List<UserTrip> findByTripId(Long tripId);
    Optional<UserTrip> findByUserIdAndTripId(Long userId, Long tripId);
    boolean existsByUserIdAndTripId(Long userId, Long tripId);
    boolean existsByUserIdAndTripIdAndRole(Long userId, Long tripId, TripRole role);
}
