# Trip Overlapping Dates

Design documentation for rejecting a new trip whose date range overlaps another
trip the user already belongs to.

## Motivation

`createTrip` already validates that a trip's `endDate` is not before its
`startDate` (`InvalidDateRangeException`), but nothing stops a user from creating
two trips whose ranges overlap — e.g. June 1–5 and June 3–8. A person cannot be
on two trips at the same time, so overlapping trips make the planner's data
nonsensical: which trip "owns" June 3rd? This change adds an overlap rule across
all of a user's trips.

## The Rule

A trip is rejected — on both **create** and **update** — if its
`[startDate, endDate]` range overlaps the range of **any** other trip the user
already participates in.

- Two ranges `[s1, e1]` and `[s2, e2]` overlap when `s1 <= e2 AND s2 <= e1`.
  Boundaries are **inclusive**: trips that merely touch on a single day (one ends
  June 5th, another starts June 5th) count as overlapping.
- On **update**, the trip being edited is excluded from the comparison so it
  never conflicts with itself — otherwise saving a trip without changing its
  dates would report a false overlap.
- **Scope is per user, across all roles.** The check spans every trip linked to
  the user through `UserTrip` — owned trips and trips they were invited to alike
  — not only trips they own. The reasoning is physical: you can't be in two
  places at once regardless of who created the trip.
- Trips with `null` dates never conflict (JPQL comparisons against `NULL` yield
  UNKNOWN, so those rows are skipped). In practice both dates are `@NotNull` on
  `CreateTripDTO`, so this only guards against legacy/partial rows.

## Enforcement: Application Level

The rule is enforced in `TripService.createTrip` with a repository existence
query before saving — **not** with a database constraint.

This matches the sibling `InvalidDateRangeException` validation right next to it,
and matches how `TripDay` already enforces its unique-date rule (see
`trip-day-unique-dates.md`). A range-overlap rule can't be expressed with a plain
unique index anyway — it would need a PostgreSQL exclusion constraint (GiST),
which is heavier than this app needs. The trade-off: two simultaneous create
requests could each pass the check and both insert, slipping an overlap through
the tiny race window. That is acceptable for this app's single-user-editing
usage.

## Error: 409 Conflict With a Structured Code

An overlapping trip is a conflict with existing data, so it is surfaced the same
way as day and invite conflicts: HTTP **409** with a structured error `code` the
frontend can branch on, rather than a plain 400 message.

A new exception mirrors the existing `DayConflictException`:

```java
public class TripConflictException extends RuntimeException {

    public enum Code {
        OVERLAPPING_DATES
    }

    private final Code code;

    public TripConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
```

The `Code` enum (rather than a single-purpose exception) leaves room for future
trip-level conflicts without adding new exception types.

## Repository

Two `@Query` methods on `TripRepository`. A derived method name can't express the
overlap predicate, so the JPQL is written out, joining trips through `UserTrip`:

```java
@Query("""
        SELECT (COUNT(ut) > 0)
        FROM UserTrip ut
        WHERE ut.user.id = :userId
          AND ut.trip.startDate <= :endDate
          AND ut.trip.endDate >= :startDate
        """)
boolean existsOverlappingTripForUser(@Param("userId") Long userId,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

@Query("""
        SELECT (COUNT(ut) > 0)
        FROM UserTrip ut
        WHERE ut.user.id = :userId
          AND ut.trip.id <> :tripId
          AND ut.trip.startDate <= :endDate
          AND ut.trip.endDate >= :startDate
        """)
boolean existsOverlappingTripForUserExcludingTrip(@Param("userId") Long userId,
                                                  @Param("tripId") Long tripId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
```

The query starts from `UserTrip` (rather than `Trip`) because ownership lives in
that join table — a `Trip` has no direct user column. Counting `UserTrip` rows
for the user and navigating `ut.trip.startDate / ut.trip.endDate` scopes the
overlap to that user's trips. The `...ExcludingTrip` variant adds
`ut.trip.id <> :tripId` so an update doesn't flag the trip being edited against
itself — the same role the `...AndIdNot` query plays for trip days.

