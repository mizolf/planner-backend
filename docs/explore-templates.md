# Explore Templates

Architecture documentation for the trip template catalogue — a curated, read-mostly set of pre-built trip ideas that users can browse and apply to start a new trip.

## Overview

A user starting from an empty dashboard has no inspiration. Explore Templates adds a discovery surface: a catalogue of pre-built trips grouped by **style** (Nightlife, Relax, Weekend Escape, …). Each style holds multiple **templates** — concrete trips with destination, recommended season, duration, and a full day-by-day itinerary. A user picks a template they like, supplies a start date, and the backend materializes a new `Trip` owned by them with all days and activities copied from the template.

Three endpoints expose the catalogue:
- **List styles:** `GET /explore/styles` — all trip styles with summary info.
- **Style detail:** `GET /explore/styles/{styleSlug}` — one style with its template list.
- **Template detail:** `GET /explore/styles/{styleSlug}/templates/{templateSlug}` — full itinerary.

One endpoint applies a template:
- **Apply:** `POST /explore/styles/{styleSlug}/templates/{templateSlug}/apply` — creates a new `Trip` owned by the caller.

**Curation:** templates live in `src/main/resources/templates.json` and are loaded by a startup seeder. There are no admin CRUD endpoints today; the catalogue is editorial content shipped with the application.

**Auth:** read endpoints (`GET`) are **public** — anonymous users can browse the catalogue. The apply endpoint (`POST`) requires a JWT because it creates a `Trip` owned by the caller. This matches discovery-surface conventions (browse is open, taking action requires an account).

## Entity Relationship Diagram

```
TripStyle ──< TripTemplate ──< TemplateDay ──< TemplateActivity
 (style)      (template)        (day)           (activity)
```

- One TripStyle has many TripTemplates.
- One TripTemplate has many TemplateDays.
- One TemplateDay has many TemplateActivities.

Applied trips are independent copies — no FK links back to templates. Editing or deleting a template does not touch any user-owned `Trip`.

## Entities

### TripStyle (table: `trip_styles`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| slug | String | unique, not null — stable identifier used in URLs and by the seeder (e.g. `nightlife`) |
| name | String | not null — display name (e.g. "Nightlife") |
| description | String | nullable |
| imageUrl | String | nullable |
| displayOrder | Integer | not null — controls catalogue ordering |

**Relationships:**
- `templates` → OneToMany to TripTemplate (cascade ALL, orphanRemoval), ordered by `displayOrder ASC`.

### TripTemplate (table: `trip_templates`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| slug | String | unique, not null (e.g. `ibiza-summer`) |
| name | String | not null |
| description | String | nullable |
| destination | String | not null |
| durationDays | Integer | not null, ≥ 1 |
| recommendedSeason | Season (enum) | not null, STRING |
| imageUrl | String | nullable |
| estimatedBudget | BigDecimal | precision=8, scale=2, nullable |
| interests | Set\<Interest\> | @ElementCollection → `trip_template_interests` table |
| displayOrder | Integer | not null |

**Relationships:**
- `style` → ManyToOne to TripStyle (LAZY, `style_id` FK, not null).
- `days` → OneToMany to TemplateDay (cascade ALL, orphanRemoval), ordered by `dayNumber ASC`.

### TemplateDay (table: `template_days`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| dayNumber | Integer | not null |
| notes | String | nullable |

**Relationships:**
- `tripTemplate` → ManyToOne to TripTemplate (LAZY, `trip_template_id` FK, not null).
- `activities` → OneToMany to TemplateActivity (cascade ALL, orphanRemoval), ordered by `startTime ASC`.

### TemplateActivity (table: `template_activities`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| name | String | not null |
| description | String | nullable |
| location | String | nullable |
| startTime | LocalTime | nullable |
| endTime | LocalTime | nullable |

**Relationships:**
- `templateDay` → ManyToOne to TemplateDay (LAZY, `template_day_id` FK, not null).

## Enums

### Season

`SPRING` | `SUMMER` | `AUTUMN` | `WINTER` | `YEAR_ROUND`

Used on `TripTemplate.recommendedSeason` to capture when a template is best done (e.g. Ibiza in `SUMMER`, a winter festival trip in `WINTER`).

### Interest (reused)

The existing `Interest` enum (`CULTURE`, `FOOD`, `ADVENTURE`, `NATURE`, `NIGHTLIFE`, `SHOPPING`, `RELAXATION`, `HISTORY`) is reused on `TripTemplate` via `@ElementCollection`, mirroring `Trip.interests`. When a template is applied, its interests are copied into the new `Trip`.

