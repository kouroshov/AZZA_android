# Roadmap — AZZA Android

Goal: everything the website does, plus Android-only features, against the
same apps/api and the same database. UI follows the web's phone layout.

## Phase 1 — foundation ✅ (this commit)

- Multi-module Gradle build with convention plugins (`build-logic/`).
- apps/api client: envelope/error parsing, bearer auth, single-use refresh
  rotation, encrypted session storage, media URLs.
- Design system from the web's tokens (dark default, light follows system),
  en + fa with RTL.
- Screens: sign in, sign up, Explore feed (cursor paging, pull to refresh),
  product page (photo pager, price, stock), own profile + sign out.
- Phone chrome: top bar + bottom bar; Search / Post / Basket /
  Notifications are placeholders.

## Phase 2 — buying ✅ (2026-10-15)

Built and checked end to end on the emulator against the local apps/api:
- **Search** (`/search/products|users|stores`), with tabs and product sort;
  debounced, and never sent with fewer than 2 letters (apps/api refuses it).
- **Favourites**: the heart on the product page (optimistic, put back if
  refused) and the list on the profile, with unfavourite.
- **Basket** = apps/api's server cart (`/cart`), so it follows the account
  across devices; the badge on the bag. Four tabs like the web's /Basket:
  basket, orders placed, orders received, appointments (phase 3).
- **Checkout** from the server cart: address, one promo code (previewed with
  `/discount-codes/validate`, decided by checkout), then the web's
  "Pay now (test)" (`/payments/simulate`, dev only).
- **Orders**: placed and received lists and details; the seller's earnings,
  fee, the buyer's address once paid, and "Mark as shipped".
- **Notifications**: the list with the web's sentences for every member-facing
  type, tap-through to the order or product, unread badge on the bell
  (refreshed on navigation and when the app comes forward).

Known gaps, deliberately left:
- **The web's basket is still browser localStorage**, so the website and the
  app do not share a basket yet. Moving the web to `/cart` (it already exists)
  would make them one basket — a web-repo change, not done here.
- **Product options** ("extra sauce") cannot be chosen in the app: the server
  cart stores product + quantity only. Needs an options column on
  `cart_items` in apps/api first.
- Comments and reviews: the web has no UI for them either, and apps/api's list
  responses carry no author name.
- Person and store rows in search are shown, not opened (profiles = phase 3).

## Phase 3 — selling and profiles (in parts)

### 3a — profiles, follow, feeds ✅ (2026-10-15)

- **The bottom bar is the web's**: Following · Explore · Map · "+" · Basket ·
  Profile (Sidebar.tsx `bottomBarItems`), "+" for signed-in people only.
- **Following** (start tab): `/feed/following`, re-read after a follow or
  unfollow anywhere in the app.
- **Explore**: the search box on top; with nothing typed, the Global feed or
  one City's feed (`/cities/feed`), the city picked from apps/api's own
  provinces and cities and remembered on the device.
- **Public profiles** (`/users/by-username/:u`) and **store pages**
  (`/stores/:idOrSlug`): header, follower count, Follow / Following (hidden
  on your own page or store), a three-column grid of posts. Post authors,
  people and stores in search all open these.
- **Own profile**: Posts and Favorites tabs.
- Needed one apps/api addition (web repo, branch `api/products-list-scope`):
  `GET /products?storeId=` and `?sellerId=&personal=true`, so a store's or a
  person's listings can be read without a token. Until that branch is
  merged there, store and profile grids need it running.

Not in 3a: followers / following lists (only your own exist in apps/api),
a person's stores on their public page (no public endpoint lists them).

### 3b — posting and editing listings ✅ (2026-10-15)

- **"+"** opens the post form: Post as (personal page or one of your
  stores), category (search, or browse the tree), offering kind (a personal
  page: physical goods only), the category's own fields, title,
  description, price, discount, condition, stock (stores only, placed at
  the main branch), photos from the system photo picker (no permission).
- **The category fields follow apps/api's rule engine**, ported to
  `core:model/SchemaRules.kt` and checked against apps/api's own
  conformance cases (`schema-rules.conformance.ts`, 18 cases). When the
  server's rules change, port the change and the new cases.
- **The web's posting order**: created hidden → photos upload in order (the
  first is the cover) → published; a failed photo leaves nothing live and
  "Try again" continues the same hidden listing. Photos are shrunk to
  2048 px JPEG under apps/api's 5 MB limit, rotation applied.
