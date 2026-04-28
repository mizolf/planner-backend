# Explore Templates — Frontend Integration

Kratki priručnik za frontend pri integraciji `/explore` API-ja. Pokriva URL-ove, oblike zahtjeva i odgovora, autentikaciju i tipične korisničke tokove.

## TL;DR

- **4 endpointa** pod `/explore`. 3 GET-a (public, bez tokena) + 1 POST (treba JWT).
- Hijerarhija: `Style` (npr. Nightlife) → `Template` (npr. Ibiza Summer) → `Day` → `Activity`.
- Apply endpoint kreira novi `Trip` u vlasništvu prijavljenog korisnika kopiranjem template stabla.

## Auth model

| Endpoint | JWT |
|----------|-----|
| `GET /explore/styles` | ❌ public |
| `GET /explore/styles/{styleSlug}` | ❌ public |
| `GET /explore/styles/{styleSlug}/templates/{templateSlug}` | ❌ public |
| `POST /explore/styles/{styleSlug}/templates/{templateSlug}/apply` | ✅ obavezan |

Anonimni korisnik može slobodno browsirati katalog (kao "Discover" surface). Bez JWT-a `POST` vraća **403**.

JWT se šalje kao i drugdje u API-ju:
```
Authorization: Bearer <token>
```

## Endpoint reference

### 1. List styles

```
GET /explore/styles
```

Vraća listu svih stilova s brojem template-a u svakom. Za hub/index ekran.

**Response 200** — `TripStyleResponse[]`:
```json
[
  {
    "id": 1,
    "slug": "nightlife",
    "name": "Nightlife",
    "description": "Clubs, festivals and late-night cities.",
    "imageUrl": null,
    "templateCount": 2
  }
]
```

Stilovi su sortirani po `displayOrder` ascending — frontend ih može renderirati po primljenom redoslijedu.

---

### 2. Get style detail

```
GET /explore/styles/{styleSlug}
```

Stil + lista template summary-ja (bez dnevnog detalja). Za "klikni na kategoriju, vidi opcije" ekran.

**Response 200** — `TripStyleDetailResponse`:
```json
{
  "id": 1,
  "slug": "nightlife",
  "name": "Nightlife",
  "description": "Clubs, festivals and late-night cities.",
  "imageUrl": null,
  "templates": [
    {
      "id": 10,
      "slug": "ibiza-summer",
      "name": "Ibiza Summer Highlights",
      "destination": "Ibiza, Spain",
      "durationDays": 4,
      "recommendedSeason": "SUMMER",
      "imageUrl": null,
      "estimatedBudget": 1200.00,
      "interests": ["NIGHTLIFE", "FOOD"]
    }
  ]
}
```

**Response 404**:
```json
{ "status": 404, "message": "Trip style not found: <slug>", "timestamp": "..." }
```

---

### 3. Get template detail

```
GET /explore/styles/{styleSlug}/templates/{templateSlug}
```

Pun template s itinerarom (dani + aktivnosti). Za detaljni preview prije apply.

**Response 200** — `TripTemplateDetailResponse`:
```json
{
  "id": 10,
  "slug": "ibiza-summer",
  "name": "Ibiza Summer Highlights",
  "description": "Four days of beach clubs and superstar DJs on the white isle.",
  "destination": "Ibiza, Spain",
  "durationDays": 4,
  "recommendedSeason": "SUMMER",
  "imageUrl": null,
  "estimatedBudget": 1200.00,
  "interests": ["NIGHTLIFE", "FOOD"],
  "days": [
    {
      "dayNumber": 1,
      "notes": "Arrival & Playa d'en Bossa",
      "activities": [
        {
          "name": "Check-in",
          "description": null,
          "location": "Hotel Garbi",
          "startTime": "15:00:00",
          "endTime": "16:00:00"
        }
      ]
    }
  ]
}
```

**Response 404** — kad ne postoji style ili template (poruka razlikuje):
```json
{ "status": 404, "message": "Trip template '<slug>' not found in style '<styleSlug>'", "timestamp": "..." }
```

---

### 4. Apply template

```
POST /explore/styles/{styleSlug}/templates/{templateSlug}/apply
Authorization: Bearer <jwt>
Content-Type: application/json
```

Materijalizira template u novi `Trip` u vlasništvu prijavljenog korisnika. Kopira sve dane i aktivnosti u nove `TripDay` / `Activity` redove. Originalni template se ne mijenja.

**Request body** — `ApplyTripTemplateDTO`:

| Polje | Tip | Required | Opis |
|-------|-----|----------|------|
| `startDate` | `string` (ISO date `YYYY-MM-DD`) | yes | Mora biti danas ili kasnije |
| `name` | `string` | no | Override imena Trip-a; default je `template.name` |
| `budget` | `number` | no | Override budget-a; default je `template.estimatedBudget` |

```json
{
  "startDate": "2026-06-01",
  "name": "Naš medeni mjesec",
  "budget": 5000.00
}
```

**Response 201 Created** — `TripResponse`:
```json
{
  "id": 52,
  "name": "Naš medeni mjesec",
  "description": "Four days of beach clubs and superstar DJs on the white isle.",
  "destination": "Ibiza, Spain",
  "startDate": "2026-06-01",
  "endDate": "2026-06-04",
  "status": "PLANNING",
  "budget": 5000.00,
  "interests": ["FOOD", "NIGHTLIFE"],
  "createdAt": null,
  "updatedAt": null
}
```

`endDate` se računa server-side kao `startDate + (durationDays - 1)`.

