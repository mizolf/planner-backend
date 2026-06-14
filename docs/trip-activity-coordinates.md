# Persist Trip & Activity Coordinates

## Why

The Angular frontend captures `latitude`/`longitude` for **trip destinations** and **activity
locations** via the Photon destination-autocomplete and already sends them in the create/update
JSON. This backend currently **drops** those fields (Jackson ignores unknown JSON properties), so:

- the activity edit dialog can't prefill a saved pin,
- the activity-search **bias** (which ranks suggestions near the trip) does nothing — it needs the
  *trip's* coordinates persisted and returned, and
- the planned Leaflet + OpenStreetMap map has nothing to plot.

This change persists `latitude`/`longitude` for **trips and activities** so the feature works
end-to-end. No geocoding happens on the backend — it only stores and returns the two numbers the
frontend already sends.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Scope | Trips **and** activities | Bias needs trip coords; prefill needs activity coords; one migration covers both |
| Type | `DOUBLE PRECISION` column ↔ `Double` field | Matches the frontend `number` and Photon's output; simpler than `BigDecimal`. 6-decimal precision ≈ 0.11 m; `double` exceeds that easily |
| Nullable | Yes | Free-text destinations/locations have no coords; existing rows have none |
| Update (PUT) rule | **Always replace** the coord pair (no null-check) | Lets the user clear a pin by retyping free text (frontend always sends both, as numbers or explicit `null`). Differs deliberately from the codebase's usual null-ignoring `updateEntity` style — see note below |
| Validation | Range checks on request DTOs | Reject out-of-range coordinates (`lat ∈ [-90,90]`, `lon ∈ [-180,180]`) |
| Change feed | `ChangeDetector` untouched | Coordinates are intentionally **not** logged as trip/activity changes (avoids "latitude 48.2 → 48.3" noise) |

> **Update-rule note.** Every other field in `TripMapper.updateEntity` / `ActivityMapper.updateEntity`
> is null-checked (`if (dto.getX() != null) ...`), i.e. `null` means "keep existing". Coordinates are
> the exception: they are assigned unconditionally so an explicit `null` **clears** them. This is safe
> because the only client (the frontend) always sends `latitude` **and** `longitude` together on every
> update. If a future caller sends an update that omits them, it would null them out — accepted.

## Stack

Spring Boot 4.0, Java 21, Gradle, PostgreSQL, **Flyway** (`spring.jpa.hibernate.ddl-auto=validate` —
schema is owned by migrations, entities must match). Lombok. Manual `@Component` mappers (not MapStruct),
so every mapped field is explicit.

## Changes

### 1. Flyway migration — `src/main/resources/db/migration/V7__add_trip_activity_coordinates.sql`
```sql
-- Trips and activities gain optional latitude/longitude captured from the Photon
-- destination autocomplete. Both nullable: existing rows have no coordinates.
ALTER TABLE public.trips      ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.trips      ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE public.activities ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.activities ADD COLUMN longitude DOUBLE PRECISION;
```

### 2. Entities (`model/Trip.java`, `model/Activity.java`)
```java
private Double latitude;
private Double longitude;
```

### 3. Request DTOs (`DTO/`) — `CreateTripDTO`, `UpdateTripDTO`, `CreateActivityDTO`, `UpdateActivityDTO`
```java
@DecimalMin(value = "-90",  message = "Latitude out of range")
@DecimalMax(value = "90",   message = "Latitude out of range")
private Double latitude;

@DecimalMin(value = "-180", message = "Longitude out of range")
@DecimalMax(value = "180",  message = "Longitude out of range")
private Double longitude;
```

### 4. Response DTOs (`responses/`) — `TripResponse`, `ActivityResponse`
Add `private Double latitude; private Double longitude;`. `TripDetailResponse` inherits from
`TripResponse`; nested activities flow through `ActivityResponse` — both covered.

### 5. Mappers (`mapper/`)
- **`TripMapper`** — `toEntity`, `toResponse`, `toDetailResponse`: add `.latitude(...).longitude(...)`.
  `updateEntity`: **always-assign** (no null-check):
  ```java
  trip.setLatitude(dto.getLatitude());
  trip.setLongitude(dto.getLongitude());
  ```
- **`ActivityMapper`** — same: `toEntity`, `toResponse`, and `updateEntity` always-assign.

### No changes
`ChangeDetector`, `TripController`, `ActivityController`, repositories, `application.properties`.

## Verification

1. `./gradlew build` — compiles; `ddl-auto=validate` makes a schema/entity mismatch fail fast.
2. Run the app → Flyway applies `V7`; startup succeeds.
3. `POST /trips` with `latitude`/`longitude` → `GET /trips/{id}` returns them; `SELECT latitude,
   longitude FROM trips` confirms persistence.
4. Add an activity with a picked suggestion → coords persisted; reload → edit dialog prefills the pin.
5. With trip coords now returned, the activity dialog biases Photon search toward the destination
   (no frontend change needed).
6. Edit an activity and retype free text over a picked location → `PUT` sends `null` coords →
   always-assign clears them (response/DB show null).
7. Existing trips/activities return `null` coords, nothing breaks.
8. `latitude: 200` in a request → `400 Bad Request` (range validation).
