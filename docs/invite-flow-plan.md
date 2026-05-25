# Trip Invite Flow — Implementation Plan

> Companion plan for `invite-flow.md`. Walks the implementation in bite-size tasks, each ending in a green `./gradlew compileJava` and a focused commit. Phase 1 scope only (in-app invite list — no email).

**Goal:** Build the in-app trip invite flow (owner creates invite → invitee sees it in `/me/invites` → accept/decline).

**Architecture:** New `TripInvite` aggregate with status machine + `@Version` optimistic lock. Owner endpoints under `/trips/{tripId}/invites`, invitee endpoints under `/me/invites/{inviteId}/...`. Existing `addMember` logic extracted into a reusable `TripMemberService.materializeMembership` helper, then the old endpoint removed. Activity feed gains 5 new event types and an `INVITE` entity type.

**Tech Stack:** Spring Boot, Spring Data JPA, Spring Security (JWT), Hibernate, Lombok, Postgres. Gradle build (`./gradlew`).

**Decisions locked in (from design discussion):**
- `materializeMembership` lives as public method on `TripMemberService`
- One `InviteConflictException` for all 409 cases
- Old `POST /trips/{tripId}/members` endpoint removed in the same PR
- No tests in this PR (codebase has no service tests yet); verification is manual via the smoke test in `invite-flow.md`
- Scheduled `markExpiredInvites` lives on `TripInviteService`

**Conventions for this plan:**
- File paths are absolute relative to repo root
- Each task ends with a `./gradlew compileJava` verify + commit
- Commit messages use the existing repo style (sentence case, present tense, no Conventional Commits prefix)

---

## Task 1: Application properties + InviteStatus enum + TripEventType / TripEventEntityType extensions

**Why:** Pure additive changes — no behavior, no risk. Lays foundation for the rest.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/model/Enums/InviteStatus.java`
- Modify: `src/main/java/com/mcesnik/planner_backend/model/Enums/TripEventType.java`
- Modify: `src/main/java/com/mcesnik/planner_backend/model/Enums/TripEventEntityType.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Create InviteStatus enum**

```java
// src/main/java/com/mcesnik/planner_backend/model/Enums/InviteStatus.java
package com.mcesnik.planner_backend.model.Enums;

public enum InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    CANCELLED
}
```

- [ ] **Step 2: Add 5 new values to TripEventType**

Append to the existing enum (after `MEMBER_REMOVED`):

```java
    MEMBER_REMOVED,
    INVITE_SENT,
    INVITE_ACCEPTED,
    INVITE_DECLINED,
    INVITE_CANCELLED,
    INVITE_EXPIRED
```

- [ ] **Step 3: Add INVITE to TripEventEntityType**

```java
public enum TripEventEntityType {
    TRIP,
    TRIP_DAY,
    ACTIVITY,
    MEMBER,
    INVITE
}
```

- [ ] **Step 4: Add invite expiry config to application.properties**

Append to the existing file:

```properties
# Invite
app.invite.expiry-days=7
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/model/Enums/InviteStatus.java \
        src/main/java/com/mcesnik/planner_backend/model/Enums/TripEventType.java \
        src/main/java/com/mcesnik/planner_backend/model/Enums/TripEventEntityType.java \
        src/main/resources/application.properties
git commit -m "Add InviteStatus enum and invite-related event types"
```

---

## Task 2: TripInvite entity + repository + Trip.invites collection

**Why:** Persistence layer for invites. After this task, the schema auto-creates `trip_invites` table on next app start.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/model/TripInvite.java`
- Create: `src/main/java/com/mcesnik/planner_backend/repository/TripInviteRepository.java`
- Modify: `src/main/java/com/mcesnik/planner_backend/model/Trip.java`

- [ ] **Step 1: Create TripInvite entity**

```java
// src/main/java/com/mcesnik/planner_backend/model/TripInvite.java
package com.mcesnik.planner_backend.model;

