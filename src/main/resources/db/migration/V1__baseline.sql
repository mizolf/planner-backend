-- Baseline schema generated via pg_dump from the running database after the
-- initial Hibernate-managed creation and the INVITE check-constraint hotfix.
-- New databases start here; existing databases are marked as already at V1
-- via spring.flyway.baseline-on-migrate.

CREATE TABLE public.activities (
    id bigint NOT NULL,
    description character varying(255),
    end_time time(0) without time zone,
    location character varying(255),
    name character varying(255) NOT NULL,
    start_time time(0) without time zone,
    trip_day_id bigint
);

CREATE SEQUENCE public.activities_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.template_activities (
    id bigint NOT NULL,
    description character varying(255),
    end_time time(0) without time zone,
    location character varying(255),
    name character varying(255) NOT NULL,
    start_time time(0) without time zone,
    template_day_id bigint NOT NULL
);

CREATE SEQUENCE public.template_activities_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.template_days (
    id bigint NOT NULL,
    day_number integer NOT NULL,
    notes character varying(255),
    trip_template_id bigint NOT NULL,
    title character varying(255)
);

CREATE SEQUENCE public.template_days_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trip_days (
    id bigint NOT NULL,
    date date,
    day_number integer NOT NULL,
    notes character varying(255),
    trip_id bigint NOT NULL,
    title character varying(255)
);

CREATE SEQUENCE public.trip_days_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trip_events (
    id bigint NOT NULL,
    actor_name character varying(255) NOT NULL,
    changes jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    entity_id bigint,
    entity_name character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    actor_id bigint,
    trip_id bigint NOT NULL,
    CONSTRAINT trip_events_entity_type_check CHECK (((entity_type)::text = ANY ((ARRAY['TRIP'::character varying, 'TRIP_DAY'::character varying, 'ACTIVITY'::character varying, 'MEMBER'::character varying, 'INVITE'::character varying])::text[]))),
    CONSTRAINT trip_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['TRIP_CREATED'::character varying, 'TRIP_UPDATED'::character varying, 'DAY_ADDED'::character varying, 'DAY_UPDATED'::character varying, 'DAY_DELETED'::character varying, 'ACTIVITY_ADDED'::character varying, 'ACTIVITY_UPDATED'::character varying, 'ACTIVITY_DELETED'::character varying, 'MEMBER_ADDED'::character varying, 'MEMBER_ROLE_CHANGED'::character varying, 'MEMBER_REMOVED'::character varying, 'INVITE_SENT'::character varying, 'INVITE_ACCEPTED'::character varying, 'INVITE_DECLINED'::character varying, 'INVITE_CANCELLED'::character varying, 'INVITE_EXPIRED'::character varying])::text[])))
);

CREATE SEQUENCE public.trip_events_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trip_interests (
    trip_id bigint NOT NULL,
    interest character varying(255),
    CONSTRAINT trip_interests_interest_check CHECK (((interest)::text = ANY ((ARRAY['CULTURE'::character varying, 'FOOD'::character varying, 'ADVENTURE'::character varying, 'NATURE'::character varying, 'NIGHTLIFE'::character varying, 'SHOPPING'::character varying, 'RELAXATION'::character varying, 'HISTORY'::character varying])::text[])))
);

CREATE TABLE public.trip_invites (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    responded_at timestamp(6) with time zone,
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    version bigint,
    accepted_user_id bigint,
    inviter_id bigint,
    revoked_by_id bigint,
    trip_id bigint NOT NULL,
    CONSTRAINT trip_invites_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'EDITOR'::character varying, 'VIEWER'::character varying])::text[]))),
    CONSTRAINT trip_invites_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE SEQUENCE public.trip_invites_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trip_styles (
    id bigint NOT NULL,
    description character varying(255),
    display_order integer NOT NULL,
    image_url character varying(255),
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL
);

CREATE SEQUENCE public.trip_styles_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trip_template_interests (
    trip_template_id bigint NOT NULL,
    interest character varying(255),
    CONSTRAINT trip_template_interests_interest_check CHECK (((interest)::text = ANY ((ARRAY['CULTURE'::character varying, 'FOOD'::character varying, 'ADVENTURE'::character varying, 'NATURE'::character varying, 'NIGHTLIFE'::character varying, 'SHOPPING'::character varying, 'RELAXATION'::character varying, 'HISTORY'::character varying])::text[])))
);

CREATE TABLE public.trip_templates (
    id bigint NOT NULL,
    description character varying(255),
    destination character varying(255) NOT NULL,
    display_order integer NOT NULL,
    duration_days integer NOT NULL,
    estimated_budget numeric(8,2),
    image_url character varying(255),
    name character varying(255) NOT NULL,
    recommended_season character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    style_id bigint NOT NULL,
    CONSTRAINT trip_templates_recommended_season_check CHECK (((recommended_season)::text = ANY ((ARRAY['SPRING'::character varying, 'SUMMER'::character varying, 'AUTUMN'::character varying, 'WINTER'::character varying, 'YEAR_ROUND'::character varying])::text[])))
);

