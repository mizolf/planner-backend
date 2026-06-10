ALTER TABLE public.template_activities ADD COLUMN category VARCHAR(32);
ALTER TABLE public.template_activities ADD COLUMN cost NUMERIC(8,2);

ALTER TABLE public.template_activities DROP CONSTRAINT IF EXISTS template_activities_category_check;
ALTER TABLE public.template_activities ADD CONSTRAINT template_activities_category_check
    CHECK (category IS NULL OR category IN
        ('ATTRACTION', 'TRANSPORT', 'ACCOMMODATION', 'RESTAURANT', 'OTHER'));
