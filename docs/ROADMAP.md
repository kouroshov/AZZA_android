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

## Phase 3 — selling and profiles

- Categories, city feed (`/cities/feed`), following feed (`/feed/following`).
- Create / edit listing with the taxonomy schema form
  (`/taxonomy/categories/:id/schema`, `/products`, media upload) — mirror the
  personal-listing rule (goods only, one item).
- Public profiles (`/users/by-username/:u`), stores (`/stores/:idOrSlug`),
  follow, followers, store management, branches, branch inventory.
- Booking: availability, appointments, provider calendar, deposits.
- Map (`/map/branches`) — MapLibre Android SDK.
- Support tickets, reports, appeals, addresses, account settings, email
  change, password reset.
- The settings ("more") menu, language switch (per-app locale), theme.

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
