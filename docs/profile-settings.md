# Profile Settings

Design documentation for the backend half of the profile-settings feature:
letting a logged-in user **change their password** and **manage their travel
preferences** (a set of `Interest` values stored per user).

The frontend counterpart (a `/settings` page plus a read-only "Travel style"
section on `/profile`) is specced in `planner-frontend/docs/profile-settings.md`.
This document covers only the API contract that spec depends on.

## Motivation

The `/profile` page is read-only today. Two pieces were deferred because they
needed backend support:

1. **Change password** — there is currently no endpoint to mutate a user. The
   only user-facing write is registration.
2. **Travel preferences** — we want to remember which `Interest`s a user cares
   about. The `Interest` enum already exists and is used on trips
   (`Trip.interests`), so we mirror that pattern on `User`.

There is also a latent bug to fix along the way: `GET /users/me` returns the
whole `User` JPA entity, so Jackson serializes the **BCrypt password hash** in
the response (only `userTrips` carries `@JsonIgnore`). Switching `/me` to a
trimmed DTO removes the leak and gives us a natural place to expose
`preferredInterests`.

## Endpoints

All live on the existing `UserController` (`@RequestMapping("/users")`):

| Method | Path | Body | Success | Notes |
|--------|------|------|---------|-------|
| GET | `/users/me` | — | `200` `UserResponse` | now a DTO, not the entity |
| PUT | `/users/me/password` | `ChangePasswordDTO` | `204 No Content` | empty body on success |
| PUT | `/users/me/preferences` | `UpdatePreferencesDTO` | `200` `UserResponse` | full replace of the set |

The caller is always taken from the JWT (via `SecurityContextHolder`, the same
way the existing `/me` does), never from the path or body — a user can only ever
read or mutate their own account.

## Entity: `User.preferredInterests`

A new `@ElementCollection`, an exact mirror of `Trip.interests`
(`model/Trip.java`), backed by its own join table:

```java
@ElementCollection
@CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
@Enumerated(EnumType.STRING)
@Column(name = "interest")
private Set<Interest> preferredInterests = new HashSet<>();
```

The same change also adds `@JsonIgnore` to the existing `password` field
(see Design Decisions #2).

`Interest` lives in `model/Enums/Interest.java`: `CULTURE`, `FOOD`, `ADVENTURE`,
`NATURE`, `NIGHTLIFE`, `SHOPPING`, `RELAXATION`, `HISTORY`.

## Migration: `V4__add_user_interests.sql`

The schema is Flyway-managed with `ddl-auto=validate`, so the new entity needs a
matching table or the app will not start. We mirror the `trip_interests` table
from `V1__baseline.sql` — table, enum CHECK constraint, and FK to `users`:

```sql
CREATE TABLE public.user_interests (
    user_id bigint NOT NULL,
    interest character varying(255),
    CONSTRAINT user_interests_interest_check CHECK (
        (interest)::text = ANY ((ARRAY[
            'CULTURE','FOOD','ADVENTURE','NATURE',
            'NIGHTLIFE','SHOPPING','RELAXATION','HISTORY'
        ])::text[])
    )
);

ALTER TABLE ONLY public.user_interests
    ADD CONSTRAINT fk_user_interests_user
    FOREIGN KEY (user_id) REFERENCES public.users(id);
```

`V1`–`V3` already exist, so `V4` is the next free version.

## DTOs

Two new request DTOs in `DTO/`, following the validation style of the existing
`RegisterUserDTO`:

```java
// ChangePasswordDTO
@NotBlank(message = "Current password is required")
private String currentPassword;

@NotBlank(message = "New password is required")
@Size(min = 8, message = "Password must be at least 8 characters")
private String newPassword;
```

```java
// UpdatePreferencesDTO  — null/empty interests clears all preferences
private Set<Interest> interests;
```

`UpdatePreferencesDTO` carries no bean-validation constraints; unknown enum
values are already rejected by Jackson at deserialization time.

## Response: `UserResponse`

`responses/UserResponse.java` already exists (Lombok `@Builder`, fields
`id`/`fullName`/`email`) and is reused by `GET /users/search`. We extend it
rather than introduce a new class:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)   // omitted from /search, which never sets it
private Set<Interest> preferredInterests;

public static UserResponse from(User u) {
    return UserResponse.builder()
            .id(u.getId())
            .fullName(u.getFullName())
            .email(u.getEmail())
            .preferredInterests(u.getPreferredInterests())
            .build();
}
```

`GET /users/me` and `PUT /users/me/preferences` build their response via
`from(...)`, so they include the interests (an empty set serializes as `[]`).
`GET /users/search` keeps its existing manual builder that sets only
`id`/`fullName`/`email`, leaving `preferredInterests` null — and the
`@JsonInclude(NON_NULL)` drops the field entirely from search results (see
Design Decisions #1).

## Service: `UserService`

`UserService` gains a constructor-injected `PasswordEncoder` (the bean already
exists in `ApplicationConfiguration`; it was only wired into
`AuthenticationService` before) and two `@Transactional` methods:

```java
public void changePassword(User user, ChangePasswordDTO dto) {
    if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
        throw new InvalidPasswordException("Current password is incorrect");
    }
    user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
    userRepository.save(user);
}

