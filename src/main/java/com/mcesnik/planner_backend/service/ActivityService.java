package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateActivityDTO;
import com.mcesnik.planner_backend.DTO.UpdateActivityDTO;
import com.mcesnik.planner_backend.mapper.ActivityMapper;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.responses.ActivityResponse;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final TripDayRepository tripDayRepository;
    private final ActivityMapper activityMapper;
    private final TripAuthorizationService authorizationService;

    public ActivityService(ActivityRepository activityRepository, TripDayRepository tripDayRepository, ActivityMapper activityMapper, TripAuthorizationService authorizationService) {
        this.activityRepository = activityRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityMapper = activityMapper;
        this.authorizationService = authorizationService;
    }

    public ActivityResponse addActivity(Long tripId, Long dayId, CreateActivityDTO dto, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);

        var activity = activityMapper.toEntity(dto);
        activity.setTripDay(day);

        activity = activityRepository.save(activity);

        return activityMapper.toResponse(activity);
    }

    public ActivityResponse updateActivity(Long tripId, Long dayId, Long activityId, UpdateActivityDTO dto, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        findDayOrThrow(dayId, tripId);
        var activity = findActivityOrThrow(activityId, dayId);

        activityMapper.updateEntity(activity, dto);
        activity = activityRepository.save(activity);

        return activityMapper.toResponse(activity);
    }

    public void deleteActivity(Long tripId, Long dayId, Long activityId, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        findDayOrThrow(dayId, tripId);
        var activity = findActivityOrThrow(activityId, dayId);

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
