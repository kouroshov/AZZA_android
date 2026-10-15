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
- Needed an apps/api fix (web repo, branch `api/android-support`): a
  deposit checkout (`appointmentIds`, no `items`) used to take the server
  cart too whenever it held anything — the app's basket is that cart.
  Now it never does; e2e test added, booking + orders 84/84.

Not in 3c: editing the provider's working week and booking settings
(`/booking/calendar`, `PUT /products/:id/booking`) — the web keeps those on
the store's Timetable tab; they come with store management (3e). The
provider side was not exercised on the emulator (the test account
provides no timed service).

### Still to do in phase 3

- **3d** Map (`/map/branches`) — MapLibre Android SDK.
- **3e** Support tickets, reports, appeals, addresses, account settings,
  email change, password reset; the settings ("more") menu, language switch
  (per-app locale), theme.

## Phase 4 — Android-only

Needs a decision and, for some, backend work in apps/api first:
- **Push notifications.** Today notifications are in-app only. FCM is not
  reliably reachable in Iran — decide FCM vs. a local provider (e.g.
  Pushe/Najva) or a WorkManager poll of `/notifications/unread-count`.
  Needs a device-token table + endpoint in apps/api.
- **Barcode scanning** of the EAN-13 codes every product already has
  (CameraX + ML Kit, or ZXing — ML Kit needs Play Services).
- Camera-first listing creation, share sheet / deep links
  (`https://<domain>/…` App Links → the same screens), home-screen widgets,
  offline cache (Room) of the feed and own data.
- Biometric unlock of the stored session.

## Before the first release

- Confirm the `applicationId` (`com.azza.app` now — permanent once uploaded).
- The production apps/api URL (https) in `app/build.gradle.kts`.
- Signing key (kept out of git), Play Console / Cafe Bazaar / Myket listing.
  Bazaar and Myket matter more than Play for Iranian users.
- Fonts: bundle the web's Geist + Noto Sans Arabic (or Vazirmatn) as
  resources; system font for now.
- Prices: the web card prints `$` + the amount; when the web picks a real
  currency label (ریال/تومان), change `PriceFormat.kt` with it.
- ckb (Sorani) translation, as the web has.
- apps/api `THROTTLE` is per IP for the app (the right behaviour); check
  the limits against real mobile traffic behind carrier NAT.
