# Trip Invite Flow

Design documentation for the trip member invite system — replaces the direct `addMember` endpoint with a consent-based invite flow where invitees see pending invites inside the app and choose to accept or decline.

## Overview

Owners can invite registered users to join their trip by email. The system creates a `TripInvite` record with a unique token and `PENDING` status. The invitee sees the pending invite in their in-app list (`GET /me/invites`) the next time they open the app, and chooses to accept or decline. Inviting non-registered emails is out of scope for Phase 1 — the target user must already have an account.

This replaces the previous `POST /trips/{tripId}/members` endpoint which added users directly without their consent. All trip member additions now flow through invites.

**Phases:**
- **Phase 1 (this document):** In-app invite list — invitees see and act on pending invites only after logging in
- **Phase 2 (future):** Email notification + public token-based preview link + production hardening (rate limiting, transactional email provider, enumeration mitigation, token hashing)
- **Phase 3+ (future):** Support for inviting non-registered emails via signup-then-accept flow

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Target audience | Only existing users | Simpler Phase 1; defer signup-then-accept complexity |
| Notification channel (Phase 1) | In-app only — `/me/invites` listing | No email infrastructure needed; faster to implement; demo-friendly |
| Accept/decline auth | Auth required, invitee's email must match `invite.email` | Logged-in user accepting on behalf of someone else is blocked |
| Token field | Generated, but unused as access key in Phase 1 | Reserved for Phase 2 email-link preview without breaking the schema |
| Duplicate PENDING invite | Regenerate token, persist update on same row | Owner can "resend" by recreating; behavior consistent with future email flow |
| Token expiry | 7 days | Stale invites should not linger even without email; aligns with future email-link standard (GitHub, Slack pattern) |
| Direct `addMember` endpoint | Removed | Consent flow is inconsistent with instant-add — single source of truth |
| Audit log | 5 new `TripEventType` values | Rich history of every invite action |
| Unverified target user | Allow invite, block accept until verified | Owner doesn't need to know verification status |

## Entity Relationship Diagram

