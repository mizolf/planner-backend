-- Activities gain an optional category (enum) and cost (money). category is
-- enforced via a CHECK constraint mirroring the trip_events pattern (V3), so an
-- unknown category value is rejected at the DB level. cost mirrors trips.budget
-- (NUMERIC(8,2)). Both are nullable: existing activities have neither.
ALTER TABLE public.activities ADD COLUMN category VARCHAR(32);
ALTER TABLE public.activities ADD COLUMN cost NUMERIC(8,2);

ALTER TABLE public.activities DROP CONSTRAINT IF EXISTS activities_category_check;

ALTER TABLE public.activities ADD CONSTRAINT activities_category_check
    CHECK (category IS NULL OR category IN
        ('ATTRACTION', 'TRANSPORT', 'ACCOMMODATION', 'RESTAURANT', 'OTHER'));
