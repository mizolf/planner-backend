# Explore Templates

Architecture documentation for the trip template catalogue — a curated, read-mostly set of pre-built trip ideas that users can browse and apply to start a new trip.

## Overview

A user starting from an empty dashboard has no inspiration. Explore Templates adds a discovery surface: a catalogue of pre-built trips grouped by **style** (Nightlife, Relax, Weekend Escape, …). Each style holds multiple **variants** — concrete trips with destination, recommended season, duration, and a full day-by-day itinerary. A user picks a variant they like, supplies a start date, and the backend materializes a new `Trip` owned by them with all days and activities copied from the template.

Three endpoints expose the catalogue:
- **List styles:** `GET /explore/templates` — all style templates with summary info.
- **Style detail:** `GET /explore/templates/{templateSlug}` — one style with its variant list.
- **Variant detail:** `GET /explore/templates/{templateSlug}/variants/{variantSlug}` — full itinerary.

One endpoint applies a variant:
- **Apply:** `POST /explore/templates/{templateSlug}/variants/{variantSlug}/apply` — creates a new `Trip` owned by the caller.

**Curation:** templates live in `src/main/resources/templates.json` and are loaded by a startup seeder. There are no admin CRUD endpoints today; the catalogue is editorial content shipped with the application.

**Auth:** all endpoints require a JWT, consistent with the rest of the API.

## Entity Relationship Diagram

```
TripTemplate ──< TemplateTrip ──< TemplateDay ──< TemplateActivity
   (style)         (variant)         (day)          (activity)
```

- One TripTemplate has many TemplateTrip variants.
- One TemplateTrip has many TemplateDays.
- One TemplateDay has many TemplateActivities.

Applied trips are independent copies — no FK links back to templates. Editing or deleting a template does not touch any user-owned `Trip`.

## Entities

### TripTemplate (table: `trip_templates`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated |
| slug | String | unique, not null — stable identifier used in URLs and by the seeder (e.g. `nightlife`) |
| name | String | not null — display name (e.g. "Nightlife") |
| description | String | nullable |
| imageUrl | String | nullable |
| displayOrder | Integer | not null — controls catalogue ordering |

**Relationships:**
- `variants` → OneToMany to TemplateTrip (cascade ALL, orphanRemoval), ordered by `displayOrder ASC`.

### TemplateTrip (table: `template_trips`)

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
| interests | Set\<Interest\> | @ElementCollection → `template_trip_interests` table |
| displayOrder | Integer | not null |

**Relationships:**
- `template` → ManyToOne to TripTemplate (LAZY, `template_id` FK, not null).
- `days` → OneToMany to TemplateDay (cascade ALL, orphanRemoval), ordered by `dayNumber ASC`.

### TemplateDay (table: `template_days`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK |
| dayNumber | Integer | not null |
| notes | String | nullable |

**Relationships:**
- `templateTrip` → ManyToOne to TemplateTrip (LAZY, `template_trip_id` FK, not null).
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

Used on `TemplateTrip.recommendedSeason` to capture when a variant is best done (e.g. Ibiza in `SUMMER`, a winter festival trip in `WINTER`).

### Interest (reused)

The existing `Interest` enum (`CULTURE`, `FOOD`, `ADVENTURE`, `NATURE`, `NIGHTLIFE`, `SHOPPING`, `RELAXATION`, `HISTORY`) is reused on `TemplateTrip` via `@ElementCollection`, mirroring `Trip.interests`. When a variant is applied, its interests are copied into the new `Trip`.

## Database Tables

Hibernate `ddl-auto=update` creates these five tables automatically:

1. **trip_templates** — style categories.
2. **template_trips** — variants under each style.
3. **template_trip_interests** — enum collection table (template_trip_id, interest).
4. **template_days** — days within a variant.
5. **template_activities** — activities within a template day.

## Seeder

`TripTemplateSeeder` (`service/TripTemplateSeeder.java`) is a `@Component` that listens for `ApplicationReadyEvent` and seeds the catalogue from `src/main/resources/templates.json`.

### Strategy

**Truncate-and-reload on every startup.** Templates are not referenced by user data — applied trips are independent copies — so wiping is safe and simpler than upsert logic.

