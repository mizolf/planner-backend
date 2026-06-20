# AI generiranje plana putovanja — backend

Backend implementacija značajke koja iz već unesenih podataka putovanja (**budžet**, **datumi**,
**interesi**, **destinacija**) automatski generira **cijeli plan dan-po-dan** pomoću Google Gemini
modela, geokodira lokacije preko Photona i popuni prazno putovanje. Korisnik dobije polaznu točku
koju dalje uređuje postojećim CRUD alatima.

Ovaj dokument opisuje što je implementirano na backendu i sadrži **handoff za frontend** (zadnja sekcija).

---

## 1. Cilj

- Ulaz: postojeće putovanje (destinacija + koordinate, raspon datuma, budžet, interesi).
- Obrada: Gemini vrati strukturirani plan (dani + aktivnosti), backend svakoj aktivnosti nađe prave
  koordinate (Photon).
- Izlaz: putovanje popunjeno danima i aktivnostima; vraća se puni `TripDetailResponse`.

---

## 2. Endpoint

```
POST /trips/{tripId}/generate-itinerary
```

| | |
|---|---|
| **Auth** | standardni JWT; korisnik mora biti **OWNER** ili **EDITOR** putovanja |
| **Tijelo zahtjeva** | prazno (sav kontekst se izvuče iz putovanja) |
| **Odgovor** | `200 TripDetailResponse` (puno putovanje s danima + aktivnostima) |

**Greške:**

| Status | Kod | Kada |
|---|---|---|
| `409` | `ITINERARY_NOT_EMPTY` | putovanje već ima dane (sprječava dvostruko generiranje) |
| `404` | — | putovanje ne postoji |
| `403` | — | korisnik nije OWNER/EDITOR |
| `502` | `AI_GENERATION_FAILED` | Gemini nedostupan / nevažeći ključ / nevaljan JSON |

---

## 3. Arhitektura / komponente

Svaki dio ima jednu odgovornost (SRP); orkestrator ih poziva redom (pipeline).

| Datoteka | Odgovornost |
|---|---|
| `config/GeminiProperties.java` | record, `@ConfigurationProperties("gemini")` (`apiKey`, `model`, `baseUrl`) |
| `config/GeminiConfig.java` | `geminiRestClient` bean (baseUrl + `x-goog-api-key` header) |
| `config/GeocodingProperties.java` | record `@ConfigurationProperties("geocoding")` (`photonUrl`, `maxRadiusKm`) |
| `config/GeocodingConfig.java` | `photonRestClient` bean (javni API, bez headera) |
| `service/ai/Coordinates.java` | record `(double latitude, double longitude)` |
| `DTO/ai/GeneratedItinerary.java` | `{ days[] }` — cilj parsiranja Geminijevog JSON-a |
| `DTO/ai/GeneratedDay.java` | `{ dayNumber, title, activities[] }` |
| `DTO/ai/GeneratedActivity.java` | `{ name, description, location, startTime, endTime, category, cost }` — vrijeme/kategorija kao **String** |
| `service/GeminiClient.java` | gradi request, zove Gemini, dvostruko parsira, baca `AIGenerationException` |
| `service/GeocodingService.java` | Photon poziv + haversine provjera radijusa → `Optional<Coordinates>` |
| `service/ai/ItineraryGenerationService.java` | `@Transactional` orkestrator (koraci 1–7) |
| `exception/AIGenerationException.java` | `RuntimeException` za AI/Gemini greške |
| `exception/GlobalExceptionHandler.java` | mapira `AIGenerationException` → `502 AI_GENERATION_FAILED` |
| `exception/TripConflictException.java` | dodan `Code.ITINERARY_NOT_EMPTY` (→ 409 preko postojećeg handlera) |
| `controller/ItineraryController.java` | endpoint + `getCurrentUser()` obrazac |

**Reuse postojećeg:** `Trip` / `TripDay` / `Activity` entiteti (+ Lombok `@Builder`),
`TripDayRepository`, `TripRepository`, `TripAuthorizationService`, `TripService.getTripDetail(...)`,
`ResourceNotFoundException`, enumi `ActivityCategory` i `Interest`.

**Ovisnosti:** nema novih. `RestClient` dolazi iz `spring-web`, Jackson je već prisutan.

---

## 4. Tok generiranja (`ItineraryGenerationService`, jedna `@Transactional`)