## Database Tables

Hibernate `ddl-auto=update` creates these five tables automatically:

1. **trip_styles** — style categories.
2. **trip_templates** — templates under each style.
3. **trip_template_interests** — enum collection table (trip_template_id, interest).
4. **template_days** — days within a template.
5. **template_activities** — activities within a template day.

## Seeder

`TripTemplateSeeder` (`service/TripTemplateSeeder.java`) is a `@Component` that listens for `ApplicationReadyEvent` and seeds the catalogue from `src/main/resources/templates.json`.

### Strategy

**Truncate-and-reload on every startup.** Templates are not referenced by user data — applied trips are independent copies — so wiping is safe and simpler than upsert logic.

Seeder steps (one `@Transactional` block):
1. Read `classpath:templates.json` via Jackson into a `TripStyleSeedData` POJO tree.
2. Delete in order: template activities → template days → trip template interests → trip templates → trip styles (children-first to honor FK constraints).
3. Insert styles → templates → days → activities from the JSON.
4. Log `Seeded N styles with M templates`.

If the file is missing or malformed, log a warning and skip — application startup is not blocked. Endpoints will simply return an empty catalogue.

### `templates.json` shape

```json
[
  {
    "slug": "nightlife",
    "name": "Nightlife",
    "description": "Clubs, festivals and late-night cities.",
    "imageUrl": "...",
    "displayOrder": 1,
    "templates": [
      {
        "slug": "ibiza-summer",
        "name": "Ibiza Summer Highlights",
        "destination": "Ibiza, Spain",
        "durationDays": 4,
        "recommendedSeason": "SUMMER",
        "interests": ["NIGHTLIFE", "FOOD"],
        "estimatedBudget": 1200.00,
        "displayOrder": 1,
        "days": [
          {
            "dayNumber": 1,
            "notes": "Arrival & Playa d'en Bossa",
            "activities": [
              { "name": "Check-in", "location": "Hotel", "startTime": "15:00", "endTime": "16:00" },
              { "name": "Ushuaïa opening party", "location": "Ushuaïa Ibiza", "startTime": "22:00", "endTime": "04:00" }
            ]
          }
        ]
      }
    ]
  }
]
```

Initial content covers three styles: **Nightlife**, **Relax**, **Weekend Escape**, with at least one or two templates each.

## Architecture

### Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `TripStyle` / `TripTemplate` / `TemplateDay` / `TemplateActivity` | `model/` | JPA entities for the catalogue |
| `Season` | `model/Enums/Season.java` | Recommended-season enum |
| `TripStyleRepository` | `repository/TripStyleRepository.java` | `findBySlug(String)` |
| `TripTemplateRepository` | `repository/TripTemplateRepository.java` | `findBySlugAndStyleId(String, Long)` |
| `TemplateDayRepository`, `TemplateActivityRepository` | `repository/` | Standard CRUD |
| `TripTemplateService` | `service/TripTemplateService.java` | `listStyles`, `getStyle`, `getTemplate`, `applyTemplate` |
| `TripTemplateSeeder` | `service/TripTemplateSeeder.java` | Loads `templates.json` on `ApplicationReadyEvent` |
| `TripTemplateController` | `controller/TripTemplateController.java` | `/explore` REST surface |
| Response DTOs | `responses/TripStyleResponse.java`, `TripStyleDetailResponse.java`, `TripTemplateSummaryResponse.java`, `TripTemplateDetailResponse.java`, `TemplateDayResponse.java`, `TemplateActivityResponse.java` | JSON shapes returned by the read endpoints. `TripTemplateSummaryResponse` is the embedded shape inside `TripStyleDetailResponse.templates`. |
| `ApplyTripTemplateDTO` | `DTO/ApplyTripTemplateDTO.java` | Request body for apply |
| Seed POJOs | `DTO/seed/TripStyleSeedData.java`, `TripTemplateSeedData.java`, `TemplateDaySeedData.java`, `TemplateActivitySeedData.java` | Jackson POJO tree matching `templates.json`. Kept in a sub-package because they have no validation and are not request bodies. |
| Mappers | `mapper/TripStyleMapper.java`, `TripTemplateMapper.java`, `TemplateDayMapper.java`, `TemplateActivityMapper.java` | Entity → response. Plain `@Component` classes (no MapStruct), matching existing `TripMapper` style. `TripStyleMapper` and `TripTemplateMapper` each expose `toSummary` and `toDetail` methods. |

### Apply Flow

