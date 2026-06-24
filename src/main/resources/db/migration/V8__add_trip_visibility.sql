-- Trips gain a visibility flag for the community feature. Existing rows default to
-- PRIVATE so nothing is published accidentally. The composite index serves the
-- paginated public listing (filter by visibility, newest first) without scanning private trips.
ALTER TABLE public.trips
    ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX idx_trips_visibility_updated ON public.trips (visibility, updated_at DESC);
