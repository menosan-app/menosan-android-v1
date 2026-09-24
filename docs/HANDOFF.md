# Handoff log — menosan-android

Newest entry first. Use `docs/HANDOFF_TEMPLATE.md` for each entry. Every agent **reads this file before starting** and **updates it before stopping**.

---

# Handoff — menosan-android — 2026-09-24 09:40 PHT (device test pass)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5), supporting a human device test. No code changes.
- **Branch:** `main`. `03ec26f` is pushed; the handoff commits after it are local.
- **Overall state:** 🟢 The first real-device pass found no bugs.

## 2. Done this session
- [x] **Device:** Samsung SM-S711B (Android 16, API 36), staging debug APK from `45a8c5f`. No crashes or app errors in logcat.
- [x] **Human-confirmed on the device:**
  - sign-up, manual and photo logging, sync;
  - photo temp files deleted (`cache/photos` empty);
  - Insights and weekly reports on a seeded demo account (`yeum.burger@gmail.com`, weeks Aug 30, Sep 6, Sep 13): totals, the Special line, hotspots and chips, ideas;
  - adopt and un-adopt on the latest report only, plus the Home report card, "trying", and impacts;
  - **offline summary (UAT 5b)**, and then **replacement by the server report** after the staging clock moved to Sun 9/27 00:05 PHT;
  - logout with an unsynced entry shows the warning; logout and login again with nothing pending;
  - export (file saved and opened) and account deletion;
  - Light, Dark, and System themes;
  - large system font;
  - **own-adoption impact (UAT 6):** adopted on Sep 20–26, logged in the week of Sep 27, rolled the server and phone to Sun Oct 4. The Sep 27–Oct 3 report showed the impacts, and the adopt window moved to the new report.
- [x] "Entries" on reports are one lower than the seed tool's count. This is correct: the seed counts Special entries, and the analyzed totals exclude them (I3).
- [x] The staging clock was moved for the offline and impact tests, then reset (`overridden:false`).

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| API 26 | emulator or old phone | One pass |
| Release signing | `local.properties` | The shared keystore isn't configured on this PC (`MENOSAN_KEYSTORE_*`). The fingerprints are in Firebase. Also set `versionName` for the beta (still 0.1.0). |

## 4. Next steps (in order)
1. **Demo account `yeum.burger@gmail.com`:** it now has reports for Sep 20–26 and Sep 27–Oct 3 made under a moved clock, plus entries dated Sep 27, which is in the future. Run `POST /internal/dev/reset {"email":"yeum.burger@gmail.com"}` before using it as a real account, then reinstall or log out on the phone so the local cache is cleared. The reset deletes all its entries and reports. Re-seed it if you need demo data again.
2. API 26 pass.
3. Release keystore SHA-1 and SHA-256 in Firebase → signed `assembleStagingRelease` → `v0.5-beta` on Firebase App Distribution (Sat 9/26).

## 5. Verify the current state
- Staging: `GET /internal/dev/clock` → `overridden:false`.

## 6. Known issues
- None new from the device.
- Turn the test phone's automatic date and time back on. It was set to Sep 27 for the offline test.

## 7. Decisions
- None.

## 8. API contract changes
- None.

## 9. Environment / setup notes
- The phone has USB debugging on. adb is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

## 10. Questions / blockers for humans
- None.

---

# Handoff — menosan-android — 2026-09-24 07:50 PHT (integration summary: AN-1…AN-4 merged)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5), integrator. AN-1…AN-4 were built by parallel agents in separate worktrees; each has its own entry below.
- **Branch:** `main` at `45a8c5f` (local). **Not pushed**: the push was blocked by the tool's permission classifier. `origin/main` is still `af47c66`; 30 commits are waiting.
- **Overall state:** 🟡 All four feature workstreams are merged and compile. 166 unit tests pass, `lintStagingDebug` finds no issues, and staging and prod debug builds succeed. **Nothing has been used on a device yet** (no emulator was run, because memory is limited).

