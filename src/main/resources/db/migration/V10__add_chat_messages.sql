CREATE SEQUENCE public.chat_messages_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE public.chat_messages (
                                      id         bigint NOT NULL,
                                      trip_id    bigint NOT NULL,
                                      sender_id  bigint NOT NULL,
                                      content    character varying(2000) NOT NULL,
                                      edited     boolean NOT NULL DEFAULT false,
                                      created_at timestamp(6) with time zone NOT NULL
);
ALTER TABLE ONLY public.chat_messages ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.chat_messages ADD CONSTRAINT fk_chat_messages_trip
    FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.chat_messages ADD CONSTRAINT fk_chat_messages_sender
    FOREIGN KEY (sender_id) REFERENCES public.users(id);
CREATE INDEX idx_chat_messages_trip_id_id ON public.chat_messages USING btree (trip_id, id DESC);

ALTER TABLE public.user_trips ADD COLUMN last_read_message_id bigint;