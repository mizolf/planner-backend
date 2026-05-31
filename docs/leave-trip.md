# Leave Trip

Design documentation for letting a member voluntarily remove **their own**
membership from a trip.

## Motivation

The API already has `DELETE /trips/{tripId}/members/{userId}`, but it is an
**owner-only** action and it explicitly *refuses* self-removal
(`"Cannot remove yourself from the trip"`). That endpoint answers "the owner
kicks someone out", not "I want out". A member who no longer wants to be on a
trip has no way to leave on their own today.

This change adds a separate, member-initiated action: leaving a trip.

## The Rule

Any member **except the owner** can remove their own membership.

- A non-owner member (EDITOR or VIEWER) leaving deletes their `UserTrip` row.
  The trip and its data are untouched; they simply lose access.
- The **owner cannot leave**. A trip must always have an owner, and there is no
  ownership-transfer feature yet. The owner's way out is to delete the whole
  trip via the existing `DELETE /trips/{tripId}`.

## Endpoint

```
DELETE /trips/{tripId}/members/me   → 204 No Content
```

Added to the existing `TripMemberController`
(`@RequestMapping("/trips/{tripId}/members")`), alongside the owner's
`DELETE /{userId}`. The two do not collide: Spring ranks the literal segment
`/me` as more specific than the `/{userId}` variable, so a request to
`.../members/me` is always routed to the leave handler rather than being bound
(and failing) as a numeric `userId`.

`me` is the well-known self alias — the caller is taken from the JWT, never from
the path, so one member can never leave on another member's behalf.

```java
@DeleteMapping("/me")
public ResponseEntity<Void> leaveTrip(@PathVariable Long tripId) {
    tripMemberService.leaveTrip(tripId, getCurrentUser());
    return ResponseEntity.noContent().build();
}
```

## Service: `TripMemberService.leaveTrip`

A new method sits next to `removeMember`. It reuses the existing authorization
helper to both verify membership and fetch the row in one step:

```java
@Transactional
public void leaveTrip(Long tripId, User currentUser) {
    UserTrip membership = authorizationService.validateMembership(tripId, currentUser);

    if (membership.getRole() == TripRole.OWNER) {
        throw new MemberConflictException(
                MemberConflictException.Code.OWNER_CANNOT_LEAVE,
                "The trip owner cannot leave; transfer ownership or delete the trip");
    }

    eventPublisher.publishEvent(new TripEventRecorded(
            membership.getTrip(), currentUser,
            TripEventType.MEMBER_LEFT, TripEventEntityType.MEMBER,
            currentUser.getId(), currentUser.getFullName(), null));

    userTripRepository.delete(membership);
}
```

Flow:

1. `validateMembership` returns the caller's `UserTrip`, or throws
   `ResourceNotFoundException` (→ **404**) if the caller is not a member.
2. If the caller is the OWNER → `MemberConflictException` (→ **409**).
3. Otherwise publish a `MEMBER_LEFT` event and delete the membership row.

For a leave, the **actor and the target are the same person** — `currentUser`
is both who performed the action and who left.

## Error: 409 Conflict With a Structured Code

The owner being blocked is a **state conflict**, not a permission failure — the
owner has the *most* access of anyone, so a 403 ("you lack permission") would be
misleading. It is surfaced the same way as the existing invite and day
conflicts: HTTP **409** with a structured `code` the frontend can branch on.

A new exception mirrors `InviteConflictException` / `DayConflictException`:

```java
public class MemberConflictException extends RuntimeException {

    public enum Code {
        OWNER_CANNOT_LEAVE
    }

    private final Code code;

    public MemberConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
```

The `Code` enum (rather than a single-purpose exception) leaves room for future
member-level conflicts without adding new exception types.

## Event Type

A new `MEMBER_LEFT` value is added to `TripEventType`:

```java
MEMBER_ADDED,
MEMBER_LEFT,          // new
MEMBER_ROLE_CHANGED,
MEMBER_REMOVED,
```

The backend passes the event type straight through `ActivityFeedMapper` to the
response; the human-readable wording ("Ana left the trip") is rendered on the
frontend. Keeping `MEMBER_LEFT` distinct from `MEMBER_REMOVED` lets the feed
tell a voluntary departure apart from an owner kicking someone out.