1. `tripAuthorizationService.validateEditorOrOwner(tripId, currentUser)`.
2. Učitaj `Trip` (`findById` → `ResourceNotFoundException` ako ne postoji).
3. `N = ChronoUnit.DAYS.between(startDate, endDate) + 1` (uključiv raspon).
4. Ako `trip.getDays()` nije prazan → `TripConflictException(ITINERARY_NOT_EMPTY)`.
5. **Centar destinacije:** ako `trip` ima `latitude`/`longitude` → `Coordinates` iz njih; inače
   `geocodingService.geocode(destination, null, null).orElse(null)` (smije ostati `null`).
6. `geminiClient.generate(destination, N, budget, interests)` → `GeneratedItinerary`.
7. Za svaki generirani dan (po **indeksu** petlje) gradi `TripDay`, za svaku aktivnost gradi
   `Activity` (konverzije + geokodiranje), postavlja vlasničke strane veza
   (`day.trip(trip)`, `activity.tripDay(day)`).
8. `tripDayRepository.saveAll(days)` (aktivnosti idu **cascade**).
9. `return tripService.getTripDetail(tripId, currentUser)`.

> **Zašto entitete gradimo direktno**, a ne preko `TripDayService` / `ActivityService`: te metode
> publishaju activity-feed event po svakom danu/aktivnosti (spam) i rade zasebne transakcije.
> Mi želimo jednu atomičnu transakciju.

---

## 5. Gemini integracija (`GeminiClient`)

- `POST {base-url}/models/{model}:generateContent`, header `x-goog-api-key` (u `geminiRestClient` beanu).
- Body:
  ```json
  {
    "systemInstruction": { "parts": [{ "text": "<fiksna pravila>" }] },
    "contents": [{ "parts": [{ "text": "<korisnički prompt>" }] }],
    "generationConfig": {
      "responseMimeType": "application/json",
      "responseSchema": { "...days[] → activities[]..." },
      "temperature": 0.7
    }
  }
  ```
- **System prompt** nosi fiksna pravila: točno N dana, 3–5 aktivnosti/dan, vrijeme `HH:mm` bez
  preklapanja, kategorije iz enuma, lokacije = stvarna geokodabilna mjesta, zbroj `cost` ≤ budžet,
  sadržaj na engleskom.
- **User prompt** nosi varijable putovanja: destinacija, N, budžet, interesi (fallback
  `"general sightseeing"` ako interesa nema).
- `responseSchema` definira strukturu; `category` je `enum`
  (`ATTRACTION, TRANSPORT, ACCOMMODATION, RESTAURANT, OTHER`) → tvrdo ograničenje.

**Dvostruko parsiranje:** Gemini uvijek vrati kuvertu; naš plan je *string* u
`candidates[0].content.parts[0].text`. Zato:
1. izvuci string iz kuverte (sigurna `.path(...)` navigacija),
2. parsiraj taj string u `GeneratedItinerary`.

Svaka greška (mreža, HTTP status, prazan odgovor, nevaljan JSON) → `AIGenerationException`.

---

## 6. Geokodiranje (`GeocodingService`)

Gemini vraća **samo ime lokacije**. Za svaku aktivnost:

1. `q = "{location}, {destination}"`; ako imamo centar → dodaj `lat`/`lon` **bias** (rangiranje,
   ne filter).
2. Uzmi **prvi** feature.
3. **Provjera radijusa (haversine):** udaljenost rezultata do centra; ako > `max-radius-km`
   (default 100) → odbaci (`Optional.empty()`). Sprječava istoimeno mjesto u drugoj državi.
4. Ako nema centra → best-effort bez provjere radijusa.
5. Bilo koja greška / prazan rezultat → `Optional.empty()`; aktivnost se svejedno spremi, samo nije
   na karti.

> ⚠️ GeoJSON redoslijed je `coordinates = [longitude, latitude]` (lon prvi). Obrtanje u "lat, lon"
> radi se na jednom mjestu, pri parsiranju.

**Politika greške** je drukčija od Geminija: Gemini = fatalno (502), geokodiranje = best-effort
(aktivnost bez koordinata).

> Napomena za produkciju: pozivi su za v1 **sekvencijalni**. Optimizacija je ograničeni paralelizam,
> a Photon bi se self-hostao (fair-use javnog API-ja).

---

## 7. Konfiguracija

`application.properties`:

