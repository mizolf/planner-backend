package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.seed.TemplateActivitySeedData;
import com.mcesnik.planner_backend.DTO.seed.TemplateDaySeedData;
import com.mcesnik.planner_backend.DTO.seed.TripStyleSeedData;
import com.mcesnik.planner_backend.DTO.seed.TripTemplateSeedData;
import com.mcesnik.planner_backend.model.TemplateActivity;
import com.mcesnik.planner_backend.model.TemplateDay;
import com.mcesnik.planner_backend.model.TripStyle;
import com.mcesnik.planner_backend.model.TripTemplate;
import com.mcesnik.planner_backend.repository.TemplateActivityRepository;
import com.mcesnik.planner_backend.repository.TemplateDayRepository;
import com.mcesnik.planner_backend.repository.TripStyleRepository;
import com.mcesnik.planner_backend.repository.TripTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripTemplateSeeder {

    private static final String TEMPLATES_RESOURCE = "classpath:templates.json";

    private final TripStyleRepository styleRepository;
    private final TripTemplateRepository templateRepository;
    private final TemplateDayRepository dayRepository;
    private final TemplateActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        Resource resource = resourceLoader.getResource(TEMPLATES_RESOURCE);
        if (!resource.exists()) {
            log.warn("templates.json not found on classpath, skipping seed");
            return;
        }

        List<TripStyleSeedData> seedData;
        try (InputStream is = resource.getInputStream()) {
            seedData = objectMapper.readValue(is, new TypeReference<>() {});
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse templates.json, skipping seed: {}", e.getMessage(), e);
            return;
        }

        try {
            activityRepository.deleteAllInBatch();
            dayRepository.deleteAllInBatch();
            templateRepository.deleteAllInBatch();
            styleRepository.deleteAllInBatch();

            List<TripStyle> styles = seedData.stream()
                    .map(this::buildStyle)
                    .toList();
            styleRepository.saveAll(styles);

            int totalTemplates = seedData.stream()
                    .mapToInt(s -> s.templates() == null ? 0 : s.templates().size())
                    .sum();
            log.info("Seeded {} styles with {} templates", seedData.size(), totalTemplates);
        } catch (RuntimeException e) {
            log.warn("Failed to seed templates, rolling back; catalogue retains previous state: {}",
                    e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private TripStyle buildStyle(TripStyleSeedData data) {
        TripStyle style = TripStyle.builder()
                .slug(data.slug())
                .name(data.name())
                .description(data.description())
                .imageUrl(data.imageUrl())
                .displayOrder(data.displayOrder())
                .build();

        List<TripTemplate> templates = data.templates() == null
                ? new ArrayList<>()
                : data.templates().stream()
                        .map(t -> buildTemplate(t, style))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        style.setTemplates(templates);
        return style;
    }

    private TripTemplate buildTemplate(TripTemplateSeedData data, TripStyle style) {
        TripTemplate template = TripTemplate.builder()
                .slug(data.slug())
                .name(data.name())
                .description(data.description())
                .destination(data.destination())
                .durationDays(data.durationDays())
                .recommendedSeason(data.recommendedSeason())
                .imageUrl(data.imageUrl())
                .estimatedBudget(data.estimatedBudget())
                .interests(data.interests())
                .displayOrder(data.displayOrder())
                .style(style)
                .build();

        List<TemplateDay> days = data.days() == null
                ? new ArrayList<>()
                : data.days().stream()
                        .map(d -> buildDay(d, template))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        template.setDays(days);
        return template;
    }

    private TemplateDay buildDay(TemplateDaySeedData data, TripTemplate template) {
        TemplateDay day = TemplateDay.builder()
                .dayNumber(data.dayNumber())
                .title(data.title())
                .notes(data.notes())
                .tripTemplate(template)
                .build();

        List<TemplateActivity> activities = data.activities() == null
                ? new ArrayList<>()
                : data.activities().stream()
                        .map(a -> buildActivity(a, day))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        day.setActivities(activities);
        return day;
    }

    private TemplateActivity buildActivity(TemplateActivitySeedData data, TemplateDay day) {
        return TemplateActivity.builder()
                .name(data.name())
                .description(data.description())
                .location(data.location())
                .startTime(data.startTime())
                .endTime(data.endTime())
                .templateDay(day)
                .build();
    }
}
