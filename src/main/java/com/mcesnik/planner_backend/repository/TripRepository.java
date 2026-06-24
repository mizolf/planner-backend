package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Enums.TripVisibility;
import com.mcesnik.planner_backend.model.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    Page<Trip> findByVisibility(TripVisibility visibility, Pageable pageable);

    @Query("""
            SELECT t FROM Trip t
            WHERE t.visibility = :visibility
              AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(t.destination) LIKE LOWER(CONCAT('%', :term, '%')))
            """)
    Page<Trip> searchByVisibility(@Param("visibility") TripVisibility visibility,
                                  @Param("term") String term,
                                  Pageable pageable);

    @Query("""
            SELECT (COUNT(ut) > 0)
            FROM UserTrip ut
            WHERE ut.user.id = :userId
              AND ut.trip.startDate <= :endDate
              AND ut.trip.endDate >= :startDate
            """)
    boolean existsOverlappingTripForUser(@Param("userId") Long userId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT (COUNT(ut) > 0)
            FROM UserTrip ut
            WHERE ut.user.id = :userId
              AND ut.trip.id <> :tripId
              AND ut.trip.startDate <= :endDate
              AND ut.trip.endDate >= :startDate
            """)
    boolean existsOverlappingTripForUserExcludingTrip(@Param("userId") Long userId,
                                                      @Param("tripId") Long tripId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);
}
