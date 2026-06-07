-- User.preferredInterests is a new @ElementCollection mirroring Trip.interests.
-- With ddl-auto=validate, the entity needs a matching table or the app won't
-- start, so this creates user_interests as an exact mirror of trip_interests
-- from V1__baseline.sql (table + enum CHECK + FK to users).
CREATE TABLE public.user_interests (
    user_id bigint NOT NULL,
    interest character varying(255),
    CONSTRAINT user_interests_interest_check CHECK (
        (interest)::text = ANY ((ARRAY[
            'CULTURE','FOOD','ADVENTURE','NATURE',
            'NIGHTLIFE','SHOPPING','RELAXATION','HISTORY'
        ])::text[])
    )
);

ALTER TABLE ONLY public.user_interests
    ADD CONSTRAINT fk_user_interests_user
    FOREIGN KEY (user_id) REFERENCES public.users(id);
