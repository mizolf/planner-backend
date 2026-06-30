# Trip Chat

Architecture documentation for the trip chat — trip members exchange text messages, persisted via REST and delivered live to other members over WebSocket/STOMP.

## Overview

Each trip gets a chat. Members read history, post, edit, and delete their own messages. Other members see changes **live**, without polling.

The defining rule: **every write goes through REST; the WebSocket only pushes.** Sending, editing, and deleting all hit REST endpoints under the existing `/trips/{tripId}` namespace, so they reuse the JWT filter and the `TripAuthorizationService` member check unchanged. After the REST call persists, the backend **broadcasts** a `ChatEvent` to a STOMP topic; every subscribed client — including the original sender — updates its list from that broadcast. There is no optimistic UI: the broadcast is the single source of truth, deduped by message `id`.

Two consequences:
- The socket carries **no client writes**, so it only needs authorization on **CONNECT** (who are you?) and **SUBSCRIBE** (are you a member of this trip?). No per-message authorization plumbing.
- A REST mutation and its broadcast are separate steps. The broadcast fires **after** the transaction commits, so a rolled-back write never produces a phantom event.

REST endpoints (all under `/trips/{tripId}`, members only):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/messages?before={id}&limit={n}` | Paginated history (cursor) |
| POST | `/messages` | Send → broadcast `CREATED` |
| PUT | `/messages/{messageId}` | Edit own → broadcast `UPDATED` |
| DELETE | `/messages/{messageId}` | Delete own → broadcast `DELETED` |
| GET | `/messages/unread-count` | Unread badge count |
| POST | `/messages/read` | Mark all read |

WebSocket: STOMP over a **raw** WebSocket at `/ws` (no SockJS). Simple broker on `/topic`. Clients subscribe to `/topic/trips/{tripId}`.

**Authorization rule:** any trip member (incl. VIEWER) can read and post. Edit/delete are restricted to the message's **author**. Non-members get **404** (consistent with every other trip-scoped endpoint — it hides trip existence rather than confirming it with a 403).

## Entity Relationship Diagram

```
Trip ──< ChatMessage
              │
              ├── trip (Trip FK, not null — CASCADE DELETE via @OnDelete)
              ├── sender (User FK, not null)
              ├── content (VARCHAR 2000)
              ├── edited (boolean — true after an edit)
              └── createdAt (Instant — auto-set)

UserTrip (existing membership row, one per (user, trip))
              └── lastReadMessageId (Long, nullable — added for the unread badge)
```

The per-(user, trip) last-read position lives on the existing `user_trips` row — that row already *is* the (user, trip) pair, so no separate table is needed.

## ChatMessage Entity (table: `chat_messages`)

| Field | Type | Constraints |
|-------|------|-------------|
| id | Long | PK, auto-generated (sequence `chat_messages_seq`, increment 50) |
| trip | Trip (FK) | not null, CASCADE DELETE via `@OnDelete` |
| sender | User (FK) | not null |
| content | String | not null, max 2000 chars |
| edited | boolean | not null, default false |
| createdAt | Instant | not null, auto-set, immutable |

**Index:** `(trip_id, id DESC)` — serves both pagination queries ("latest N" and "before id X").

**Cascade:** `@OnDelete(action = OnDeleteAction.CASCADE)` on the trip FK — deleting a trip drops its messages at the DB level, mirroring `TripEvent`. The sender FK has no cascade: users are disabled, never deleted.

**Why order by `id`, not `createdAt`:** `id` is monotonically increasing per insert and unique, so it is a stable cursor. `createdAt` could collide at the same instant; `id` cannot. The frontend pages backward with `before={oldestLoadedId}`.

### UserTrip change (table: `user_trips`)

One nullable column added: `last_read_message_id BIGINT`. Null means "never opened the chat" (everything is unread).

## Enums

### ChatEventType

| Value | Meaning |
|-------|---------|
| `CREATED` | A new message was posted |
| `UPDATED` | An existing message's content changed |
| `DELETED` | A message was removed |

Serialized as its name, so the broadcast frame's `type` is exactly `"CREATED"` / `"UPDATED"` / `"DELETED"`, matching the frontend union type.

## Architecture

### Write + broadcast flow

```
Client ──REST──> ChatController ──> ChatService [@Transactional]
                       │                  ├── validateMembership (404 if not a member)
                       │                  ├── persist (save / update / delete)
                       │                  └── return ChatMessageResponse
                       │            (transaction commits here)
                       │
                       └── SimpMessagingTemplate.convertAndSend(
                               "/topic/trips/{tripId}", new ChatEvent(type, msg))
                                       │
                                       ▼
                  Simple broker fans out to every subscriber of /topic/trips/{tripId}
                                       │
                                       ▼
                       All clients (incl. sender) apply the event, dedup by id
