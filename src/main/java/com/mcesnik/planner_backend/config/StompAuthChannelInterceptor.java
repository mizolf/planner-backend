package com.mcesnik.planner_backend.config;

import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.service.JwtService;
import com.mcesnik.planner_backend.service.TokenBlacklistService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/trips/";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserTripRepository userTripRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       UserDetailsService userDetailsService,
                                       TokenBlacklistService tokenBlacklistService,
                                       UserTripRepository userTripRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userTripRepository = userTripRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    // Mirrors JwtAuthenticationFilter: validate the bearer token and attach the User as the socket principal.
    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Missing or invalid Authorization header");
        }

        String jwt = authHeader.substring(7);
        if (tokenBlacklistService.isBlacklisted(jwt)) {
            throw new MessagingException("Token is blacklisted");
        }

        String email = jwtService.extractUsername(jwt); // validates signature + expiry on parse
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtService.isTokenValid(jwt, userDetails)) {
            throw new MessagingException("Invalid token");
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(authentication);
    }

    // Only members of a trip may subscribe to its topic.
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Long tripId = parseTripId(accessor.getDestination());
        if (tripId == null) {
            return; // not a trip topic — nothing to gate
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new MessagingException("Not authenticated");
        }

        if (!userTripRepository.existsByUserIdAndTripId(user.getId(), tripId)) {
            throw new MessagingException("Not a member of this trip");
        }
    }

    private Long parseTripId(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(destination.substring(TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
