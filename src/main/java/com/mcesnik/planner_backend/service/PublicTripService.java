package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.mapper.PublicTripMapper;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Enums.TripVisibility;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.PublicTripActivityResponse;
import com.mcesnik.planner_backend.responses.PublicTripDayResponse;
import com.mcesnik.planner_backend.responses.PublicTripDetailResponse;
import com.mcesnik.planner_backend.responses.PublicTripSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PublicTripService {

    private final TripRepository tripRepository;
    private final UserTripRepository userTripRepository;
    private final TripDayRepository tripDayRepository;
    private final ActivityRepository activityRepository;
    private final PublicTripMapper publicTripMapper;

    public PublicTripService(TripRepository tripRepository, UserTripRepository userTripRepository,
                             TripDayRepository tripDayRepository, ActivityRepository activityRepository,
                             PublicTripMapper publicTripMapper) {
        this.tripRepository = tripRepository;
        this.userTripRepository = userTripRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityRepository = activityRepository;
        this.publicTripMapper = publicTripMapper;
    }

    @Transactional(readOnly = true)
    public Page<PublicTripSummaryResponse> listPublic(String search, Pageable pageable) {
        Page<Trip> trips = (search == null || search.isBlank())
                ? tripRepository.findByVisibility(TripVisibility.PUBLIC, pageable)
                : tripRepository.searchByVisibility(TripVisibility.PUBLIC, search.trim(), pageable);

        List<Long> tripIds = trips.getContent().stream().map(Trip::getId).toList();

        Map<Long, List<UserTrip>> membersByTripId = tripIds.isEmpty()
                ? Collections.emptyMap()
                : userTripRepository.findByTripIdIn(tripIds).stream()
                        .collect(Collectors.groupingBy(ut -> ut.getTrip().getId()));

        return trips.map(trip -> {
            List<UserTrip> members = membersByTripId.getOrDefault(trip.getId(), Collections.emptyList());
            return publicTripMapper.toSummary(trip, ownerDisplayName(members), members.size());
        });
    }

    @Transactional(readOnly = true)
    public PublicTripDetailResponse getPublicDetail(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .filter(t -> t.getVisibility() == TripVisibility.PUBLIC)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        List<TripDay> tripDays = tripDayRepository.findByTripIdOrderByDayNumberAsc(tripId);
        List<Long> dayIds = tripDays.stream().map(TripDay::getId).toList();

        Map<Long, List<Activity>> activitiesByDayId = dayIds.isEmpty()
                ? Collections.emptyMap()
                : activityRepository.findByTripDayIdInOrderByStartTimeAsc(dayIds).stream()
                        .collect(Collectors.groupingBy(activity -> activity.getTripDay().getId()));

        List<PublicTripDayResponse> days = tripDays.stream()
                .map(day -> {
                    List<PublicTripActivityResponse> activities =
                            activitiesByDayId.getOrDefault(day.getId(), Collections.emptyList()).stream()
                                    .map(publicTripMapper::toActivityResponse)
                                    .toList();
                    return publicTripMapper.toDayResponse(day, activities);
                })
                .toList();

        List<UserTrip> members = userTripRepository.findByTripId(tripId);
        return publicTripMapper.toDetail(trip, days, ownerDisplayName(members), members.size());
    }

    private String ownerDisplayName(List<UserTrip> members) {
        return members.stream()
                .filter(ut -> ut.getRole() == TripRole.OWNER)
                .map(ut -> ut.getUser().getFullName())
                .findFirst()
                .orElse(null);
    }
}