## 2. Done
- [x] AN-0 foundation and restyle, then the parallel prep (frozen `EntryRepository`, shared entry form, per-feature navigation, strings, and DAO files).
- [x] **AN-1** manual logging, offline outbox + `SyncWorker`, current-week merge, retention, Audit tab, entry details, edit and delete (merged `0e23152`).
- [x] **AN-3** analytics port (passes the shared test vectors), `ReportRepository` with cache and optimistic adopt, `LocalReportGenerator` offline summaries, Insights and weekly report screens (merged `679a8b2`).
- [x] **AN-2** photo logging: camera or gallery, EXIF, resize and compress, upload, every error case, review with AI marking and a required confirmation, temp files deleted (merged `8f078ca`).
- [x] **AN-4** Home dashboard, Profile (appearance, privacy, export via the system file picker, logout with a pending warning, type-DELETE account deletion) (merged `1ef162c`).
- [x] Integration:
  - reports refresh after each successful sync (`data/repo/ReportSyncHooks.kt`), and on app open and whenever the phone comes back online (`sync/SyncTriggers`);
  - logout and account deletion cancel pending sync work and clear sync notices (`LocalDataCleaner`, `45a8c5f`).
- [x] Staging debug APK: `D:\CCS6\Menosan\builds\menosan-staging-debug.apk` (from `45a8c5f`, signed with this PC's debug key, which is registered in Firebase).

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Device verification | phone with a Google account + updated Play services | Everything below in §4. |
| AN-5 QA and release | plan §10, §11.2 | Full UAT checklist on an API 26 emulator (with updated Play services) and a low-end phone; signed `assembleStagingRelease`; Firebase App Distribution. |

## 4. Next steps (in order)
1. **Push:** `git push origin main` (run it yourself; it was blocked for the agent).
2. **Install and sign in (developers):** `adb install -r D:\CCS6\Menosan\builds\menosan-staging-debug.apk`, then Welcome → Create Account → consent → Google → "You're in" → Home. Also Log in with an existing account. Staging may take ~60 s to wake up.
3. **AN-1 check (beta blocker):** in airplane mode, log 5 entries, kill the app, reopen it, turn airplane mode off. Each entry should show Synced exactly once (`GET /v1/entries`). Also edit, delete, and "Couldn't sync".
4. **Reports (AN-3):** seed a *demo* account with `/internal/dev/seed-history` (menosan-api `docs/ENVIRONMENTS.md` §6). Check Insights, the weekly report, hotspots, adopt and un-adopt, and impact. For the offline summary: roll the staging clock forward, go to airplane mode, then reconnect and see the server report replace it. **Reset the clock afterwards**, and never use it once real testers start.
5. **Photo (AN-2):** camera and gallery, sideways photos, a sachet / PET bottle / leftover rice against staging, and the error cases (offline, a non-waste photo). `adb shell run-as app.menosan.android ls cache/photos` should be empty after a scan.
6. **Home and Profile (AN-4):** empty states for a new user, export (open the file), logout with and without pending entries, delete account with a throwaway Google account, light, dark, and system themes, 1.3× font on a small screen.
7. Fix what the device pass finds, then do AN-5: register the shared release keystore's SHA-1 and SHA-256 in Firebase, then build, sign, and distribute `v0.5-beta`.

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 166 tests, lint "No issues found"
```

## 6. Known issues (from the workstream entries below)
- Only unit tested: no screen has been viewed on a device since the restyle.
- AN-1: an entry that couldn't sync and belongs to a closed week can't be edited or deleted on the phone, so it keeps the "Couldn't sync" chip.
- AN-2: HEIC gallery photos can't be decoded on Android 8.0–8.1 (friendly error). If the app is killed on the review screen, the suggestion is lost.
- AN-3: adopting two ideas quickly can make the first one flicker briefly.
- AN-4: the DELETE confirmation accepts any letter case (agent's call; see DECISIONS). If the `204` answer to account deletion is lost, the user is asked to log in again.
- The illustrations are low resolution (ask the designer for 3× or SVG).

## 7. Decisions
- 54 rows in `docs/DECISIONS.md` (AN-0 through AN-4). Most notable: the prod flavor uses the staging backend, Roboto, the mockups are style-only, type-DELETE, no extra preferences in v1.

## 8. API contract changes
- None.

## 9. Environment / setup notes
- New dependency: `androidx.exifinterface` (AN-2). New manifest `FileProvider` (`${applicationId}.photos`). Agent worktrees are in `.claude/worktrees/` (gitignored; their branches are merged and can be removed with `git worktree remove`).
- New agent worktrees start from `origin/main`. Until you push, branch from local `main`.

## 10. Questions / blockers for humans
- Push `main` (§4.1). The device checks (§4.2–6) and the release keystore fingerprints in Firebase.

---

# Handoff — menosan-android — 2026-09-24 (AN-4 Home and Profile)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-4: Home dashboard, Profile (settings and privacy), export, logout, account deletion, and polish (plan §10)
- **Branch:** `feat/an4-settings-home` (from `main` `679a8b2`). Not pushed and not merged. Commits: `6da765e` screens, `f3375b6` tests and polish, plus `docs: handoff AN-4`.
- **Overall state:** 🟡 Code complete. `testStagingDebugUnitTest lintStagingDebug assembleStagingDebug` passes (122 tests, lint "No issues found"). **Not yet checked on a device or against staging** (no emulator allowed this session).

## 2. Done this session
- [x] **Home** (`feature/dashboard/HomeScreen.kt`, `HomeViewModel.kt`) replaces the AN-0 placeholder:
  - the wordmark, the Online/Offline pill, and the greeting;
  - a Moss hero card with this week's range, live entries and pieces, and Canvas Sun–Sat bars (today highlighted), with no percentages;
  - **Log manually** and **Log with photo** quick actions;
  - banners for entries waiting to sync and for entries that couldn't sync (the second opens Audit);
  - the latest report card ("Your report for … is ready", "Your latest report: …", or "Offline summary for …"), which opens `ReportRoute`;
  - the top hotspot, labeled "From your … report";
  - "This week you're trying" (only while `canAdopt`), or a "Pick an idea" nudge;
  - "How your changes went" (report impacts);
  - the first-report empty state;
  - the 3 newest entries using AN-1's `EntryRow` (details, edit, and delete through `DeleteEntryDialog`), with **View all** → the Audit tab.

  On open it calls `reportRepository.refreshReports()` and `entryRepository.refreshCurrentWeek()`. The temporary Sign out button is gone.
- [x] **Profile** (`feature/settings/ProfileScreen.kt`, `ProfileViewModel.kt`):
  - initials avatar, name, email, and a "Google account" pill;
  - an Appearance bottom sheet (System / Light / Dark → `AppPreferences`);
  - the privacy statement, plus the full `PrivacyNotice` in a sheet;
  - **Export my data**: a confirm dialog → SAF `CreateDocument("application/json")` named `menosan-export-YYYY-MM-DD.json` (Manila date) → `GET /v1/export` streamed to the file on IO. A partial file is deleted on failure, and export is refused offline.
  - **Delete my account & data**: type DELETE (any case) to enable Delete. It's online only. A 5xx is retried up to 3 attempts, then "try again"; a 401 asks the user to log in again. After `204`: `LocalDataCleaner.clearAll()` + `SignOutAction` + a goodbye Toast.
  - **Log out**: if the outbox isn't empty, a warning offers "Try to sync first", "Log out anyway", or Cancel. Then it clears local data and signs out. The nav host returns to Welcome.
- [x] Seams in AN-4 packages (`SettingsPorts.kt`: `NetworkStatus`, `AppearanceStore`, `AccountActions` + `DefaultAccountActions` + `SettingsModule`; `HomeViewModel.kt`: `SubcategoryLabels` + `DashboardModule`).
- [x] Strings: `strings_settings.xml`, new `strings_dashboard.xml`. The dashboard strings moved out of `strings.xml`, and the unused `action_sign_out` was removed.
- [x] Tests: `HomeViewModelTest` (8) and `ProfileViewModelTest` (19), with fakes in `test/.../feature/settings/An4Fakes.kt`. They cover the Home state building, the logout decision, the delete flow (the DELETE gate, a retry on 500, stopping after 3 tries, offline, 401, no dismiss while deleting, clear then sign out on 204), and export (the Manila-dated name, offline, cancel, errors).
- [x] Polish: content descriptions on icon-only elements (the bars have a spoken summary, the avatar has a label, and decorative icons are null), headings semantics, scrolling screens, no fixed heights on text, theme colors only (`MenosanTheme.colors`, color scheme), and light and dark previews.

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Device check | phone (no emulator this session) | See §4.1. |

## 4. Next steps (in order)
1. **Human, on a device against staging:**
   - Home with a new account (empty states) and with a seeded demo account (report card, hotspot, trying, impacts; tap through to the report).
   - Export: pick a folder, open the file, and check that it holds the entries and reports. Also try in airplane mode.
   - Delete account with a **throwaway** Google account: type DELETE → the goodbye Toast → Welcome → sign in again → treated as new (UAT 8).
   - Logout with pending entries (airplane mode, log 2, Log out → the warning; try both choices), and logout with none.
   - Appearance: switch System, Light, and Dark, and check both themes on Home and Profile.
   - Font scale 1.3× and a 720p screen: nothing clipped, and everything scrolls.
2. **Integrator:** merge order AN-1 → AN-3 → AN-2 → AN-4. AN-4 touches only its own packages plus `strings.xml` (removed the dashboard strings and `action_sign_out`). If AN-2 used `action_sign_out` or `dashboard_*`, restore them there.
3. AN-5: full UAT pass on an API 26 emulator and a low-end phone.

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 122 tests pass; lint: No issues found
```

## 6. Known issues / failing tests
- UI not viewed on a device yet (compile, lint, and unit tests only).
- Logout doesn't cancel an in-flight `SyncWorker` (owned by AN-1's `sync/`). After `clearAll()` the outbox is empty, so a running sync only finds nothing to send, but a current-week merge that was already in flight could write rows back before sign-out finishes (a very small window).
- If a delete's `204` is lost (for example, the connection drops right after), the retry gets `401` because the Firebase user is gone. The user sees "log out and log in again". Logging out then clears the phone, and signing in again creates a new account.
- `SyncNotices` (AN-1) is still not cleared on logout (a count only, no personal data).

