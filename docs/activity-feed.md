# Activity Feed

Architecture documentation for the trip activity feed — tracks all CRUD operations within a trip and presents them as a chronological feed to trip members.

## Overview

When any trip member performs a CRUD operation (create/update/delete) on a trip, day, activity, or member, the system records the event with fine-grained field-level detail. All trip members can view the feed via a paginated REST endpoint.

**Privacy rule:** VIEWERs cannot see member management actions (add/remove/role change). Requesting `entityType=MEMBER` as a VIEWER returns 403 Forbidden.
**Retention:** Activity records older than a configurable period (default: 3 months) are automatically deleted.

## Entity Relationship Diagram

```
Trip ──< TripActivity
              │
              ├── actor (User FK, nullable — SET NULL on user deletion)
              ├── actorName (denormalized — survives user deletion)
              ├── eventType (TRIP_CREATED, ACTIVITY_UPDATED, etc.)
              ├── entityType (TRIP, TRIP_DAY, ACTIVITY, MEMBER)
              ├── entityName (denormalized — survives entity deletion)
              └── changes (JSONB — field-level diffs for updates)
```

## TripActivity Entity (table: `trip_activities`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| trip | Trip (FK) | not null, CASCADE DELETE via @OnDelete |
| actor | User (FK) | nullable, SET NULL via @OnDelete — preserved via actorName |
| actorName | String | not null — denormalized name, preserved after user deletion |
| eventType | ActivityEventType (enum) | not null, STRING |
| entityType | EntityType (enum) | not null, STRING |
| entityId | Long | ID of affected entity |
| entityName | String | not null — denormalized name, preserved after entity deletion |
| changes | String (JSONB) | nullable — JSON array of field diffs for UPDATE events |
| createdAt | Instant | not null, auto-set |

**Index:** `(trip_id, created_at DESC)` — optimizes the primary query pattern.

**Cascade:** `@OnDelete(action = OnDeleteAction.CASCADE)` on the trip FK. When a trip is deleted, all its activity records are cleaned up at the DB level without modifying the Trip entity.

**Actor deletion:** `@OnDelete(action = OnDeleteAction.SET_NULL)` on the actor FK. When a user is deleted, `actor_id` becomes null but `actorName` preserves the user's name. Frontend displays "Deleted user" when `actorId` is null.

### Changes JSON Format

For UPDATE events, the `changes` column stores a JSON array of field-level diffs:

```json
[
  {"field": "destination", "oldValue": "Paris", "newValue": "London"},
  {"field": "budget", "oldValue": "1000", "newValue": "1500"}
]
```

For CREATE and DELETE events, `changes` is `null`. The `entityName` field preserves the entity's name at the time of the event.

### ChangeDetector Implementation Notes

- **BigDecimal fields:** compared via `compareTo() == 0` (not `equals()`) to avoid false positives from scale differences (`1000.00` vs `1000.0`).
- **Collection fields (e.g. Set\<Interest\>):** elements sorted before `toString()` to avoid false positives from ordering differences.
- **Null-to-value transitions:** detected (DTO field is non-null, entity field is null → change recorded).
- **Value-to-null:** not detectable — null DTO fields mean "don't change" per existing mapper convention.

## Enums

### ActivityEventType

| Value | Trigger |
|-------|---------|
| `TRIP_CREATED` | Trip created |
| `TRIP_UPDATED` | Trip fields modified |
| `DAY_ADDED` | TripDay created |
| `DAY_UPDATED` | TripDay fields modified |
| `DAY_DELETED` | TripDay deleted |
| `ACTIVITY_ADDED` | Activity created |
| `ACTIVITY_UPDATED` | Activity fields modified |
| `ACTIVITY_DELETED` | Activity deleted |
| `MEMBER_ADDED` | User added to trip |
| `MEMBER_ROLE_CHANGED` | User's role changed |
| `MEMBER_REMOVED` | User removed from trip |

**Note:** `TRIP_DELETED` is intentionally absent — cascade delete removes both the trip and all its activity records. No one can view a deleted trip's feed.

### EntityType

