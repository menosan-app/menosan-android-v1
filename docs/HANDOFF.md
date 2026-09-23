# Handoff log — menosan-android

Newest entry first. Use `docs/HANDOFF_TEMPLATE.md` for each entry. Every agent **reads this file before starting** and **updates it before stopping**.

---

# Handoff — menosan-android — 2026-09-24 (AN-1 manual logging and offline sync)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-1 Manual logging and offline sync (plan §10)
- **Branch:** `feat/an1-logging` (based on `main` 68e8fda). Not pushed and not merged.
- **Overall state:** 🟡 Code complete: 69 unit tests pass, lint has 0 errors and 0 warnings, and `assembleStagingDebug` works. **Not yet run on a device or emulator** (no emulator in this session), so the DoD check (airplane mode, log 5, kill, reconnect, synced exactly once) still needs a human.

## 2. Done this session
- [x] **Sync** (`sync/`): `EntrySyncEngine` pushes the outbox in batches of ≤ 500 through `POST /v1/entries/sync` and applies per-item results in one transaction per batch, only to rows that are unchanged since sending:
  - `OK`: the row takes the server copy (an OK delete removes the row).
  - `WEEK_CLOSED`: the row reverts to the server copy and a notice is counted.
  - `INVALID`, `INVALID_TIMESTAMP`, `CONFLICT`: the row gets `lastError` ("Couldn't sync") and leaves the outbox until the user edits it.
  - `ERROR`, unknown statuses, network errors, 5xx: retried.

  It also does the current-week merge (`pullCurrentWeek`) and retention (`pruneOldEntries`: current week + 2 previous; pending rows are never pruned).