- **Edit and delete** your own listing from its page ("Edit"): the same form
  pre-filled with the saved fields and photos; a saved photo is removed at
  once; delete is apps/api's soft DELETED status, after a confirmation.
- Your own listing has no basket button (checkout refuses it anyway).

Not in 3b: product options ("extra sauce"), the occupation step, the
deposit for a bookable listing (3c), reordering photos, store creation and
management (needs a seller subscription — 3e), posting from a store was
not exercised on the emulator (the test account has no store).

### 3c — booking ✅ (2026-10-15)

- **On a bookable listing** (`/products/:id/booking[/availability]`): a week
  of days with their free times, shown in the provider's timezone, a note,
  then Book (confirmed at once, paid at the visit) or Hold (when a deposit
  keeps the time). A lost race is apps/api's 409 SLOT_TAKEN and the week
  re-reads. No basket button on a bookable listing; none on your own.
- **Basket → Appointments**: deposits to pay (with the hold's countdown;
  name, email and an optional address, then the deposit as its own order,
  paid like any order), upcoming, past; cancel. **My calendar**: what was
  booked with you, with the customer's contact details and note; done /
  no-show / cancel.
- Dates follow the web's Intl output: Jalali calendar and Persian digits in
  Persian (android.icu), e.g. «پنجشنبه ۲۳ مهر، ۱۵:۰۰».
