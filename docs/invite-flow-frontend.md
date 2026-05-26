# Trip Invite Flow — Frontend Integration

Kratki priručnik za frontend pri integraciji invite API-ja. Pokriva URL-ove, oblike zahtjeva i odgovora, autentikaciju, status mašinu i tipične korisničke tokove.

## TL;DR

- **6 endpoint-a**, svi zahtjevaju JWT.
- **3 owner-facing** pod `/trips/{tripId}/invites` (create / list / cancel) — owner-only (validira `OWNER` rolu).
- **3 invitee-facing** pod `/me/invites` (list / accept / decline) — bilo koji ulogiran user, ali email mora odgovarati emailu na invite-u.
- Invite ima 5 statusa: `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `CANCELLED`. Samo `PENDING` se može mijenjati.
- Default expiry = **7 dana** od kreiranja (konfigurabilno preko `app.invite.expiry-days`).
- **409 Conflict odgovori uvijek imaju `code` polje** za stabilan programatski match (npr. `CONCURRENT_MODIFICATION` za retry-safe, `INVITE_NOT_PENDING` za "već procesirano"). **Ne match-aj po `message`** — poruke su informativne i mogu se mijenjati.
- Phase 1 = in-app only. **Nema email notifikacija**, invitee vidi pozive tek kad otvori app.
- Direktni `POST /trips/{tripId}/members` više **ne postoji** — članstvo se dodaje isključivo preko invite + accept toka.

## Auth model

| Endpoint | JWT | Tko može |
|----------|-----|----------|
| `POST /trips/{tripId}/invites` | ✅ | Samo OWNER trip-a |
| `GET /trips/{tripId}/invites` | ✅ | Samo OWNER trip-a |
| `DELETE /trips/{tripId}/invites/{inviteId}` | ✅ | Samo OWNER trip-a |
| `GET /me/invites` | ✅ | Bilo koji ulogiran user (vidi samo svoje) |
| `POST /me/invites/{inviteId}/accept` | ✅ | Samo user čiji email matcha invite |
| `POST /me/invites/{inviteId}/decline` | ✅ | Samo user čiji email matcha invite |

JWT se šalje kao i drugdje u API-ju:
```
Authorization: Bearer <token>
```

Bez tokena svi endpointi vraćaju 403 (Spring Security default).

## Status mašina

```
                      ┌────────────────────────┐
                      │                        │
                      │       PENDING          │  ← createInvite
                      │                        │
                      └─┬───────┬──────┬─────┬─┘
                        │       │      │     │
              acceptInvite      │      │     cancelInvite
                        │       │      │     │
                        ▼       ▼      ▼     ▼
                  ACCEPTED  DECLINED  EXPIRED  CANCELLED
                  (terminal — više se ne mijenja)