```properties
# Gemini AI
gemini.api-key=${GEMINI_API_KEY}
gemini.model=gemini-2.5-flash
gemini.base-url=https://generativelanguage.googleapis.com/v1beta

# Geocoding (Photon)
geocoding.photon-url=https://photon.komoot.io/api/
geocoding.max-radius-km=100
```

`GEMINI_API_KEY` se dohvati besplatno na **Google AI Studio** (bez kartice) i upiše u `.env`
(isti `${VAR}` obrazac kao JWT / mail tajne; `.env` se učitava preko
`spring.config.import=optional:file:.env[.properties]`).

---

## 8. Ključne odluke (i zašto)

- **Trust boundary:** `startTime` / `endTime` / `category` u DTO-u su **String** (sirovo od AI-a);
  konverzija u `LocalTime` / `ActivityCategory` (s fallbackom `OTHER`) radi se pri mapiranju u
  entitet, da jedna kriva vrijednost ne sruši cijelo spremanje.
- **`dayNumber` / `date` po indeksu petlje**, ne po Geminijevom `dayNumber` — garantira sekvencijalne,
  jedinstvene datume unutar raspona (datume dodjeljuje backend, ne model).
- **Jedna `@Transactional`** → atomično (sve ili ništa), bez feed-spam evenata.
- **Sekvencijalno geokodiranje** za v1 (vidi napomenu u sekciji 6).
- **Reuse:** `TripConflictException` (umjesto nove klase, već mapiran na 409) i
  `tripService.getTripDetail(...)` (umjesto novog mappera za odgovor).

---

## 9. Frontend — što treba napraviti (handoff)

Backend je gotov i izlaže ugovor iz sekcije 2. Frontend dio (prema
`planner-frontend/docs/ai-itinerary-generation.md`):

### `core/services/trip.service.ts`
- `_generating = signal(false)` + `generating` readonly.
- `generateItinerary(tripId): Observable<TripDetailResponse>` → `POST /trips/{id}/generate-itinerary`.
  - start: `_generating.set(true)`
  - uspjeh: `_tripDetail.set(detail)` + `_generating.set(false)`
  - greška: `_generating.set(false)`

### `create-trip-dialog.component.ts/.html`
- Nova kontrola `generateWithAi` (checkbox) + UI checkbox (Tailwind, lokalno).
- Injektaj `Router`. U `onSubmit()` na uspjeh `createTrip`:
  - ako `generateWithAi` → `close()`, `router.navigate(['/trips', newTrip.id])`, zatim
    `tripService.generateItinerary(newTrip.id).subscribe({ error: toast })`;
  - inače → postojeće ponašanje (close + success toast).

### `trip-detail-page.component.html`
- Kad je `generating()` true, u sekciji dana prikaži "AI generira tvoj plan…" loading (reuse
  postojeći `animate-pulse` skeleton). Prazno stanje (`days.length === 0`) ostaje nepromijenjeno.

### i18n (`public/assets/i18n/en.json`, `hr.json`)
- Novi ključevi: checkbox label, loading poruka, success / error toast.

### Modeli
- Nema novih tipova — `generate-itinerary` vraća `TripDetailResponse` (već tipiziran); request bez tijela.

### API ugovor (za mapiranje toastova)
- Uspjeh `200` → `TripDetailResponse`.
- `409 ITINERARY_NOT_EMPTY`, `403`, `404`, `502 AI_GENERATION_FAILED` → odgovarajući error toast.

---

## 10. Verifikacija (manualni smoke test)

1. Besplatni Gemini API ključ (Google AI Studio) → `GEMINI_API_KEY` u backend `.env`.
2. Pokreni backend i frontend.
3. Kreiraj putovanje (destinacija iz autocompletea radi koordinata, ~4 dana, budžet, par interesa),
   uključi "✨ Generiraj plan s AI", spremi.
4. Očekivano: navigacija na detalje → loading → popunjeni dani s aktivnostima. Provjeri: broj dana =
   raspon datuma, vremena logična bez preklapanja, kategorije ispravne, zbroj cijena ≈ unutar budžeta,
   geokodirane aktivnosti vidljive na karti.
5. Rubni slučajevi: nevažeći `GEMINI_API_KEY` → error toast, putovanje ostaje prazno (ne puca);
   destinacija bez koordinata → aktivnosti se i dalje generiraju; ponovni poziv na putovanju s danima → 409.
6. Bez checkboxa: tok kreiranja radi kao prije.
