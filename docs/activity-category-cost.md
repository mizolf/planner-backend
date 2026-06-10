# Activity Category & Cost

Architecture documentation for adding an optional **category** (enum) and **cost** (money) to activities — both the live activities a user edits on a trip, and the curated template activities in the explore catalogue.

## Overview

An activity used to carry only `name`, `description`, `location`, `startTime`, `endTime`. Two fields are added so a trip's itinerary can be classified and costed:

- **category** — what kind of activity it is (sightseeing, transport, a meal, …). A fixed enum, so the value is filterable and validatable rather than free text.
- **cost** — how much the activity costs, as money (`BigDecimal`).

Both fields are **nullable**: existing activities have neither, and authors can leave either out.

The feature spans **two parallel chains** that never share entities:

1. **Live `Activity` chain** — the activities a user creates/edits on their own trip. Already implemented (migration `V5`).
2. **Template chain** (`TemplateActivity`) — the read-mostly catalogue activities seeded from `templates.json` and exposed by `/explore`. This document's primary subject (migration `V6`).

When a user applies a template, each `TemplateActivity` is copied into a new `Activity` — so `category` and `cost` must be carried across that copy too, or the data is lost on apply.

## Enum: ActivityCategory

`model/Enums/ActivityCategory.java`

```
ATTRACTION | TRANSPORT | ACCOMMODATION | RESTAURANT | OTHER
```

| Value | Used for |
|-------|----------|
| `ATTRACTION` | sights, tours, hikes, museums, beaches, clubs, spas, gardens |
| `TRANSPORT` | flights, transfers, car/ferry/bus, drives |
| `ACCOMMODATION` | check-in / lodging |
| `RESTAURANT` | meals, drinks, coffee |
| `OTHER` | free time, rest, anything that fits nothing above |

Stored as `@Enumerated(EnumType.STRING)` (the string name, not the ordinal) so reordering or inserting enum values never corrupts existing rows.

## Data Model Changes

The same two fields are added to **both** entities, identically:

| Field | Type | JPA mapping | Column |
|-------|------|-------------|--------|
| category | `ActivityCategory` | `@Enumerated(EnumType.STRING)` `@Column(name = "category")` | `category VARCHAR(32)` |
| cost | `BigDecimal` | `@Column(precision = 8, scale = 2)` | `cost NUMERIC(8,2)` |

`NUMERIC(8,2)` mirrors `trips.budget` and `trip_templates.estimated_budget` — up to 6 integer digits and 2 decimals. The DTO validates cost with `@DecimalMin("0.00")` (no negative money) and `@Digits(integer = 6, fraction = 2)`.

## Live Activity Chain (migration V5 — already done)

These changes are already in the working tree:

| File | Change |
|------|--------|
| `model/Enums/ActivityCategory.java` | new enum |
| `model/Activity.java` | add `category` + `cost` columns |
| `DTO/CreateActivityDTO.java` | add `category` + validated `cost` |
| `DTO/UpdateActivityDTO.java` | add `category` + validated `cost` |
| `mapper/ActivityMapper.java` | map both fields in `toEntity`, `applyUpdate`, `toResponse` |
| `responses/ActivityResponse.java` | expose `category` + `cost` |
| `event/ChangeDetector.java` | track `category` and `cost` changes (so edits appear in the activity feed) |
| `db/migration/V5__add_activity_category_and_cost.sql` | add columns + CHECK constraint to `activities` |

## Template Chain (migration V6 — this change)

The template chain is fully parallel to the live one. To surface `category` and `cost` in the template detail response (`GET /explore/styles/{s}/templates/{t}`) and to carry them onto applied trips, the fields are threaded through these seven touchpoints:

| # | File | Change |
|---|------|--------|
| 1 | `DTO/seed/TemplateActivitySeedData.java` | add `ActivityCategory category` + `BigDecimal cost` record components — Jackson reads them from `templates.json` here. Without this they are silently ignored. |
| 2 | `model/TemplateActivity.java` | add `category` + `cost` columns (identical mapping to `Activity`) |
| 3 | `db/migration/V6__add_template_activity_category_and_cost.sql` | add columns + CHECK constraint to `template_activities` |
| 4 | `service/TripTemplateSeeder.java` (`buildActivity`) | `.category(data.category())` `.cost(data.cost())` when building the entity from seed data |
| 5 | `responses/TemplateActivityResponse.java` | add `category` + `cost` fields |
| 6 | `mapper/TemplateActivityMapper.java` (`toResponse`) | map both fields — **this is what makes them appear in the API response** |
| 7 | `service/TripTemplateService.java` (`applyTemplate`) | copy `category` + `cost` from `TemplateActivity` into the new `Activity` |

