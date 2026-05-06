package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.ApplyTripTemplateDTO;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.mapper.TemplateActivityMapper;
import com.mcesnik.planner_backend.mapper.TemplateDayMapper;
import com.mcesnik.planner_backend.mapper.TripMapper;
import com.mcesnik.planner_backend.mapper.TripStyleMapper;
import com.mcesnik.planner_backend.mapper.TripTemplateMapper;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Enums.TripStatus;
import com.mcesnik.planner_backend.model.TemplateActivity;
import com.mcesnik.planner_backend.model.TemplateDay;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.TripStyle;
import com.mcesnik.planner_backend.model.TripTemplate;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.TripStyleRepository;
import com.mcesnik.planner_backend.repository.TripTemplateRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.TemplateActivityResponse;
import com.mcesnik.planner_backend.responses.TemplateDayResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import com.mcesnik.planner_backend.responses.TripStyleDetailResponse;
import com.mcesnik.planner_backend.responses.TripStyleResponse;
import com.mcesnik.planner_backend.responses.TripTemplateDetailResponse;
import com.mcesnik.planner_backend.responses.TripTemplateSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripTemplateService {

    private final TripStyleRepository styleRepository;
    private final TripTemplateRepository templateRepository;
    private final TripRepository tripRepository;
    private final UserTripRepository userTripRepository;
    private final TripStyleMapper styleMapper;
    private final TripTemplateMapper templateMapper;
    private final TemplateDayMapper dayMapper;
    private final TemplateActivityMapper activityMapper;
    private final TripMapper tripMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TripStyleResponse> listStyles() {
        return styleRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(styleMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripStyleDetailResponse getStyle(String slug) {
        TripStyle style = styleRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip style not found: " + slug));

        List<TripTemplateSummaryResponse> templateSummaries = style.getTemplates().stream()
                .map(templateMapper::toSummary)
                .toList();

        return styleMapper.toDetail(style, templateSummaries);
    }

    @Transactional(readOnly = true)
    public TripTemplateDetailResponse getTemplate(String styleSlug, String templateSlug) {
        TripStyle style = styleRepository.findBySlug(styleSlug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip style not found: " + styleSlug));

        TripTemplate template = templateRepository.findBySlugAndStyleId(templateSlug, style.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip template '" + templateSlug + "' not found in style '" + styleSlug + "'"));

        List<TemplateDayResponse> dayResponses = template.getDays().stream()
                .map(day -> {
                    List<TemplateActivityResponse> activities = day.getActivities().stream()
                            .map(activityMapper::toResponse)
                            .toList();
                    return dayMapper.toResponse(day, activities);
                })
                .toList();

        return templateMapper.toDetail(template, dayResponses);
    }

    @Transactional
    public TripResponse applyTemplate(String styleSlug, String templateSlug,
                                      ApplyTripTemplateDTO dto, User currentUser) {
        TripStyle style = styleRepository.findBySlug(styleSlug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip style not found: " + styleSlug));

        TripTemplate template = templateRepository.findBySlugAndStyleId(templateSlug, style.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip template '" + templateSlug + "' not found in style '" + styleSlug + "'"));

        LocalDate startDate = dto.getStartDate();
        LocalDate endDate = startDate.plusDays(template.getDurationDays() - 1);

        Trip trip = Trip.builder()
                .name(dto.getName() != null ? dto.getName() : template.getName())
                .description(template.getDescription())
                .destination(template.getDestination())
                .startDate(startDate)
                .endDate(endDate)
                .status(TripStatus.PLANNING)
                .budget(dto.getBudget() != null ? dto.getBudget() : template.getEstimatedBudget())
                .interests(template.getInterests() == null
                        ? new HashSet<>()
                        : new HashSet<>(template.getInterests()))
                .build();

        List<TripDay> newDays = new ArrayList<>();
        for (TemplateDay sourceDay : template.getDays()) {
            TripDay newDay = TripDay.builder()
                    .dayNumber(sourceDay.getDayNumber())
                    .date(startDate.plusDays(sourceDay.getDayNumber() - 1))
                    .title(sourceDay.getTitle())
                    .notes(sourceDay.getNotes())
                    .trip(trip)
                    .build();

            List<Activity> dayActivities = new ArrayList<>();
            for (TemplateActivity sourceAct : sourceDay.getActivities()) {
                dayActivities.add(Activity.builder()
                        .name(sourceAct.getName())
                        .description(sourceAct.getDescription())
                        .location(sourceAct.getLocation())
                        .startTime(sourceAct.getStartTime())
                        .endTime(sourceAct.getEndTime())
                        .tripDay(newDay)
                        .build());
            }
            newDay.setActivities(dayActivities);
            newDays.add(newDay);
        }
        trip.setDays(newDays);

        trip = tripRepository.save(trip);

        UserTrip ownership = UserTrip.builder()
                .user(currentUser)
                .trip(trip)
                .role(TripRole.OWNER)
                .build();
        userTripRepository.save(ownership);

        eventPublisher.publishEvent(new TripEventRecorded(
                trip, currentUser,
                TripEventType.TRIP_CREATED, TripEventEntityType.TRIP,
                trip.getId(), trip.getName(), null));

        return tripMapper.toResponse(trip);
    }
}