- [x] `SyncWorker` (`@HiltWorker`) + `WorkManagerSyncRequester`: unique work `entry-sync`, REPLACE, CONNECTED, 30 s exponential backoff. `SyncTriggers` (started in `MenosanApp.onCreate`): prune on start, request a sync on start and sign-in, merge the current week whenever a signed-in user is online. Seams for tests: `EntryRemote`, `TransactionRunner`, `SyncNotices`, and `SyncRequester` (`SyncPorts.kt`), bound in `SyncModule`.
- [x] `DefaultEntryRepository`: requests a sync after each write, delete always leaves a tombstone, `observeCurrentWeek()` follows the Sunday 00:00 Manila rollover (`data/repo/CurrentWeekFlow.kt`, re-checked every minute), and `refreshCurrentWeek()` does the merge. The interface is unchanged.
- [x] `EntryDao`: added `getOutbox()` and `getWeekIncludingDeleted()`. No renames or removals, and **no schema change** (v1 JSON unchanged).
- [x] Screens:
  - `feature/logging/LogEntry.kt`: manual log and edit on `EntryFormFields`, with an offline banner, a toast confirmation, and read-only when the entry's week is closed.
  - `feature/entries/AuditScreen.kt` + `AuditViewModel.kt`: the Audit tab (week range, entries and pieces, Sun–Sat Canvas bars, 4 category tiles, quick actions, entry rows with sync chips and a ⋮ menu, banners for waiting to sync, couldn't sync, and reverted changes, and read-only earlier weeks).
  - `EntryDetails.kt`: `EntryDetailsRoute` with "Editable until Sat, Oct 3, 11:59 pm", entry method, sync status, and Edit/Delete.
  - `EntryUi.kt`: `SyncChip`, `EntryRow`, `CategoryBadge`, and `DeleteEntryDialog`.
  - `EntryFormats.kt`: Manila date text and `WeekSummary`.
  - Registered in `LoggingNavigation.kt` and `EntriesNavigation.kt`. Strings are in `strings_logging.xml`.
- [x] Tests: `EntrySyncEngineTest` (17), `DefaultEntryRepositoryTest` (8), `EntryFormatsTest` (3), with fakes in `test/.../sync/SyncFakes.kt`.

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Device check | phone or API 26 emulator | The DoD offline scenario and a visual check of the new screens in light and dark mode. |

## 4. Next steps (in order)
1. **Human:** `./gradlew installStagingDebug` on a phone. Go into airplane mode, log 5 entries, kill the app, reopen it, and turn airplane mode off. The 5 entries should show "Synced" and appear exactly once in `GET /v1/entries`. Also try edit and delete online and offline, and check the Audit tab in dark mode.
2. **Integrator:** merge `feat/an1-logging` first (the order is AN-1 → AN-3 → AN-2 → AN-4).
3. **AN-3:** to refresh reports after late entries sync, add an `AfterSyncAction` with `@IntoSet` in your own Hilt module (see `sync/SyncPorts.kt`). `SyncWorker` calls it after every pass that needs no retry, with the number of changes sent.
4. **AN-2:** save the confirmed photo draft with `EntryRepository.create(draft.copy(source = PHOTO))`. `EntryFormFields`' API is unchanged. `WasteCategory.icon()` is now public.
5. **AN-4:** logout should check `EntryRepository.hasPendingChanges()` before `LocalDataCleaner.clearAll()`. Home can reuse `WeekSummary`, `EntryRow`, and `SyncChip` from `feature/entries`, and `observePendingCount()` for the badge.

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 69 tests pass; lint 0 errors, 0 warnings
```

## 6. Known issues / failing tests
- An entry that "couldn't sync" in a **closed** week can't be edited or deleted locally, so it keeps the Couldn't sync chip (e.g. an offline create older than 14 days → `INVALID_TIMESTAMP`). This is rare during testing.
- `SyncNotices` (a revert count only) is not cleared by `LocalDataCleaner`, so a notice could outlive a logout. It holds no personal data.
- The week in the UI uses the device clock (in Manila). If the phone's clock is wrong, the server's `WEEK_CLOSED` or timestamp rules decide, and the local change is reverted or marked Couldn't sync.

## 7. Decisions made (also logged in docs/DECISIONS.md)
- Deletes always leave a tombstone. Sync uses REPLACE with 30 s backoff and a retry/stop matrix. Final item errors wait for the user. `WEEK_CLOSED` reverts and leaves a persisted notice. Results are applied only to unchanged rows. When the merge runs. What the Audit tab shows. Toast confirmations. No schema change.

## 8. API contract changes
- None.

## 9. Environment / setup notes
- No new dependencies.
- Files outside AN-1's folders: `MenosanApp.kt` (inject and start `SyncTriggers`, +5 lines). `feature/logging/EntryFormFields.kt` is AN-1's; there, only `WasteCategory.icon()` changed from private to public.

## 10. Questions / blockers for humans
- Run the device DoD check (§4.1). I didn't start an emulator (memory rule).

---

# Handoff — menosan-android — 2026-09-24 06:00 PHT (prep for parallel workstreams)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5), integrator
- **Workstream(s):** AN-0 prep so AN-1…AN-4 can run in parallel worktrees
- **Branch:** `feat/an0-foundation` → merged into `main`
- **Overall state:** 🟢 41 unit tests pass; lint 0 errors.

## 2. Done this session
- [x] `core/model/Entry.kt` (`Entry`, `EntryDraft`, `EntrySyncStatus`, `EntryRules`) and the **frozen `data/repo/EntryRepository` interface**. `DefaultEntryRepository` has the Room part (create, update, delete with the right outbox states and validation); sync and refresh are `TODO(AN-1)`. Bound in `di/RepositoryModule.kt`.
- [x] `feature/logging/EntryFormState.kt` + `EntryFormFields.kt`: the shared, stateless entry form (4 category tiles including Special, a subcategory dropdown, name with a /60 counter, quantity in pcs, AI-suggestion marking per field). Used by AN-1 (manual and edit) and AN-2 (photo review). Tests are in `EntryFormStateTest`.
- [x] DAOs split into `EntryDao.kt` (AN-1), `ReportCacheDao.kt` (AN-3), and `TaxonomyDao.kt`. `data/local/LocalDataCleaner` (wipes Room + session for logout and account deletion).
- [x] `core/settings/AppPreferences` (`ThemeMode` System/Light/Dark), wired into `MainActivity`. AN-4 only builds the UI.
- [x] One navigation file per feature (`feature/<x>/<X>Navigation.kt`, `NavGraphBuilder.xxxScreens(navController)`), called from `MenosanNavHost`. One strings file per workstream (`values/strings_logging.xml`, `strings_photo.xml`, `strings_reports.xml`, `strings_settings.xml`).
- [x] `gradle.properties` heap lowered (Gradle 2 GB, Kotlin daemon 1.5 GB) so two worktrees can build at once on this 16 GB machine.

## 3. Parallel development rules (all workstreams)
1. Work in your own git worktree and branch (`feat/an1-logging`, `feat/an2-photo`, `feat/an3-reports`, `feat/an4-settings-home`). Never merge into `main` or push. The integrator merges branches that pass `./gradlew testStagingDebugUnitTest lintStagingDebug`, in the order AN-1 → AN-3 → AN-2 → AN-4.
2. Copy the gitignored `local.properties` and `app/google-services.json` from the main checkout (`D:\CCS6\Menosan\menosan-android`) into your worktree. Never commit them.
3. Edit only files your workstream owns (AGENTS.md table). Per feature:
   - screens go in your `feature/<x>/<X>Navigation.kt`;
   - feature-only routes go in your own package; `navigation/Routes.kt` is shared, so don't edit it;
   - strings go in your `strings_<x>.xml`;
   - Hilt bindings go in a module in your own package.
   - Do **not** edit `di/*`, `navigation/*`, `core/*`, `MenosanNavHost`, or another workstream's files. If you really must, keep it minimal and list it in your handoff and final report.
4. `EntryRepository` signatures are frozen. Don't rename or remove existing `EntryDao` methods (AN-3 reads `getWeek`/`getPending`). Adding methods is fine.
5. **Database:** only AN-1 changes entities or the schema (v1 may still change until the 9/26 beta; after that, migrations only). AN-3 uses `reports_cache` as is and may add queries to `ReportCacheDao.kt`.
6. New dependencies: add them only when needed, at the end of the right section of `gradle/libs.versions.toml` and `app/build.gradle.kts`, and list them in your handoff.
7. **Never start an emulator** (memory). Build only when needed. Don't raise the Gradle heap.
8. Put your handoff entry at the top of `docs/HANDOFF.md` and append rows to `docs/DECISIONS.md`. The integrator resolves merge conflicts in those two files.

## 4. Next steps
1. AN-1 and AN-3 in parallel now. AN-2 and AN-4 after those two are merged.

---

# Handoff — menosan-android — 2026-09-24 05:00 PHT

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-0 follow-up: UI design context and app-shell restyle (docs/DESIGN.md)
- **Branch:** `feat/an0-foundation` (not pushed and not merged into `main`)
- **Overall state:** 🟡 The restyle builds, and tests and lint pass. **It hasn't been viewed on a device yet** (see §6). The E2E sign-in check from the entry below is still open.

## 2. Done this session
- [x] `docs/DESIGN.md`: the design reference (tokens, components, screen → workstream map, and conflicts with the plan). CLAUDE.md imports it, and AGENTS.md has a rule to follow it. 28 mockups in `docs/design/`, and the official palette, logo, and illustrations in `docs/design/essentials/`.
- [x] Theme (`core/ui/theme/`): the official light and dark palettes (`Color.kt`), `MenosanTheme.colors` for category, chip, and banner colors, Roboto (the bundled variable font `res/font/roboto.ttf`), and shapes.
- [x] Logo: `@drawable/ic_menosan_logo` from `Logo.svg`. The launcher icon is the two-leaf mark on Paper, plus a monochrome themed icon.
- [x] Shared components (`core/ui/components/Brand.kt`): `MenosanLogo`, `MenosanWordmark`, `BackButton`, `ScreenHeader`, `MessageBanner`, `BrandLoading`, `GoogleButton` (with the Google G), and `Pill`.
- [x] Signed-out flow per the mockups: `WelcomeScreen`, `SignInScreen` (Log in), `CreateAccountScreen` (privacy card + consent → Google → `POST /v1/account`), `AccountReadyScreen` ("You're in"), and brand loading screens. Start destination: Welcome, Log in (signed in with Google but not confirmed), or Home (confirmed).
- [x] App shell (`navigation/MainBottomBar.kt`): the bottom bar Home · Audit · + · Insights · Profile, and the `AddEntrySheet` (Scan with Photo → `LogPhotoRoute`, Log Waste Manually → `LogManualRoute`). Tabs are `DashboardRoute`, `EntriesRoute`, `HistoryRoute`, and `SettingsRoute`.
- [x] Home placeholder restyled (wordmark, Online/Offline pill, greeting, Moss hero card with the week range). AN-4 replaces it.

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Visual check of the restyle | emulator or device | Screens haven't been viewed yet. The emulator was stopped earlier because memory was low. |

## 4. Next steps (in order)
1. **Human:** view the new screens on a device or emulator in light and dark mode, and run the E2E sign-in check (entry below, §4.1). Then merge `feat/an0-foundation`.
2. **AN-1 (Sat 9/26, critical path):** build the Audit tab (`EntriesRoute`) and the manual entry form (`LogManualRoute`) with the mockups `Waste Audit Dashboard`, `Manual Waste Entry`, and friends (DESIGN.md §4), using the shared components and `MenosanTheme.colors`. Also see the AN-1 notes in the entry below.
3. AN-2 / AN-3 / AN-4 as planned. Low priority (team): extra preferences. Account deletion uses the type-DELETE confirmation (team). The mockups are a style reference only; the plan defines behavior.

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 33 tests pass; lint 0 errors, 1 warning (Gradle 9.7.1)
```

## 6. Known issues / failing tests
- The restyle is only compile-checked, not viewed yet.
- The illustrations are only ~340 px, so they look soft on high-density screens. Ask the designer for 3× or SVG exports with transparent backgrounds.
- Dark mode draws the illustrations on a Paper Light card (their background is opaque).

## 7. Decisions made (also logged in docs/DECISIONS.md)
- Roboto (bundled variable font). In dark mode `primary` is Moss Light and brand fills use `primaryContainer` = Moss. Signed-out flow and app shell as above. Extra preferences are low priority. Account deletion uses type-DELETE. The mockups are a style reference only. The prod flavor uses the staging backend URL (team).

## 8. API contract changes
- None.

## 9. Environment / setup notes
- New assets: `res/font/roboto.ttf` (OFL), `res/drawable-nodpi/illustration_*.png`, and `res/drawable/ic_menosan_logo.xml`, `ic_google_g.xml`, `ic_launcher_monochrome.xml`.

## 10. Questions / blockers for humans
- Higher-resolution illustrations (see §6).

---

# Handoff — menosan-android — 2026-09-24 03:15 PHT

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-0 Foundation (see docs/DEVELOPMENT_PLAN.md §10)
- **Branch:** `feat/an0-foundation` · **Last commit:** `1b48388 feat(an0): foundation — …` (plus this handoff commit). Not pushed and not merged into `main`.
- **Overall state:** 🟡 Code complete and verified on the JVM and an API 26 emulator. The DoD's end-to-end Google sign-in → create account → dashboard against staging still needs a human with a Google account (see §6).

## 2. Done this session
- [x] Gradle: AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, KSP, Hilt 2.60.1, Room 2.8.5 plus the Room Gradle plugin, Compose BOM 2026.09.00, Retrofit 3 + kotlinx.serialization, OkHttp 5, Firebase BoM 34.19.0, Credential Manager 1.6. Version catalog in `gradle/libs.versions.toml`. Gradle 9.6.0 wrapper.
- [x] Flavors `staging` and `prod` (`app/build.gradle.kts`). Staging defaults to `https://menosan-api-staging.onrender.com/` with 75 s connect/read and 120 s call timeouts (Render cold start). Prod uses the placeholder `https://menosan-api-prod.invalid/`. Both can be overridden via `local.properties`. Optional release signing from the `MENOSAN_KEYSTORE_*` keys.
- [x] `core/time/WeekCalc.kt`: a port of the backend, Asia/Manila, plus `latestReportWeekStart()`.
- [x] `core/network`: `MenosanJson`, Instant/LocalDate serializers (Instant truncated to ms), `FallbackEnumSerializer`, `ApiResult`/`ApiError`/`ApiErrorCode` + `safeApiCall`, `AuthInterceptor` (one forced-refresh retry on 401), `ConnectivityObserver`, `DebugLogInterceptor` (debug only, no bodies or headers).
- [x] `core/auth`: `AuthService`/`FirebaseAuthService` (also the `IdTokenProvider`), `SessionStore` (remembers the confirmed account uid → offline start).
- [x] `data/remote/MenosanApi.kt`: **every** contract v1 app endpoint, with DTOs in `data/remote/dto/` that mirror contract §3, §5–§7.
- [x] Room v1 `data/local/`: `entries`, `reports_cache`, `taxonomy` + basic DAOs. Schema exported to `app/schemas/.../1.json` (committed). `MenosanDatabase.MIGRATIONS` is empty, and there is no destructive fallback.
- [x] `data/repo/TaxonomyRepository` (bundled asset, or a newer server copy stored in Room) and `AccountRepository` (`checkAccount()` → Ready / NeedsAccount / Failed, `createAccount()`).
- [x] `feature/auth`: `GoogleSignInClient` (Credential Manager, `GetSignInWithGoogleOption`), `SignInScreen`/VM (auto-checks `/v1/me`, "server is waking up" after 5 s), `CreateAccountScreen`/VM (privacy notice, consent checkbox, `POST /v1/account`, "use a different account"), `SignOutAction`.
- [x] `navigation/`: type-safe routes (`Routes.kt`) and `MenosanNavHost` with placeholders for AN-1…AN-4. Signing out anywhere returns to sign-in.
- [x] A minimal `feature/dashboard/DashboardScreen.kt` (week range, action buttons, temporary sign-out). **AN-4 replaces it.**
- [x] Theme (calm green, light and dark), adaptive launcher icon, backup disabled, WorkManager set up with `HiltWorkerFactory` (the default initializer is removed in the manifest).
- [x] 33 unit tests: `WeekCalcTest`, `AuthInterceptorTest`, `MenosanApiTest` (MockWebServer), `ReportDtoTest`, `TaxonomyAssetTest`.
- [x] Recorded 10 decisions in `docs/DECISIONS.md`.

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| E2E sign-in against staging | device or emulator | A human signs in with a Google account (see §4.1). |

## 4. Next steps (in order)
1. **Human:** run the DoD check. Use a real phone, or an emulator where you've signed in to the Play Store so Play services updates. Run `./gradlew installStagingDebug`, then Continue with Google → create account → dashboard. Wait up to ~60 s on the first call if staging is asleep. Then merge `feat/an0-foundation` into `main`.
2. **AN-0 follow-up (UI shell, from `docs/DESIGN.md`):** retheme `core/ui/theme` with the design tokens (cream / forest / sage / amber), bundle the Lexend-style font, replace the launcher icon with the two-leaf logo, restyle sign-in to match the Welcome, Login, and Create Account mockups, and add the bottom-nav shell (Home · Audit · + · Insights · Profile) with the "Add New Entry" sheet. Logo and illustration SVGs are needed from the designer.
3. **AN-1 (Sat 9/26, critical path):** `EntryRepository` (write Room first, then enqueue sync), `sync/SyncWorker` (`@HiltWorker`, it already gets injected) using `MenosanApi.syncEntries` and `SyncStatus`, and the manual logging form and entries list. Swap the `LogManualRoute`/`EntriesRoute` placeholders in `navigation/MenosanNavHost.kt`. `EntryEntity`/`EntryDao` are ready to extend. Use `UUID.randomUUID()` for ids and `clock.instant()` for `createdAt`.
4. **Before the first tester build:** if AN-1 changes the `entries` columns, it can still edit schema v1 **only until the 9/26 beta ships**. After that, bump the version and add a `Migration`.
5. AN-2 / AN-3 / AN-4 per plan §10. AN-3 stores `ReportDto` JSON in `reports_cache.payload_json` (`MenosanJson.encodeToString(ReportDto.serializer(), …)`).

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash; Android Studio's JBR (JDK 25)
./gradlew testStagingDebugUnitTest lintStagingDebug   # 33 tests pass; lint 0 errors, 1 warning (Gradle 9.7.1 available)
./gradlew assembleStagingDebug assembleStagingRelease  # release APK is unsigned without MENOSAN_KEYSTORE_* keys
```
- Emulator: AVD `Medium_Phone` is **API 26** (`-gpu auto`; `swiftshader_indirect` crashed with exit 139). The app installs and launches, and the sign-in screen renders with no crash.

## 6. Known issues / failing tests
- The `Medium_Phone` API 26 image ships Google Play services 11.7 (2017), so Credential Manager throws `GetCredentialProviderConfigurationException`. The app now shows "Google sign-in needs an up-to-date Google Play services…". Fix it by signing in to the Play Store on the emulator and updating Play services, or test on a real device.
- The dashboard's temporary "Sign out" (`SignOutAction`) doesn't touch Room. AN-4's logout must check the outbox and clear Room (plan §10), and Room data isn't scoped by user.
- `safeApiCall` returns `Unit` as the body for any empty 2xx. It's only correct for `Response<Unit>` endpoints, which are the only ones the contract lets return 204.
- Lint's `ObsoleteSdkInt` suggestion to move `mipmap-anydpi-v26` breaks AAPT linking, so `app/lint.xml` ignores it for that folder.

## 7. Decisions made (also logged in docs/DECISIONS.md)
- Both flavors share `applicationId` `app.menosan.android`, because only that package is in `google-services.json`.
- Prod URL placeholder, generous staging timeouts, no minify yet, lenient enum decoding, ms-truncated `createdAt`, and the Room v1 layout. See DECISIONS.md.

## 8. API contract changes
- None. `docs/api-contract.md` here is a read-only copy of the backend's.

## 9. Environment / setup notes
- There is no system `java`. Run Gradle with Android Studio's JBR (see §5). SDK: platform `android-37.0`, build-tools 36.
- `local.properties` (gitignored) needs `sdk.dir`. Optional keys: `API_BASE_URL_STAGING`, `API_BASE_URL_PROD`, `MENOSAN_KEYSTORE_FILE|PASSWORD`, `MENOSAN_KEY_ALIAS|PASSWORD`.
- `app/google-services.json` is gitignored. This machine's debug SHA-1 `C0:1A:51:…:16:E0` is already registered in Firebase. Other developers must register theirs, and the shared release keystore's SHA-1 and SHA-256 must be registered before the 9/26 beta.

## 10. Questions / blockers for humans
- Please run the E2E sign-in check (§4.1). I can't complete Google sign-in.
- The prod base URL is needed once hosting is final (replace the placeholder or set `API_BASE_URL_PROD`).
- Register the shared release keystore's SHA fingerprints in Firebase before building `v0.5-beta`.