```
Trip ──< TripInvite
              │
              ├── inviter (User FK, nullable — owner who created the invite)
              ├── acceptedUser (User FK, nullable — populated on accept)
              ├── revokedBy (User FK, nullable — populated on cancel)
              ├── email (target email, lowercase normalized)
              ├── token (UUID, unique — reserved for Phase 2)
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
| inviter | User (FK) | nullable |
| email | String | not null, lowercase normalized |
| token | String | not null, **unique** (UUID) — reserved for Phase 2 |
| role | TripRole (enum) | not null, STRING — role granted to invitee on accept |
| status | InviteStatus (enum) | not null, STRING |
| createdAt | Instant | not null |
| expiresAt | Instant | not null |
| respondedAt | Instant | nullable — set on accept/decline/cancel/expire |
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

State transitions are one-way: `PENDING` is the only state with outgoing transitions. Once an invite leaves `PENDING`, it stays in the terminal state for audit purposes.

## API Endpoints

### Owner-facing (auth required, validates OWNER role)

```
POST   /trips/{tripId}/invites              Create new invite
GET    /trips/{tripId}/invites              List invites for trip (optional ?status=PENDING)
DELETE /trips/{tripId}/invites/{inviteId}   Cancel a PENDING invite
```

### Invitee-facing (auth required, validates current user's email matches invite)

```
GET    /me/invites                              List my PENDING invites
POST   /me/invites/{inviteId}/accept            Accept a pending invite
POST   /me/invites/{inviteId}/decline           Decline a pending invite
```

### Request / Response Shapes

**`POST /trips/{tripId}/invites` request:**
```json
{
  "email": "newmember@example.com",
  "role": "EDITOR"
}
```

**`GET /me/invites` response (list of `MyInviteResponse`):**
```json
[
  {
    "id": 42,
    "tripId": 7,
    "tripName": "Summer Italy 2026",
    "tripDestination": "Italy",
    "inviterName": "Mislav Cesnik",
    "role": "EDITOR",
    "expiresAt": "2026-05-30T15:00:00Z"
  }
]
```

The invitee sees minimum trip context to decide whether to accept (trip name, destination, who invited them, what role they would get).

**`GET /trips/{tripId}/invites` response (owner view — `TripInviteResponse`):**
```json
[
  {
    "id": 42,
    "email": "newmember@example.com",
    "role": "EDITOR",
    "status": "PENDING",
    "inviterName": "Mislav Cesnik",
    "createdAt": "2026-05-23T10:00:00Z",
    "expiresAt": "2026-05-30T15:00:00Z"
  }
]
```

## Service Logic

### createInvite (transactional)

1. Validate caller is OWNER via `TripAuthorizationService.validateOwner`
2. Normalize email to lowercase
3. Reject self-invite (`email == currentUser.email`, case-insensitive) → 409 Conflict
4. Look up user by email; if not found → 404 "User with this email is not registered"
5. Reject if user is already a trip member → 409 Conflict
6. Check for existing PENDING invite for `(tripId, email)`:
   - **Exists** → regenerate token, reset `createdAt` and `expiresAt`, persist
   - **Does not exist** → create new invite with fresh UUID token, expiry = `now + ${app.invite.expiry-days}`
7. Publish `INVITE_SENT` event for activity feed
8. Return `TripInviteResponse`

### listForTrip (read-only, owner only)

Owner-facing list of invites for a trip with optional `?status=` filter. Validates `OWNER` role.

### listMyInvites (read-only, requires auth)

Returns all `PENDING` invites where `invite.email == currentUser.email` (case-insensitive). Lazily marks any matching `PENDING` invite with `expiresAt < now` as `EXPIRED` (and publishes `INVITE_EXPIRED`) before returning, so the invitee never sees stale PENDING rows.

### acceptInvite (transactional, requires auth)

Validation order:
1. Find by ID; missing → 404
2. `status != PENDING` → 409 "Invite is no longer pending"
3. `expiresAt < now` → mark `EXPIRED`, publish event, throw 409 "Invite has expired"
4. `invite.email != currentUser.email` (case-insensitive) → 403 "This invite is not for you"
5. `!currentUser.isEnabled()` (unverified) → 403 "Verify your email before accepting invites"
6. Idempotency: if already a member, mark `ACCEPTED` and return (no duplicate UserTrip)

On success:
- Create `UserTrip` via `TripMemberService.materializeMembership(trip, user, role, actor)` — extracted from removed `addMember`. The helper publishes `MEMBER_ADDED`.
- Set invite `status = ACCEPTED`, `respondedAt = now`, `acceptedUser = currentUser`
- Publish `INVITE_ACCEPTED` event

**Concurrency:** `@Version` field triggers `ObjectOptimisticLockingFailureException` (Spring's wrapper for JPA's `OptimisticLockException`) if two requests race to accept the same invite. The exception is mapped to 409 in `GlobalExceptionHandler`.

### declineInvite (transactional, requires auth)

1. Find by ID; missing → 404
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

Finds all `PENDING` invites where `expiresAt < now`, transitions them to `EXPIRED`, and publishes `INVITE_EXPIRED` events. Follows the existing pattern in `ActivityFeedService.purgeOldEvents` and `TokenBlacklistService.cleanupExpiredTokens`. Lives directly on `TripInviteService`.

The 04:00 offset prevents collision with the existing 03:00 activity-feed purge job.

## TripEventType Extensions

Five new audit-log event types added to `TripEventType.java`:

| Event | When | Entity referenced |
|---|---|---|
| `INVITE_SENT` | `createInvite` | INVITE (the invite itself) |
| `INVITE_ACCEPTED` | `acceptInvite` (paired with `MEMBER_ADDED` from `materializeMembership`) | INVITE |
| `INVITE_DECLINED` | `declineInvite` | INVITE |
| `INVITE_CANCELLED` | `cancelInvite` | INVITE |
| `INVITE_EXPIRED` | scheduled job + lazy expiry in `listMyInvites` / `acceptInvite` | INVITE |

New `TripEventEntityType.INVITE` is added so events can reference the invite record. The existing `MEMBER_ADDED` event is also published on accept (via `materializeMembership`), so the activity feed naturally shows both "X invited Y" and "Y joined" entries.

## Edge Case Handling

| Case | Handled by |
|---|---|
| Inviter user deleted | `inviter` is nullable. Mapper renders "Deleted user" when null. (DB-level `ON DELETE SET NULL` deferred to Flyway migration.) |
| Trip deleted while invites PENDING | `Trip.invites` collection with `cascade=ALL, orphanRemoval=true` cleans up invites at JPA level |
| Two parallel accepts | `@Version` → `ObjectOptimisticLockingFailureException` → 409 |
| Self-invite | Explicit guard in `createInvite` |
| Email case sensitivity (`User@x.com` vs `user@x.com`) | Lowercase normalization on save; `equalsIgnoreCase` on comparison |
| Unverified user accepting | Explicit `!currentUser.isEnabled()` check in accept |
| Stale PENDING rows after expiry | Scheduled batch job + lazy expiry on read in `listMyInvites` / `acceptInvite` |
| Owner spam-creates invites | Out of scope for Phase 1 (see Production Considerations) |

## Security Configuration

All invite endpoints are authenticated; no `permitAll()` rules are added in Phase 1. JWT auth applies to both:
- Owner endpoints under `/trips/{tripId}/invites`
- Invitee endpoints under `/me/invites`

Phase 2 will add `GET /invites/{token}` as a public preview endpoint (for the email link), with its own `permitAll()` rule.

## Migrations & Schema

The project currently uses Hibernate `spring.jpa.hibernate.ddl-auto=update` — no explicit migration tool (Flyway/Liquibase). Adding `TripInvite` as a new entity will auto-create the `trip_invites` table on app startup.

**Production note:** `ddl-auto=update` is only acceptable for development. Before any production deployment, Flyway must be adopted — see *Production Deployment Considerations* below.

## Verification

End-to-end smoke test with three verified users (A=owner, B=invitee, C=non-member):

1. A logs in, creates trip → A is OWNER
2. A: `POST /trips/{id}/invites` with B's email + `role=EDITOR` → 201 with `TripInviteResponse`
3. A: `GET /trips/{id}/invites` → 200, shows PENDING invite
4. B logs in: `GET /me/invites` → 200, shows the pending invite with trip name + inviter name
5. B: `POST /me/invites/{inviteId}/accept` → 200; B is now EDITOR
6. B: `GET /trips/{id}` → 200 (member view)
7. C: `GET /trips/{id}` → 404 (non-member, security obscurity)

Edge case checks:
- A invites unregistered email → 404
- A invites B again after acceptance → 409 (already member)
- A invites self → 409
- B accepts twice → second attempt 409 (no longer PENDING)
- A creates new invite while one is PENDING → same row updated with new token + fresh expiry
- B tries to accept an invite that wasn't addressed to them → 403
- B accepts before verifying email (`enabled=false`) → 403
- Manually backdate `expires_at` in DB → B sees no pending invite (lazy expiry triggers)
- Trigger scheduled job manually → expired invites flip to EXPIRED, activity feed shows `INVITE_EXPIRED` events

## Production Deployment Considerations

This section captures gaps between the Phase 1 design above and production-readiness. The design is sound for development and small-scale demo deployment; the items below must (or should) be addressed before serving real users.

### Phase 1 — Critical (blocker for production)

| # | Issue | Impact | Resolution |
|---|---|---|---|
| 1 | `spring.jpa.hibernate.ddl-auto=update` for schema management | No schema versioning. Hibernate does not drop columns, does not handle renames, has no rollback path. Field renames silently leak data into orphan columns. Cannot roll back a bad deploy. | Adopt **Flyway**: add `flyway-core` dependency, switch to `ddl-auto=validate`, generate baseline `V1__baseline.sql` from current schema, add `V2__create_trip_invites.sql` for this feature. |

### Phase 1 — Important hardening

| # | Issue | Impact | Resolution |
|---|---|---|---|
| 2 | Scheduled job `markExpiredInvites` runs on every instance | When scaled horizontally (multiple replicas), the job executes N times in parallel: duplicate `INVITE_EXPIRED` events, repeated work. | Add **ShedLock** distributed lock backed by Postgres — guarantees only one instance runs the job per cron tick. |
| 3 | User enumeration via `createInvite` response codes | `404 "User not registered"` vs `409 "Already a member"` vs `201 Created` lets any OWNER enumerate registered users in the system. | Return a constant response regardless of registration/membership status. Internally still skip the lookup for non-existent or already-member emails. |
| 4 | No rate limiting on `POST /trips/{tripId}/invites` | An owner can create thousands of invite DB rows. Even without email send, this is a cheap DoS vector against database storage and a vehicle for user-enumeration attacks. | Add a per-user bucket via **Bucket4j** (e.g., 10 invites/hour, 50/day). Apply as a Spring filter or method-level interceptor. |
| 5 | Race condition: no DB uniqueness on `(trip_id, email)` for PENDING invites | Two concurrent `createInvite` calls for the same email both pass the existence check and create duplicate invites. | Add a Postgres partial unique index via Flyway: `CREATE UNIQUE INDEX idx_one_pending_invite ON trip_invites (trip_id, email) WHERE status = 'PENDING';` |
| 6 | No `ON DELETE SET NULL` at DB level for `inviter`, `accepted_user`, `revoked_by` FKs | If a user is hard-deleted (unlikely in current app, but possible), FKs become dangling. | Add FK constraints via Flyway with `ON DELETE SET NULL`. |
| 7 | No observability on the invite flow | Cannot answer: invite volume, accept rate, expired-without-action rate. Blind to incidents in production. | Spring Boot **Actuator** + **Micrometer** + **Prometheus**. Custom counters: `invites.created`, `invites.accepted`, `invites.expired`. |
| 8 | No GDPR / data retention policy for invite records | `trip_invites` retains target email addresses indefinitely. EU privacy regulation requires a defined retention policy. | Extend the daily scheduled job to delete CANCELLED/EXPIRED/DECLINED invites older than 90 days. Keep ACCEPTED for audit (optionally anonymize the email field after 90 days). |

### Phase 2 — Email integration (future)

Phase 2 adds out-of-band email notifications. The following items become relevant only when email is wired up:

| # | Issue | Resolution |
|---|---|---|
| 9 | `app.frontend-url` config missing for email links | Add as required env var with per-profile overrides. Must be HTTPS in production. |
| 10 | Gmail SMTP unsuitable for transactional mail at scale | Switch to **SendGrid**, **AWS SES**, **Mailgun**, or **Postmark**. Configure SPF + DKIM DNS records for the sending domain. |
| 11 | Synchronous email send inside DB transaction | Adopt the **outbox pattern**: persist invite + `outbox_event` row in same transaction; a separate worker reads the outbox and sends with retry. Decouples DB transaction from SMTP latency. |
| 12 | Email body hardcoded in Java code | Move to **Thymeleaf** template at `src/main/resources/templates/email/invite.html`. Enables future i18n (en/hr). |
| 13 | Public `GET /invites/{token}` endpoint introduces email enumeration / PII concerns | Return minimal `PublicInviteResponse` (trip name + inviter first name + role + status + expiry); never the target email. |
| 14 | Tokens stored in plaintext in DB | Hash tokens before storing (SHA-256 — no salt needed since tokens are random 128-bit UUIDs). Send raw token in email; look up by hash on accept/decline. |

### Recommended implementation order

| Priority | Task | Phase | Estimated effort |
|---|---|---|---|
| P0 | Flyway migration setup + baseline + `V2` for `trip_invites` | Phase 1 | 0.5 day |
| P0 | Partial unique index on `(trip_id, email) WHERE status='PENDING'` | Phase 1 | 15 min |
| P0 | Rate limiting via Bucket4j on `POST /trips/{tripId}/invites` | Phase 1 | 2–3 hours |
| P0 | User enumeration fix (constant response from `createInvite`) | Phase 1 | 30 min |
| P0 | FK `ON DELETE SET NULL` constraints (Flyway) | Phase 1 | 30 min |
| P1 | ShedLock for distributed scheduled job | Phase 1 | 1 hour |
| P1 | Observability (Actuator / Micrometer / Prometheus / Grafana) | Phase 1 | 1 day |
| P1 | GDPR retention extension to the existing scheduled job | Phase 1 | 1 hour |
| P2 | `app.frontend-url` per-profile config + HTTPS in production | Phase 2 | 30 min |
| P2 | Production email provider (SendGrid free tier sufficient to start) | Phase 2 | 2 hours |
| P2 | Token hashing in DB (SHA-256) | Phase 2 | 2 hours |
| P2 | Public `GET /invites/{token}` endpoint with `PublicInviteResponse` | Phase 2 | 2 hours |
| P2 | Move email body to Thymeleaf template | Phase 2 | 2 hours |
| P2 | Async email + outbox pattern | Phase 2 | 1–2 days |

The P0 items must be done before any production deploy. P1 items should follow within the first weeks after launch as traffic grows. P2 items are the Phase 2 deliverable and can be deferred until email out-of-band notifications are needed.
