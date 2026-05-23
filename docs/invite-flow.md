# Trip Invite Flow

Design documentation for the trip member invite system — replaces the direct `addMember` endpoint with a consent-based invite flow where users receive an email link and can accept or decline.

## Overview

Owners can invite registered users to join their trip by email. The system creates a `TripInvite` record with a unique token, sends an email containing a frontend link, and waits for the invitee to accept or decline. Inviting non-registered emails is out of scope for Phase 1 — the target user must already have an account.

This replaces the previous `POST /trips/{tripId}/members` endpoint which added users directly without their consent. All trip member additions now flow through invites.

**Phases:**
- **Phase 1 (this document):** Email-based invite for registered users only
- **Phase 2 (future):** In-app invite UI with notification badge — list of pending invites visible inside the app
- **Phase 3+ (future):** Support for inviting non-registered emails via signup-then-accept flow

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Target audience | Only existing users | Simpler Phase 1; defer signup-then-accept complexity |
| Accept/decline auth | Token + JWT login required | Token alone (forwarded email) cannot accept on behalf of someone |
| Duplicate PENDING invite | Regenerate token, resend mail | Owner can simply "resend" by recreating; old token becomes invalid |
| Token expiry | 7 days | Standard for invite links (GitHub, Slack pattern) |
| Direct `addMember` endpoint | Removed | Consent flow is inconsistent with instant-add — single source of truth |
| Mail failure handling | Transactional rollback | If SMTP fails, no orphan invite is created |
| Audit log | 5 new `TripEventType` values | Rich history of every invite action |
| Unverified target user | Allow invite, block accept until verified | Owner doesn't need to know verification status |

## Entity Relationship Diagram

```
Trip ──< TripInvite
              │
              ├── inviter (User FK, nullable — SET NULL on user deletion)
              ├── acceptedUser (User FK, nullable — populated on accept)
              ├── revokedBy (User FK, nullable — populated on cancel)
              ├── email (target email, lowercase normalized)
              ├── token (UUID, unique)
              ├── role (TripRole — role granted on accept)
              ├── status (PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED)
              ├── version (@Version — optimistic locking)
              └── timestamps (createdAt, expiresAt, respondedAt)
```

## TripInvite Entity (table: `trip_invites`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| trip | Trip (FK) | not null, cascade via `Trip.invites` collection (orphanRemoval=true) |
| inviter | User (FK) | nullable, SET NULL on user deletion |
| email | String | not null, lowercase normalized |
| token | String | not null, **unique** (UUID) |
| role | TripRole (enum) | not null, STRING — role granted to invitee on accept |
| status | InviteStatus (enum) | not null, STRING |
| createdAt | Instant | not null |
| expiresAt | Instant | not null |
| respondedAt | Instant | nullable — set on accept/decline/cancel |
| acceptedUser | User (FK) | nullable — set on accept (handles email change scenarios) |
| revokedBy | User (FK) | nullable — set on cancel |
| version | Long | `@Version` — optimistic locking for concurrent accept |

### InviteStatus Enum

```
PENDING    → initial state after creation
ACCEPTED   → user accepted, UserTrip created
DECLINED   → user declined
EXPIRED    → token expired before accept/decline
CANCELLED  → owner revoked the invite
```

State transitions are one-way: PENDING is the only state with outgoing transitions. Once an invite leaves PENDING, it stays in the terminal state for audit purposes.

## API Endpoints

### Owner-facing (auth required, validates OWNER role)

```
POST   /trips/{tripId}/invites              Create new invite, send email
GET    /trips/{tripId}/invites              List invites for trip (optional ?status=PENDING)
DELETE /trips/{tripId}/invites/{inviteId}   Cancel a PENDING invite
```

### Invitee-facing

```
GET    /invites/{token}              PUBLIC — returns minimal trip + inviter info
POST   /invites/{token}/accept       AUTH — logged-in email must match invite email
POST   /invites/{token}/decline      AUTH — logged-in email must match invite email
```

### Request / Response Shapes

**`POST /trips/{tripId}/invites` request:**
```json
{
  "email": "newmember@example.com",
  "role": "EDITOR"
}
```

**`GET /invites/{token}` response (PublicInviteResponse — no PII leak):**
```json
{
  "tripName": "Summer Italy 2026",
  "inviterFirstName": "Mislav",
  "role": "EDITOR",
  "status": "PENDING",
  "expiresAt": "2026-05-30T15:00:00Z"
}
```

The public response deliberately omits the target email and inviter's full name/email to avoid PII leakage to anyone in possession of the link.

## Service Logic

### createInvite (transactional, rollback on any exception)

1. Validate caller is OWNER via `TripAuthorizationService.validateOwner`
2. Normalize email to lowercase
3. Reject self-invite (`email == currentUser.email`) → 409 Conflict
4. Look up user by email; if not found → 404 "User with this email is not registered"
5. Reject if user is already a trip member → 409 Conflict
6. Check for existing PENDING invite for `(tripId, email)`:
   - **Exists** → regenerate token, reset `createdAt` and `expiresAt`, persist
   - **Does not exist** → create new invite with fresh UUID token, expiry = now + 7 days
7. Send email via `EmailService.sendInviteEmail(...)` with accept URL: `{app.frontend-url}/invite/{token}`
8. Publish `INVITE_SENT` event for activity feed

If email sending fails (`MessagingException`), `@Transactional(rollbackFor = Exception.class)` rolls back the invite — no orphan record. Spring's default does not rollback checked exceptions; explicit `rollbackFor` is required.

### getInviteByToken (read-only, public)

- Find invite by token; missing → 404
- If `status == PENDING && expiresAt < now`, lazily mark EXPIRED (saves within the transaction)
- Return `PublicInviteResponse` — no email, no full PII