### Why a migration is required

`application.properties` sets `spring.jpa.hibernate.ddl-auto=validate`. Hibernate **does not create columns** — it only checks that the schema matches the entities at startup. Adding fields to `TemplateActivity` without a matching migration makes startup fail schema validation. The schema is owned by Flyway; every column the entity declares must exist via a migration.

> Note: `docs/explore-templates.md` predates this and says `ddl-auto=update`. The current config is `validate` + Flyway.

### `templates.json`

The seed file already carries the data — every activity now has `category` and `cost` (free stops use `0.00`):

```json
{
  "name": "Ushuaïa opening party",
  "description": "Marquee headliner set",
  "location": "Ushuaïa Ibiza",
  "startTime": "22:00",
  "endTime": "04:00",
  "category": "ATTRACTION",
  "cost": 220.00
}
```

## Database Migrations

### V5 — `activities` (done)

```sql
ALTER TABLE public.activities ADD COLUMN category VARCHAR(32);
ALTER TABLE public.activities ADD COLUMN cost NUMERIC(8,2);

ALTER TABLE public.activities DROP CONSTRAINT IF EXISTS activities_category_check;
ALTER TABLE public.activities ADD CONSTRAINT activities_category_check
    CHECK (category IS NULL OR category IN
        ('ATTRACTION', 'TRANSPORT', 'ACCOMMODATION', 'RESTAURANT', 'OTHER'));
```

### V6 — `template_activities` (this change)

```sql
ALTER TABLE public.template_activities ADD COLUMN category VARCHAR(32);
ALTER TABLE public.template_activities ADD COLUMN cost NUMERIC(8,2);

ALTER TABLE public.template_activities DROP CONSTRAINT IF EXISTS template_activities_category_check;
ALTER TABLE public.template_activities ADD CONSTRAINT template_activities_category_check
    CHECK (category IS NULL OR category IN
        ('ATTRACTION', 'TRANSPORT', 'ACCOMMODATION', 'RESTAURANT', 'OTHER'));
```

A `VARCHAR + CHECK` (rather than a Postgres `enum` type) mirrors the existing `trip_events` / `activities` pattern: the constraint rejects unknown values at the DB level, and adding a new enum value later is a one-line `CHECK` swap instead of an `ALTER TYPE`. The string column matches `@Enumerated(EnumType.STRING)`.

## Apply Flow Change

`TripTemplateService.applyTemplate` copies each `TemplateActivity` into a new `Activity`. The copy gains the two fields:

```java
dayActivities.add(Activity.builder()
        .name(sourceAct.getName())
        .description(sourceAct.getDescription())
        .location(sourceAct.getLocation())
        .startTime(sourceAct.getStartTime())
        .endTime(sourceAct.getEndTime())
        .category(sourceAct.getCategory())   // added
        .cost(sourceAct.getCost())           // added
        .tripDay(newDay)
        .build());
```

The applied trip is still an independent copy (no FK back to the template); it simply starts with the template's category and cost on each activity. The single `TRIP_CREATED` event behaviour is unchanged.

## API Impact

### Template detail — `GET /explore/styles/{styleSlug}/templates/{templateSlug}`

Each activity in the response now includes `category` and `cost`:

```json
{
  "name": "Seaplane to resort",
  "description": null,
  "location": "Malé Airport",
  "startTime": "13:00:00",
  "endTime": "14:00:00",
  "category": "TRANSPORT",
  "cost": 700.00
}
```

### Activity endpoints (live trips)

`ActivityResponse` already returns both fields; `Create`/`UpdateActivityDTO` already accept them. No change in this batch.

## Edge Cases

1. **Null category / cost** — both are nullable end to end. An activity with neither serializes them as `null`; the CHECK constraint explicitly allows `category IS NULL`.
2. **Free activities** — represented as `cost: 0.00`, not `null`, so the frontend can distinguish "free" from "unknown/unpriced".
3. **Unknown enum value in JSON** — Jackson fails to deserialize an unknown `ActivityCategory`, which aborts the seed transaction; the seeder logs a warning and the catalogue retains its previous state (app still starts). The DB CHECK is a second line of defence.
4. **Re-seed on startup** — `template_activities` is truncate-and-reloaded every boot, so existing template rows pick up `category`/`cost` from `templates.json` on the next start; no data backfill needed for templates. Live `activities` rows created before V5 keep `null` for both.
5. **Cost precision** — values beyond `NUMERIC(8,2)` (e.g. 7 integer digits) are rejected by `@Digits` validation on the live DTO before they reach the DB; seed values are authored within range.