```

- **PENDING** → jedini status iz kojeg se može tranzicionirati.
- **EXPIRED** se postavlja na 3 mjesta:
  1. Scheduled cron job (dnevno u 04:00) prelazi sve `PENDING` invite-e gdje je `expiresAt < now`.
  2. Lazy expiry u `GET /me/invites` — pri svakom dohvaćanju, expired PENDING se filtrira van i flipa u EXPIRED.
  3. Eager check u `POST /me/invites/{inviteId}/accept` — ako je expired, flipa u EXPIRED i vraća 409.

## Error codes (409 Conflict)

Svaki 409 odgovor nosi stabilan `code` u response body-ju koji ne mijenja vrijednost između verzija. **Match po `code`**, ne po `message` — poruke su informativne i mogu se mijenjati (lokalizacija, refactor).

| `code` | Što znači | Retry safe? | UX preporuka |
|--------|-----------|-------------|--------------|
| `CONCURRENT_MODIFICATION` | Optimistic-lock konflikt — netko drugi (ili drugi tab) je istovremeno mijenjao isti invite | ✅ **DA** | Auto-retry tiho (1–2 pokušaja); ako i dalje fail-a, prikaži generičku grešku |
| `SELF_INVITE` | Owner pokušava pozvati sam sebe | ❌ ne | Inline form error "Ne možeš pozvati sebe" |
| `ALREADY_MEMBER` | Target user je već član trip-a | ❌ ne | Inline form error "Ovaj korisnik je već član" |
| `INVITE_NOT_PENDING` | Invite je već u terminal stanju (ACCEPTED / DECLINED / CANCELLED / EXPIRED) — netko ga je već procesirao | ❌ ne | Refresh listu, info toast "Invite je već obrađen" |
| `INVITE_EXPIRED` | Invite je istekao između trenutka kad ga je user vidio i trenutka kad je kliknuo accept | ❌ ne | Refresh listu, info toast "Ovaj poziv je istekao" |

**Retry pattern u Angular HTTP interceptoru** (ili gdje god rukujete HTTP greškama):

```typescript
catchError((err: HttpErrorResponse) => {
  if (err.status === 409 && err.error?.code === 'CONCURRENT_MODIFICATION') {
    // safe retry — drugi pokušaj će vjerojatno naći invite u terminalnom stanju
    // i tretirati kao success
    return retryRequest();
  }
  return throwError(() => err);
})
```

> ⚠️ Ne match-aj na `err.error?.message?.includes('Concurrent')` — ako sutra promijenimo poruku u "Optimistic lock conflict, please retry", retry tiho prestane raditi i user vidi error umjesto auto-retry-a. Stabilan ugovor je `code` polje, ne tekst poruke.

## Endpoint reference

### 1. Create invite (owner)

```
POST /trips/{tripId}/invites
Authorization: Bearer <jwt>
Content-Type: application/json
```

Kreira novi `PENDING` invite za target email. Ako PENDING invite već postoji za isti `(tripId, email)`, **istu** ulaznu liniju se update-a: nova token, novi `createdAt`, resetirani `expiresAt` (= now + 7 dana), novi `inviter` i `role`. To je u suštini "resend" — frontend ne treba zvati neki poseban endpoint.

**Request body** — `CreateInviteDTO`:

| Polje | Tip | Required | Opis |
|-------|-----|----------|------|
| `email` | `string` (validan format) | yes | Target email, case-insensitive |
| `role` | `'EDITOR' \| 'VIEWER'` | yes | Rola koja se dodjeljuje na accept |

```json
{
  "email": "newmember@example.com",
  "role": "EDITOR"
}
```

> ⚠️ `OWNER` nije dozvoljen kao role.

**Response 201 Created** — `TripInviteResponse`:
```json
{
  "id": 42,
  "email": "newmember@example.com",
  "role": "EDITOR",
  "status": "PENDING",
  "inviterName": "Mislav Cesnik",
  "createdAt": "2026-05-23T10:00:00Z",
  "expiresAt": "2026-05-30T10:00:00Z"
}
```

**Error responses:**

| Status | Razlog | Primjer poruke |
|--------|--------|----------------|
| 400 | Validation fail | `{"fieldErrors":{"email":"Invalid email format"},"message":"Validation failed","status":400}` |
| 403 | Caller nije OWNER | `{"status":403,"message":"You don't have permission to manage this trip"}` |
| 404 | Trip ne postoji ili user s tim emailom nije registriran | `{"status":404,"message":"User with this email is not registered"}` |
| 409 | Invite samog sebe | `{"status":409,"code":"SELF_INVITE","message":"You cannot invite yourself"}` |
| 409 | Target user je već član trip-a | `{"status":409,"code":"ALREADY_MEMBER","message":"User is already a member of this trip"}` |

---

### 2. List invites for trip (owner)

```
GET /trips/{tripId}/invites
GET /trips/{tripId}/invites?status=PENDING
Authorization: Bearer <jwt>
```

Vraća sve invite-e za trip, sortirane po `createdAt` descending. Opcionalni `?status=` filter (case-sensitive — koristi enum vrijednosti).

**Response 200** — `TripInviteResponse[]`:
```json
[
  {
    "id": 42,
    "email": "newmember@example.com",
    "role": "EDITOR",
    "status": "PENDING",
    "inviterName": "Mislav Cesnik",
    "createdAt": "2026-05-23T10:00:00Z",
    "expiresAt": "2026-05-30T10:00:00Z"
  },
  {
    "id": 41,
    "email": "old@example.com",
    "role": "VIEWER",
    "status": "DECLINED",
    "inviterName": "Mislav Cesnik",
    "createdAt": "2026-05-20T08:00:00Z",
    "expiresAt": "2026-05-27T08:00:00Z"
  }
]
```

**Error responses:**

| Status | Razlog |
|--------|--------|
| 403 | Caller nije OWNER |
| 404 | Trip ne postoji |
| 400 | Invalid `?status=` vrijednost (Spring vraća default conversion error) |

---

### 3. Cancel invite (owner)

```
DELETE /trips/{tripId}/invites/{inviteId}
Authorization: Bearer <jwt>
```

Owner opoziva PENDING invite. Status se prebacuje u `CANCELLED`, `revokedBy` se postavlja na caller-a, `respondedAt` na trenutni timestamp. Invite se **ne briše** iz baze — ostaje za audit trail.

**Response 204 No Content** (prazno tijelo).

**Error responses:**

| Status | Razlog |
|--------|--------|
| 403 | Caller nije OWNER |
| 404 | Invite ne postoji ili ne pripada datom trip-u |
| 409 | Invite nije u PENDING statusu — `code: INVITE_NOT_PENDING` |

---

### 4. List my invites (invitee)

```
GET /me/invites
Authorization: Bearer <jwt>
```

Vraća samo `PENDING` invite-e gdje email odgovara emailu trenutno ulogiranog user-a (case-insensitive). **Lazy expiry**: bilo koji PENDING invite koji je istekao prije ovog poziva se flipa u EXPIRED prije nego što se response sastavi — frontend ne treba samo provjeravati expiry.

Ovo je primarna lista koju invitee vidi u "Notifications" / "Invites" delu app-a.

**Response 200** — `MyInviteResponse[]`:
```json
[
  {
    "id": 42,
    "tripId": 7,
    "tripName": "Summer Italy 2026",
    "tripDestination": "Italy",
    "inviterName": "Mislav Cesnik",
    "role": "EDITOR",
    "expiresAt": "2026-05-30T10:00:00Z"
  }
]
```

`MyInviteResponse` namjerno **ne vraća email** invitee-a (već je njegov, suvišan) niti `status` (uvijek PENDING jer se ostali statusi filtriraju van).

---

### 5. Accept invite (invitee)

```
POST /me/invites/{inviteId}/accept
Authorization: Bearer <jwt>
```

Prihvaća invite, kreira `UserTrip` u istom transakcijskom batchu i prebacuje invite u `ACCEPTED`. Idempotentno na razini članstva — ako je user iz nekog razloga već član, neće se kreirati duplikat (ali invite se svejedno markira ACCEPTED).

**Response 204 No Content** (prazno tijelo).

Frontend obično nakon 204 napravi navigaciju na `/trips/{tripId}` da prikaže novi pristupan trip.

**Error responses:**

| Status | Razlog | Primjer poruke |
|--------|--------|----------------|
| 403 | Email se ne poklapa | `{"status":403,"message":"This invite is not for you"}` |
| 403 | User još nije verificiran email (`enabled=false`) | `{"status":403,"message":"Verify your email before accepting invites"}` |
| 404 | Invite ne postoji | `{"status":404,"message":"Invite not found"}` |
| 409 | Invite više nije PENDING | `{"status":409,"code":"INVITE_NOT_PENDING","message":"Invite is no longer pending"}` |
| 409 | Invite je istekao (server flipa u EXPIRED prije bacanja) | `{"status":409,"code":"INVITE_EXPIRED","message":"Invite has expired"}` |
| 409 | Optimistic lock — dva paralelna accept-a istog invite-a | `{"status":409,"code":"CONCURRENT_MODIFICATION","message":"Concurrent modification — please retry"}` |

---

### 6. Decline invite (invitee)

```
POST /me/invites/{inviteId}/decline
Authorization: Bearer <jwt>
```

Odbija invite. Status → `DECLINED`, `respondedAt` se popunjava, nikakav UserTrip se ne kreira.

**Response 204 No Content**.

**Error responses:**

| Status | Razlog |
|--------|--------|
| 403 | Email se ne poklapa |
| 404 | Invite ne postoji |
| 409 | Invite više nije PENDING — `code: INVITE_NOT_PENDING` |

## TypeScript interfaces

Spremno za kopiranje u frontend modela:

```typescript
export type InviteStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'CANCELLED';