## 7. Decisions made (also logged in docs/DECISIONS.md)
- Home: only live counts for the week in progress, with every report card labeled by its closed week. The report card wording, "trying" only while `canAdopt`, and the pick-an-idea nudge. Home refreshes reports and the current week on open. DELETE in any case. Delete retry and 401 handling. The logout warning's three choices. The export flow. The Profile contents. The AN-4 seam interfaces.

## 8. API contract changes
- None.

## 9. Environment / setup notes
- No new dependencies.
- Files outside `feature/dashboard/` and `feature/settings/`: `res/values/strings.xml` (the dashboard strings moved to `strings_dashboard.xml`, and the unused `action_sign_out` was removed), plus `docs/DECISIONS.md` and `docs/HANDOFF.md`. AN-4 reuses AN-1's public UI (`EntryRow`, `DeleteEntryDialog`, `EntryFormats`, `WeekSummary`, the internal `deleteMessage`, `EntryDetailsRoute`), AN-3's `formatWeekRange`, and `PrivacyNotice` from `feature/auth` without changing them.

## 10. Questions / blockers for humans
- Please run the device checks in §4.1, especially the account deletion with a throwaway account.

---

# Handoff — menosan-android — 2026-09-24 (AN-2 photo logging)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-2 Photo logging (plan §10 "Photo logging" and AN-2)
- **Branch:** `feat/an2-photo` (based on `main` 34021e6, which has AN-0 and AN-1). Not pushed and not merged.
- **Overall state:** 🟡 Code complete: 113 unit tests pass (44 new), lint has 0 errors and 0 warnings, and `assembleStagingDebug` works. **Not yet run on a device** (no emulator, memory rule), so the camera, gallery, EXIF, and a real staging analysis still need a human (§4.1).