public UserResponse updatePreferences(User user, Set<Interest> interests) {
    user.setPreferredInterests(interests != null ? interests : new HashSet<>());
    userRepository.save(user);
    return UserResponse.from(user);
}
```

`updatePreferences` is a **full replace** of the set, which matches PUT
semantics: the request body is the new complete state, not a delta.

## Error: 400 With a Structured Code

A wrong current password is a client error, but it must be distinguishable from
ordinary bean-validation failures so the frontend can render the message under
the *current password* field instead of as a generic error. We surface it the
same way the invite/day/member conflicts are surfaced — an HTTP status plus a
structured `code` the frontend can branch on — using the existing
`ErrorResponse(status, code, message)` constructor.

A new exception:

```java
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
```

And one handler in `GlobalExceptionHandler`, mirroring the existing conflict
handlers but returning **400**:

```java
@ExceptionHandler(InvalidPasswordException.class)
public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex) {
    ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(), "INVALID_CURRENT_PASSWORD", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

This sits next to the existing `MethodArgumentNotValidException` handler, which
returns 400 with a `fieldErrors` map — so the frontend tells "wrong current
password" (`code`) apart from "field failed validation" (`fieldErrors`).

## API Impact

- **`GET /users/me`** now returns:

  ```json
  { "id": 1, "fullName": "Ana Anić", "email": "ana@example.com", "preferredInterests": ["FOOD", "NATURE"] }
  ```

  No `password` field. A user with no saved interests gets `"preferredInterests": []`.

- **`PUT /users/me/password`** — `204 No Content` on success. Wrong current
  password:

  ```json
  { "status": 400, "code": "INVALID_CURRENT_PASSWORD", "message": "Current password is incorrect" }
  ```

  A blank/too-short new password is the usual bean-validation `400` with
  `fieldErrors`.

- **`PUT /users/me/preferences`** — `200` with the updated `UserResponse`.
  An empty `interests` array clears all preferences.

- **`GET /users/search`** — unchanged in practice: the response still contains
  only `id`/`fullName`/`email`; `preferredInterests` is omitted.

The JWT remains valid after a password change. The design is stateless with no
token versioning, so we deliberately do not force a re-login — consistent with
existing behavior.

## Files Touched

| File | Change |
|------|--------|
| `model/User.java` | add `preferredInterests` `@ElementCollection`; add `@JsonIgnore` on `password` |
| `db/migration/V4__add_user_interests.sql` | **new** — `user_interests` table + CHECK + FK |
| `DTO/ChangePasswordDTO.java` | **new** — `currentPassword` + validated `newPassword` |
| `DTO/UpdatePreferencesDTO.java` | **new** — `Set<Interest> interests` |
| `responses/UserResponse.java` | add `preferredInterests` (`@JsonInclude(NON_NULL)`) + `from(User)` |
| `service/UserService.java` | inject `PasswordEncoder`; add `changePassword` + `updatePreferences` |
| `controller/UserController.java` | `/me` returns `UserResponse`; add `PUT /me/password`, `PUT /me/preferences` |
| `exception/InvalidPasswordException.java` | **new** — runtime exception |
| `exception/GlobalExceptionHandler.java` | add `InvalidPasswordException` handler (400 + code) |

## Design Decisions

1. **Shared `UserResponse`, but `/search` stays lean** — adding the field to the
   existing DTO avoids a near-duplicate class. The concern is that `/search`
   (used to find users to invite) would then expose every user's interests.
   Whether it does is purely a function of the DTO, not the entity: `/search`
   keeps building only `id`/`fullName`/`email`, and `@JsonInclude(NON_NULL)`
   drops the unset `preferredInterests` from its JSON. So `/me` shows interests,
   `/search` doesn't — without a second response class. `/me` always sets the
   field (empty → `[]`), so the omission only ever affects `/search`.

2. **DTO *and* `@JsonIgnore` on `password`** — switching `/me` to `UserResponse`
   fixes the immediate hash leak, but the entity could still be serialized by
   some future endpoint. Annotating `User.password` with `@JsonIgnore` is a
   one-line, defense-in-depth guarantee that the hash is never written to any
   JSON response, regardless of who returns the entity.

3. **`InvalidPasswordException` → 400 with a code, not a bare 400** — a wrong
   current password and a malformed new password are both client errors, but the
   frontend needs to place them differently in the form. A structured `code`
   (mirroring the existing conflict exceptions) gives a stable branch point and
   keeps it out of the `fieldErrors` channel.

4. **`PUT` preferences is a full replace, not a merge** — PUT semantics: the
   body is the complete new set. This makes "clear all preferences" simply an
   empty array, with no separate delete endpoint.

5. **No forced re-login after a password change** — the system is stateless JWT
   with no token versioning. Invalidating issued tokens would require new
   machinery (a token version or a denylist); we accept that existing sessions
   stay valid until natural expiry, matching current behavior. Worth revisiting
   if/when token revocation is added.

## Verification (end-to-end)

1. `./gradlew bootRun` — Flyway applies `V4` and the app starts cleanly, proving
   the entity matches the new table under `ddl-auto=validate`.
2. Logged in:
   - `GET /users/me` → `id`/`fullName`/`email`/`preferredInterests`, **no `password`**.
   - `PUT /users/me/preferences` `{ "interests": ["FOOD","NATURE"] }` → `200`, returns the set;
     `GET /users/me` again shows it persisted. `{ "interests": [] }` clears it.
   - `PUT /users/me/password` with wrong `currentPassword` → `400` `INVALID_CURRENT_PASSWORD`;
     with the correct one → `204`, and login with the new password works.
   - `GET /users/search?email=<other user>` → response has **no `preferredInterests` key**.
3. DB: `user_interests(user_id, interest)` exists and rows reflect the saved set.
