# AGENTS.md — menosan-android

Kotlin + Jetpack Compose app for Menosan. **Read `docs/DEVELOPMENT_PLAN.md` §1–§8 and §10 before you start any task.** The backend contract lives in `menosan-api/docs/api-contract.md`. Treat it as read-only here.

## Ground rules
- `minSdk 26` (Android 8.0). Compose + Material 3, MVVM with immutable UI state, Hilt, Room, WorkManager, Retrofit + kotlinx.serialization, Firebase Auth + Credential Manager.
- Week math always uses `ZoneId.of("Asia/Manila")`, Sunday to Saturday, never the device default zone.
- **Room is the source of truth for current-week entries**, and it keeps the two previous weeks too, plus the full cached server reports for the last 2 reports. Every write goes to Room first, then the outbox syncs through `POST /v1/entries/sync`. Entry ids are client-generated UUIDs, and `createdAt` is set at save time.
- **No destructive Room migrations after the first tester build (Sat 9/26).** Write a `Migration` for every schema change.
- Photo logging is online-only. Delete temp image files right after analysis. AI-suggested fields must look visibly different until the user edits or confirms them, and saving requires an explicit confirmation.
- **Offline report (plan §5.7):** when a week has closed and the device is offline, build a provisional report on the device using `core/analytics`, a port of the backend's `aggregate()`, `findHotspots()`, `compare()`, and `measureImpact()`. It includes totals and hotspots, plus the comparison, the impact of last week's adoptions, and a last-week recap when local data allows. New intervention recommendations and adoption stay online-only. It must pass `app/src/test/resources/analytics-test-vectors.json`. The server report replaces it once the device is online.
- Never call Gemini from the app, and never embed API keys. All AI goes through the backend.
- Copy is English, short, and supportive. No blame, no rankings, no comparisons with other households.
- **UI style follows `docs/DESIGN.md`** (tokens, components, and a screen map to the mockups in `docs/design/`). The mockups are a **style reference only**: flows, features, and behavior come from the plan and the contract (DESIGN.md §5). Open the matching mockup before building a screen.
- `taxonomy.json` in `assets/` must match `menosan-api/src/main/resources/taxonomy.json`.

## Session start and handoff (mandatory)
1. **Before starting:** read `docs/HANDOFF.md` (newest entry first), then `git log --oneline -15` and `git status`. Continue from "Next steps" unless the human says otherwise.
2. **Before stopping:** update `docs/HANDOFF.md` using `docs/HANDOFF_TEMPLATE.md`, putting the new entry at the top. Do the same at the end of a session, after finishing a workstream, or when your context or tokens are running low. Stop coding and write the handoff first.
3. Commit the handoff with the code: `docs: handoff <workstream>`.
4. Never leave uncommitted work without describing it in the handoff.

## Workstreams and ownership
| WS | Owns |
|---|---|
| AN-0 | Gradle, `di/`, `navigation/`, `core/`, `data/remote/`, Room setup, `feature/auth/` |
| AN-1 | `data/local/` entry tables, `data/repo/EntryRepository`, `sync/`, `feature/logging/`, `feature/entries/` |
| AN-2 | `feature/photo/` |
| AN-3 | `feature/reports/`, `feature/interventions/`, `data/repo/ReportRepository`, `core/analytics/`, `LocalReportGenerator` |
| AN-4 | `feature/dashboard/`, `feature/settings/` |
| AN-5 | Test pass, release build |

## Commands
- `./gradlew testStagingDebugUnitTest lintStagingDebug` must pass before every PR.
- `./gradlew assembleStagingRelease` builds tester APKs (a human signs them with the shared keystore).

## Done means
Feature works against staging, unit tests cover repository and sync logic, it was checked on an API 26 emulator, and any deviation is recorded in `docs/DECISIONS.md`.
