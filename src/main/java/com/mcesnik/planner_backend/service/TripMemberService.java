package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.AddTripMemberDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripMemberDTO;
import com.mcesnik.planner_backend.mapper.UserTripMapper;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import org.springframework.stereotype.Service;

@Service
public class TripMemberService {
    private final UserTripRepository userTripRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final UserTripMapper userTripMapper;
    private final TripAuthorizationService authorizationService;

    public TripMemberService(UserTripRepository userTripRepository, UserRepository userRepository, TripRepository tripRepository, UserTripMapper userTripMapper, TripAuthorizationService authorizationService) {
        this.userTripRepository = userTripRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.userTripMapper = userTripMapper;
        this.authorizationService = authorizationService;
    }

    public TripMemberResponse addMember(Long tripId, AddTripMemberDTO dto, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        if (dto.getRole() == TripRole.OWNER) {
            throw new RuntimeException("Cannot assign OWNER role to a new member");
        }

        User targetUser = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + dto.getEmail()));

        if (userTripRepository.existsByUserIdAndTripId(targetUser.getId(), tripId)) {
            throw new RuntimeException("User is already a member of this trip");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        UserTrip userTrip = UserTrip.builder()
                .user(targetUser)
                .trip(trip)
                .role(dto.getRole())
                .build();
        userTrip = userTripRepository.save(userTrip);

        return userTripMapper.toResponse(userTrip);
    }

    public TripMemberResponse updateMemberRole(Long tripId, Long userId, UpdateTripMemberDTO dto, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        if (dto.getRole() == TripRole.OWNER) {
            throw new RuntimeException("Cannot assign OWNER role");
        }

        if (currentUser.getId().equals(userId)) {
            throw new RuntimeException("Cannot change your own role");
        }

        UserTrip userTrip = userTripRepository.findByUserIdAndTripId(userId, tripId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this trip"));

        if (userTrip.getRole() == TripRole.OWNER) {
            throw new RuntimeException("Cannot change the role of the trip owner");
        }

        userTrip.setRole(dto.getRole());
        userTrip = userTripRepository.save(userTrip);

        return userTripMapper.toResponse(userTrip);
    }

    public void removeMember(Long tripId, Long userId, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        if (currentUser.getId().equals(userId)) {
            throw new RuntimeException("Cannot remove yourself from the trip");
        }

        UserTrip userTrip = userTripRepository.findByUserIdAndTripId(userId, tripId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this trip"));

        if (userTrip.getRole() == TripRole.OWNER) {
            throw new RuntimeException("Cannot remove the trip owner");
        }

        userTripRepository.delete(userTrip);
    }
}