## 2. Done this session
- [x] **Flow** (`feature/photo/PhotoLogViewModel.kt`, `PhotoScreens.kt`), registered in `PhotoNavigation.kt` on `LogPhotoRoute`:
  - **Pick** (mockup `Upload Photo`): dashed upload card (gallery via `PickVisualMedia`, images only) and "Take a Photo" (system camera via `TakePicture`). The AI notice (SFR9.5: suggestions can be wrong, photo isn't stored) and a tip. **Offline:** "Photo logging needs internet — you can log manually." with a Log manually button; the photo buttons are disabled.
  - **Analyzing** (mockup `Loading Screen (After Scanning)`): `BrandLoading` "Analyzing your Photo". After 10 s it adds "The server may be waking up…" (Render cold start). Cancel returns to Pick.
  - **Errors**, each friendly and blame-free with a way forward (`PhotoErrors.kt`): network, `NOT_WASTE`, `ANALYSIS_FAILED`, `IMAGE_TOO_LARGE`, `RATE_LIMITED` (shows `details.resetsAt` in Manila time), `VALIDATION_FAILED`, `UNAUTHENTICATED`/`ACCOUNT_NOT_FOUND`, unknown/5xx, plus local "couldn't open this photo" and "no camera app". Retryable errors offer **Try again** (resends the in-memory photo), plus Try another photo and Log manually.
  - **Review** ("Check the Details"): the shared `EntryFormFields` prefilled from the suggestion, all four fields in `aiSuggested` at first (`PhotoReview.kt`). A field is unmarked when its value changes or its chip is tapped. The server `warning` is in a banner. Save needs the **"I checked these details"** checkbox and a valid form, then `EntryRepository.create(draft)` with `source = PHOTO` (same offline outbox as manual entries). A Toast confirms and the flow pops back.
- [x] **Image preparation** (`PhotoImageMath.kt` pure + `PhotoProcessing.kt` Android): bounds decode, power-of-two downsampling, EXIF orientation (all 8 values), long side ≤ 1280 px, JPEG q80, and a size budget of 2 MiB − 64 KB (quality 80 → 50, then shrink × 0.75). On `Dispatchers.IO`. Unreadable/HEIC-on-old-Android/OOM → friendly error.
- [x] **Privacy (SFR8.5):** camera files live in `cache/photos/` and are deleted in `finally` right after reading (also on error or cancel); an empty file is deleted when the user backs out of the camera; leftovers are swept when the screen opens and closes. Gallery photos are read in place. The JPEG stays in memory only until success or the user moves on. Nothing is logged.
- [x] **Seams for tests** (`PhotoPorts.kt`, Hilt `PhotoModule` in the same file): `PhotoProcessor`, `PhotoFiles`, `PhotoAnalysisClient` (`safeApiCall { api.analyzePhoto(MenosanApi.imagePart(jpeg)) }`), `NetworkStatus` (wraps `ConnectivityObserver`), `TaxonomySource` (wraps `TaxonomyRepository`).
- [x] **Tests** (`app/src/test/.../feature/photo/`): `PhotoImageMathTest` (10), `PhotoReviewTest` (8), `PhotoErrorTest` (7), `PhotoLogViewModelTest` (19: gallery and camera paths, file deletion including errors and process death, retry, waking-up hint, cancel, confirmation required, save as PHOTO with edits, offline save message, invalid form).

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Device check | phone (or API 26 emulator with an updated Play services) | See §4.1. |

## 4. Next steps (in order)
1. **Human, on a device** (`./gradlew installStagingDebug`):
   - **Camera:** + → Scan with Photo → Take a Photo → shoot a sachet → the review opens with all four fields tinted and chipped. Back out of the camera once too: you should land on the Pick screen with no error.
   - **Gallery:** choose a photo; also try a portrait photo taken with the phone held sideways and one rotated in the gallery app (**EXIF rotation**: the analysis should still name the item correctly).
   - **Real analysis against staging:** sachet, PET bottle, leftover rice (plan UAT 3). Edit one field → its chip disappears; tap another chip → it disappears; Save stays disabled until "I checked these details" is ticked; the entry shows in Audit as a photo entry and syncs. Add the results to `menosan-api/docs/photo-smoke.md` if you like.
   - **Errors:** airplane mode on the Pick screen (offline banner + Log manually); a non-waste photo (a room) → "We couldn't spot waste here"; first call while staging sleeps → the waking-up hint after 10 s.
   - **Privacy:** after a scan, `adb shell run-as app.menosan.android ls cache/photos` should be empty.
   - Light and dark mode on the Pick, loading, error, and review screens.
2. **Integrator:** merge order is AN-1 → AN-3 → AN-2 → AN-4. Expected conflicts only in `docs/HANDOFF.md`, `docs/DECISIONS.md`, and possibly `gradle/libs.versions.toml` / `app/build.gradle.kts` / `AndroidManifest.xml` if another branch also appends there.
3. **AN-4:** the Home "Log with photo" quick action should navigate to `LogPhotoRoute` (the flow handles offline itself).

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 113 tests pass; lint 0 errors, 0 warnings
./gradlew testStagingDebugUnitTest --tests 'app.menosan.android.feature.photo.*'   # the 44 AN-2 tests
```

## 6. Known issues / failing tests
- Not run on a device yet (§4.1). The Android part of image preparation (`AndroidPhotoProcessor`) has no JVM test; its math is covered by `PhotoImageMathTest`.
- HEIC/HEIF gallery photos can't be decoded on Android 8.0–8.1 (`BitmapFactory` supports HEIF from API 28). The user sees "We couldn't open this photo" and can pick another or log manually.
- Transparent PNGs (e.g. screenshots with alpha) are encoded to JPEG without a white background, so transparent areas turn black. Rare for waste photos.
- If the app is killed while the **review** is open, the suggestion is lost (it's only in the ViewModel), and the user starts over. A capture in progress is not lost (the camera file path is saved).

## 7. Decisions made (also logged in docs/DECISIONS.md)
- System camera + photo picker, no CAMERA permission. Sizing and quality budget. When temp files are deleted. The in-memory photo for "Try again". AI-mark rules and the separate confirmation box. The 10 s waking-up hint. "Log manually" replaces the photo screen. Rate limit and sign-in error handling.

## 8. API contract changes
- None. Uses `POST /v1/photo-analysis` exactly as in contract §7.1.

## 9. Environment / setup notes
- **New dependency:** `androidx.exifinterface:exifinterface:1.4.2` (`libs.androidx.exifinterface`, added at the end of `[versions]`/`[libraries]` and after `googleid` in `app/build.gradle.kts`).
- **Files outside `feature/photo/`:** `app/src/main/AndroidManifest.xml` (+1 `<provider>` for `androidx.core.content.FileProvider`, authority `${applicationId}.photos`), new `app/src/main/res/xml/photo_paths.xml`, `res/values/strings_photo.xml` (AN-2's), `gradle/libs.versions.toml`, `app/build.gradle.kts`. No shared Kotlin files were changed, and `EntryFormFields`' API is untouched.
- The merged manifest has no CAMERA permission (checked).

## 10. Questions / blockers for humans
- Run the device checks (§4.1). I didn't start an emulator (memory rule).

---

# Handoff — menosan-android — 2026-09-24 (AN-3 reports)

## 1. Session
- **Agent / model:** Claude Code (Claude Opus 5.5)
- **Workstream(s):** AN-3: reports, hotspots, interventions, impact, and offline provisional reports (plan §5.7, §10)
- **Branch:** `feat/an3-reports` (from `main` `68e8fda`). Not pushed and not merged. Commits: `7dc26c4` analytics port, `5e88a83` repository and generator, `a93e86f` screens, `2bd2b37` lint fixes, plus `docs: handoff AN-3`.
- **Overall state:** 🟡 Code complete. `testStagingDebugUnitTest lintStagingDebug assembleStagingDebug` passes (67 tests, lint "No issues found"). **Not yet checked against staging or on a device** (no emulator allowed this session), and two integration hooks need wiring (see §4).

## 2. Done this session
- [x] `core/analytics/Analytics.kt`: the backend's `aggregate()`, `findHotspots()`, `compare()`, and `measureImpact()`, copied with only the package changed (`ALGORITHM_VERSION = 1`). `AnalyticsTaxonomy.kt` has `Taxonomy.analyticsTaxonomy()`. `app/src/test/resources/analytics-test-vectors.json` is a copy of the backend's, and `AnalyticsVectorsTest` runs every case (all pass, same JSON).
- [x] `data/repo/ReportRepository.kt` (interface + `ReportListItem`, `ReportView`, `RefreshResult`, `AdoptionResult`, `ApiError.isUnreachable`), `ReportRepositoryImpl.kt` (`DefaultReportRepository`), `ReportRepositoryModels.kt` (cache formats and analytics↔DTO conversions), and `ReportRepositoryModule.kt` (Hilt).
  - `refreshReports()`: `GET /v1/reports` → summary rows; then full reports for the newest 2, the latest, provisional rows, and changed summaries. Rows the server no longer lists are removed. If the API is unreachable, it builds offline reports instead.
  - `setAdopted()`: only while the report is the latest (I7, from the device clock plus `isLatest`). The cache is updated optimistically and reverted on error. `ADOPTION_WINDOW_CLOSED` also locks the cached report.
  - `refreshAfterSync()` for `SyncWorker`; `generateOfflineReports()` for app open; `observeReports()`, `observeReport(week)`, and `observeLatestReport()` flows.
- [x] `data/repo/LocalReportGenerator.kt` (plan §5.7): builds from local W entries (unsynced ones included). The comparison uses the cached server W−1 `stats`, or else local W−1 entries. Impact comes from the adoptions on the cached W−1 report. Also a last-week recap and the `missingComparisons` flag. Never recommendations. `ReportEntrySource` / `RoomReportEntrySource` wrap `EntryDao.getWeek`/`getPending`.
- [x] `ReportCacheDao.getAll()` (the only DAO change; no entity or schema changes).
- [x] Screens: `feature/reports/InsightsScreen.kt` (Insights tab: list newest first, latest highlighted, "Offline summary" label, pull to refresh, patient loading, empty and error states) and `ReportScreen.kt` + `ReportViewModel.kt` (the weekly report: totals, Canvas category bars, breakdown with taxonomy labels, Special line, comparison or "No comparison…", hotspot cards with criteria chips, ideas per hotspot, "How your changes went", offline banner and recap). `feature/interventions/Interventions.kt` has the recommendation card (type, cost, and effort badges, note, Keep it up, Adopt/Adopted), the details sheet (What to do, A note for you, Steps), and the impact card with supportive copy. Registered in `ReportsNavigation.kt`. Strings are in `strings_reports.xml`.
- [x] Tests: `AnalyticsVectorsTest` (3), `LocalReportGeneratorTest` (9), `ReportRepositoryTest` (14), with in-memory fakes in `ReportTestFixtures.kt` (fake API, cache DAO, entry source).

## 3. In progress (unfinished)
| Item | Where | What's left |
|---|---|---|
| Staging and device check | device (not an emulator this session) | Seed a demo account with `/internal/dev/seed-history`, open Insights and a report, and adopt and un-adopt. Then airplane mode plus a week rollover → offline summary → reconnect → server report (plan AN-3 DoD, UAT 5, 5b, 6). |

## 4. Next steps (in order)
1. **Integrator, after AN-1 merges:** in `sync/SyncWorker`, after the outbox flush succeeds, call `reportRepository.refreshAfterSync()` (inject `ReportRepository`). Ignore its result; it never throws.
2. **Integrator or AN-4:** on app open (for example, a `LaunchedEffect` in Home, or `MainActivity` once signed in), call `reportRepository.refreshReports()`. It builds offline reports by itself when the API is unreachable. For offline-only starts, `generateOfflineReports()` does just the local part. Opening the Insights tab already calls `refreshReports()`.
3. **AN-4 Home:** `reportRepository.observeLatestReport()` gives the newest report with details (`ReportView`). Use `weekStart`/`weekEnd` for "Your report for … is ready", `isProvisional` for the offline label, and `adopted` + `canAdopt` for "This week you're trying: …" (only while `canAdopt`, the latest report). `observeReports().first()` gives the newest summary row. Open a report with `navController.navigate(ReportRoute(weekStart.toString()))`.
4. Human: run the §3 checks on staging and a device, light and dark mode.

## 5. Verify the current state
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug   # 67 tests pass; lint: No issues found
```

## 6. Known issues / failing tests
- UI not viewed on a device yet (compile, lint, and unit tests only).
- A concurrent adopt on two ideas of the same report can briefly flicker, because the first server response overwrites the second's optimistic flag until its own response arrives.
- If an adopted intervention's hotspot was dropped by a late-sync regeneration (§5.6), the cached W−1 report no longer lists it, so the offline impact misses that adoption. The server report has it.
- A week whose server summary row is replaced by a provisional one loses the "missing comparisons" hint on the next regeneration (edge case).

## 7. Decisions made (also logged in docs/DECISIONS.md)
- One scrolling report screen, bars instead of a donut, the cache and refresh policy, the provisional payload format, "unreachable" = network or 5xx, when the "Some comparisons…" line shows, when provisional rows are kept, the offline impact baseline and the "Not measured" rule, the un-adopt control, and `ReportEntrySource`.

## 8. API contract changes
- None.

## 9. Environment / setup notes
- No new dependencies. Files outside AN-3's packages: only `data/local/ReportCacheDao.kt` (added `getAll()`, allowed by the parallel rules).

## 10. Questions / blockers for humans
- Please run the staging and device checks (§3).

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