CREATE SEQUENCE public.trip_templates_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.trips (
    id bigint NOT NULL,
    budget numeric(8,2),
    created_at timestamp(6) with time zone,
    description character varying(255),
    destination character varying(255),
    end_date date,
    name character varying(255) NOT NULL,
    start_date date,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone,
    CONSTRAINT trips_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNING'::character varying, 'UPCOMING'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])))
);

CREATE SEQUENCE public.trips_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.user_trips (
    id bigint NOT NULL,
    joined_at timestamp(6) without time zone,
    role character varying(255) NOT NULL,
    trip_id bigint,
    user_id bigint,
    CONSTRAINT user_trips_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'EDITOR'::character varying, 'VIEWER'::character varying])::text[])))
);

CREATE SEQUENCE public.user_trips_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    full_name character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    verification_code character varying(255),
    verification_expiration timestamp(6) without time zone
);

CREATE SEQUENCE public.users_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT activities_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_activities
    ADD CONSTRAINT template_activities_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.template_days
    ADD CONSTRAINT template_days_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_days
    ADD CONSTRAINT trip_days_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_events
    ADD CONSTRAINT trip_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT trip_invites_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_styles
    ADD CONSTRAINT trip_styles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_templates
    ADD CONSTRAINT trip_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT trips_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_trips
    ADD CONSTRAINT user_trips_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);

ALTER TABLE ONLY public.trip_templates
    ADD CONSTRAINT uk7w8tpxpl866ymy2pibmfljy3w UNIQUE (slug);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT uk8g6voc9lssacu0omp47ow2jgp UNIQUE (token);

ALTER TABLE ONLY public.trip_styles
    ADD CONSTRAINT ukfwbajnq78lhl2a7puf2vly6rn UNIQUE (slug);

ALTER TABLE ONLY public.user_trips
    ADD CONSTRAINT uktfiuri1lalbumcmveexncxke UNIQUE (user_id, trip_id);

CREATE INDEX idx_trip_events_trip_id_created_at ON public.trip_events USING btree (trip_id, created_at DESC);

ALTER TABLE ONLY public.template_days
    ADD CONSTRAINT fk4faseumk75cfvw31x6bpldy2l FOREIGN KEY (trip_template_id) REFERENCES public.trip_templates(id);

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fk6inlkell3177be3dq11c36m4n FOREIGN KEY (trip_day_id) REFERENCES public.trip_days(id);

ALTER TABLE ONLY public.trip_events
    ADD CONSTRAINT fk7o8tgtmydiuaxu6uwu7oneox0 FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_trips
    ADD CONSTRAINT fkeugu8jc15ctftt06w3q3cjp44 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.trip_events
    ADD CONSTRAINT fkf4bgo19rflq7q3peymw1e488n FOREIGN KEY (actor_id) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.trip_interests
    ADD CONSTRAINT fkgpdplwil0butd8nsia14nvs5 FOREIGN KEY (trip_id) REFERENCES public.trips(id);

ALTER TABLE ONLY public.trip_days
    ADD CONSTRAINT fkiloomiphl4iokhoruuguwu7cw FOREIGN KEY (trip_id) REFERENCES public.trips(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT fkj4to8q1mxhahnw2y4gc8251m1 FOREIGN KEY (trip_id) REFERENCES public.trips(id);

ALTER TABLE ONLY public.template_activities
    ADD CONSTRAINT fkobmngqkplmk8kfckrciddnj7k FOREIGN KEY (template_day_id) REFERENCES public.template_days(id);

ALTER TABLE ONLY public.trip_templates
    ADD CONSTRAINT fkofphb20ug48ipr0l0m7vuaoqg FOREIGN KEY (style_id) REFERENCES public.trip_styles(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT fkovrh7b5ydj59rk3l77xb8317d FOREIGN KEY (revoked_by_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT fkr0pntm8ixywy6ce2jy8m8d1oa FOREIGN KEY (accepted_user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.trip_template_interests
    ADD CONSTRAINT fkrtvmp8lkwof8xpb5j7kxlc8qg FOREIGN KEY (trip_template_id) REFERENCES public.trip_templates(id);

ALTER TABLE ONLY public.user_trips
    ADD CONSTRAINT fkt767trb60cw98insyi58jdvrh FOREIGN KEY (trip_id) REFERENCES public.trips(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT fkt98fgfbqwose8pl64odwm81b3 FOREIGN KEY (inviter_id) REFERENCES public.users(id);