**Napomena:** `createdAt` / `updatedAt` mogu biti `null` u apply response-u (Hibernate timestamp se postavlja na flush). Ako frontend treba točan timestamp odmah, dohvati `GET /trips/{id}` nakon apply-a.

**Error responses:**

| Status | Razlog | Primjer |
|--------|--------|---------|
| 400 | Validation fail | `{"fieldErrors":{"startDate":"Start date is required"},"message":"Validation failed","status":400}` |
| 400 | Past date | `{"fieldErrors":{"startDate":"Start date must be today or later"},...}` |
| 400 | Negative budget | `{"fieldErrors":{"budget":"Budget must be non-negative"},...}` |
| 403 | Bez JWT-a | (Spring Security default response) |
| 404 | Nepoznat style | `{"status":404,"message":"Trip style not found: <slug>"}` |
| 404 | Nepoznat template | `{"status":404,"message":"Trip template '<slug>' not found in style '<styleSlug>'"}` |

## TypeScript interfaces

Spremno za kopiranje u frontend modela:

```typescript
export type Season = 'SPRING' | 'SUMMER' | 'AUTUMN' | 'WINTER' | 'YEAR_ROUND';

export type Interest =
  | 'CULTURE' | 'FOOD' | 'ADVENTURE' | 'NATURE'
  | 'NIGHTLIFE' | 'SHOPPING' | 'RELAXATION' | 'HISTORY';

export interface TripStyleResponse {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  templateCount: number;
}

export interface TripTemplateSummaryResponse {
  id: number;
  slug: string;
  name: string;
  destination: string;
  durationDays: number;
  recommendedSeason: Season;
  imageUrl: string | null;
  estimatedBudget: number | null;
  interests: Interest[];
}

export interface TripStyleDetailResponse {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  templates: TripTemplateSummaryResponse[];
}

export interface TemplateActivityResponse {
  name: string;
  description: string | null;
  location: string | null;
  startTime: string | null;   // "HH:mm:ss"
  endTime: string | null;
}

export interface TemplateDayResponse {
  dayNumber: number;
  notes: string | null;
  activities: TemplateActivityResponse[];
}

export interface TripTemplateDetailResponse {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  destination: string;
  durationDays: number;
  recommendedSeason: Season;
  imageUrl: string | null;
  estimatedBudget: number | null;
  interests: Interest[];
  days: TemplateDayResponse[];
}

export interface ApplyTripTemplateRequest {
  startDate: string;          // "YYYY-MM-DD"
  name?: string;
  budget?: number;
}
```

`TripResponse` (response apply endpointa) se već treba poklapati s onim koji frontend koristi za `POST /trips`.

## Tipičan korisnički tok

1. Korisnik dolazi na "Explore" stranicu (može biti anoniman).
   - `GET /explore/styles` → renderaj card-ove stilova, koristi `templateCount` za badge.
2. Klik na karticu stila → ekran s template summary-jima.
   - `GET /explore/styles/{styleSlug}` → renderaj listu template-a (slika, ime, destinacija, trajanje, season tag, budget, interest chip-ovi).
3. Klik na pojedini template → preview ekran s itinerarom.
   - `GET /explore/styles/{styleSlug}/templates/{templateSlug}` → renderaj dane i aktivnosti.
   - Ako je korisnik anoniman: prikaži CTA "Login to apply" (button vodi na login flow).
4. Korisnik s tokenom klikne "Apply" → modal traži `startDate` (datepicker, min = danas) + opcionalne `name` i `budget` overrides.
   - `POST /explore/styles/{styleSlug}/templates/{templateSlug}/apply` → 201 sa `TripResponse`.
   - Redirect na `/trips/{newTripId}` da korisnik vidi svoj novi trip s svim danima/aktivnostima.

## Stvari na koje treba paziti

1. **Slug URL encoding** — slugovi su lowercase + crtice (`ibiza-summer`), bez specijalnih znakova. Frontend ih može koristiti direktno u URL-u bez `encodeURIComponent`-a, ali ne škodi za sigurnost.
2. **Activity sort** — aktivnosti unutar dana su sortirane po `startTime` ascending. Aktivnosti koje prelaze ponoć (npr. Pacha night 00:30–06:00) pojavljuju se na početku dana, ne na kraju. Frontend može dodatno regrupirati ako UI to traži, ali default sort je takav.
3. **`null` polja** — slika (`imageUrl`), `description` i `estimatedBudget` mogu biti `null`. Frontend mora handle-ati.
4. **`recommendedSeason`** kao tag — `YEAR_ROUND` je posebna vrijednost koju treba prikazati npr. kao "Whole year" umjesto "Year round".
5. **Dva apply-a istog template-a** su dozvoljena — svaki kreira nezavisan Trip. Frontend ne treba sprječavati.
6. **Reseed** — backend može osvježiti katalog na restart-u (truncate-and-reload). To znači da `id`-jevi stilova/template-a mogu se promijeniti između sessiona; **slugovi su stabilni**, koristi njih za URL-ove i za navigaciju, ne `id`.
7. **Apply ne briše/mijenja template** — applied Trip je nezavisna kopija. Brisanje user-trip-a nikad ne utječe na katalog.

## Quick curl recipes

```bash
# Browse
curl http://localhost:8080/explore/styles
curl http://localhost:8080/explore/styles/nightlife
curl http://localhost:8080/explore/styles/nightlife/templates/ibiza-summer

# Login first to get JWT
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"..."}' | jq -r '.token')

# Apply
curl -X POST http://localhost:8080/explore/styles/nightlife/templates/ibiza-summer/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2026-06-01"}'
```
