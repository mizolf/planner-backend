# Trip Status Derived From Dates

Design documentation for changing `TripStatus` from a stored, manually-managed
column into a value **computed from the trip's dates on every read**.

## Motivation

Previously `status` was a persisted column on `trips`, hardcoded to `PLANNING`
on creation and only changeable through an explicit `PUT /trips/{id}` with a
`status` field. Nothing ever moved a trip forward automatically — no lifecycle
hook, no scheduled job. In practice every trip stayed `PLANNING` forever unless
a user manually edited it, so the stored value was almost always stale.

The four statuses (`PLANNING` / `UPCOMING` / `IN_PROGRESS` / `COMPLETED`) are all
positions in time relative to the trip's `startDate` / `endDate`. That makes
them a perfect fit for derivation rather than storage: the dates are the single
source of truth, and the status is just a view of them.

## The Derivation Rule

Status is computed from `startDate`, `endDate`, and today's date:

| Condition | Status |
|-----------|--------|
| `today < startDate` | `UPCOMING` |
| `today > endDate` | `COMPLETED` |
| otherwise (`startDate ≤ today ≤ endDate`) | `IN_PROGRESS` |

Edge cases (decided deliberately):

- A trip whose `startDate` is **today** → `IN_PROGRESS` (it has started).
- The **last day** of a trip (`today == endDate`) → still `IN_PROGRESS`.
- The day **after** `endDate` → `COMPLETED`.

`startDate` and `endDate` are `@NotNull` on `CreateTripDTO`, so they are never
null and the rule always resolves to exactly one status.

## Status Set Change

`PLANNING` is removed. `PLANNING` meant "still drafting", a state the dates
cannot express — once dates exist, a trip that hasn't started is simply
`UPCOMING`. The enum becomes three values:

```java
public enum TripStatus {
    UPCOMING,     // startDate is in the future
    IN_PROGRESS,  // today is between startDate and endDate (inclusive)
    COMPLETED     // endDate has passed
}
```

## Where The Computation Lives

A small, pure utility class. Dates are passed in explicitly (including "today")
so it is trivially unit-testable without mocking the clock:

```java
public final class TripStatusCalculator {
    private TripStatusCalculator() {}

    public static TripStatus calculate(LocalDate start, LocalDate end, LocalDate today) {
        if (today.isBefore(start)) return TripStatus.UPCOMING;
        if (today.isAfter(end))    return TripStatus.COMPLETED;
        return TripStatus.IN_PROGRESS;
    }
}
```

The status is computed **only when building a response**, inside `TripMapper`.
The `Trip` entity no longer carries a status at all — it is pure persisted data
(dates), and the status is a presentation concern derived on the way out.

This means status is recomputed on **every GET**. Because there is no stored
value, it can never be stale: each read uses `LocalDate.now()` at that moment.
There is no scheduled job and nothing to keep in sync.

## Entity & Database Changes

### Entity

`Trip.java` — the `status` field is removed entirely:

```java
// removed:
@Column(nullable = false)
@Enumerated(EnumType.STRING)
private TripStatus status = TripStatus.PLANNING;
```

`spring.jpa.hibernate.ddl-auto=validate` requires the entity and the schema to
match, so the column and the field must be dropped together.

### Migration

New Flyway migration `V2__drop_trip_status.sql`:

```sql
ALTER TABLE public.trips DROP CONSTRAINT trips_status_check;
ALTER TABLE public.trips DROP COLUMN status;
```

### Existing Data

The migration only drops the column — no data backfill is needed. Every existing
trip's status is recomputed from its dates on the next read:

- past trips (endDate already passed) → `COMPLETED`
- ongoing trips → `IN_PROGRESS`
- future trips → `UPCOMING`

The old stored `PLANNING` values simply disappear with the column. No trip can
report `PLANNING` anymore.

## Code Cleanup

Every touch point for the old stored status:

| File | Before | After |
|------|--------|-------|
| `model/Enums/TripStatus.java` | 4 values | remove `PLANNING` |
| `model/Trip.java` | persisted `status` field | remove field |
| `mapper/TripMapper.java` (`toEntity`) | `.status(TripStatus.PLANNING)` | remove |
| `mapper/TripMapper.java` (`updateEntity`) | `if (dto.getStatus() != null) trip.setStatus(...)` | remove |
| `mapper/TripMapper.java` (response builders) | `trip.getStatus()` | `TripStatusCalculator.calculate(startDate, endDate, LocalDate.now())` |
| `DTO/UpdateTripDTO.java` | `private TripStatus status` | remove (status is no longer user-settable) |
| `responses/TripResponse.java` | `private TripStatus status` | **keep** — frontend reads it for the status chip and home-page tabs |
| `event/ChangeDetector.java` | `compare(changes, "status", trip.getStatus(), dto.getStatus())` | remove (nothing to track) |
| `service/TripTemplateService.java` | `.status(TripStatus.PLANNING)` | remove |

## API Impact

- **Request bodies**: `UpdateTripDTO` loses its `status` field. `CreateTripDTO`
  never had one. Status is read-only from the client's perspective.
- **Response bodies**: unchanged shape. `TripResponse` / `TripDetailResponse`
  still include a `status` field — clients see no difference except that the
  value is now always accurate and never `PLANNING`.

## Frontend Impact

The status chip mechanism is unchanged: the backend still returns `status` in
every trip response, so the chip renders exactly as before. The frontend team
will, on their side:

- remove `PLANNING` from the chip's value/label/colour set
- ensure styling exists for `UPCOMING` / `IN_PROGRESS` / `COMPLETED`
- implement the home-page status tabs

### Home-page filtering

Filtering by status (clicking `UPCOMING` / `IN_PROGRESS` tabs) is done on the
**frontend**. The home page already loads all of the user's trips with their
computed status, so a tab simply filters the in-memory list. No backend filter
endpoint and no `?status=` query parameter are added — a user's trip list is
small enough that this never needs SQL-level filtering.

## Testing

`TripStatusCalculator` is a pure function, so it is covered by plain unit tests
with no database and no clock mocking:

- `today` before `startDate` → `UPCOMING`
- `today` after `endDate` → `COMPLETED`
- `today` between the dates → `IN_PROGRESS`
- `today == startDate` → `IN_PROGRESS` (boundary)
- `today == endDate` → `IN_PROGRESS` (boundary)

## Design Decisions

1. **Computed-on-read instead of stored + scheduled job** — a derived value can
   never be stale because nothing is stored to go stale. A stored column would
   require a job to keep it current and could drift between job runs. The only
   cost is the inability to filter by status in SQL, which this app does not
   need (per-user trip lists are small).

2. **Drop the column rather than keep it alongside the computation** — keeping
   both a stored column and a computed value creates two sources of truth that
   can disagree. Removing the column leaves exactly one: the dates.

3. **`UPCOMING` over `PLANNING` for the pre-start state** — with status derived
   purely from time, the relevant fact is "the trip hasn't started yet", which
   `UPCOMING` describes. `PLANNING` implies a drafting phase that the dates
   cannot represent.

4. **Computation in the mapper, not on the entity** — keeps `Trip` as pure
   persisted data and isolates the time-dependent logic to response building,
   where the testable `TripStatusCalculator` is the single place it lives.

5. **Frontend-side filtering for home-page tabs** — the trip list is already
   loaded with each trip's status, so tab filtering is a presentation concern.
   This keeps the backend unchanged and gives instant tab switching with no
   extra requests.
