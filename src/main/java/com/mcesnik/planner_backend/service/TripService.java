package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.mapper.ActivityMapper;
import com.mcesnik.planner_backend.mapper.TripDayMapper;
import com.mcesnik.planner_backend.mapper.TripMapper;
import com.mcesnik.planner_backend.mapper.UserTripMapper;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public TripService(TripMapper tripMapper, TripRepository tripRepository, UserTripRepository userTripRepository, TripDayRepository tripDayRepository, ActivityRepository activityRepository, TripAuthorizationService authorizationService, TripDayMapper tripDayMapper, ActivityMapper activityMapper, UserTripMapper userTripMapper) {
        this.tripMapper = tripMapper;
        this.tripRepository = tripRepository;
        this.userTripRepository = userTripRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityRepository = activityRepository;
        this.authorizationService = authorizationService;
        this.tripDayMapper = tripDayMapper;
        this.activityMapper = activityMapper;
        this.userTripMapper = userTripMapper;
    }


    public TripResponse createTrip(CreateTripDTO request, User currentUser){
        Trip trip = tripMapper.toEntity(request);
        trip = tripRepository.save(trip);

        UserTrip ownership = UserTrip.builder()
                .user(currentUser)
                .trip(trip)
                .role(TripRole.OWNER)
                .build();
        userTripRepository.save(ownership);

        return tripMapper.toResponse(trip);
    }

    public List<TripResponse> getTripsForUser(User currentUser){
        List<UserTrip> userTrips = userTripRepository.findByUserId(currentUser.getId());
        return userTrips.stream()
                .map(userTrip -> tripMapper.toResponse(userTrip.getTrip()))
                .toList();
    }

    public TripDetailResponse getTripDetail(Long tripId, User currentUser){
        authorizationService.validateMembership(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        var days = tripDayRepository.findByTripIdOrderByDayNumberAsc(tripId).stream()
                .map(day -> {
                    var activities = activityRepository.findByTripDayIdOrderByStartTimeAsc(day.getId()).stream()
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

    public TripResponse updateTrip(Long tripId, UpdateTripDTO request, User currentUser){
        authorizationService.validateEditorOrOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        tripMapper.updateEntity(trip, request);
        trip = tripRepository.save(trip);

        return tripMapper.toResponse(trip);
    }

    public void deleteTrip(Long tripId, User currentUser){
        authorizationService.validateOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        tripRepository.delete(trip);
    }
}
