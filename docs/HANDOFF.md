# Handoff log — menosan-android

Newest entry first. Use `docs/HANDOFF_TEMPLATE.md` for each entry. Every agent **reads this file before starting** and **updates it before stopping**.

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
