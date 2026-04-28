package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TemplateDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateDayRepository extends JpaRepository<TemplateDay, Long> {
}