- Needed an apps/api fix (web repo, PR #37, merged into `master` 2026-10-15): a
  deposit checkout (`appointmentIds`, no `items`) used to take the server
  cart too whenever it held anything — the app's basket is that cart.
  Now it never does; e2e test added, booking + orders 84/84.

Not in 3c: editing the provider's working week and booking settings
(`/booking/calendar`, `PUT /products/:id/booking`) — the web keeps those on
the store's Timetable tab; they come with store management (3e). The
provider side was not exercised on the emulator (the test account
provides no timed service).

### 3d — map ✅ (2026-10-15)

- **The "AZZA Map" tab** (the web's `/`): MapLibre GL with the web's tiles
  (OpenFreeMap "liberty", no key), store branches loaded for the visible
  area (`/map/branches`), blue when open now, grey otherwise. A dot opens
  the store preview (`/map/branches/:id`): open status, address, some of
  its products, and "Open the store".
- apps/api refuses an area wider than 22° a side; zoomed further out, the
  app asks for the middle of the screen.
- Found and fixed in apps/api (web repo, PR #37, merged into `master` 2026-10-15):
  the public map counted and previewed **unpublished** listings (a DRAFT
  with branch stock showed its title and price to anyone). e2e added.

Not in 3d: the web's category filter and store list beside the map, "near
me" (location permission), clustering.

### 3e — account and support ✅ (2026-10-15)

- **Settings** (the top bar's ⋮, the web's settings popover as a page):
  edit profile, support, language, theme, log out (or sign in).
- **Language**: phone's / English / فارسی, per app
  (`AppCompatDelegate.setApplicationLocales`, stored by AppCompat below
  Android 13, by the system from 13 up).
- **Theme**: dark (default, like the web), light, or the phone's setting;
  stored in DataStore, applied in `MainActivity`, system-bar icons follow.
- **Edit profile**: first and last name, bio (150 characters, counted as a
  person counts them); `PATCH /users/me`.
- **Support tickets** (`/tickets`): list with "new reply", open a ticket
  (topic, subject, text), the thread, reply, "I no longer need help".
- **Report** a listing, an account or a store (`POST /reports`): the web's
  reasons and copy; "already reported" and "your own" answered by code.
  A guest is sent to sign in.
- **Forgot password** on sign-in (`POST /auth/forgot-password`): the same
  answer whether or not the account exists. The link opens the website;
  the app has no reset screen.

Left out of 3e, for later: changing the avatar, username or email;
addresses; moderation appeals; creating and managing a store (needs a
seller subscription), and with it posting from a store and the provider's
timetable editor.

## Phase 4 — Android-only (in parts)

Decided 2026-10-15: notifications by polling (not FCM / an Iranian push
provider); this round builds 4a–4d below; biometric unlock waits.

### 4a — notifications on the phone ✅ (2026-10-15)

- **Polling, not push.** FCM is unreliable in Iran and a local provider
  (Pushe/Najva) needs an account and apps/api work. WorkManager runs
  `NotificationPollWorker` about every 15 minutes (Android's minimum),
  only on a network, only while signed in and switched on. It reads the
  first page of `/notifications` and posts what is unread and newer than
  the last poll — the first poll after sign-in only draws that line, so a
  phone is never flooded with history. No apps/api change.
- Up to four entries, more as one "N new notifications"; a tap opens the
  in-app list, which clears the shade. Signing out (or switching it off)
  stops the work and clears the shade; the line is reset per account.
- Android 13+: the permission is asked once, after sign-in. Settings has
  "Notifications on this phone".
- The text is the in-app list's own (`NotificationText.kt`), in the app's
  language (below Android 13, once the app has been opened in that process;
  before that, the phone's).
- Real push later replaces `NotificationPolling` only; `SystemNotifications`
  stays.

### 4b — barcode scanning ✅ (2026-10-15)

- Explore's search field has a scan button when empty. CameraX + **ZXing**
  (pure Java): ML Kit's "bundled" barcode library still depends on Play
  Services libraries, and many phones in Iran have none. EAN-13 only,
  read level or sideways, check digit verified; the torch can be switched
  on; the 13 digits can be typed instead (no camera, or a worn label).
- apps/api (web repo, PR #37, merged into `master` 2026-10-15): new public
  `GET /products/barcode/:code` → `{ productId }`, for exactly the listings
  `GET /products/:id` shows. e2e in `geography-stores`.

### 4c — share and deep links ✅ (2026-10-15)

- **Share** on a listing, a person's page and a store page: the phone's
  share sheet with the title and the website's address (`WebLinks`, the
  web's paths `/store/<id>`, `/u/<username>`, `/stores/<slug>`).
- **Links open in the app**: the same three paths, with or without a
  locale (`/fa/…`, `/ckb/…`). Any other page on the site is handed back to
  the browser. The origin comes from the build: `azza.webBaseUrl` in
  `local.properties` for debug (default `http://10.0.2.2:3000`, next dev
  on the PC), `https://azza.example` (placeholder) for release.
- **App Links** (`autoVerify`): the website now serves
  `/.well-known/assetlinks.json` from `ANDROID_APP_PACKAGE` /
  `ANDROID_CERT_SHA256` (web repo, PR #37, merged into `master` 2026-10-15). Until
  the release key's fingerprint is set there, Android asks which app to
  open a link with instead of opening AZZA directly.

### 4d — offline and the widget ✅ (2026-10-15)

- **Offline cache** (`core:database`, Room, one table): the FIRST page of
  Explore, Following, notifications, orders placed and received, and the
  last 100 listings opened, saved as apps/api sent them (`OfflineCache`).
  Only when a request gets no answer at all is the saved copy shown, with
  "Offline — showing what was saved …" above the list and no further
  pages; any answer from the server, an error included, wins. Personal
  copies are deleted on sign-out. Photos stay in Coil's disk cache
  (cache headers ignored: media keys never change). A schema change just
  drops the cache — no migrations.
- **Home-screen widget** (Glance): Search, Scan, Alerts, Basket, with the
  unread and basket counts; each opens that screen. It shows saved numbers
  (`WidgetCounts`), updated by the app and by every notification poll —
  it never fetches by itself.

### Still to do in phase 4

- Biometric unlock of the stored session; camera-first listing creation;
  real push (replacing the poll) once a provider is chosen.

## Before the first release

- Confirm the `applicationId` (`com.azza.app` now — permanent once uploaded).
- The production apps/api URL (https) and website origin (`webLinks(…)`)
  in `app/build.gradle.kts`; the release key's SHA-256 in the website's
  `ANDROID_CERT_SHA256` so App Links verify.
- Signing key (kept out of git), Play Console / Cafe Bazaar / Myket listing.
  Bazaar and Myket matter more than Play for Iranian users.
- Fonts: bundle the web's Geist + Noto Sans Arabic (or Vazirmatn) as
  resources; system font for now.
- Prices: the web card prints `$` + the amount; when the web picks a real
  currency label (ریال/تومان), change `PriceFormat.kt` with it.
- ckb (Sorani) translation, as the web has.
- apps/api `THROTTLE` is per IP for the app (the right behaviour); check
  the limits against real mobile traffic behind carrier NAT.
