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

## Phase 5 — closing the gap with the website (in parts)

Decided 2026-10-16: everything the website does should also be in the app.
5a the rest of the account; 5b stores (create, manage, branches, stock,
posting from a store, the booking calendar); 5c the leftovers (Kurdish,
fonts, fingerprint unlock, the smaller "Not in 3x" items).

### 5a — the rest of the account ✅ (2026-10-16)

- **Own profile** in the web's phone layout: photo with the follower and
  following counters beside it (tap → the list), "Edit profile" (sign out
  is in Settings, as on the web). The grid shows the owner's listings in
  EVERY state — drafts, sold, taken down — with a label on each.
- **Owner's view of a listing**: a draft says so; one taken down shows why
  (cause and reason), with the web's appeal form when the decision was
  about this listing (`ADMIN_UNPUBLISHED`) — one appeal per decision, its
  status and the answer afterwards. A held listing has no Edit (it is
  locked) and no buy/share. Opening it from anywhere works: when the
  public page answers 404, a signed-in owner gets the owner's view.
- **Edit profile**: photo (change / remove, shrunk like listing photos),
  username (checked as you type; once every 30 days, the old one retired),
  name and bio.
- **Settings → Email address**: request a change (the link goes to the
  new address and is opened on the website), see it pending, cancel.
- **Settings → Addresses**: add, edit, delete, a default one. Checkout
  fills in the default and offers the others as one tap.
