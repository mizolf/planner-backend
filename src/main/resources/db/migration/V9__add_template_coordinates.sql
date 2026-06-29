ALTER TABLE public.trip_templates ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.trip_templates ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE public.template_activities ADD COLUMN latitude  DOUBLE PRECISION;
ALTER TABLE public.template_activities ADD COLUMN longitude DOUBLE PRECISION;