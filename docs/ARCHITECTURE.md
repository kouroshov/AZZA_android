# Architecture — AZZA Android

## 1. Why a separate repository

The web repo (`AZZA/AZZA`, GitHub `kouroshov/AZZA`) holds the Next.js app,
apps/api and the one Prisma schema. This app shares **no code** with it —
only the HTTP contract of apps/api. A separate repo keeps Gradle, Android
Studio and Play-release tooling out of the web repo's CI and lets the two
release on their own schedules. The contract is the coupling point, so:

- **apps/api is the only backend.** The app never talks to Postgres, and
  never to the Next.js app's `/api/*` routes (those are a BFF for the
  browser, authenticated by a cookie the app does not have).
- **A response change in apps/api can break installed apps**, which update
  slowly. Add fields freely (the app's `Json` ignores unknown keys); never
  rename or remove one without a version plan.

## 2. Authentication

- `POST /auth/login` / `register` return `{ user, accessToken, refreshToken }`.
  Both tokens are stored encrypted (AES-GCM, Android Keystore key) in
  DataStore — `core:datastore/SessionStore`, the only writer.
- Every request carries `Authorization: Bearer <access>`
  (`AuthInterceptor`). On a 401, `TokenAuthenticator` spends the refresh
  token once — refresh tokens are **single-use and rotated** by apps/api, so
  this is serialised behind a mutex, and a request that lost the race just
  retries with the new token.
- A refused refresh (expired, revoked, suspended account —
  `sessions_valid_after`) clears the session: the person is signed out.
- Accounts are the website's accounts. apps/api verifies both hash schemes
  (bcrypt rows from the web's signup, Argon2id of its own).
- The Next.js session bridge (`/auth/bridge`) is NOT used — it exists for a
  server that already holds a web session.

## 3. Rules carried over from the web (CLAUDE.md there is the source)

- **Money**: integer minor units + currency; 100 minor per major for every
  currency. A percentage is applied as basis points in integer math
  (`core:ui/PriceFormat.kt`). The server is the authority on any price that
  is charged — the app only displays.
- **Branch on `error.code`, never on the message.** `safeApiCall` keeps the
  code; screens map codes to their own sentences.
- **Media** arrives as a storage key; `MediaUrlResolver` turns it into
  `<apps/api>/api/v1/media/<key>`, mirroring the web's `resolveMediaUrl`.
- **The phone layout**: top bar (brand start; notifications + more end),
  bottom bar (feed, search, +, basket, own page) — the web's phone layout,
  which is itself modelled on Instagram's. No stories or highlights.
- **A personal listing is one physical item** — enforced by apps/api; the
  create screen must mirror it (goods only, no quantity field), as the web
  form does.
- **Design tokens** in `core:designsystem/theme/Color.kt` are the web's
  `globals.css` values. Change both together.

## 4. Module rules

- `feature:*` depends on `core:*` only. Features meet in `:app`'s NavHost
  through type-safe `@Serializable` routes each feature exports.
- DTOs (`core:network`) never reach a screen; repositories map them to
  `core:model` types.
- Every network call goes through `safeApiCall` and returns `Outcome`.
- New dependency versions go into `gradle/libs.versions.toml` only.

## 5. Machine notes (this PC)

- WDAC refuses unsigned native binaries. Android Studio, the SDK and the
  Gradle distribution are signed; if a build tool is ever refused, do not
  change the machine's security settings — find a signed alternative.

## 6. The scanner

- **`core:scanner`**: `CodeScanner` — CameraX + ZXing, EAN-13 / EAN-8 /
  UPC-A / UPC-E (expanded to its UPC-A) / Code 128 / QR, the same value
  ignored for 1.5 s, beep + buzz on a new one, torch, camera-permission
  explanation with typing as the fallback, and keyboard-wedge (Bluetooth /
  USB) scanners. ZXing, not ML Kit: no Google Play Services needed. A
  `ScanSource` replaces the camera in tests. Used by the Search box's
  barcode button (`FindByBarcodeRoute`), which opens the post with that
  code.
- The sales register and the store inventory (POS-03 to POS-05) were
  removed on 2026-10-21 at the product owner's request; the app cleans up
  what they left on a phone (`AzzaApplication.removeSalesRegisterLeftovers`).
- `AppError.Api.details` carries the error envelope's `details` (raw JSON).

## 7. The real-estate map (2026-10-22)

- **`core:map`**: what every map shares — `MAP_STYLE_URL`, the satellite
  look (`Style.applyBaseLook`: one raster layer above the background, the
  base style's fills and lines hidden while it shows; `SATELLITE_TILES` is
  Esri World Imagery for development only — licensing is the web repo's
  docs/PRODUCTION-READINESS.md §8), the deal colours, `currentLocation`,
  the boundary geometry (the same formulas as apps/api's parcel-geometry;
  the server measures again on save) and the boundary editor.
- **The listing form** (`feature:create`): for a category apps/api says goes
  on the map (`GET /map/parcels/rules`), `ParcelSection` offers none / a
  point / (land) the borders. Drawing is full screen (`ParcelEditorDialog`):
  a tap adds a corner, side lengths are drawn on the map, area and perimeter
  live; a touch that starts on a corner, a side's middle dot or the pin is
  the editor's (`MapView.setOnTouchListener`), anything else is the map's.
  An existing boundary of the same land is offered (`POST /map/parcels/similar`)
  — a copy, never a link.
- **The map** (`feature:map`): Stores | Real estate, and Satellite, as chips.
  Real estate: dots and clusters, boundaries from zoom 14 (`RealEstateLayer.kt`),
  deal filter chips; a tap opens `ParcelCard` — every listing on that spot,
  newest first, with the agency's (or seller's) page.
