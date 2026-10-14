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
