package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateInviteDTO;
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.exception.ForbiddenException;
import com.mcesnik.planner_backend.exception.InviteConflictException;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.mapper.TripInviteMapper;
import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripInvite;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.TripInviteRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TripInviteService {

    private final TripInviteRepository inviteRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final UserTripRepository userTripRepository;
    private final TripAuthorizationService authorizationService;
    private final TripMemberService memberService;
    private final TripInviteMapper inviteMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final long expiryDays;

    public TripInviteService(
            TripInviteRepository inviteRepository,
            TripRepository tripRepository,
            UserRepository userRepository,
            UserTripRepository userTripRepository,
            TripAuthorizationService authorizationService,
            TripMemberService memberService,
            TripInviteMapper inviteMapper,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.invite.expiry-days:7}") long expiryDays) {
        this.inviteRepository = inviteRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.userTripRepository = userTripRepository;
        this.authorizationService = authorizationService;
        this.memberService = memberService;
        this.inviteMapper = inviteMapper;
        this.eventPublisher = eventPublisher;
        this.expiryDays = expiryDays;
    }

    @Transactional
    public TripInviteResponse createInvite(Long tripId, CreateInviteDTO dto, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        String email = dto.getEmail().toLowerCase(Locale.ROOT);

        if (email.equalsIgnoreCase(currentUser.getEmail())) {
            throw new InviteConflictException(InviteConflictException.Code.SELF_INVITE, "You cannot invite yourself");
        }

        User targetUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with this email is not registered"));

        if (userTripRepository.existsByUserIdAndTripId(targetUser.getId(), tripId)) {
            throw new InviteConflictException(InviteConflictException.Code.ALREADY_MEMBER, "User is already a member of this trip");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiryDays, ChronoUnit.DAYS);

        TripInvite invite = inviteRepository
                .findByTripIdAndEmailAndStatus(tripId, email, InviteStatus.PENDING)
                .map(existing -> {
                    existing.setToken(UUID.randomUUID().toString());
                    existing.setCreatedAt(now);
                    existing.setExpiresAt(expiresAt);
                    existing.setInviter(currentUser);
                    existing.setRole(dto.getRole());
                    return existing;
                })
                .orElseGet(() -> TripInvite.builder()
                        .trip(trip)
                        .inviter(currentUser)
                        .email(email)
                        .token(UUID.randomUUID().toString())
                        .role(dto.getRole())
                        .status(InviteStatus.PENDING)
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .build());

        invite = inviteRepository.save(invite);

        eventPublisher.publishEvent(new TripEventRecorded(
                trip, currentUser,
                TripEventType.INVITE_SENT, TripEventEntityType.INVITE,
                invite.getId(), invite.getEmail(), null));

        return inviteMapper.toOwnerResponse(invite);
    }

    @Transactional(readOnly = true)
    public List<TripInviteResponse> listForTrip(Long tripId, InviteStatus statusFilter, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        List<TripInvite> invites = (statusFilter == null)
                ? inviteRepository.findByTripIdOrderByCreatedAtDesc(tripId)
                : inviteRepository.findByTripIdAndStatusOrderByCreatedAtDesc(tripId, statusFilter);

        return invites.stream().map(inviteMapper::toOwnerResponse).toList();
    }

    @Transactional
    public void cancelInvite(Long tripId, Long inviteId, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (!invite.getTrip().getId().equals(tripId)) {
            throw new ResourceNotFoundException("Invite not found");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException(InviteConflictException.Code.INVITE_NOT_PENDING, "Only pending invites can be cancelled");
        }

        invite.setStatus(InviteStatus.CANCELLED);
        invite.setRevokedBy(currentUser);
        invite.setRespondedAt(Instant.now());
        inviteRepository.save(invite);

        eventPublisher.publishEvent(new TripEventRecorded(
                invite.getTrip(), currentUser,
                TripEventType.INVITE_CANCELLED, TripEventEntityType.INVITE,
                invite.getId(), invite.getEmail(), null));
    }

    @Transactional
    public List<MyInviteResponse> listMyInvites(User currentUser) {
        List<TripInvite> pending = inviteRepository
                .findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(currentUser.getEmail(), InviteStatus.PENDING);

        Instant now = Instant.now();
        List<TripInvite> stillPending = new ArrayList<>();

        for (TripInvite invite : pending) {
            if (invite.getExpiresAt().isBefore(now)) {
                invite.setStatus(InviteStatus.EXPIRED);
                invite.setRespondedAt(now);
                inviteRepository.save(invite);
                eventPublisher.publishEvent(new TripEventRecorded(
                        invite.getTrip(), null,
                        TripEventType.INVITE_EXPIRED, TripEventEntityType.INVITE,
                        invite.getId(), invite.getEmail(), null));
            } else {
                stillPending.add(invite);
            }
        }

        return stillPending.stream().map(inviteMapper::toMyInviteResponse).toList();
    }

    @Transactional
    public void acceptInvite(Long inviteId, User currentUser) {
        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException(InviteConflictException.Code.INVITE_NOT_PENDING, "Invite is no longer pending");
        }

        Instant now = Instant.now();
        if (invite.getExpiresAt().isBefore(now)) {
            invite.setStatus(InviteStatus.EXPIRED);
            invite.setRespondedAt(now);
            inviteRepository.save(invite);
            eventPublisher.publishEvent(new TripEventRecorded(
                    invite.getTrip(), null,
                    TripEventType.INVITE_EXPIRED, TripEventEntityType.INVITE,
                    invite.getId(), invite.getEmail(), null));
            throw new InviteConflictException(InviteConflictException.Code.INVITE_EXPIRED, "Invite has expired");
        }

        if (!invite.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new ForbiddenException("This invite is not for you");
        }

        if (!currentUser.isEnabled()) {
            throw new ForbiddenException("Verify your email before accepting invites");
        }

        boolean alreadyMember = userTripRepository
                .existsByUserIdAndTripId(currentUser.getId(), invite.getTrip().getId());

        if (!alreadyMember) {
            memberService.materializeMembership(invite.getTrip(), currentUser, invite.getRole(), currentUser);
        }

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setRespondedAt(now);
        invite.setAcceptedUser(currentUser);
        inviteRepository.save(invite);

        eventPublisher.publishEvent(new TripEventRecorded(
                invite.getTrip(), currentUser,
                TripEventType.INVITE_ACCEPTED, TripEventEntityType.INVITE,
                invite.getId(), invite.getEmail(), null));
    }

    @Transactional
    public void declineInvite(Long inviteId, User currentUser) {
        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException(InviteConflictException.Code.INVITE_NOT_PENDING, "Invite is no longer pending");
        }

        if (!invite.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new ForbiddenException("This invite is not for you");
        }

        invite.setStatus(InviteStatus.DECLINED);
        invite.setRespondedAt(Instant.now());
        inviteRepository.save(invite);

        eventPublisher.publishEvent(new TripEventRecorded(
                invite.getTrip(), currentUser,
                TripEventType.INVITE_DECLINED, TripEventEntityType.INVITE,
                invite.getId(), invite.getEmail(), null));
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void markExpiredInvites() {
        Instant now = Instant.now();
        List<TripInvite> expired = inviteRepository
                .findAllByStatusAndExpiresAtBefore(InviteStatus.PENDING, now);

        for (TripInvite invite : expired) {
            invite.setStatus(InviteStatus.EXPIRED);
            invite.setRespondedAt(now);
            inviteRepository.save(invite);

            eventPublisher.publishEvent(new TripEventRecorded(
                    invite.getTrip(), null,
                    TripEventType.INVITE_EXPIRED, TripEventEntityType.INVITE,
                    invite.getId(), invite.getEmail(), null));
        }
    }
}
