package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TripTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TripTemplateRepository extends JpaRepository<TripTemplate, Long> {
    Optional<TripTemplate> findBySlugAndStyleId(String slug, Long styleId);
}
