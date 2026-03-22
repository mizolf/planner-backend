package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripDayRepository extends JpaRepository<TripDay, Long> {
    List<TripDay> findByTripIdOrderByDayNumberAsc(Long tripId);
}