import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "trip_invites")
public class TripInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id")
    private User inviter;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TripRole role;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InviteStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_user_id")
    private User acceptedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private User revokedBy;

    @Version
    private Long version;
}
```

- [ ] **Step 2: Create TripInviteRepository**

```java
// src/main/java/com/mcesnik/planner_backend/repository/TripInviteRepository.java
package com.mcesnik.planner_backend.repository;

import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.TripInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TripInviteRepository extends JpaRepository<TripInvite, Long> {

    Optional<TripInvite> findByToken(String token);

    Optional<TripInvite> findByTripIdAndEmailAndStatus(Long tripId, String email, InviteStatus status);

    List<TripInvite> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<TripInvite> findByTripIdAndStatusOrderByCreatedAtDesc(Long tripId, InviteStatus status);

    List<TripInvite> findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, InviteStatus status);

    List<TripInvite> findAllByStatusAndExpiresAtBefore(InviteStatus status, Instant cutoff);
}
```

- [ ] **Step 3: Add invites collection to Trip**

Modify `src/main/java/com/mcesnik/planner_backend/model/Trip.java` — append a new `@OneToMany` field below `userTrips`:

```java
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripInvite> invites;
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verify schema auto-creates (optional but recommended)**

Start the app briefly with `./gradlew bootRun`, watch logs for `create table trip_invites`, Ctrl+C to stop.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/model/TripInvite.java \
        src/main/java/com/mcesnik/planner_backend/repository/TripInviteRepository.java \
        src/main/java/com/mcesnik/planner_backend/model/Trip.java
git commit -m "Add TripInvite entity, repository and Trip.invites cascade"
```

---

## Task 3: InviteConflictException + GlobalExceptionHandler updates

**Why:** Wire 409 mapping for invite conflicts and optimistic-lock conflicts before anything throws them.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/exception/InviteConflictException.java`
- Modify: `src/main/java/com/mcesnik/planner_backend/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create the exception**

```java
// src/main/java/com/mcesnik/planner_backend/exception/InviteConflictException.java
package com.mcesnik.planner_backend.exception;

public class InviteConflictException extends RuntimeException {
    public InviteConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Add two handlers to GlobalExceptionHandler**

Add the import:
```java
import org.springframework.orm.ObjectOptimisticLockingFailureException;
```

Append the two handlers inside the class (before the closing `}`):

```java
    @ExceptionHandler(InviteConflictException.class)
    public ResponseEntity<ErrorResponse> handleInviteConflict(InviteConflictException ex) {
        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), "Concurrent modification — please retry");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/exception/InviteConflictException.java \
        src/main/java/com/mcesnik/planner_backend/exception/GlobalExceptionHandler.java
git commit -m "Add InviteConflictException and 409 handlers for invite + optimistic lock"
```

---

## Task 4: DTOs + TripInviteMapper

**Why:** Request and response shapes before the service uses them.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/DTO/CreateInviteDTO.java`
- Create: `src/main/java/com/mcesnik/planner_backend/responses/TripInviteResponse.java`
- Create: `src/main/java/com/mcesnik/planner_backend/responses/MyInviteResponse.java`
- Create: `src/main/java/com/mcesnik/planner_backend/mapper/TripInviteMapper.java`

- [ ] **Step 1: CreateInviteDTO**

```java
// src/main/java/com/mcesnik/planner_backend/DTO/CreateInviteDTO.java
package com.mcesnik.planner_backend.DTO;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInviteDTO {
    @NotBlank
    @Email
    private String email;

    @NotNull
    private TripRole role;
}
```

- [ ] **Step 2: TripInviteResponse**

```java
// src/main/java/com/mcesnik/planner_backend/responses/TripInviteResponse.java
package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.Enums.TripRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TripInviteResponse {
    private Long id;
    private String email;
    private TripRole role;
    private InviteStatus status;
    private String inviterName;
    private Instant createdAt;
    private Instant expiresAt;
}
```

- [ ] **Step 3: MyInviteResponse**

```java
// src/main/java/com/mcesnik/planner_backend/responses/MyInviteResponse.java
package com.mcesnik.planner_backend.responses;

import com.mcesnik.planner_backend.model.Enums.TripRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyInviteResponse {
    private Long id;
    private Long tripId;
    private String tripName;
    private String tripDestination;
    private String inviterName;
    private TripRole role;
    private Instant expiresAt;
}
```

- [ ] **Step 4: TripInviteMapper**

```java
// src/main/java/com/mcesnik/planner_backend/mapper/TripInviteMapper.java
package com.mcesnik.planner_backend.mapper;

import com.mcesnik.planner_backend.model.TripInvite;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import org.springframework.stereotype.Component;

@Component
public class TripInviteMapper {

