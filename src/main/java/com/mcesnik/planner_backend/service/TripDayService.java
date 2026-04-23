package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateTripDayDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDayDTO;
import com.mcesnik.planner_backend.event.ChangeDetector;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.mapper.ActivityMapper;
import com.mcesnik.planner_backend.mapper.TripDayMapper;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.exception.InvalidDateRangeException;
import com.mcesnik.planner_backend.responses.TripDayResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TripDayService {
    private final TripDayRepository tripDayRepository;
    private final TripRepository tripRepository;
    private final TripDayMapper tripDayMapper;
    private final ActivityMapper activityMapper;
    private final ActivityRepository activityRepository;
    private final TripAuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChangeDetector changeDetector;

    public TripDayService(TripDayRepository tripDayRepository, TripRepository tripRepository, TripDayMapper tripDayMapper, ActivityMapper activityMapper, ActivityRepository activityRepository, TripAuthorizationService authorizationService, ApplicationEventPublisher eventPublisher, ChangeDetector changeDetector) {
        this.tripDayRepository = tripDayRepository;
        this.tripRepository = tripRepository;
        this.tripDayMapper = tripDayMapper;
        this.activityMapper = activityMapper;
        this.activityRepository = activityRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.changeDetector = changeDetector;
    }

    @Transactional
    public TripDayResponse addDay(Long tripId, CreateTripDayDTO request, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        var day = tripDayMapper.toEntity(request);
        day.setTrip(trip);

        if (day.getDate() != null && trip.getStartDate() != null && trip.getEndDate() != null) {
            if (day.getDate().isBefore(trip.getStartDate()) || day.getDate().isAfter(trip.getEndDate())) {
                throw new InvalidDateRangeException("Day date must be within the trip date range");
            }
        }

        day = tripDayRepository.save(day);

        eventPublisher.publishEvent(new TripEventRecorded(
                trip, currentUser,
                TripEventType.DAY_ADDED, TripEventEntityType.TRIP_DAY,
                day.getId(), "Day " + day.getDayNumber(), null));

        return tripDayMapper.toResponse(day, List.of());
    }
    @Transactional
    public TripDayResponse updateDay(Long tripId, Long dayId, UpdateTripDayDTO dto, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);

        List<FieldChange> changes = changeDetector.detectDayChanges(day, dto);

        tripDayMapper.updateEntity(day, dto);

        Trip trip = day.getTrip();
        if (day.getDate() != null && trip.getStartDate() != null && trip.getEndDate() != null) {
            if (day.getDate().isBefore(trip.getStartDate()) || day.getDate().isAfter(trip.getEndDate())) {
                throw new InvalidDateRangeException("Day date must be within the trip date range");
            }
        }

        day = tripDayRepository.save(day);

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new TripEventRecorded(
                    trip, currentUser,
                    TripEventType.DAY_UPDATED, TripEventEntityType.TRIP_DAY,
                    day.getId(), "Day " + day.getDayNumber(), changes));
        }

        var activities = activityRepository.findByTripDayIdOrderByStartTimeAsc(dayId).stream()
                .map(activityMapper::toResponse)
                .toList();

        return tripDayMapper.toResponse(day, activities);
    }

    @Transactional
    public void deleteDay(Long tripId, Long dayId, User currentUser) {
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        var day = findDayOrThrow(dayId, tripId);

        eventPublisher.publishEvent(new TripEventRecorded(
                day.getTrip(), currentUser,
                TripEventType.DAY_DELETED, TripEventEntityType.TRIP_DAY,
                day.getId(), "Day " + day.getDayNumber(), null));

        tripDayRepository.delete(day);
    }

    private TripDay findDayOrThrow(Long dayId, Long tripId) {
        TripDay day = tripDayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day not found"));
        if (!day.getTrip().getId().equals(tripId)) {
            throw new RuntimeException("Day does not belong to this trip");
        }
        return day;
    }
}
