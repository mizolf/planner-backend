# Trip Day Unique Dates

Design documentation for rejecting two days within the same trip that share the
same date.

## Motivation

A `TripDay` already validates that its `date` falls **within** the trip's
`startDate` / `endDate` range (`InvalidDateRangeException`), but nothing stops
two days in the same trip from carrying the **same** date. A trip's days are
meant to be distinct positions on the calendar — "day 1 = May 3rd, day 2 = May
4th". Two days on May 3rd makes the itinerary ambiguous: which "May 3rd" owns
which activities? This change adds a uniqueness rule on `(trip, date)`.

## The Rule

Within one trip, no two `TripDay` rows may share the same **non-null** `date`.

- A `null` date never conflicts. This is consistent with the existing range
  check, which also skips `null` dates (`date` is nullable on `TripDay`).
- The rule is enforced on **both** `addDay` and `updateDay` — otherwise a user
  could edit an existing day's date onto another day's date and slip a duplicate
  past an add-only check.

## Enforcement: Application Level

The rule is enforced in `TripDayService` with a repository existence query
before saving — **not** with a database unique constraint.

This matches the existing `InvalidDateRangeException` validation that lives
right next to it, and avoids a new Flyway migration (a partial unique index over
a nullable column is fiddly). The trade-off: two simultaneous requests could
each pass the check and both insert, slipping a duplicate through the tiny race
window. That is acceptable for this app's single-user-editing usage; a database
constraint can be added later if concurrent editing ever becomes real.

## Error: 409 Conflict With a Structured Code

A duplicate date is a conflict with existing data, so it is surfaced the same
way as invite conflicts: HTTP **409** with a structured error `code` the
frontend can branch on, rather than a plain 400 message.

A new exception mirrors the existing `InviteConflictException`:

```java
public class DayConflictException extends RuntimeException {

    public enum Code {
        DUPLICATE_DATE
    }

    private final Code code;

    public DayConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
```

The `Code` enum (rather than a single-purpose exception) leaves room for future
day-level conflicts without adding new exception types.

## Repository

Two derived query methods on `TripDayRepository` — Spring Data generates both
from their names, no SQL required:

```java
boolean existsByTripIdAndDate(Long tripId, LocalDate date);
boolean existsByTripIdAndDateAndIdNot(Long tripId, LocalDate date, Long id);
```

The `...AndIdNot` variant excludes the day currently being edited, so an update
that leaves a day's date unchanged does not report the day as conflicting with
itself.

## Service Checks

One new block in each method, placed immediately after the existing range check
(the range check is a cheap in-memory comparison; the duplicate check costs a
query, so it runs second).

`addDay` — the new day is not yet persisted, so a plain existence check against
the trip's existing days is correct:

```java
if (day.getDate() != null
        && tripDayRepository.existsByTripIdAndDate(tripId, day.getDate())) {
    throw new DayConflictException(
            DayConflictException.Code.DUPLICATE_DATE,
            "Another day already uses this date");
}
```

`updateDay` — must exclude the day being edited via `dayId`:

```java
if (day.getDate() != null
        && tripDayRepository.existsByTripIdAndDateAndIdNot(tripId, day.getDate(), dayId)) {
    throw new DayConflictException(
            DayConflictException.Code.DUPLICATE_DATE,
            "Another day already uses this date");
}
```

## Exception Handler

One new handler in `GlobalExceptionHandler`, a copy of the invite-conflict
handler — 409 with the code name and message:

```java
@ExceptionHandler(DayConflictException.class)
public ResponseEntity<ErrorResponse> handleDayConflict(DayConflictException ex) {
    ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(), ex.getCode().name(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

## API Impact

- **Request bodies**: unchanged. `CreateTripDayDTO` / `UpdateTripDayDTO` keep the
  same fields and validation.
- **Response bodies**: unchanged on success. On a duplicate date, `POST
  /trips/{tripId}/days` and the day-update endpoint now return:

  ```json
  { "status": 409, "code": "DUPLICATE_DATE", "message": "Another day already uses this date" }
  ```

  The frontend can switch on `code === "DUPLICATE_DATE"` to show a targeted
  message, the same way it already handles the invite conflict codes.

## Files Touched

| File | Change |
|------|--------|
| `exception/DayConflictException.java` | **new** — exception + `Code` enum |
| `repository/TripDayRepository.java` | add two `existsBy...` methods |
| `service/TripDayService.java` | duplicate check in `addDay` and `updateDay` |
| `exception/GlobalExceptionHandler.java` | add `DayConflictException` handler |

## Design Decisions

1. **Application check, not a database constraint** — keeps the rule next to the
   sibling range validation, needs no migration, and sidesteps the awkwardness
   of a unique index over a nullable column. The cost is a small concurrency
   race window, which this app's usage does not exercise.

2. **409 with a structured code, not a 400 message** — a duplicate date is a
   conflict with existing data, so it is modelled like the invite conflicts the
   API already exposes, giving the frontend a stable `code` to branch on.

3. **Enforce on update as well as add** — an add-only check would be half a
   rule; editing a day's date onto another day's date is just as much a
   duplicate and is closed off with the `...AndIdNot` query.

4. **`null` dates never conflict** — a day without a date is not yet placed on
   the calendar, so it cannot collide with anything; this also matches how the
   existing range check treats `null`.
