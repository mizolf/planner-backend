package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.event.ChangeDetector;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.mapper.ActivityMapper;
import com.mcesnik.planner_backend.mapper.TripDayMapper;
import com.mcesnik.planner_backend.mapper.TripMapper;
import com.mcesnik.planner_backend.mapper.UserTripMapper;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.exception.InvalidDateRangeException;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TripService {

    private final TripMapper tripMapper;
    private final TripRepository tripRepository;
    private final UserTripRepository userTripRepository;
    private final TripDayRepository tripDayRepository;
    private final ActivityRepository activityRepository;
    private final TripAuthorizationService authorizationService;
    private final TripDayMapper tripDayMapper;
    private final ActivityMapper activityMapper;
    private final UserTripMapper userTripMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ChangeDetector changeDetector;

    public TripService(TripMapper tripMapper, TripRepository tripRepository, UserTripRepository userTripRepository, TripDayRepository tripDayRepository, ActivityRepository activityRepository, TripAuthorizationService authorizationService, TripDayMapper tripDayMapper, ActivityMapper activityMapper, UserTripMapper userTripMapper, ApplicationEventPublisher eventPublisher, ChangeDetector changeDetector) {
        this.tripMapper = tripMapper;
        this.tripRepository = tripRepository;
        this.userTripRepository = userTripRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityRepository = activityRepository;
        this.authorizationService = authorizationService;
        this.tripDayMapper = tripDayMapper;
        this.activityMapper = activityMapper;
        this.userTripMapper = userTripMapper;
        this.eventPublisher = eventPublisher;
        this.changeDetector = changeDetector;
    }


    @Transactional
    public TripResponse createTrip(CreateTripDTO request, User currentUser){
        Trip trip = tripMapper.toEntity(request);

        if (trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new InvalidDateRangeException("End date must not be before start date");
        }

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

    @Transactional(readOnly = true)
    public List<TripResponse> getTripsForUser(User currentUser){
        List<UserTrip> userTrips = userTripRepository.findByUserId(currentUser.getId());
        return userTrips.stream()
                .map(userTrip -> tripMapper.toResponse(userTrip.getTrip()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(Long tripId, User currentUser){
        authorizationService.validateMembership(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        var tripDays = tripDayRepository.findByTripIdOrderByDayNumberAsc(tripId);

        List<Long> dayIds = tripDays.stream().map(day -> day.getId()).toList();

        Map<Long, List<Activity>> activitiesByDayId = dayIds.isEmpty()
                ? Collections.emptyMap()
                : activityRepository.findByTripDayIdInOrderByStartTimeAsc(dayIds).stream()
                        .collect(Collectors.groupingBy(activity -> activity.getTripDay().getId()));

        var days = tripDays.stream()
                .map(day -> {
                    var activities = activitiesByDayId.getOrDefault(day.getId(), Collections.emptyList()).stream()
                            .map(activityMapper::toResponse)
                            .toList();
                    return tripDayMapper.toResponse(day, activities);
                })
                .toList();

        var members = userTripRepository.findByTripId(tripId).stream()
                .map(userTripMapper::toResponse)
                .toList();
        return tripMapper.toDetailResponse(trip, days, members);
    }

    @Transactional
    public TripResponse updateTrip(Long tripId, UpdateTripDTO request, User currentUser){
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        List<FieldChange> changes = changeDetector.detectTripChanges(trip, request);

        tripMapper.updateEntity(trip, request);

        if (trip.getStartDate() != null && trip.getEndDate() != null
                && trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new InvalidDateRangeException("End date must not be before start date");
        }

        trip = tripRepository.save(trip);

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new TripEventRecorded(
                    trip, currentUser,
                    TripEventType.TRIP_UPDATED, TripEventEntityType.TRIP,
                    trip.getId(), trip.getName(), changes));
        }

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public void deleteTrip(Long tripId, User currentUser){
        authorizationService.validateOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        tripRepository.delete(trip);
    }
}
