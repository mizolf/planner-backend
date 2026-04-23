package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateActivityDTO;
import com.mcesnik.planner_backend.DTO.UpdateActivityDTO;
import com.mcesnik.planner_backend.event.ChangeDetector;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.mapper.ActivityMapper;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.exception.InvalidDateRangeException;
import com.mcesnik.planner_backend.responses.ActivityResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final TripDayRepository tripDayRepository;
    private final ActivityMapper activityMapper;
    private final TripAuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChangeDetector changeDetector;

    public ActivityService(ActivityRepository activityRepository, TripDayRepository tripDayRepository, ActivityMapper activityMapper, TripAuthorizationService authorizationService, ApplicationEventPublisher eventPublisher, ChangeDetector changeDetector) {
        this.activityRepository = activityRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityMapper = activityMapper;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.changeDetector = changeDetector;
    }

    @Transactional
    public ActivityResponse addActivity(Long tripId, Long dayId, CreateActivityDTO dto, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);

        var activity = activityMapper.toEntity(dto);
        activity.setTripDay(day);

        if (activity.getStartTime() != null && activity.getEndTime() != null
                && activity.getEndTime().isBefore(activity.getStartTime())) {
            throw new InvalidDateRangeException("End time must not be before start time");
        }

        activity = activityRepository.save(activity);

        eventPublisher.publishEvent(new TripEventRecorded(
                day.getTrip(), currentUser,
                TripEventType.ACTIVITY_ADDED, TripEventEntityType.ACTIVITY,
                activity.getId(), activity.getName(), null));

        return activityMapper.toResponse(activity);
    }

    @Transactional
    public ActivityResponse updateActivity(Long tripId, Long dayId, Long activityId, UpdateActivityDTO dto, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);
        var activity = findActivityOrThrow(activityId, dayId);

        List<FieldChange> changes = changeDetector.detectActivityChanges(activity, dto);

        activityMapper.updateEntity(activity, dto);

        if (activity.getStartTime() != null && activity.getEndTime() != null
                && activity.getEndTime().isBefore(activity.getStartTime())) {
            throw new InvalidDateRangeException("End time must not be before start time");
        }

        activity = activityRepository.save(activity);

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new TripEventRecorded(
                    day.getTrip(), currentUser,
                    TripEventType.ACTIVITY_UPDATED, TripEventEntityType.ACTIVITY,
                    activity.getId(), activity.getName(), changes));
        }

        return activityMapper.toResponse(activity);
    }

    @Transactional
    public void deleteActivity(Long tripId, Long dayId, Long activityId, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);
        var activity = findActivityOrThrow(activityId, dayId);

        eventPublisher.publishEvent(new TripEventRecorded(
                day.getTrip(), currentUser,
                TripEventType.ACTIVITY_DELETED, TripEventEntityType.ACTIVITY,
                activity.getId(), activity.getName(), null));

        activityRepository.delete(activity);
    }

    private TripDay findDayOrThrow(Long dayId, Long tripId) {
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day not found"));
        if (!day.getTrip().getId().equals(tripId)) {
            throw new RuntimeException("Day does not belong to this trip");
        }
        return day;
    }

    private Activity findActivityOrThrow(Long activityId, Long dayId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
        if (!activity.getTripDay().getId().equals(dayId)) {
            throw new RuntimeException("Activity does not belong to this day");
        }
        return activity;
    }
}