```

The broadcast is done **in the controller, after the service method returns** — i.e. after commit. This keeps `SimpMessagingTemplate` out of the transactional service and guarantees we never broadcast a write that later rolls back.

### Socket auth flow

The HTTP JWT filter does **not** cover the WebSocket — the socket is a separate path. A `StompAuthChannelInterceptor` on the client-inbound channel reuses the exact JWT pieces from `JwtAuthenticationFilter`:

```
CONNECT frame  ── reads native header "Authorization: Bearer <jwt>"
                  ├── jwtService.extractUsername(jwt)        (validates signature + expiry)
                  ├── userDetailsService.loadUserByUsername(email)
                  ├── jwtService.isTokenValid(jwt, userDetails)
                  └── accessor.setUser(UsernamePasswordAuthenticationToken(user, …))
                  → reject (ERROR frame) if any step fails

SUBSCRIBE frame ── destination "/topic/trips/{tripId}"
                  ├── principal = accessor.getUser()  (set on CONNECT)
                  └── userTripRepository.existsByUserIdAndTripId(user.id, tripId)
                  → reject if not a member (blocks eavesdropping on other trips)
```

The handshake HTTP request to `/ws` is permitted at the Spring Security layer (a browser's raw WS handshake can't send an `Authorization` header), so the real check happens on the STOMP CONNECT frame.

### Key components

| Component | Location | Purpose |
|-----------|----------|---------|
| `ChatMessage` | `model/ChatMessage.java` | JPA entity |
| `ChatEventType` | `model/Enums/ChatEventType.java` | CREATED / UPDATED / DELETED |
| `ChatMessageRepository` | `repository/ChatMessageRepository.java` | Pagination + unread + lookup queries |
| `ChatMessageRequestDTO` | `DTO/ChatMessageRequestDTO.java` | `{ content }`, validated; used by POST and PUT |
| `ChatMessageResponse` | `responses/ChatMessageResponse.java` | Message wire shape |
| `ChatMessagePageResponse` | `responses/ChatMessagePageResponse.java` | `{ content, hasMore }` |
| `UnreadCountResponse` | `responses/UnreadCountResponse.java` | `{ count }` |
| `ChatEvent` | `responses/ChatEvent.java` | `{ type, message }` broadcast frame |
| `ChatMessageMapper` | `mapper/ChatMessageMapper.java` | Entity → response |
| `ChatService` | `service/ChatService.java` | Member check + persistence + unread logic |
| `ChatController` | `controller/ChatController.java` | REST endpoints + broadcast |
| `WebSocketConfig` | `config/WebSocketConfig.java` | STOMP endpoint + broker + interceptor wiring |
| `StompAuthChannelInterceptor` | `config/StompAuthChannelInterceptor.java` | CONNECT/SUBSCRIBE auth |

### Design decisions

1. **Socket is receive-only.** Writes go through REST so they reuse the existing JWT filter and `validateMembership`. The socket only needs auth on CONNECT + SUBSCRIBE — far less security surface than authorizing every inbound message.

2. **Broadcast after commit, from the controller.** The `@Transactional` service has committed by the time the controller broadcasts, so a failed write never emits a phantom event, and the transactional service has no dependency on `SimpMessagingTemplate`.

3. **Cursor (keyset) pagination, not offset.** Chat grows from the bottom and new messages arrive constantly; offset pages would shift under inserts and show duplicates/gaps. Keying on `id < before` is stable. The response is the minimal `{ content, hasMore }` — deliberately **not** a Spring `Page` (no `totalElements`/`totalPages`, which chat doesn't need and which would cost a count query).

4. **Last-read on `user_trips`.** The membership row is already the per-(user, trip) record. A nullable `last_read_message_id` avoids a whole extra table + sequence. Unread = messages in the trip with `id > lastRead` **and** `sender != me` (you never have unread messages from yourself).

5. **Order by `id`.** Monotonic and unique → a stable cursor, unlike `createdAt`.

6. **`@EntityGraph(attributePaths = "sender")`** on the read queries — prevents an N+1 when the mapper reads `sender.fullName` for each message. Same pattern as `TripEventRepository`.

7. **Edit/delete authored-only.** `validateMembership` gates trip access (404 for non-members); a second check compares `message.sender.id` to the caller and throws `ForbiddenException` (403) otherwise.

8. **Raw WebSocket, no SockJS.** Keeps the frontend dependency to `@stomp/stompjs` only. `addEndpoint("/ws")` without `.withSockJS()`.

## REST API — worked examples

All examples assume a logged-in member of trip `1`. The JWT is sent as `Authorization: Bearer <jwt>` (the sender is resolved from it — never from the body).

### Send a message

```
POST /trips/1/messages
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "content": "Tko rezervira hotel?" }
```

`201 Created`:

```json
{
  "id": 130,
  "senderId": 5,
  "senderName": "Mislav Cesnik",
  "content": "Tko rezervira hotel?",
  "createdAt": "2026-06-30T11:42:09.512Z",
  "edited": false
}
```

…and a `CREATED` event is broadcast to `/topic/trips/1` (see STOMP section). The sender's own UI also renders the message from that broadcast, not from this 201 body.

**Validation:** blank or >2000-char content → `400`:

```json
{ "status": 400, "message": "Validation failed",
  "fieldErrors": { "content": "Content is required" } }
