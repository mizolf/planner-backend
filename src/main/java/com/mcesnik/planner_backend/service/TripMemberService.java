package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.AddTripMemberDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripMemberDTO;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.mapper.UserTripMapper;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TripMemberService {
    private final UserTripRepository userTripRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final UserTripMapper userTripMapper;
    private final TripAuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public TripMemberService(UserTripRepository userTripRepository, UserRepository userRepository, TripRepository tripRepository, UserTripMapper userTripMapper, TripAuthorizationService authorizationService, ApplicationEventPublisher eventPublisher) {
        this.userTripRepository = userTripRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.userTripMapper = userTripMapper;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
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

        eventPublisher.publishEvent(new TripEventRecorded(
                trip, currentUser,
                TripEventType.MEMBER_ADDED, TripEventEntityType.MEMBER,
                targetUser.getId(), targetUser.getFullName(), null));

        return userTripMapper.toResponse(userTrip);
    }

    @Transactional
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

        TripRole oldRole = userTrip.getRole();
        TripRole newRole = dto.getRole();

        userTrip.setRole(newRole);
        userTrip = userTripRepository.save(userTrip);

        if (oldRole != newRole) {
            User targetUser = userTrip.getUser();
            eventPublisher.publishEvent(new TripEventRecorded(
                    userTrip.getTrip(), currentUser,
                    TripEventType.MEMBER_ROLE_CHANGED, TripEventEntityType.MEMBER,
                    targetUser.getId(), targetUser.getFullName(),
                    List.of(new FieldChange("role", oldRole.name(), newRole.name()))));
        }

        return userTripMapper.toResponse(userTrip);
    }

    @Transactional
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

        User targetUser = userTrip.getUser();
        eventPublisher.publishEvent(new TripEventRecorded(
                userTrip.getTrip(), currentUser,
                TripEventType.MEMBER_REMOVED, TripEventEntityType.MEMBER,
                targetUser.getId(), targetUser.getFullName(), null));

        userTripRepository.delete(userTrip);
    }
}