`TRIP` | `TRIP_DAY` | `ACTIVITY` | `MEMBER`

MEMBER events are hidden from VIEWER role users at the query level.

## Architecture

### Event Flow

```
Service method (e.g. TripService.updateTrip)     [@Transactional]
    │
    ├── 1. Detect field changes (ChangeDetector)
    ├── 2. Apply update (mapper.updateEntity)
    ├── 3. Save entity (repository.save)
    ├── 4. Publish TripActivityEvent (ApplicationEventPublisher)
    │
    └── @TransactionalEventListener(BEFORE_COMMIT)
            │
            └── TripActivityEventListener
                    │
                    └── Serialize changes to JSON, save TripActivity
```

**Prerequisite:** All service methods that publish events must be annotated with `@Transactional`. Without a transaction boundary, `@TransactionalEventListener(BEFORE_COMMIT)` has no commit phase to hook into.

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `FieldChange` | `event/FieldChange.java` | Record: `field`, `oldValue`, `newValue` |
| `TripActivityEvent` | `event/TripActivityEvent.java` | In-memory Spring event (not JPA) |
| `ChangeDetector` | `event/ChangeDetector.java` | Compares entity state vs update DTO |
| `TripActivityEventListener` | `event/TripActivityEventListener.java` | Persists events inside the transaction |
| `TripActivityService` | `service/TripActivityService.java` | Query feed + scheduled cleanup |
| `TripActivityController` | `controller/TripActivityController.java` | REST endpoint |
| `TripActivityMapper` | `mapper/TripActivityMapper.java` | Entity → response DTO |

### Design Decisions

1. **Spring ApplicationEventPublisher** — decouples activity tracking from business logic. Services don't depend on TripActivityRepository. Same event infrastructure supports future SSE integration (add a second listener that pushes to SseEmitter registry — zero changes to services).

2. **@TransactionalEventListener(phase = BEFORE_COMMIT)** — runs inside the same transaction. If event recording fails, the business operation rolls back too. Guarantees consistency.

3. **ChangeDetector** — mirrors the null-check pattern from existing mappers. Only non-null DTO fields are compared. Returns empty list if values are identical → no event recorded for no-op updates. Uses `compareTo()` for BigDecimal and sorted toString() for collections.

4. **JSONB for changes** — Hibernate `@JdbcTypeCode(SqlTypes.JSON)` with PostgreSQL. All values stored as strings via `toString()`. No extra dependencies needed.

5. **deleteTrip — no event recorded** — cascade delete removes both the trip and its activity records. Since nobody can view a deleted trip's feed, this is acceptable. All other DELETE events (day, activity, member) ARE recorded — the event is published before the entity is deleted, and both operations commit together.

6. **Privacy at query level** — repository has separate queries for VIEWER (excludes `EntityType.MEMBER`) vs OWNER/EDITOR (all events). Enforced in `TripActivityService`, not filtered in Java. If a VIEWER explicitly requests `entityType=MEMBER`, the service returns 403 Forbidden.

7. **@OnDelete(CASCADE) instead of JPA cascade** — avoids adding a `List<TripActivity>` collection to Trip entity, which would affect existing queries and lazy loading.

8. **Denormalized actorName** — stored at event creation time. When a user is deleted (`actor_id` set to null via `@OnDelete(SET_NULL)`), the feed still displays the original actor name. Frontend shows "Deleted user" when `actorId` is null.

9. **@EntityGraph on repository queries** — `@EntityGraph(attributePaths = {"actor"})` on all TripActivityRepository query methods to prevent N+1 lazy loading of actor for each feed item.

## API Endpoint

### Get Activity Feed

