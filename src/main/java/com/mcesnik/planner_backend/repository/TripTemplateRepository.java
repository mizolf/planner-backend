package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TripTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripTemplateRepository extends JpaRepository<TripTemplate, Long> {
    Optional<TripTemplate> findBySlugAndStyleId(String slug, Long styleId);

    @Query("select t from TripTemplate t join fetch t.style s "
            + "order by s.displayOrder asc, t.displayOrder asc")
    List<TripTemplate> findAllOrderedWithStyle();
}
