package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

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
