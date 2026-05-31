package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.UpdateTripMemberDTO;
import com.mcesnik.planner_backend.event.FieldChange;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.exception.MemberConflictException;
import com.mcesnik.planner_backend.mapper.UserTripMapper;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.model.UserTrip;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.TripMemberResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TripMemberService {
    private final UserTripRepository userTripRepository;
    private final UserTripMapper userTripMapper;
    private final TripAuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public TripMemberService(UserTripRepository userTripRepository, UserTripMapper userTripMapper, TripAuthorizationService authorizationService, ApplicationEventPublisher eventPublisher) {
        this.userTripRepository = userTripRepository;
        this.userTripMapper = userTripMapper;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
    }

    public UserTrip materializeMembership(Trip trip, User user, TripRole role, User actor) {
        UserTrip userTrip = UserTrip.builder()
                .user(user)
                .trip(trip)
                .role(role)
                .build();
        userTrip = userTripRepository.save(userTrip);

        eventPublisher.publishEvent(new TripEventRecorded(
                trip, actor,
                TripEventType.MEMBER_ADDED, TripEventEntityType.MEMBER,
                user.getId(), user.getFullName(), null));

        return userTrip;
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

    @Transactional
    public void leaveTrip(Long tripId, User currentUser) {
        UserTrip membership = authorizationService.validateMembership(tripId, currentUser);

        if (membership.getRole() == TripRole.OWNER) {
            throw new MemberConflictException(
                    MemberConflictException.Code.OWNER_CANNOT_LEAVE,
                    "The trip owner cannot leave; transfer ownership or delete the trip");
        }

        eventPublisher.publishEvent(new TripEventRecorded(
                membership.getTrip(), currentUser,
                TripEventType.MEMBER_LEFT, TripEventEntityType.MEMBER,
                currentUser.getId(), currentUser.getFullName(), null));

        userTripRepository.delete(membership);
    }
}
