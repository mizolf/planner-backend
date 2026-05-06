package com.mcesnik.planner_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "template_days")
public class TemplateDay {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    private String notes;

    @Column(nullable = true, length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_template_id", nullable = false)
    private TripTemplate tripTemplate;

    @OneToMany(mappedBy = "templateDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    private List<TemplateActivity> activities;
}
