# AZZA — Android

The native Android app of the AZZA marketplace: **Kotlin + Jetpack Compose**,
multi-module, Hilt, Retrofit/OkHttp, kotlinx.serialization, Coil.

It shares **one database** with the website — but never touches it. Both
clients talk to the same backend, **apps/api** (NestJS, in the web repo at
`AZZA/apps/api`), and apps/api is the only thing that reads or writes
Postgres. Same accounts, same listings, same orders.

Two repositories, one product:
- this app — `D:\Folders	he_store\AZZA_android`, GitHub `kouroshov/AZZA_android` (private);
- the website and apps/api — `D:\Folders	he_store\AZZA\AZZA`, GitHub `kouroshov/AZZA` (private).

A change the app needs on the server goes into the web repo, on its own
branch, merged to `master` through a pull request (e.g. #37).

```
 Android app ──HTTPS──┐
                      ├──► apps/api (/api/v1) ──► PostgreSQL (one schema)
 Next.js web ─────────┘        ▲
   (BFF: server-side calls, session bridge)
```

## Run it (local dev)

1. Install **Android Studio** (it ships its own JDK and the Android SDK).
   Toolchain: AGP 9.4 (built-in Kotlin), Gradle 9.8, Kotlin 2.4, compileSdk 37.
2. In the web repo, start the backend as usual:
   `npx prisma dev` (repo root) and `npm run start:dev` (in `apps/api`).
   apps/api listens on `0.0.0.0:4000`.
3. Open this folder in Android Studio and let Gradle sync.
4. Run the `app` configuration on an **emulator** — it reaches the PC as
   `10.0.2.2`, which is the debug default.
   **On a real phone** (same Wi-Fi / hotspot), copy `local.properties.example`
   to `local.properties` and set `azza.apiBaseUrl=http://<PC LAN IP>:4000/api/v1/`.
   apps/api's dev CORS does not matter here — a native app is not a browser.

Command line (Studio's bundled JDK works: set `JAVA_HOME` to
`<Android Studio>/jbr`):

```
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

## Where things are

| Module | What |
|---|---|
| `app` | Activity, the navigation graph, top/bottom bars, environment config |
| `core:model` | Domain types the screens use (no JSON, no Android) |
| `core:network` | apps/api DTOs, Retrofit interfaces, token refresh, media URLs |
| `core:datastore` | The session, encrypted under an Android Keystore key |
| `core:data` | Repositories: DTO → model, every call returns an `Outcome` |
| `core:designsystem` | The web's design tokens as a Compose theme |
| `core:ui` | Shared composables (post card, states, price formatting) |
| `feature:*` | One area of the app each; features never depend on each other |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the rules and
[docs/ROADMAP.md](docs/ROADMAP.md) for what is built and what is next.