`TripTemplateService.applyTemplate(styleSlug, templateSlug, dto, currentUser)` — `@Transactional`:

```
Resolve style by slug                                             → 404 if missing
Resolve template by slug + parent style id                        → 404 if missing

Build the Trip tree in memory (no save yet):
    Trip.name        = dto.name        ?? template.name
    Trip.description = template.description
    Trip.destination = template.destination
    Trip.startDate   = dto.startDate
    Trip.endDate     = startDate.plusDays(durationDays - 1)
    Trip.status      = PLANNING
    Trip.budget      = dto.budget      ?? template.estimatedBudget
    Trip.interests   = new HashSet<>(template.interests)
    For each TemplateDay (in dayNumber order):
        TripDay.dayNumber = sourceDay.dayNumber
        TripDay.date      = startDate.plusDays(dayNumber - 1)
        TripDay.notes     = sourceDay.notes
        TripDay.trip      = trip
        For each TemplateActivity:
            Activity copies name/description/location/startTime/endTime
            Activity.tripDay = newDay
        Attach activities to newDay
    Attach days to trip

tripRepository.save(trip)   // JPA cascade saves days + activities in one go
userTripRepository.save(UserTrip(user=currentUser, trip=trip, role=OWNER))
Publish TripEventRecorded(trip, currentUser, TRIP_CREATED, TRIP, trip.id, trip.name, null)
return tripMapper.toResponse(trip)
```

The whole `Trip → TripDay → Activity` tree is persisted via JPA cascade on a single `tripRepository.save(trip)`. This bypasses `TripDayService` and `ActivityService` (which would otherwise publish per-row `DAY_ADDED` / `ACTIVITY_ADDED` events) — applying a template emits a single `TRIP_CREATED` event in the activity feed.

### Design Decisions

1. **JSON seeder over admin CRUD** — the project has no `User.role` field today, and admin CRUD would require adding role-based authorization, an admin API, and probably an admin UI. Editorial templates change rarely; shipping them as content alongside the app is simpler and lets the catalogue evolve in code review. CRUD endpoints can be layered on later — same entities, same read endpoints.

2. **Truncate-and-reload seeding** — applied trips are independent copies, so templates can be wiped on every boot without risking user data. This avoids the complexity of upsert-by-slug logic for nested children (days/activities have no stable identifier across edits). Cost is rewriting a few hundred rows on startup, which is negligible.

3. **Two-level hierarchy: TripStyle → TripTemplate** — matches the user-facing concept ("Nightlife style *contains* Ibiza summer, Berlin winter festival, …"). A flat list with a style tag would lose the grouping that makes browsing meaningful.

4. **Separate template entities (not JSON column)** — mirrors `TripDay` / `Activity` so the apply flow is a clean entity-to-entity copy. No PostgreSQL-specific JSON type, queryable structure, normal Hibernate semantics.

5. **No FK from `Trip` back to template** — applied trips are fully independent. Editing a template never affects existing user trips, and deleting a template never cascades into user data.

6. **Single `TRIP_CREATED` event on apply** — applying builds the `Trip → TripDay → Activity` tree in memory and persists it through one cascading `tripRepository.save(trip)`. Cascade goes through Hibernate's persistence layer, not through `TripDayService` / `ActivityService`, so the per-row `DAY_ADDED` / `ACTIVITY_ADDED` events those services would emit never fire. A single event keeps the activity feed readable when a template is applied.

7. **Apply replicates ownership inline** — `TripTemplateService.applyTemplate` builds the `UserTrip(role=OWNER)` row itself rather than calling into `TripService.createTrip`, because `createTrip` accepts a `CreateTripDTO` and would not know about the pre-built days/activities. The four lines of duplication are accepted as the simpler trade-off; if more flows need this, a shared helper can be extracted from `TripService`.

8. **Slug-based URLs** — slugs are unique per style (and per template within a style) and are stable across reseeds. URLs like `/explore/styles/nightlife/templates/ibiza-summer/apply` are human-readable; numeric IDs are not exposed.

9. **`Season` enum, not free text** — fixed set of values is filterable and validatable. Special cases (specific events, exact dates) belong in the template's `description`.

## API

