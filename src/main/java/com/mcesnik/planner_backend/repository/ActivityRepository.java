package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByTripDayIdOrderByStartTimeAsc(Long tripDayId);
}