```
GET /trips/{tripId}/activity-feed?entityType={filter}&page={n}&size={n}
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| entityType | String (optional) | — | Filter: `TRIP`, `TRIP_DAY`, `ACTIVITY`, `MEMBER` |
| page | int | 0 | Page number (0-indexed) |
| size | int | 20 | Page size |

**Authorization:** Any trip member. VIEWERs automatically receive a filtered feed (no MEMBER events). VIEWERs requesting `entityType=MEMBER` receive 403 Forbidden.

**Response:** Spring `Page<TripActivityResponse>`

```json
{
  "content": [
    {
      "id": 42,
      "eventType": "TRIP_UPDATED",
      "entityType": "TRIP",
      "entityId": 1,
      "entityName": "Summer Europe Trip",
      "actorName": "Mislav",
      "actorId": 5,
      "changes": [
        { "field": "destination", "oldValue": "Paris", "newValue": "London" }
      ],
      "createdAt": "2026-04-18T14:30:00Z"
    },
    {
      "id": 41,
      "eventType": "MEMBER_ADDED",
      "entityType": "MEMBER",
      "entityId": 7,
      "entityName": "Ana Horvat",
      "actorName": "Mislav",
      "actorId": 5,
      "changes": null,
      "createdAt": "2026-04-18T14:25:00Z"
    },
    {
      "id": 40,
      "eventType": "ACTIVITY_DELETED",
      "entityType": "ACTIVITY",
      "entityId": 15,
      "entityName": "Louvre Visit",
      "actorName": null,
      "actorId": null,
      "changes": null,
      "createdAt": "2026-04-18T14:20:00Z"
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

**Note:** The third example shows a feed entry where the actor was deleted — `actorId` is null, `actorName` is null. Frontend should display "Deleted user" in this case.

## Tracked Events by Service

### TripService

| Method | Event | entityName |
|--------|-------|------------|
| `createTrip` | `TRIP_CREATED` | trip.name |
| `updateTrip` | `TRIP_UPDATED` | trip.name (with field changes) |
| `deleteTrip` | — | Not recorded (cascade delete) |

### TripDayService

| Method | Event | entityName |
|--------|-------|------------|
| `addDay` | `DAY_ADDED` | "Day " + dayNumber |
| `updateDay` | `DAY_UPDATED` | "Day " + dayNumber (with field changes) |
| `deleteDay` | `DAY_DELETED` | "Day " + dayNumber |

### ActivityService

| Method | Event | entityName |
|--------|-------|------------|
| `addActivity` | `ACTIVITY_ADDED` | activity.name |
| `updateActivity` | `ACTIVITY_UPDATED` | activity.name (with field changes) |
| `deleteActivity` | `ACTIVITY_DELETED` | activity.name |

### TripMemberService

| Method | Event | entityName |
|--------|-------|------------|
| `addMember` | `MEMBER_ADDED` | targetUser.fullName |
| `updateMemberRole` | `MEMBER_ROLE_CHANGED` | user.fullName + FieldChange("role", old, new) |
| `removeMember` | `MEMBER_REMOVED` | user.fullName |

## Retention

A scheduled job runs daily at 3:00 AM (`@Scheduled(cron = "0 0 3 * * *")`), deleting all `TripActivity` records with `createdAt` older than the configured retention period.

```properties
# application.properties
activity.feed.retention-months=3
```

Uses `@EnableScheduling` which is already configured on the main application class. Retention period is read via `@Value("${activity.feed.retention-months:3}")`.

## Edge Cases

1. **No-op updates** — ChangeDetector returns empty list → no event published
2. **Deleted entity names** — stored in `entityName` at event creation time
3. **Deleted actor** — `actor_id` set to null via DB cascade, `actorName` preserved. Frontend displays "Deleted user"
4. **VIEWER requests entityType=MEMBER** — returns 403 Forbidden
5. **Concurrent updates** — events recorded in transaction commit order
6. **Lazy loading** — `@Transactional` on service methods keeps Hibernate session open
7. **BigDecimal equality** — `compareTo()` prevents false changes from scale differences
8. **Collection ordering** — sorted before comparison to prevent false changes

## Future: SSE Integration

The event architecture is SSE-ready. To add real-time notifications:

1. Create an `SseEmitterRegistry` that tracks open connections per trip
2. Add a second `@EventListener` method on `TripActivityEventListener` that pushes to connected emitters
3. Add `GET /trips/{tripId}/activity-feed/stream` endpoint returning `SseEmitter`
4. No changes needed to any existing service methods — they already publish events
