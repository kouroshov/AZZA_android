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

## 6. The sales register — offline data (POS-03)

The register works without internet; the server (apps/api, POS-01/02 — see
the web repo's `docs/architecture/pos-register-api.md`) is the source of truth.

- **Its own database**: `core:database/pos/PosDatabase` (`azza_pos.db`), NOT
  the cache `AzzaDatabase`. The cache may be dropped on any schema change;
  this one holds sales that have not reached the server, so its schema is
  exported (`core/database/schemas`), every version step gets a migration in
  `PosMigrations`, and there is no destructive fallback.
- **Tables**: `catalog_products`, `catalog_codes` (looked up by storeId +
  code, never across stores), `branch_stock` (`serverStoreQty` as the server
  said; `storeQty` = that less the sales still queued), `sync_state` (the
  server's cursor per store/branch), `pending_sales` + `pending_sale_items`.
- **Codes**: `core:common/ProductCodes` is a line-for-line port of apps/api's
  `product-code.util.ts`; its unit test runs the server's own vectors.
  `PosCatalogRepository.lookup()` answers Product / UnknownCode / PostLink
  (an AZZA `/store/<id>` or `/q/<…>` address) / Invalid, from the local copy.
- **Catalog sync**: full on the first open of a store/branch
  (`PosCatalogRepository.open`), then deltas with the cursor, tombstones
  applied; a refused cursor starts over. Triggers: app to foreground, network
  back, after an upload that recorded sales, every 30 min (WorkManager),
  and on sign-in (each own store's main branch).
- **Sales**: `SaleQueue.recordSale()` writes the sale, its lines and the local
  shop-floor counts in ONE Room transaction, with an id made on the phone —
  the server's idempotency key — then enqueues `SaleUploadWorker` (unique,
  APPEND_OR_REPLACE, network constraint, exponential backoff). Batches of up
  to 100; ACCEPTED/DUPLICATE leave the queue, REJECTED stay with the reason
  (`SaleQueue.rejected`). A batch with no answer is resent whole: safe,
  because the server records an id once.
- **Account**: `PosSession.signOut()` is refused while sales are queued
  (Settings shows how many); with an empty queue it clears the register's
  tables. A sign-out the app cannot refuse (expired session, password
  changed) keeps queued sales; they upload when that account signs in again.
  `AppPreferences.posDeviceId()` is the phone's stable register id.