**Database constraint:** the schema is Flyway-managed (`ddl-auto=validate`), and
the `trip_events` table has a `trip_events_event_type_check` CHECK constraint
that lists the allowed enum strings. Adding a value to the Java enum is *not*
enough — without a matching migration, inserting a `MEMBER_LEFT` event is
rejected (Postgres error `23514`), which rolls back the whole `leaveTrip`
transaction (the event is persisted by a `BEFORE_COMMIT` listener in the same
transaction), so the member is never actually removed. A Flyway migration must
extend the constraint.

## Exception Handler

One new handler in `GlobalExceptionHandler`, a copy of the invite/day conflict
handlers — 409 with the code name and message:

```java
@ExceptionHandler(MemberConflictException.class)
public ResponseEntity<ErrorResponse> handleMemberConflict(MemberConflictException ex) {
    ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(), ex.getCode().name(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

## API Impact

- **Request body**: none. The caller is identified by the JWT.
- **Success**: `204 No Content`, empty body.
- **Not a member**: `404 Not Found` (existing `ResourceNotFoundException` shape).
- **Owner tries to leave**: `409 Conflict`:

  ```json
  { "status": 409, "code": "OWNER_CANNOT_LEAVE", "message": "The trip owner cannot leave; transfer ownership or delete the trip" }
  ```

  The frontend can switch on `code === "OWNER_CANNOT_LEAVE"` to point the owner
  at "delete the trip", the same way it handles the invite and day conflict
  codes.

## Tests

Unit tests for `TripMemberService.leaveTrip`, mocking `UserTripRepository`,
`TripAuthorizationService`, and `ApplicationEventPublisher`:

| Case | Expectation |
|------|-------------|
| EDITOR/VIEWER leaves | membership deleted, one `MEMBER_LEFT` event published |
| OWNER leaves | `MemberConflictException` (`OWNER_CANNOT_LEAVE`), nothing deleted, no event |
| Caller is not a member | `ResourceNotFoundException` propagates (from `validateMembership`) |

The project currently has minimal service-level test coverage, so these
establish the pattern for `TripMemberService`.

## Files Touched

| File | Change |
|------|--------|
| `exception/MemberConflictException.java` | **new** — exception + `Code` enum |
| `model/Enums/TripEventType.java` | add `MEMBER_LEFT` |
| `db/migration/V3__add_member_left_event_type.sql` | **new** — add `MEMBER_LEFT` to the `trip_events_event_type_check` constraint |
| `service/TripMemberService.java` | add `leaveTrip` method |
| `controller/TripMemberController.java` | add `DELETE /me` mapping |
| `exception/GlobalExceptionHandler.java` | add `MemberConflictException` handler |
| `docs/trip-model.md` | add the leave endpoint to the Member Management table |

## Design Decisions

1. **Separate endpoint, not a relaxed `removeMember`** — leaving and being
   removed are different actions with different authorization (self vs owner) and
   different feed semantics. Folding self-removal into the owner-only endpoint
   would tangle two authorization paths in one method.

2. **`DELETE /members/me`, not `POST /leave`** — keeps the action under the
   existing members resource and reads as the symmetric counterpart to the
   owner's `DELETE /members/{userId}`, rather than introducing a verb endpoint
   that breaks the API's REST convention.

3. **Owner is blocked, not auto-transferred** — a trip must always have an
   owner. Auto-transfer needs a selection rule and a "no other members" branch;
   that is more machinery than this feature warrants while no
   ownership-transfer feature exists. The owner already has a clean exit:
   delete the trip.

4. **409 with a structured code, not 403** — the owner block is a conflict with
   the trip's state, not a lack of permission. Modelling it like the existing
   invite/day conflicts gives the frontend a stable `code` to branch on.

5. **Distinct `MEMBER_LEFT` event** — reusing `MEMBER_REMOVED` would make the
   feed claim a self-departing member was "removed". A dedicated type is cheap
   on the backend (the wording lives on the frontend) and keeps the feed honest.
