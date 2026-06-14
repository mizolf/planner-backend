-- Trips and activities gain optional latitude/longitude captured from the Photon
-- destination autocomplete. Both nullable: existing rows have no coordinates.
ALTER TABLE public.trips      ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.trips      ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE public.activities ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.activities ADD COLUMN longitude DOUBLE PRECISION;
