# menosan-android

Kotlin + Jetpack Compose Android app for **Menosan** (minSdk 26 / Android 8.0).

- Development plan: [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md)
- Agent rules: [`AGENTS.md`](AGENTS.md) · Handoff log: [`docs/HANDOFF.md`](docs/HANDOFF.md)
- API contract: [`docs/api-contract.md`](docs/api-contract.md) (copy of `menosan-api/docs/api-contract.md`)
- Bundled taxonomy: `app/src/main/assets/taxonomy.json` (must match the API copy)

## Local setup
1. Android Studio (latest stable). Gradle runs on Android Studio's JBR (`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` on the command line).
2. Put `google-services.json` in `app/` (it's gitignored; never commit the prod one) and register your debug SHA-1/SHA-256 in Firebase.
3. Optional `local.properties` keys:
   - `API_BASE_URL_STAGING` (default `https://menosan-api-staging.onrender.com/`), `API_BASE_URL_PROD` (default is also the staging URL: the team uses the staging backend for release).
   - `MENOSAN_KEYSTORE_FILE`, `MENOSAN_KEYSTORE_PASSWORD`, `MENOSAN_KEY_ALIAS`, `MENOSAN_KEY_PASSWORD` to sign release builds with the shared keystore. Without them, release APKs are unsigned.

## Commands
- `./gradlew testStagingDebugUnitTest lintStagingDebug` must pass before every PR.
- `./gradlew assembleStagingDebug` builds a debug APK against staging.
- `./gradlew assembleStagingRelease` builds tester APKs.
