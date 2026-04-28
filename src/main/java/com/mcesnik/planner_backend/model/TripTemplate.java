package com.mcesnik.planner_backend.model;

import com.mcesnik.planner_backend.model.Enums.Interest;
import com.mcesnik.planner_backend.model.Enums.Season;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "trip_templates")
public class TripTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String destination;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "recommended_season", nullable = false)
    @Enumerated(EnumType.STRING)
    private Season recommendedSeason;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "estimated_budget", precision = 8, scale = 2)
    private BigDecimal estimatedBudget;

    @ElementCollection
    @CollectionTable(name = "trip_template_interests", joinColumns = @JoinColumn(name = "trip_template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest")
    private Set<Interest> interests;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_id", nullable = false)
    private TripStyle style;

    @OneToMany(mappedBy = "tripTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<TemplateDay> days;
}
