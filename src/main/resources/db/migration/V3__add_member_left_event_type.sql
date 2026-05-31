-- MEMBER_LEFT was added to the TripEventType enum (member voluntarily leaves a
-- trip). The trip_events table enforces the enum via a CHECK constraint, so the
-- new value must be added there or inserts of MEMBER_LEFT events are rejected.
ALTER TABLE public.trip_events DROP CONSTRAINT IF EXISTS trip_events_event_type_check;

ALTER TABLE public.trip_events ADD CONSTRAINT trip_events_event_type_check
    CHECK (event_type IN (
        'TRIP_CREATED', 'TRIP_UPDATED',
        'DAY_ADDED', 'DAY_UPDATED', 'DAY_DELETED',
        'ACTIVITY_ADDED', 'ACTIVITY_UPDATED', 'ACTIVITY_DELETED',
        'MEMBER_ADDED', 'MEMBER_LEFT', 'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED',
        'INVITE_SENT', 'INVITE_ACCEPTED', 'INVITE_DECLINED', 'INVITE_CANCELLED', 'INVITE_EXPIRED'
    ));