```

### Get history (first page)

No `before` → newest `limit` messages, returned **oldest → newest** so the client renders top-to-bottom directly.

```
GET /trips/1/messages?limit=3
Authorization: Bearer eyJhbGci...
```

`200 OK`:

```json
{
  "content": [
    { "id": 128, "senderId": 7, "senderName": "Ana Horvat",  "content": "Bok!",                  "createdAt": "2026-06-30T11:40:00Z", "edited": false },
    { "id": 129, "senderId": 5, "senderName": "Mislav Cesnik","content": "Pozdrav ekipa",         "createdAt": "2026-06-30T11:41:00Z", "edited": false },
    { "id": 130, "senderId": 5, "senderName": "Mislav Cesnik","content": "Tko rezervira hotel?",  "createdAt": "2026-06-30T11:42:09Z", "edited": false }
  ],
  "hasMore": true
}
```

`hasMore: true` means older messages exist before `id 128`.

### Get history (older page)

Page backward by passing the oldest loaded id as `before`:

```
GET /trips/1/messages?before=128&limit=3
Authorization: Bearer eyJhbGci...
```

`200 OK`:

```json
{
  "content": [
    { "id": 125, "senderId": 7, "senderName": "Ana Horvat",   "content": "Idemo li u petak?", "createdAt": "2026-06-30T10:00:00Z", "edited": false },
    { "id": 126, "senderId": 5, "senderName": "Mislav Cesnik", "content": "Da",                "createdAt": "2026-06-30T10:01:00Z", "edited": false },
    { "id": 127, "senderId": 7, "senderName": "Ana Horvat",    "content": "Super",             "createdAt": "2026-06-30T10:02:00Z", "edited": false }
  ],
  "hasMore": false
}
```

`hasMore: false` → these are the oldest messages; the "Load older" button hides.

> **How `hasMore` is computed:** the query fetches `limit + 1` rows. If `limit + 1` come back, more exist → `hasMore = true` and the extra row is dropped. Then the page is reversed to oldest → newest.

### Edit own message

```
PUT /trips/1/messages/130
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "content": "Tko rezervira hotel? (do petka)" }
```

`200 OK`:

```json
{
  "id": 130,
  "senderId": 5,
  "senderName": "Mislav Cesnik",
  "content": "Tko rezervira hotel? (do petka)",
  "createdAt": "2026-06-30T11:42:09.512Z",
  "edited": true
}
```

→ broadcasts `UPDATED`. Editing someone else's message → `403`:

```json
{ "status": 403, "message": "You can only edit your own messages" }
```

### Delete own message

```
DELETE /trips/1/messages/130
Authorization: Bearer eyJhbGci...
```

`204 No Content` → broadcasts `DELETED` (only the id is needed downstream). Deleting another user's message → `403`.

### Unread count

Counts messages newer than the caller's last-read mark, **excluding the caller's own** messages.

```
GET /trips/1/messages/unread-count
Authorization: Bearer eyJhbGci...
```

`200 OK`:

```json
{ "count": 4 }
```

### Mark read

Sets the caller's `last_read_message_id` to the newest message id in the trip.

```
POST /trips/1/messages/read
Authorization: Bearer eyJhbGci...
```

`204 No Content`. A subsequent `unread-count` returns `{ "count": 0 }`.

### Non-member

Any of the above against a trip the caller is not a member of → `404` (existence hidden):

```json
{ "status": 404, "message": "Trip not found" }
```

## STOMP API — worked examples

### Connect (with auth)

Raw WebSocket at `ws://localhost:8080/ws`, then a STOMP CONNECT frame carrying the JWT as a header:

```
CONNECT
Authorization: Bearer eyJhbGci...
accept-version:1.2

^@
```

Valid token → `CONNECTED` frame. Missing/expired/invalid token → the interceptor rejects and the broker returns an `ERROR` frame; the client never reaches a connected state.

### Subscribe to a trip

```
SUBSCRIBE
id:sub-0
destination:/topic/trips/1

^@
```

The interceptor parses `tripId = 1`, resolves the principal set on CONNECT, and verifies membership. Non-member → `ERROR` frame, subscription refused (no peeking at other trips' chats).

### Broadcast frames received

Every subscriber of `/topic/trips/1` receives a `MESSAGE` frame whose body is a `ChatEvent`:

**After a POST:**

```json
{
  "type": "CREATED",
  "message": {
    "id": 131,
    "senderId": 7,
    "senderName": "Ana Horvat",
    "content": "Ja mogu hotel!",
    "createdAt": "2026-06-30T11:45:00Z",
    "edited": false
  }
}
```

**After a PUT:**

```json
{
  "type": "UPDATED",
  "message": {
    "id": 131, "senderId": 7, "senderName": "Ana Horvat",
    "content": "Ja mogu hotel (rezervirano).", "createdAt": "2026-06-30T11:45:00Z", "edited": true
  }
}
```

**After a DELETE** (only `id` matters; the client removes by id):

```json
{ "type": "DELETED", "message": { "id": 131 } }
```

### Client reducer (reference)

How a client applies an event — for context, this lives in the frontend:

- `CREATED` → append if `id` not already present; if not mine and the panel is collapsed, `unreadCount++`.
- `UPDATED` → replace the message with matching `id`.
- `DELETED` → remove the message with matching `id`.

On reconnect the client re-syncs via REST (`unread-count`, and `messages` if the panel is open) to fill any events missed while the socket was down.

## Database migration

`V10__add_chat_messages.sql`:

```sql
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
```

The sequence is **required**: with `ddl-auto=validate` the app boots without it, but the first message insert would fail when Hibernate asks the missing `chat_messages_seq` for an id. The name `{table}_seq` matches the project's existing convention.

## Repository queries

| Method | Used by |
|--------|---------|
| `findByTripIdOrderByIdDesc(tripId, Pageable)` | First page (newest N) |
| `findByTripIdAndIdLessThanOrderByIdDesc(tripId, before, Pageable)` | Older page |
| `findByIdAndTripId(id, tripId)` | Edit/delete lookup (trip-scoped) |
| `countByTripIdAndIdGreaterThanAndSenderIdNot(tripId, lastRead, senderId)` | Unread count |
| `findTopByTripIdOrderByIdDesc(tripId)` | Newest id for mark-read |

The two paging queries and the lookup carry `@EntityGraph(attributePaths = "sender")`. Pagination passes `PageRequest.of(0, limit + 1)`.

## Edge cases

1. **Empty trip** — first page returns `{ "content": [], "hasMore": false }`.
2. **Never read** — `last_read_message_id` null → treated as `0`, so every message from others is unread.
3. **Own messages** — never counted as unread (`sender != me` in the count query).
4. **Edit/delete by non-author** — `403`, even though the caller is a trip member.
5. **Non-member (REST)** — `404` (hides existence), via `validateMembership`.
6. **Non-member (SUBSCRIBE)** — `ERROR` frame, subscription refused.
7. **Bad/expired JWT on CONNECT** — `ERROR` frame, never connects.
8. **Rolled-back write** — broadcast is post-commit, so no event is emitted.
9. **Duplicate broadcast on the sender** — client dedups by `id`, so the sender seeing both its 201 response and the broadcast renders the message once.
10. **Reconnect** — client re-syncs over REST; missed events are reconstructed from server state.
11. **Trip deleted** — `ON DELETE CASCADE` removes the messages; nobody can view a deleted trip's chat.
```