export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER';

export interface CreateInviteRequest {
  email: string;
  role: Exclude<TripRole, 'OWNER'>;
}

// Owner view — GET /trips/{tripId}/invites + POST response
export interface TripInviteResponse {
  id: number;
  email: string;
  role: TripRole;
  status: InviteStatus;
  inviterName: string;        // "Deleted user" ako je inviter user obrisan
  createdAt: string;          // ISO instant, npr. "2026-05-23T10:00:00Z"
  expiresAt: string;
}

// Invitee view — GET /me/invites
export interface MyInviteResponse {
  id: number;
  tripId: number;
  tripName: string;
  tripDestination: string | null;
  inviterName: string;
  role: TripRole;
  expiresAt: string;
}
```

Error response shape je isti kao i drugdje u app-u (`ErrorResponse`):
```typescript
export type InviteErrorCode =
  | 'CONCURRENT_MODIFICATION'
  | 'SELF_INVITE'
  | 'ALREADY_MEMBER'
  | 'INVITE_NOT_PENDING'
  | 'INVITE_EXPIRED';

export interface ErrorResponse {
  status: number;
  code?: InviteErrorCode | string;       // popunjeno na 409; drugi handleri ga mogu izostaviti
  message: string;
  fieldErrors?: Record<string, string>;  // samo na 400
  timestamp?: string;
}
```

## Tipičan korisnički tok

### Owner — pozivanje člana

1. Owner je već na detaljima trip-a (`GET /trips/{id}`).
2. Klik na "Invite member" → modal s formom (email + role select).
3. `POST /trips/{tripId}/invites` → 201 sa `TripInviteResponse`.
4. Refresh liste poziva ako je vidljiva (`GET /trips/{tripId}/invites`).
5. Ako 409 (already member / self-invite) → prikazati error u formi, ostaviti modal otvoren.
6. Ako 404 (user nije registriran) → friendly poruka "Korisnik s tim emailom nije registriran". Phase 2 će omogućiti pozivanje neregistriranih.

### Owner — pregled i upravljanje pozivima

1. Tab "Invites" u trip detaljima → `GET /trips/{tripId}/invites`.
2. Renderirati listu s statusom (badge boja po statusu: PENDING žuto, ACCEPTED zeleno, ostalo sivo).
3. Pored svakog PENDING invite-a button "Cancel" → `DELETE /trips/{tripId}/invites/{inviteId}` → 204 → refresh liste.
4. Za PENDING invite-e koji su skoro istekli (< 24h) opcionalno prikazati "Resend" button koji jednostavno zove istu `POST /trips/{tripId}/invites` rutu s istom email/role kombinacijom — backend će update-ati postojeću liniju s novim tokenom i resetiranim expiry-jem.

### Invitee — prihvaćanje poziva

1. Na entry u app (npr. landing nakon login-a) pozove se `GET /me/invites`.
2. Ako lista nije prazna, prikazati notifikaciju ili badge ("Imaš 2 nova poziva").
3. Stranica `/invites` → renderirati `MyInviteResponse[]` (trip ime, destinacija, tko poziva, role, kad ističe).
4. Buttons "Accept" i "Decline":
   - Accept → `POST /me/invites/{inviteId}/accept` → 204 → refresh liste + redirect na `/trips/{tripId}`.
   - Decline → `POST /me/invites/{inviteId}/decline` → 204 → refresh liste, ostani na stranici.
5. Ako accept vrati 409 sa `code === 'CONCURRENT_MODIFICATION'` → auto-retry (1–2 pokušaja); ako i dalje fail-a, prikaži generičku grešku. Ne tretirati kao trajni error.
6. Ako accept vrati 409 sa `code === 'INVITE_NOT_PENDING'` ili `'INVITE_EXPIRED'` → refresh `GET /me/invites` (invite je već procesiran ili istekao između prikaza liste i klika).
7. Ako accept vrati 403 "Verify your email" → linkati na resend-verification flow.

## Stvari na koje treba paziti

1. **Email case-insensitive** — backend normalizira email na lowercase pri spremanju. Slanje `User@Example.com` se trimat će na `user@example.com`. Frontend može slati u bilo kojem case-u.
2. **Resend = isti endpoint** — frontend ne mora razlikovati "create" od "resend"; `POST /trips/{tripId}/invites` će prepoznati postojeći PENDING invite za isti `(trip, email)` i update-ati ga. Token i expiry se resetiraju.
3. **Lazy expiry** — `GET /me/invites` već automatski filtrira out istekle PENDING-e. Klijent ne mora računati `expiresAt < now` na response-u. Ako želi prikazati "ističe za X" kao countdown, koristi `expiresAt` polje.
4. **Optimistic lock 409** — accept može vratiti 409 zbog konkurentne modifikacije (drugi prozor istog user-a, double-click). Tretirati kao "retry safe" — pokušati još jednom, ne kao trajni error. **Match po `err.error?.code === 'CONCURRENT_MODIFICATION'`**, ne po tekstu poruke (vidi sekciju Error codes).
5. **`tripDestination` može biti `null`** — trip-ovi ne moraju imati destinaciju. Frontend mora handle-ati taj prikaz.
6. **`inviterName` može biti "Deleted user"** — ako je inviter user obrisan, mapper renderira string `"Deleted user"`. Frontend ne mora posebno provjeravati — samo render-aj polje.
7. **No email notifications (Phase 1)** — invitee mora otvoriti app da vidi poziv. Frontend bi trebao osigurati da se `GET /me/invites` poziva pri svakom login-u / app foreground / pull-to-refresh.
8. **204 No Content znači success** — accept, decline i cancel ne vraćaju body. Frontend ne smije parse-ati JSON iz odgovora; treba samo provjeriti status code.
9. **Status filter case-sensitive** — `?status=PENDING` radi, `?status=pending` ne (Spring enum binding je strict by default). Slati uppercase ili izostaviti parametar.
10. **Validation order na accept-u** — server provjerava redom: invite postoji → status PENDING → expiry → email match → email verified → membership. Frontend ne mora replicirati taj redoslijed, samo handle-ati error porukama.

## Quick curl recipes

```bash
# Login kao owner i invitee (dvije sesije)
OWNER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@example.com","password":"..."}' | jq -r '.token')

INVITEE_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"invitee@example.com","password":"..."}' | jq -r '.token')

# Owner: kreira invite
curl -X POST http://localhost:8080/trips/7/invites \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"invitee@example.com","role":"EDITOR"}'

# Owner: lista pozive za trip
curl http://localhost:8080/trips/7/invites \
  -H "Authorization: Bearer $OWNER_TOKEN"

# Owner: filtrira po statusu
curl "http://localhost:8080/trips/7/invites?status=PENDING" \
  -H "Authorization: Bearer $OWNER_TOKEN"

# Owner: opoziva invite
curl -X DELETE http://localhost:8080/trips/7/invites/42 \
  -H "Authorization: Bearer $OWNER_TOKEN"

# Invitee: lista svoje pozive
curl http://localhost:8080/me/invites \
  -H "Authorization: Bearer $INVITEE_TOKEN"

# Invitee: prihvaća
curl -X POST http://localhost:8080/me/invites/42/accept \
  -H "Authorization: Bearer $INVITEE_TOKEN"

# Invitee: odbija
curl -X POST http://localhost:8080/me/invites/43/decline \
  -H "Authorization: Bearer $INVITEE_TOKEN"
```