Seeder steps (one `@Transactional` block):
1. Read `classpath:templates.json` via Jackson into a `TripTemplateSeedData` POJO tree.
2. Delete in order: template activities → template days → template trip interests → template trips → trip templates (children-first to honor FK constraints).
3. Insert templates → variants → days → activities from the JSON.
4. Log `Seeded N templates with M variants`.

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
    "variants": [
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

Initial content covers three styles: **Nightlife**, **Relax**, **Weekend Escape**, with at least one or two variants each.

## Architecture

### Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `TripTemplate` / `TemplateTrip` / `TemplateDay` / `TemplateActivity` | `model/` | JPA entities for the catalogue |
| `Season` | `model/Enums/Season.java` | Recommended-season enum |
| `TripTemplateRepository` | `repository/TripTemplateRepository.java` | `findBySlug(String)` |
| `TemplateTripRepository` | `repository/TemplateTripRepository.java` | `findBySlugAndTemplateId(String, Long)` |
| `TemplateDayRepository`, `TemplateActivityRepository` | `repository/` | Standard CRUD |
| `TripTemplateService` | `service/TripTemplateService.java` | `listTemplates`, `getTemplate`, `getVariant`, `applyVariant` |
| `TripTemplateSeeder` | `service/TripTemplateSeeder.java` | Loads `templates.json` on `ApplicationReadyEvent` |
| `TripTemplateController` | `controller/TripTemplateController.java` | `/explore/templates` REST surface |
| `ApplyTripTemplateDTO` | `DTO/ApplyTripTemplateDTO.java` | Request body for apply |
| `TripTemplateSeedData` | `DTO/TripTemplateSeedData.java` | Jackson POJO matching `templates.json` |
| Mappers | `mapper/TripTemplateMapper.java`, `TemplateTripMapper.java`, `TemplateDayMapper.java`, `TemplateActivityMapper.java` | Entity → response |

### Apply Flow

`TripTemplateService.applyVariant(templateSlug, variantSlug, dto, currentUser)` — `@Transactional`:

```
Resolve template by slug                                          → 404 if missing
Resolve variant by slug + parent template id                      → 404 if missing
Build Trip:
    name             = dto.name        ?? variant.name
    description      = variant.description
    destination      = variant.destination
    startDate        = dto.startDate
    endDate          = startDate.plusDays(durationDays - 1)
    status           = PLANNING
    budget           = dto.budget      ?? variant.estimatedBudget
    interests        = new HashSet<>(variant.interests)
tripRepository.save(trip)
userTripRepository.save(UserTrip(user=currentUser, trip=trip, role=OWNER))
For each TemplateDay (in dayNumber order):
    create TripDay(dayNumber, date=startDate.plusDays(dayNumber-1), notes, trip)
tripDayRepository.saveAll(...)
For each new TripDay, for each source TemplateActivity:
    create Activity(name, description, location, startTime, endTime, tripDay)
activityRepository.saveAll(...)
Publish TripEventRecorded(trip, currentUser, TRIP_CREATED, TRIP, trip.id, trip.name, null)
return tripMapper.toResponse(trip)
```

Days and activities are saved through the repositories directly (not through `TripDayService` / `ActivityService`) so that no per-row `DAY_ADDED` / `ACTIVITY_ADDED` events fire — applying a template should look like a single creation in the activity feed.

### Design Decisions

1. **JSON seeder over admin CRUD** — the project has no `User.role` field today, and admin CRUD would require adding role-based authorization, an admin API, and probably an admin UI. Editorial templates change rarely; shipping them as content alongside the app is simpler and lets the catalogue evolve in code review. CRUD endpoints can be layered on later — same entities, same read endpoints.

2. **Truncate-and-reload seeding** — applied trips are independent copies, so templates can be wiped on every boot without risking user data. This avoids the complexity of upsert-by-slug logic for nested children (days/activities have no stable identifier across edits). Cost is rewriting a few hundred rows on startup, which is negligible.

3. **Two-level hierarchy: TripTemplate → TemplateTrip** — matches the user-facing concept ("Nightlife template *contains* Ibiza summer, Berlin winter festival, …"). A flat list with a style tag would lose the grouping that makes browsing meaningful.

4. **Separate template entities (not JSON column)** — mirrors `TripDay` / `Activity` so the apply flow is a clean entity-to-entity copy. No PostgreSQL-specific JSON type, queryable structure, normal Hibernate semantics.

5. **No FK from `Trip` back to template** — applied trips are fully independent. Editing a template never affects existing user trips, and deleting a template never cascades into user data.

6. **Single `TRIP_CREATED` event on apply** — going through repositories directly bypasses `TripDayService` and `ActivityService`, both of which publish per-row events. A single event keeps the activity feed readable when a template is applied.

7. **Apply replicates ownership inline** — `TripTemplateService.applyVariant` builds the `UserTrip(role=OWNER)` row itself rather than calling into `TripService.createTrip`, because `createTrip` accepts a `CreateTripDTO` and would not know about the pre-built days/activities. The four lines of duplication are accepted as the simpler trade-off; if more flows need this, a shared helper can be extracted from `TripService`.

8. **Slug-based URLs** — slugs are unique per template (and per variant within a template) and are stable across reseeds. URLs like `/explore/templates/nightlife/variants/ibiza-summer/apply` are human-readable; numeric IDs are not exposed.

9. **`Season` enum, not free text** — fixed set of values is filterable and validatable. Special cases (specific events, exact dates) belong in the variant's `description`.

## API

All endpoints require JWT authentication.

### List Templates

```
GET /explore/templates
```

**Response:** `List<TripTemplateResponse>`

```json
[
  {
    "id": 1,
    "slug": "nightlife",
    "name": "Nightlife",
    "description": "Clubs, festivals and late-night cities.",
    "imageUrl": "...",
    "variantCount": 2
  },
  {
    "id": 2,
    "slug": "relax",
    "name": "Relax",
    "description": "Slow itineraries, beaches, quiet places.",
    "imageUrl": "...",
    "variantCount": 2
  }
]
```

### Get Template

```
GET /explore/templates/{templateSlug}
```

**Response:** `TripTemplateDetailResponse` — template fields plus a list of variant summaries (no day-level detail).

```json
{
  "id": 1,
  "slug": "nightlife",
  "name": "Nightlife",
  "description": "Clubs, festivals and late-night cities.",
  "imageUrl": "...",
  "variants": [
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

### Get Variant

```
GET /explore/templates/{templateSlug}/variants/{variantSlug}
```

**Response:** `TemplateTripDetailResponse` — variant fields plus full days and activities.

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
POST /explore/templates/{templateSlug}/variants/{variantSlug}/apply
```

**Request body:** `ApplyTripTemplateDTO`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| startDate | LocalDate | yes | Must be today or later |
| name | String | no | Override variant name; defaults to `variant.name` |
| budget | BigDecimal | no | Override estimated budget; defaults to `variant.estimatedBudget` |

**Authorization:** any authenticated user. The created `Trip` is owned by the caller (`UserTrip.role = OWNER`).

**Response:** `TripResponse` — same shape as `POST /trips`.

**Side effects:**
- New `Trip` row + `UserTrip(role=OWNER)` row.
- New `TripDay` rows (one per `TemplateDay`), with `date` derived from `startDate + dayNumber - 1`.
- New `Activity` rows (one per `TemplateActivity`), copying name/description/location/startTime/endTime.
- Single `TripEvent(eventType=TRIP_CREATED, entityType=TRIP)` recorded — visible in the per-trip and dashboard activity feeds.

**Error responses:**
- `404` — unknown template slug, or unknown variant slug under that template.
- `400` — `startDate` missing or in the past, or `endDate` before `startDate` (defensive: would only happen if duration is corrupted).

## Edge Cases

1. **Missing or malformed `templates.json`** — seeder logs a warning and skips. App still starts; catalogue endpoints return empty list / 404. No exception escapes.
2. **Re-seed after edit** — editing `templates.json` and restarting wipes and reloads the catalogue. User trips already created from a previous variant version are unaffected (independent copies).
3. **Applying twice** — re-applying the same variant creates a second independent trip. Allowed by design; no uniqueness constraint between user and template.
4. **Variant duration shorter than the day list in JSON** — seeder treats `durationDays` and `days.length` as independent fields. Authoring contract: keep them aligned. No runtime check today.
5. **Activity time ordering** — `@OrderBy("startTime ASC")` on `TemplateDay.activities` means null `startTime`s sort first; activities without times appear at the start of the day in the response.
6. **Slug collisions in JSON** — duplicate template slug or duplicate variant slug under the same template will cause a unique-constraint violation at insert time and roll the seed transaction back. The seeder logs the failure and the app still starts (with no templates).
