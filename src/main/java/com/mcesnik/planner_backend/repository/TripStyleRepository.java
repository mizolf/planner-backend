package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TripStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripStyleRepository extends JpaRepository<TripStyle, Long> {
    Optional<TripStyle> findBySlug(String slug);

    List<TripStyle> findAllByOrderByDisplayOrderAsc();
}
