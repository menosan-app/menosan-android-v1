# Decisions log

Record every deviation from `docs/DEVELOPMENT_PLAN.md` here. The team uses this file to update the SRS/SDP after release.

| Date | Workstream | Decision | Reason |
|---|---|---|---|
| 2026-09-24 | AN-0 | Both flavors (`staging`, `prod`) use the same `applicationId` `app.menosan.android`, so they can't be installed side by side. | `google-services.json` registers only that package. Testers only get staging, and updates install over the beta. |
| 2026-09-24 | AN-0 | The prod `API_BASE_URL` is the placeholder `https://menosan-api-prod.invalid/` until hosting is decided. Override with `API_BASE_URL_PROD` in `local.properties`. | The prod URL is TBD. |
| 2026-09-24 | AN-0 | Staging OkHttp timeouts are 75 s (connect, read, write) and 120 s per call. Prod uses 15/30/60 s. The sign-in and create-account screens say "The server is waking up" after 5 s. | Render Free takes ~60 s to wake up. |
| 2026-09-24 | AN-0 | AGP 9.4 with built-in Kotlin (no `kotlin-android` plugin), Kotlin 2.4.20, KSP for Hilt and Room, `compileSdk`/`targetSdk` 37. | Latest stable, and matches the backend's Kotlin. |
| 2026-09-24 | AN-0 | Release builds aren't minified yet (`isMinifyEnabled = false`). | Less R8 risk for the Sat 9/26 beta. Revisit in AN-5. |
| 2026-09-24 | AN-0 | Enum-like DTO fields (`SyncStatus`, `HotspotCriterion`, `Trend`, `CostLevel`, …) decode unknown values to `UNKNOWN` instead of failing. | Server changes after 9/26 are additive only. A new value must not break an installed app. |
| 2026-09-24 | AN-0 | The JSON serializer truncates `createdAt` to milliseconds. | The server stores milliseconds and rejects an update whose `createdAt` differs from the stored value (contract §6.3). |
| 2026-09-24 | AN-0 | Room v1: `entries` (plan AN-1 fields plus `updated_at`), `reports_cache` (one row per week: summary columns, a nullable full `payload_json`, `is_provisional`, `algorithm_version`), and `taxonomy` (a single JSON row). Schemas are exported to `app/schemas/`. | One table covers the history list, cached full reports, and provisional offline reports (§5.7). The taxonomy blob avoids migrations when labels change. |
| 2026-09-24 | AN-0 | A device that has confirmed the account (`SessionStore`) opens straight to the dashboard without calling `GET /v1/me`. | Manual logging must work offline after the first sign-in. |
| 2026-09-24 | AN-0 | Android backup and device transfer are disabled (`allowBackup=false`, exclude-all extraction rules). | Private household data stays on the device and in the user's account (NFR1–3). |