## Service Checks

One new block in each method, placed immediately after the existing range check
(the range check is a cheap in-memory comparison; the overlap check costs a
query, so it runs second) and before `tripRepository.save(trip)`.

`createTrip` — the new trip is not yet persisted, so a plain overlap check
against the user's existing trips is correct:

```java
if (tripRepository.existsOverlappingTripForUser(
        currentUser.getId(), trip.getStartDate(), trip.getEndDate())) {
    throw new TripConflictException(
            TripConflictException.Code.OVERLAPPING_DATES,
            "You already have a trip during these dates");
}
```

`updateTrip` — runs on the new dates (after `tripMapper.updateEntity`) and must
exclude the trip being edited via `tripId`. It is also guarded on non-null dates,
matching the range check it sits beside (`UpdateTripDTO` allows partial updates):

```java
if (trip.getStartDate() != null && trip.getEndDate() != null
        && tripRepository.existsOverlappingTripForUserExcludingTrip(
                currentUser.getId(), tripId, trip.getStartDate(), trip.getEndDate())) {
    throw new TripConflictException(
            TripConflictException.Code.OVERLAPPING_DATES,
            "You already have a trip during these dates");
}
```

## Exception Handler

One new handler in `GlobalExceptionHandler`, a copy of the day-conflict
handler — 409 with the code name and message:

```java
@ExceptionHandler(TripConflictException.class)
public ResponseEntity<ErrorResponse> handleTripConflict(TripConflictException ex) {
    ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(), ex.getCode().name(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

## API Impact

- **Request bodies**: unchanged. `CreateTripDTO` / `UpdateTripDTO` keep the same
  fields and validation.
- **Response bodies**: unchanged on success. On an overlapping range, `POST
  /trips` and the trip-update endpoint now return:

  ```json
  { "status": 409, "code": "OVERLAPPING_DATES", "message": "You already have a trip during these dates" }
  ```

  The frontend can switch on `code === "OVERLAPPING_DATES"` to show a targeted
  message, the same way it already handles the day and invite conflict codes.

## Scope: Per User, Across Roles

The rule is scoped per acting user, across every trip they belong to via
`UserTrip`. On a **shared** trip, the check uses `currentUser` (the editor), so it
catches overlaps with the editor's own other trips but not with another member's
unrelated trips. This matches how the rest of the system enforces the constraint
only at the acting user's boundary — accepting an invite, for example, does not
run an overlap check. Full multi-member enforcement is out of scope.

## Files Touched

| File | Change |
|------|--------|
| `exception/TripConflictException.java` | **new** — exception + `Code` enum |
| `repository/TripRepository.java` | add two overlap `@Query` methods (plain + `...ExcludingTrip`) |
| `service/TripService.java` | overlap check in `createTrip` and `updateTrip` |
| `exception/GlobalExceptionHandler.java` | add `TripConflictException` handler |

## Design Decisions

1. **Application check, not a database constraint** — keeps the rule next to the
   sibling range validation and matches the `TripDay` precedent. A range-overlap
   rule needs a PostgreSQL exclusion constraint, which is heavier than this app
   requires. The cost is a small concurrency race window the app's usage does not
   exercise.

2. **409 with a structured code, not a 400 message** — an overlapping trip is a
   conflict with existing data, so it is modelled like the day and invite
   conflicts the API already exposes, giving the frontend a stable `code`.

3. **Overlap, not exact-match** — blocking only identical date ranges would still
   allow nonsensical partial overlaps. The `s1 <= e2 AND s2 <= e1` predicate
   captures every overlapping case, including one range fully containing another.

4. **Scoped per user, across all roles** — the rule reflects a physical
   constraint on the person, so it spans every trip they belong to via
   `UserTrip`, not just trips they own.

5. **Enforce on update as well as create** — a create-only check would be half a
   rule; editing a trip's dates to overlap another trip is just as much a
   conflict and is closed off with the `...ExcludingTrip` query (mirroring the
   `...AndIdNot` query used for trip days).