`GET` endpoints are public; the `POST` apply endpoint requires JWT authentication. See the [Auth note](#overview) above.

### List Styles

```
GET /explore/styles
```

**Response:** `List<TripStyleResponse>`

```json
[
  {
    "id": 1,
    "slug": "nightlife",
    "name": "Nightlife",
    "description": "Clubs, festivals and late-night cities.",
    "imageUrl": "...",
    "templateCount": 2
  },
  {
    "id": 2,
    "slug": "relax",
    "name": "Relax",
    "description": "Slow itineraries, beaches, quiet places.",
    "imageUrl": "...",
    "templateCount": 2
  }
]
```

### Get Style

```
GET /explore/styles/{styleSlug}
```

**Response:** `TripStyleDetailResponse` — style fields plus a list of template summaries (no day-level detail).

```json
{
  "id": 1,
  "slug": "nightlife",
  "name": "Nightlife",
  "description": "Clubs, festivals and late-night cities.",
  "imageUrl": "...",
  "templates": [
    {
      "id": 10,
      "slug": "ibiza-summer",
      "name": "Ibiza Summer Highlights",
      "destination": "Ibiza, Spain",
      "durationDays": 4,
      "recommendedSeason": "SUMMER",
      "imageUrl": "...",
      "estimatedBudget": 1200.00,
      "interests": ["NIGHTLIFE", "FOOD"]
    }
  ]
}
```

### Get Template

```
GET /explore/styles/{styleSlug}/templates/{templateSlug}
```

**Response:** `TripTemplateDetailResponse` — template fields plus full days and activities.

```json
{
  "id": 10,
  "slug": "ibiza-summer",
  "name": "Ibiza Summer Highlights",
  "description": "...",
  "destination": "Ibiza, Spain",
  "durationDays": 4,
  "recommendedSeason": "SUMMER",
  "imageUrl": "...",
  "estimatedBudget": 1200.00,
  "interests": ["NIGHTLIFE", "FOOD"],
  "days": [
    {
      "dayNumber": 1,
      "notes": "Arrival & Playa d'en Bossa",
      "activities": [
        { "name": "Check-in", "location": "Hotel", "startTime": "15:00", "endTime": "16:00" },
        { "name": "Ushuaïa opening party", "location": "Ushuaïa Ibiza", "startTime": "22:00", "endTime": "04:00" }
      ]
    }
  ]
}
```

### Apply Template

```
POST /explore/styles/{styleSlug}/templates/{templateSlug}/apply
```

**Request body:** `ApplyTripTemplateDTO`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| startDate | LocalDate | yes | Must be today or later. Validated with `@NotNull` + `@FutureOrPresent`. |
| name | String | no | Override template name; defaults to `template.name` |
| budget | BigDecimal | no | Override estimated budget; defaults to `template.estimatedBudget` |

**Authorization:** any authenticated user. The created `Trip` is owned by the caller (`UserTrip.role = OWNER`).

**Response:** `TripResponse` — same shape as `POST /trips`.

**Side effects:**
- New `Trip` row + `UserTrip(role=OWNER)` row.
- New `TripDay` rows (one per `TemplateDay`), with `date` derived from `startDate + dayNumber - 1`.
- New `Activity` rows (one per `TemplateActivity`), copying name/description/location/startTime/endTime.
- Single `TripEvent(eventType=TRIP_CREATED, entityType=TRIP)` recorded — visible in the per-trip and dashboard activity feeds.

**Error responses:**
- `404` (`ResourceNotFoundException`) — unknown style slug, or unknown template slug under that style. Handled centrally by `GlobalExceptionHandler`.
- `400` — `startDate` missing or in the past (Bean Validation fails on `ApplyTripTemplateDTO`), or `endDate` before `startDate` (defensive: would only happen if duration is corrupted).

## Edge Cases

1. **Missing or malformed `templates.json`** — seeder logs a warning and exits without touching the database. App still starts; the catalogue retains whatever it had before (empty on first boot, or the previous seed on subsequent boots). No exception escapes.
2. **Re-seed after edit** — editing `templates.json` and restarting wipes and reloads the catalogue. User trips already created from a previous template version are unaffected (independent copies).
3. **Applying twice** — re-applying the same template creates a second independent trip. Allowed by design; no uniqueness constraint between user and template.
4. **Template duration shorter than the day list in JSON** — seeder treats `durationDays` and `days.length` as independent fields. Authoring contract: keep them aligned. No runtime check today.
5. **Activity time ordering** — `@OrderBy("startTime ASC")` on `TemplateDay.activities` means null `startTime`s sort first; activities without times appear at the start of the day in the response.
6. **Slug collisions in JSON** — duplicate style slug or duplicate template slug under the same style will cause a unique-constraint violation at insert time. The seed transaction (truncate + insert in one atomic block) is rolled back, so the catalogue retains its previous state. The seeder logs the failure and the app still starts.
