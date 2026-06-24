package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CloneTripDTO;
import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripVisibilityDTO;
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
import com.mcesnik.planner_backend.model.Enums.TripVisibility;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.exception.InvalidDateRangeException;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.exception.TripConflictException;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

        if (tripRepository.existsOverlappingTripForUser(
                currentUser.getId(), trip.getStartDate(), trip.getEndDate())) {
            throw new TripConflictException(
                    TripConflictException.Code.OVERLAPPING_DATES,
                    "You already have a trip during these dates");
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

        if (trip.getStartDate() != null && trip.getEndDate() != null
                && tripRepository.existsOverlappingTripForUserExcludingTrip(
                        currentUser.getId(), tripId, trip.getStartDate(), trip.getEndDate())) {
            throw new TripConflictException(
                    TripConflictException.Code.OVERLAPPING_DATES,
                    "You already have a trip during these dates");
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

    @Transactional
    public TripResponse changeVisibility(Long tripId, UpdateTripVisibilityDTO request, User currentUser){
        authorizationService.validateOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        trip.setVisibility(request.getVisibility());
        trip = tripRepository.save(trip);

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse cloneTrip(Long tripId, CloneTripDTO request, User currentUser){
        Trip source = tripRepository.findById(tripId)
                .filter(t -> t.getVisibility() == TripVisibility.PUBLIC)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        LocalDate startDate = request.getStartDate();
        long durationDays = ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate()) + 1;
        LocalDate endDate = startDate.plusDays(durationDays - 1);

        if (tripRepository.existsOverlappingTripForUser(currentUser.getId(), startDate, endDate)) {
            throw new TripConflictException(
                    TripConflictException.Code.OVERLAPPING_DATES,
                    "You already have a trip during these dates");
        }

        Trip clone = Trip.builder()
                .name(request.getName() != null ? request.getName() : source.getName())
                .description(source.getDescription())
                .destination(source.getDestination())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .startDate(startDate)
                .endDate(endDate)
                .interests(source.getInterests() == null
                        ? new HashSet<>()
                        : new HashSet<>(source.getInterests()))
                .build();
        // budget intentionally left null; visibility defaults to PRIVATE via @Builder.Default

        List<TripDay> sourceDays = tripDayRepository.findByTripIdOrderByDayNumberAsc(tripId);
        List<Long> dayIds = sourceDays.stream().map(TripDay::getId).toList();
        Map<Long, List<Activity>> activitiesByDayId = dayIds.isEmpty()
                ? Collections.emptyMap()
                : activityRepository.findByTripDayIdInOrderByStartTimeAsc(dayIds).stream()
                        .collect(Collectors.groupingBy(activity -> activity.getTripDay().getId()));

        List<TripDay> newDays = new ArrayList<>();
        int index = 0;
        for (TripDay sourceDay : sourceDays) {
            int dayNumber = sourceDay.getDayNumber() != null ? sourceDay.getDayNumber() : index + 1;
            TripDay newDay = TripDay.builder()
                    .dayNumber(dayNumber)
                    .date(startDate.plusDays(dayNumber - 1))
                    .title(sourceDay.getTitle())
                    .notes(sourceDay.getNotes())
                    .trip(clone)
                    .build();

            List<Activity> newActivities = new ArrayList<>();
            for (Activity sourceAct : activitiesByDayId.getOrDefault(sourceDay.getId(), Collections.emptyList())) {
                newActivities.add(Activity.builder()
                        .name(sourceAct.getName())
                        .description(sourceAct.getDescription())
                        .location(sourceAct.getLocation())
                        .latitude(sourceAct.getLatitude())
                        .longitude(sourceAct.getLongitude())
                        .startTime(sourceAct.getStartTime())
                        .endTime(sourceAct.getEndTime())
                        .category(sourceAct.getCategory())
                        .tripDay(newDay)
                        .build());
                // cost intentionally left null
            }
            newDay.setActivities(newActivities);
            newDays.add(newDay);
            index++;
        }
        clone.setDays(newDays);

        clone = tripRepository.save(clone);

        UserTrip ownership = UserTrip.builder()
                .user(currentUser)
                .trip(clone)
                .role(TripRole.OWNER)
                .build();
        userTripRepository.save(ownership);

        eventPublisher.publishEvent(new TripEventRecorded(
                clone, currentUser,
                TripEventType.TRIP_CREATED, TripEventEntityType.TRIP,
                clone.getId(), clone.getName(), null));

        return tripMapper.toResponse(clone);
    }
}
