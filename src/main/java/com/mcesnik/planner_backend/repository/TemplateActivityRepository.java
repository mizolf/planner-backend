package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.TemplateActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateActivityRepository extends JpaRepository<TemplateActivity, Long> {
}
