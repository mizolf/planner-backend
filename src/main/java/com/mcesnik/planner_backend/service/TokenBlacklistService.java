package com.mcesnik.planner_backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {
    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, Instant expiresAt) {
        blacklist.put(token, expiresAt);
    }

    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    @Scheduled(fixedRate = 900_000)
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
