# AZZA Android — notes for agents

Read `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` first.

- Backend = **apps/api** in the web repo (`D:\Folders\the_store\AZZA\AZZA\apps\api`,
  GitHub `kouroshov/AZZA`). Its `CLAUDE.md` holds the business rules (money,
  ownership, personal listings, error codes); they apply here too. Read a
  controller/DTO there before modelling a new endpoint — never guess a shape.
- Never call the Next.js `/api/*` routes and never touch the database.
- Kotlin + Jetpack Compose only (decided by the product owner, 2026-10-14).
  No WebView screens.
- Versions live in `gradle/libs.versions.toml`; module wiring in
  `build-logic/convention`.
- This machine's WDAC refuses unsigned native binaries — do not change
  security settings to get a tool to run.
