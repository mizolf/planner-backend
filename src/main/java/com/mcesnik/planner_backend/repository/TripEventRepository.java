package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.TripEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<TripEvent> findByTripId(Long tripId, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Page<TripEvent> findByTripIdAndEntityType(Long tripId, TripEventEntityType entityType, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Page<TripEvent> findByTripIdAndEntityTypeNot(Long tripId, TripEventEntityType entityType, Pageable pageable);

    long deleteByCreatedAtBefore(Instant threshold);
}
