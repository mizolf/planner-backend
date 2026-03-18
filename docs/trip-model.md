# Trip Domain Model

Architecture documentation for the trip planning domain — the core feature of the planner-backend.

## Overview

A **Trip** is a collaborative travel plan owned by one user and optionally shared with others. Trips contain **Days**, which contain **Activities**. Access is controlled through a **UserTrip** join table with roles.

## Entity Relationship Diagram

```
User ──< UserTrip >── Trip ──< TripDay ──< Activity
              │
           role: OWNER | EDITOR | VIEWER
```

- One User can participate in many Trips (via UserTrip)
- One Trip can have many Users (via UserTrip)
- One Trip has many TripDays
- One TripDay has many Activities

## Entities

### Trip (table: `trips`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| name | String | not null |
| description | String | nullable |
| destination | String | nullable |
| startDate | LocalDate | |
| endDate | LocalDate | |
| status | TripStatus (enum) | not null, STRING — defaults to PLANNING on creation |
| budget | BigDecimal | precision=10, scale=2 |
| interests | Set\<Interest\> | @ElementCollection → separate `trip_interests` table |
| createdAt | LocalDateTime | set automatically on insert |
| updatedAt | LocalDateTime | set automatically on insert and update |

**Relationships:**
- `days` → OneToMany to TripDay (cascade ALL, orphanRemoval)
- `userTrips` → OneToMany to UserTrip (cascade ALL, orphanRemoval)

### TripDay (table: `trip_days`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| dayNumber | Integer | not null — for ordering (Day 1, Day 2, ...) |
| date | LocalDate | the actual calendar date |
| notes | String | nullable |

**Relationships:**
- `trip` → ManyToOne to Trip (LAZY, owning side via `trip_id` FK)
- `activities` → OneToMany to Activity (cascade ALL, orphanRemoval)

### Activity (table: `activities`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| name | String | not null |
| description | String | nullable |
| location | String | nullable |
| startTime | LocalTime | time-of-day only (date comes from parent TripDay) |
| endTime | LocalTime | |

**Relationships:**
- `tripDay` → ManyToOne to TripDay (LAZY, owning side via `trip_day_id` FK)

### UserTrip (table: `user_trips`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| role | TripRole (enum) | not null, STRING |
| joinedAt | LocalDateTime | set automatically on insert |

**Relationships:**
- `user` → ManyToOne to User (LAZY, `user_id` FK)
- `trip` → ManyToOne to Trip (LAZY, `trip_id` FK)

**Constraints:** Unique on (user_id, trip_id) — a user cannot be added to the same trip twice.

This is a full entity (not `@ManyToMany`) because it carries its own data (role, joinedAt).

## Enums

### TripStatus
`PLANNING` | `UPCOMING` | `IN_PROGRESS` | `COMPLETED`

### TripRole
`OWNER` | `EDITOR` | `VIEWER`

### Interest
`CULTURE` | `FOOD` | `ADVENTURE` | `NATURE` | `NIGHTLIFE` | `SHOPPING` | `RELAXATION` | `HISTORY`

Stored as an `@ElementCollection` on Trip — creates a `trip_interests` join table with columns `(trip_id, interest)`.

## Database Tables

Hibernate `ddl-auto=update` generates these tables automatically:

1. **trips** — core trip data
2. **trip_interests** — enum collection table (trip_id, interest)
3. **trip_days** — days within a trip
4. **activities** — activities within a day
5. **user_trips** — user-trip membership with roles

## Authorization Model

Access control is enforced in `TripService` (not at the controller/security filter level):

| Action | Required Role |
|--------|--------------|
| View trip details | Any member (OWNER, EDITOR, VIEWER) |
| Edit trip / add days / add activities | EDITOR or OWNER |
| Delete trip | OWNER only |
| Manage members (add/remove/change role) | OWNER only |
| List own trips | Any authenticated user |

Helper methods:
- `validateMembership(tripId, user)` — checks user is a member, throws otherwise
- `validateEditorOrOwner(tripId, user)` — checks user is EDITOR or OWNER
- `validateOwner(tripId, user)` — checks user is OWNER

## API Endpoints

All endpoints are under `/trips` and require authentication (JWT Bearer token).

### Trip CRUD

| Method | Path | Body | Returns | Auth |
|--------|------|------|---------|------|
| POST | `/trips` | CreateTripDTO | TripResponse | Authenticated (creator becomes OWNER) |
| GET | `/trips` | — | List\<TripResponse\> | Authenticated (own trips only) |
| GET | `/trips/{tripId}` | — | TripDetailResponse | Member |
| PUT | `/trips/{tripId}` | UpdateTripDTO | TripResponse | Editor/Owner |
| DELETE | `/trips/{tripId}` | — | message | Owner |

### Day Management

| Method | Path | Body | Returns | Auth |
|--------|------|------|---------|------|
| POST | `/trips/{tripId}/days` | CreateTripDayDTO | TripDayResponse | Editor/Owner |
| PUT | `/trips/{tripId}/days/{dayId}` | UpdateTripDayDTO | TripDayResponse | Editor/Owner |
| DELETE | `/trips/{tripId}/days/{dayId}` | — | message | Editor/Owner |

### Activity Management

| Method | Path | Body | Returns | Auth |
|--------|------|------|---------|------|
| POST | `/trips/{tripId}/days/{dayId}/activities` | CreateActivityDTO | ActivityResponse | Editor/Owner |
| PUT | `/trips/{tripId}/days/{dayId}/activities/{activityId}` | UpdateActivityDTO | ActivityResponse | Editor/Owner |
| DELETE | `/trips/{tripId}/days/{dayId}/activities/{activityId}` | — | message | Editor/Owner |

### Member Management

| Method | Path | Body | Returns | Auth |
|--------|------|------|---------|------|
| POST | `/trips/{tripId}/members` | AddTripMemberDTO | TripMemberResponse | Owner |
| PUT | `/trips/{tripId}/members/{userId}` | UpdateTripMemberDTO | TripMemberResponse | Owner |
| DELETE | `/trips/{tripId}/members/{userId}` | — | message | Owner |

## Response Objects

- **TripResponse** — flat trip fields (id, name, description, destination, dates, status, budget, interests, timestamps)
- **TripDetailResponse** — extends TripResponse with nested `days` and `members` lists
- **TripDayResponse** — day fields + nested `activities` list
- **ActivityResponse** — activity fields
- **TripMemberResponse** — userId, username, email, role, joinedAt

## Design Decisions

1. **Interests as @ElementCollection** — simpler than a full entity for a fixed set of enum tags. Hibernate replaces the full set on update, which is acceptable for max 8 values.

2. **UserTrip as a full entity** — needed because the join table carries role and joinedAt. A `@ManyToMany` annotation cannot hold extra columns.

3. **Authorization in service layer** — keeps controllers thin and ensures authorization is enforced regardless of how the service is called.

4. **LAZY fetch everywhere** — all `@ManyToOne` and `@OneToMany` use LAZY loading. TripService uses explicit repository queries to load related data, avoiding N+1 problems.

5. **No TravelStyle yet** — budget field covers spending level. Travel style / companion type will be designed together with the AI trip generation feature.

6. **@JsonIgnore on User.userTrips** — prevents infinite recursion when serializing User (e.g. `GET /users/me`). The existing endpoint returns raw User entities.