    public TripInviteResponse toOwnerResponse(TripInvite invite) {
        return TripInviteResponse.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .role(invite.getRole())
                .status(invite.getStatus())
                .inviterName(invite.getInviter() != null ? invite.getInviter().getFullName() : "Deleted user")
                .createdAt(invite.getCreatedAt())
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    public MyInviteResponse toMyInviteResponse(TripInvite invite) {
        return MyInviteResponse.builder()
                .id(invite.getId())
                .tripId(invite.getTrip().getId())
                .tripName(invite.getTrip().getName())
                .tripDestination(invite.getTrip().getDestination())
                .inviterName(invite.getInviter() != null ? invite.getInviter().getFullName() : "Deleted user")
                .role(invite.getRole())
                .expiresAt(invite.getExpiresAt())
                .build();
    }
}
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/DTO/CreateInviteDTO.java \
        src/main/java/com/mcesnik/planner_backend/responses/TripInviteResponse.java \
        src/main/java/com/mcesnik/planner_backend/responses/MyInviteResponse.java \
        src/main/java/com/mcesnik/planner_backend/mapper/TripInviteMapper.java
git commit -m "Add invite DTOs and mapper"
```

---

## Task 5: Extract materializeMembership in TripMemberService

**Why:** Pull the "create UserTrip + emit MEMBER_ADDED" block out of `addMember` so `TripInviteService.acceptInvite` can reuse it. `addMember` stays functional and calls the new helper.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripMemberService.java`

- [ ] **Step 1: Add materializeMembership method**

In `TripMemberService`, add a new public method below the existing `addMember`:

```java
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
```

- [ ] **Step 2: Refactor addMember to call materializeMembership**

Replace the body of `addMember` (lines ~42-73) with a call to the helper. The new method:

```java
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

        UserTrip userTrip = materializeMembership(trip, targetUser, dto.getRole(), currentUser);
        return userTripMapper.toResponse(userTrip);
    }
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripMemberService.java
git commit -m "Extract materializeMembership helper from TripMemberService.addMember"
```

---

## Task 6: Remove the legacy `POST /trips/{tripId}/members` endpoint

**Why:** Consent flow is now the only path to membership. The direct add endpoint and its service method are deleted.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/controller/TripMemberController.java`
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripMemberService.java`
- Possibly delete: `src/main/java/com/mcesnik/planner_backend/DTO/AddTripMemberDTO.java` (only if no other usages)

- [ ] **Step 1: Remove `@PostMapping addMember` from TripMemberController**

Delete this block from `TripMemberController.java`:

```java
    @PostMapping
    public ResponseEntity<TripMemberResponse> addMember(@PathVariable Long tripId, @Valid @RequestBody AddTripMemberDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripMemberService.addMember(tripId, dto, getCurrentUser()));
    }
```

Also remove the now-unused imports:
```java
import com.mcesnik.planner_backend.DTO.AddTripMemberDTO;
import org.springframework.http.HttpStatus;
```

- [ ] **Step 2: Remove `addMember` method from TripMemberService**

Delete the `addMember` method (the one you just refactored in Task 5) from `TripMemberService.java`. Keep `materializeMembership`, `updateMemberRole`, and `removeMember`.

Also remove the now-unused import:
```java
import com.mcesnik.planner_backend.DTO.AddTripMemberDTO;
```

- [ ] **Step 3: Check if AddTripMemberDTO is used elsewhere**

```bash
grep -rn "AddTripMemberDTO" src/
```

If no results (other than the deleted lines), delete the file:
```bash
git rm src/main/java/com/mcesnik/planner_backend/DTO/AddTripMemberDTO.java
```

If it is still used somewhere unexpected, leave the file alone and revisit.

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/controller/TripMemberController.java \
        src/main/java/com/mcesnik/planner_backend/service/TripMemberService.java
# Plus the deletion if applicable
git commit -m "Remove direct addMember endpoint in favour of invite flow"
```

---

## Task 7: TripInviteService scaffold

**Why:** Lay down the class with all dependencies and empty method stubs so subsequent tasks each fill in one method at a time. Keeps each commit reviewable.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Create the service with constructor injection and stubbed methods**

```java
// src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateInviteDTO;
import com.mcesnik.planner_backend.mapper.TripInviteMapper;
import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.TripInviteRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.repository.UserRepository;
import com.mcesnik.planner_backend.repository.UserTripRepository;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Transactional(readOnly = true)
    public List<TripInviteResponse> listForTrip(Long tripId, InviteStatus statusFilter, User currentUser) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Transactional
    public void cancelInvite(Long tripId, Long inviteId, User currentUser) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Transactional
    public List<MyInviteResponse> listMyInvites(User currentUser) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Transactional
    public void acceptInvite(Long inviteId, User currentUser) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Transactional
    public void declineInvite(Long inviteId, User currentUser) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public void markExpiredInvites() {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Add TripInviteService scaffold with stubbed methods"
```

---

## Task 8: Implement `createInvite`

**Why:** Owner can now create invites end-to-end. First real business logic in the service.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Add imports**

Add to the existing imports:
```java
import com.mcesnik.planner_backend.event.TripEventRecorded;
import com.mcesnik.planner_backend.exception.InviteConflictException;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.model.Enums.TripEventEntityType;
import com.mcesnik.planner_backend.model.Enums.TripEventType;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripInvite;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
```

- [ ] **Step 2: Replace `createInvite` body**

```java
    @Transactional
    public TripInviteResponse createInvite(Long tripId, CreateInviteDTO dto, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        String email = dto.getEmail().toLowerCase(Locale.ROOT);

        if (email.equalsIgnoreCase(currentUser.getEmail())) {
            throw new InviteConflictException("You cannot invite yourself");
        }

        User targetUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with this email is not registered"));

        if (userTripRepository.existsByUserIdAndTripId(targetUser.getId(), tripId)) {
            throw new InviteConflictException("User is already a member of this trip");
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
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Implement createInvite in TripInviteService"
```

---

## Task 9: Implement `listForTrip` and `cancelInvite` (owner endpoints)

**Why:** Round out the owner side of the service. Listing + cancel are both straightforward read/update operations on existing invites.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Replace `listForTrip` body**

```java
    @Transactional(readOnly = true)
    public List<TripInviteResponse> listForTrip(Long tripId, InviteStatus statusFilter, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        List<TripInvite> invites = (statusFilter == null)
                ? inviteRepository.findByTripIdOrderByCreatedAtDesc(tripId)
                : inviteRepository.findByTripIdAndStatusOrderByCreatedAtDesc(tripId, statusFilter);

        return invites.stream().map(inviteMapper::toOwnerResponse).toList();
    }
```

- [ ] **Step 2: Replace `cancelInvite` body**

```java
    @Transactional
    public void cancelInvite(Long tripId, Long inviteId, User currentUser) {
        authorizationService.validateOwner(tripId, currentUser);

        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (!invite.getTrip().getId().equals(tripId)) {
            throw new ResourceNotFoundException("Invite not found");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException("Only pending invites can be cancelled");
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
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Implement listForTrip and cancelInvite for owner-facing endpoints"
```

---

## Task 10: Implement `listMyInvites` (invitee endpoint with lazy expiry)

**Why:** Invitee sees their pending invites; any that have just expired get marked here so the user never sees a stale PENDING.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Replace `listMyInvites` body**

```java
    @Transactional
    public List<MyInviteResponse> listMyInvites(User currentUser) {
        List<TripInvite> pending = inviteRepository
                .findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(currentUser.getEmail(), InviteStatus.PENDING);

        Instant now = Instant.now();
        List<TripInvite> stillPending = new java.util.ArrayList<>();

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
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Implement listMyInvites with lazy expiry transition"
```

---

## Task 11: Implement `acceptInvite`

**Why:** The most behaviorally rich method — full validation chain, optimistic-lock support, idempotency, and reuse of `materializeMembership`.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Add import for ForbiddenException**

```java
import com.mcesnik.planner_backend.exception.ForbiddenException;
```

- [ ] **Step 2: Replace `acceptInvite` body**

```java
    @Transactional
    public void acceptInvite(Long inviteId, User currentUser) {
        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException("Invite is no longer pending");
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
            throw new InviteConflictException("Invite has expired");
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
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Implement acceptInvite with full validation chain and idempotency"
```

---

## Task 12: Implement `declineInvite`

**Why:** Symmetric counterpart to accept — simpler, no membership materialization.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Replace `declineInvite` body**

```java
    @Transactional
    public void declineInvite(Long inviteId, User currentUser) {
        TripInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InviteConflictException("Invite is no longer pending");
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
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Implement declineInvite"
```

---

## Task 13: Implement `markExpiredInvites` scheduled job

**Why:** Catches abandoned PENDING invites in a periodic sweep, ensuring activity feed sees `INVITE_EXPIRED` events even when no one accesses the invite.

**Files:**
- Modify: `src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java`

- [ ] **Step 1: Add import**

```java
import org.springframework.scheduling.annotation.Scheduled;
```

- [ ] **Step 2: Replace `markExpiredInvites` body**

```java
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
```

- [ ] **Step 3: Verify `@EnableScheduling` is already active**

```bash
grep -rn "@EnableScheduling" src/main/java/
```
Expected: at least one match (likely on the main `PlannerBackendApplication` class or a config class). If not, add `@EnableScheduling` to the main `@SpringBootApplication` class.

- [ ] **Step 4: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/service/TripInviteService.java
git commit -m "Add scheduled markExpiredInvites job to TripInviteService"
```

---

## Task 14: TripInviteController (owner + invitee endpoints)

**Why:** Expose the service over HTTP. All six endpoints in one controller — `/trips/{tripId}/invites` for owners and `/me/invites` for invitees.

**Files:**
- Create: `src/main/java/com/mcesnik/planner_backend/controller/TripInviteController.java`

- [ ] **Step 1: Create the controller**

```java
// src/main/java/com/mcesnik/planner_backend/controller/TripInviteController.java
package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateInviteDTO;
import com.mcesnik.planner_backend.model.Enums.InviteStatus;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.MyInviteResponse;
import com.mcesnik.planner_backend.responses.TripInviteResponse;
import com.mcesnik.planner_backend.service.TripInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TripInviteController {

    private final TripInviteService inviteService;

    public TripInviteController(TripInviteService inviteService) {
        this.inviteService = inviteService;
    }

    // ───────────────────── Owner-facing ─────────────────────

    @PostMapping("/trips/{tripId}/invites")
    public ResponseEntity<TripInviteResponse> create(
            @PathVariable Long tripId,
            @Valid @RequestBody CreateInviteDTO dto) {
        TripInviteResponse response = inviteService.createInvite(tripId, dto, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/trips/{tripId}/invites")
    public ResponseEntity<List<TripInviteResponse>> listForTrip(
            @PathVariable Long tripId,
            @RequestParam(required = false) InviteStatus status) {
        return ResponseEntity.ok(inviteService.listForTrip(tripId, status, getCurrentUser()));
    }

    @DeleteMapping("/trips/{tripId}/invites/{inviteId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long tripId,
            @PathVariable Long inviteId) {
        inviteService.cancelInvite(tripId, inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    // ───────────────────── Invitee-facing ─────────────────────

    @GetMapping("/me/invites")
    public ResponseEntity<List<MyInviteResponse>> listMine() {
        return ResponseEntity.ok(inviteService.listMyInvites(getCurrentUser()));
    }

    @PostMapping("/me/invites/{inviteId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long inviteId) {
        inviteService.acceptInvite(inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/invites/{inviteId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long inviteId) {
        inviteService.declineInvite(inviteId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcesnik/planner_backend/controller/TripInviteController.java
git commit -m "Add TripInviteController with owner and invitee endpoints"
```

---

## Task 15: End-to-end manual verification

**Why:** The codebase has no integration tests; this task is the smoke-test pass that proves the feature works as designed.

- [ ] **Step 1: Start the app**

```bash
./gradlew bootRun
```
Wait for `Started PlannerBackendApplication`.

- [ ] **Step 2: Walk through the happy-path scenario**

Following the *Verification* section of `docs/invite-flow.md` — use Postman/curl/HTTP client of choice:

1. As user A (OWNER): `POST /trips` to create a trip
2. As user A: `POST /trips/{tripId}/invites` with B's email + `role=EDITOR` → expect 201
3. As user A: `GET /trips/{tripId}/invites` → expect 200 with B's invite in PENDING
4. As user B: `GET /me/invites` → expect 200 with the invite
5. As user B: `POST /me/invites/{inviteId}/accept` → expect 204
6. As user B: `GET /trips/{tripId}` → expect 200 (member view)
7. As user C (non-member): `GET /trips/{tripId}` → expect 404

- [ ] **Step 3: Walk through edge cases**

- A invites unregistered email → 404
- A invites B again after acceptance → 409
- A invites self → 409
- A creates new invite while one is PENDING → same row, new token
- B accepts twice → second attempt 409
- B tries `/me/invites/{otherId}/accept` for an invite not addressed to him → 403
- B with `enabled=false` tries to accept → 403

- [ ] **Step 4: Spot-check activity feed events**

Query `trip_events` table directly (or via dashboard activity endpoint) and confirm rows for `INVITE_SENT`, `INVITE_ACCEPTED`, `MEMBER_ADDED` (from materializeMembership), etc. exist with the right `actor` and `entity_id`.

- [ ] **Step 5: No commit — verification only.**

---

## Self-review (sanity check before execution)

- **Spec coverage:** All sections in `invite-flow.md` map to a task above (entity ✔ Task 2; service logic ✔ Tasks 8–12; scheduled ✔ Task 13; security ✔ no changes needed per design; verification ✔ Task 15).
- **Type consistency:** `materializeMembership` signature `(Trip, User, TripRole, User)` used identically in Tasks 5 and 11. `InviteConflictException` thrown only from `TripInviteService`; `ForbiddenException` reused from existing code. DTO field names consistent across mapper (Task 4) and responses (Task 4) and consumers (Tasks 8–12).
- **Placeholders:** None.
- **Risks:** `@EnableScheduling` confirmation in Task 13 — may need to be added if absent.

---

## Execution mode

Plan is ready. Recommended execution: **inline, task by task in this session** — after each commit, surface results and pause briefly so we can react to anything unexpected before moving on. This matches the user's preference for collaborative implementation over autonomous batches.