- **Followers / Following** lists (your own; apps/api keeps everyone
  else's private).
- apps/api (web repo, PR #38, merged into `master` 2026-10-16): `GET /products/mine` and
  `GET /products/mine/:id` — the owner's own listings in every state; the
  website reads the database directly for the same thing.
- Found on the way: the app's Json leaves out a property equal to its
  default, so a request field with a default value is never sent. Request
  DTOs must not default what apps/api requires (`AddressRequest`).

### 5b — stores ✅ (2026-10-16)

- **Your stores** (own profile → "Your stores"): the list, and "Create a
  store" when the account has a seller subscription (apps/api's
  eligibility; otherwise it says so).
- **Create / edit a store**: name, username (its page's address), what it
  does (the occupation search), phone, description.
- **The store's own page**: photo (change / remove), followers (count and
  the owner-only list), "New post from this store" (the listing form opens
  with that store chosen), the public page, edit, the timetable, branches.
  A suspended store shows why, with the appeal form when an admin decided
  it (`ADMIN_SUSPENDED`); one closed with its owner's account cannot be
  appealed on its own.
- **Branches**: add one; per branch its name, phone, active switch, opening
  hours per day (time pickers, week starting on the locale's first day),
  closed days, and **where it is**: a map with a fixed pin (moving to the
  chosen province and city), plus the address. apps/api checks the point
  against the city.
- **Stock per branch**: every physical listing of the store with its count
  at this branch (0 when not stocked yet); a tap sets it.
- **Timetable**: ONE per store for all its timed services — the working
  week as windows per day, and days off (a whole day in the calendar's own
  zone). "Extra open" exceptions set on the web are kept untouched.
- **Booking settings** of one service (from the timetable, or from the
  listing's owner view): session length, step, gaps before/after, notice,
  how far ahead, deposit %.
- apps/api (web repo, PR #39, merged into `master` 2026-10-16): `GET /stores/mine` now
  carries the store's status and a hold's cause and reason (never who
  decided).
- Found on the way: apps/api pages branch stock at most 60 per request;
  the area list has each province's and city's centre (now used to move the
  map); the occupation list has no "bakery".

### 5c — leftovers ✅ (2026-10-16)

- **Kurdish (Sorani, `ckb`)**: every screen, in `values-b+ckb`. Where the
  English matches the website's, its `messages/ckb.json` wording is used
  (437 strings, placeholders converted by name); the rest (174) written for
  the app in the same vocabulary (فرۆشگا store, حیجز booking, ڕیکلام
  listing). A language option «کوردی»; dates Gregorian, as the web formats
  `ckb`. Category names show in Persian, as on the web (it has no Kurdish
  ones).
- **Fonts**: the web's own — Geist and Noto Sans Arabic, bundled (Google's
  downloadable fonts need Play Services); the family follows the app's
  language (Arabic script for fa/ckb). OFL licences in `docs/licenses`.
- **Fingerprint lock** (Settings, only on a phone that has a fingerprint,
  face or — Android 11+ — a screen lock): asked at a cold start and after a
  minute away; turning it on or off is itself confirmed. Not exercised on
  the emulator (it has no screen lock, and the device's security settings
  were not changed to make one).
- **Listing options** ("extra sauce"): the seller adds them in the listing
  form (name, extra price, how many one item can take); the buyer picks
  them on the listing page. The server cart cannot hold them, so the choice
  is kept on the phone (`BasketOptions`) and checkout then sends explicit
  lines with the options — apps/api prices them from the database — and
  empties the server cart itself. One set of options per product in the
  basket.
- **Reordering a listing's photos** while editing (at once, like deleting
  one): "move earlier"; the first is the cover.
- **Near me** on the map: the phone's own location service (no Play
  Services), approximate location, asked when the button is used.
- apps/api (web repo, PR #40, merged into `master` 2026-10-16): a listing's options come
  back with it (they were only readable by the website, from the database).

Still not built (no endpoint, or a product decision): a person's stores on
their public page, clustering, a "you are here" dot on the map.

### 5d — the parity audit, closed ✅ (2026-10-16)

Every page and tool of the website was listed and checked against the app;
every gap except the admin panel (kept website-only, by decision) is now
built:

- **Product page:** the category's specifications, free-form features, a
  quantity stepper with the total, the owner's stock per branch, the
  listing's barcode (view and print), "open the timetable".
- **Search and market:** filters (category, condition, availability, price
  range) with the result count; browsing by category with no query (the
  web's marketplace); several Explore cities at once, "All Iran", previous
  choices and a city search; seller badge on people.
- **Stores:** the public store page's hours, branches, "run by", post count;
  a public branch page (address, hours, closures, what it has, maps, call,
  share); the owner's products list (every state, search), the week of
  appointments (done / no-show / cancel), hours for every day at once,
  typed coordinates, stock search and "not published", a setup prompt.
- **Account:** the full sign-up (photo, mobile, province and city, confirm
  password, live username check); mobile and city on Edit profile; the
  emailed reset-password and confirm-email links open in the app; leaving
  an edited profile asks first.
- **Orders and notifications:** All / Personal / per-store filters; a
  printable shipping label (A6, Persian and Latin); chosen options on order
  lines; "saved money" in the basket.
- **Own profile:** long-press a post for Edit / Barcode / Delete / Select,
  then one discount or delete for all selected ("N done, M failed" — one
  request per post, as on the web); post count; share; follow buttons in
  the follower lists; press-and-hold Profile to jump to a store.
- **Map:** category filter, the stores in view, a province/city chooser,
  the distance from you, the "outside Iran" notice.
- **Posting:** the occupation step and its suggested categories, free-form
  features, photos moved later or made the cover, search in your own posts.
- apps/api (web repo, PR #41, merged into `master` 2026-10-16): post counts
  on a person's page and a store's, and a store's owner.

### 5e — directions to a shop ✅ (2026-10-17)

- **Directions** from the store page (single branch), the branch page and
  the map's store preview: the way drawn on AZZA's own map (MapLibre), with
  time and distance by car, on foot or by bike, every turn listed with its
  street name (Persian names from OpenStreetMap), and **Start** to follow
  it live — the next turn in large type, the map following the phone, a
  new way found when the phone leaves the route, "you have arrived" at the
  door, the screen kept on. Voice guidance uses the phone's own
  text-to-speech in the app's language (and says so when the phone has no
  voice for it). "Other app" hands the place to the phone's maps app.
- Precise location is asked for on this screen only (the map's "near me"
  stays approximate); with approximate only, it still shows the way and
  says following may be rough.
- The way comes from apps/api (`GET /map/branches/:id/directions`, web PR
  #43), which asks an OSRM routing server — FOSSGIS's public one by default.
  **Before release: self-host OSRM or Valhalla with an Iran extract** and set
  `ROUTING_URL_TEMPLATE`; the public server is for development and low
  volume only, and the visitor's position (rounded to ~11 m) goes to it.
- Turn-by-turn logic (`RouteFollower`) is plain Kotlin with its own tests.

## Before the first release

- Confirm the `applicationId` (`com.azza.app` now — permanent once uploaded).
- Self-hosted routing server for directions (see 5e).
- The production apps/api URL (https) and website origin (`webLinks(…)`)
  in `app/build.gradle.kts`; the release key's SHA-256 in the website's
  `ANDROID_CERT_SHA256` so App Links verify.
- Signing key (kept out of git), Play Console / Cafe Bazaar / Myket listing.
  Bazaar and Myket matter more than Play for Iranian users.
- Prices: the web card prints `$` + the amount; when the web picks a real
  currency label (ریال/تومان), change `PriceFormat.kt` with it.
- Have a Kurdish speaker read the app's own 174 Sorani strings (the rest
  are the website's wording).
- apps/api `THROTTLE` is per IP for the app (the right behaviour); check
  the limits against real mobile traffic behind carrier NAT.