### acceptInvite (transactional, requires auth)

Validation order:
1. Find by token; missing → 404
2. `status != PENDING` → 409 "Invite is no longer pending"
3. `expiresAt < now` → mark EXPIRED, publish event, throw 409 "Invite has expired"
4. `invite.email != currentUser.email` (case-insensitive) → 403 "This invite is not for you"
5. `!currentUser.isEnabled()` (unverified) → 403 "Verify your email before accepting invites"
6. Idempotency: if already a member, mark ACCEPTED and return (no duplicate UserTrip)

On success:
- Create `UserTrip` via private helper `materializeMembership(trip, user, role, actor)` — reuses extracted logic from removed `TripMemberService.addMember`
- Set invite `status = ACCEPTED`, `respondedAt = now`, `acceptedUser = currentUser`
- Publish `INVITE_ACCEPTED` and `MEMBER_ADDED` events

**Concurrency:** `@Version` field triggers `OptimisticLockException` if two requests race to accept the same invite. The exception is mapped to 409 in `GlobalExceptionHandler`.

### declineInvite (transactional, requires auth)

1. Find by token; missing → 404
2. `status != PENDING` → 409
3. Email mismatch → 403
4. Mark `status = DECLINED`, `respondedAt = now`
5. Publish `INVITE_DECLINED` event

### cancelInvite (transactional, owner only)

1. `validateOwner`
2. Find invite, verify `invite.trip.id == tripId`
3. `status != PENDING` → 409
4. Mark `status = CANCELLED`, `revokedBy = currentUser`, `respondedAt = now`
5. Publish `INVITE_CANCELLED` event

## Scheduled Job: Expire PENDING Invites

```java
@Scheduled(cron = "0 0 4 * * *")  // daily at 04:00, offset from 03:00 feed purge
public void markExpiredInvites()
```

Finds all `PENDING` invites where `expiresAt < now`, transitions them to `EXPIRED`, and publishes `INVITE_EXPIRED` events. Follows the existing pattern in `ActivityFeedService.purgeOldEvents` and `TokenBlacklistService.cleanupExpiredTokens`.

The 4 AM offset prevents collision with the existing 3 AM activity-feed purge job.

## Email Template

`EmailService.sendInviteEmail(to, inviterName, tripName, acceptUrl)`:

```
Subject: You've been invited to "{tripName}"

Body (HTML):
  {inviterName} invited you to join their trip "{tripName}".
  Open invite: {acceptUrl}
  This invite expires in 7 days.
```

`acceptUrl` is constructed from `application.properties`:
```properties
app.frontend-url=http://localhost:4200
app.invite.expiry-days=7
```

## TripEventType Extensions

Five new audit-log event types added to `TripEventType.java`:

| Event | When | Entity referenced |
|---|---|---|
| `INVITE_SENT` | `createInvite` | INVITE (the invite itself) |
| `INVITE_ACCEPTED` | `acceptInvite` (paired with existing `MEMBER_ADDED`) | INVITE |
| `INVITE_DECLINED` | `declineInvite` | INVITE |
| `INVITE_CANCELLED` | `cancelInvite` | INVITE |
| `INVITE_EXPIRED` | scheduled job | INVITE |

New `TripEventEntityType.INVITE` is added so events can reference the invite record. The existing `MEMBER_ADDED` event is also published on accept, so the activity feed naturally shows both "X invited Y" and "Y joined" entries.

## Edge Case Handling

| Case | Handled by |
|---|---|
| Inviter user deleted | `inviter` is nullable + `ON DELETE SET NULL`. Mapper renders "Deleted user" when null. |
| Trip deleted while invites PENDING | `Trip.invites` collection with `cascade=ALL, orphanRemoval=true` cleans up invites at JPA level |
| Two parallel accepts | `@Version` → `OptimisticLockException` → 409 |
| Self-invite | Explicit guard in `createInvite` |
| Email case sensitivity (`User@x.com` vs `user@x.com`) | Lowercase normalization on save; `equalsIgnoreCase` on comparison |
| Email enumeration via invite endpoint | Consistent 404 message; public GET omits email |
| Unverified user accepting | Explicit `!currentUser.isEnabled()` check in accept |
| SMTP transient failure | `@Transactional(rollbackFor = Exception.class)` rolls back invite creation |
| Owner spam-creates invites | Out of scope for Phase 1 (no rate limiting yet) |

## Security Configuration

`SecurityConfiguration.securityFilterChain` is extended:

```java
.requestMatchers(HttpMethod.GET, "/invites/*").permitAll()
```

Only the public `GET /invites/{token}` is unauthenticated. Accept, decline, and all owner endpoints require JWT auth.

## Migrations & Schema

The project uses Hibernate `spring.jpa.hibernate.ddl-auto=update` — no explicit migration tool (Flyway/Liquibase). Adding `TripInvite` as a new entity will auto-create the `trip_invites` table on app startup.

## Verification

End-to-end smoke test with three verified users (A=owner, B=invitee, C=non-member):

1. A creates trip → A is OWNER
2. `POST /trips/{id}/invites` with B's email + role=EDITOR → 201, B receives email
3. `GET /invites/{token}` (no JWT) → 200 with PublicInviteResponse
4. B logs in → `POST /invites/{token}/accept` → 204; B is now EDITOR
5. B `GET /trips/{id}` → 200 (member)
6. C `GET /trips/{id}` → 404 (non-member, security obscurity)

Edge case checks:
- A invites unregistered email → 404
- A invites B again after acceptance → 409 (already member)
- A invites self → 409
- B accepts twice → second attempt 409
- A creates new invite while one is PENDING → old token invalidated, new email sent
- Mock SMTP failure → invite does not exist in DB (rollback verified)
- Manually backdate `expires_at` in DB → accept → 409 (expired)
