package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.ActivityRepository;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import org.springframework.stereotype.Service;

@Service
public class TripAuthorizationService {
    private final UserTripRepository userTripRepository;
    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final ActivityRepository activityRepository;

    public TripAuthorizationService(UserTripRepository userTripRepository, TripRepository tripRepository, TripDayRepository tripDayRepository, ActivityRepository activityRepository) {
        this.userTripRepository = userTripRepository;
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.activityRepository = activityRepository;
    }

    public UserTrip validateMembership(Long tripId, User currentUser){
        return userTripRepository.findByUserIdAndTripId(currentUser.getId(), tripId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this trip"));
    }

    public UserTrip validateEditorOrOwner(Long tripId, User currentUser){
        UserTrip userTrip = validateMembership(tripId, currentUser);

        if(userTrip.getRole() == TripRole.VIEWER){
            throw new RuntimeException("You do not have permission to edit this trip");
        }
        return userTrip;
    }

    public UserTrip validateOwner(Long tripId, User currentUser){
        UserTrip userTrip = validateMembership(tripId, currentUser);

        if(userTrip.getRole() != TripRole.OWNER){
            throw new RuntimeException("Only the trip owner can perform this action");
        }
        return userTrip;
    }



}
