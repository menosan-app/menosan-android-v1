# Menosan UI design reference

The mockups in `docs/design/*.png` (28 screens) are a **style reference only** (team, 2026-09-24): palette, type, layout, components, and tone. They are not specs for flows, features, or behavior. **Behavior, data, flows, and copy rules come from `docs/DEVELOPMENT_PLAN.md` and `docs/api-contract.md`.** When a mockup shows something different (a feature, a flow, a dialog), follow the plan (see §5). If you deviate from the plan, log it in `docs/DECISIONS.md`.

Before building a screen, open its mockup(s) from the table in §4.

## 1. Look and feel
- Warm, calm, and natural: a Paper (cream) background, Moss green as the main color, Sage for soft surfaces and banners, and Ochre/Sand for highlights. Nothing loud or neon.
- Rounded shapes everywhere: cards and inputs are ~8–12 dp, buttons ~8 dp, chips are pills, and tiles are ~8 dp.
- White cards with a thin Moss or grey-green outline (1 dp) on the Paper background. Flat, with almost no shadows.
- Friendly bold headings and simple line icons in Moss or Ink. Illustrations (Welcome, Ready) are flat, with Sage and Moss leaves and Ochre accents.
- Copy is short and encouraging ("A cleaner home starts with small steps", "Let's work on reducing it first!").

## 2. Design tokens (official palette)

The source of truth is `docs/design/essentials/Light Color Scheme.png` and `Dark Color Scheme.png`. It's implemented in `core/ui/theme/Color.kt` and `Theme.kt`.

| Role | Light | Dark |
|---|---|---|
| Primary | Moss `#2F5D50` | Moss `#2F5D50` |
| Accent | Ochre `#C98A3B` | Ochre `#C98A3B` |
| Secondary | Sage `#8FA98F` | Sage `#8FA98F` |
| Danger | Rust `#B5533E` | Rust `#B5533E` |
| Background | Paper `#F6F3EA` | Deep Forest `#171C19` |
| Primary text | Ink `#22271F` | Paper `#F6F3EA` |
| Cards | White | Forest Gray `#1D2420` |
| Secondary text | ~`#5F6360` | Mist `#B9C0B8` |
| Borders | ~`#55675D` | Muted Sage `#465047` |
| Biodegradable | Moss `#2F5D50` | Moss Light `#7EAE9A` |
| Recyclable | Ochre `#C98A3B` | Ochre Light `#E1B46F` |
| Residual | Antique `#8C8272` | Antique Light `#B8AFA2` |

Supporting tints from the mockups: Paper Light `#FCF9F1` (Welcome), Sage Light `#C7D1C0` (unselected tab), Sage Mist `#E6EEDD` (soft info card), Sand `#DCB786` ("To Sync" and tag chips), Rust container `#DFBBAE` (error banner).

Material 3 mapping (`Theme.kt`):
- Brand fills (FAB, hero card, selected tile or tab, main CTA) use `primaryContainer` = **Moss** in both modes.
- `primary` is Moss in light mode and **Moss Light in dark mode**, because Moss text or icons on Deep Forest are unreadable.
- `secondary` = Sage, `tertiary` = Ochre, `error` = Rust.
- Category, chip, and banner colors that Material has no role for are in `MenosanTheme.colors` (`biodegradable`, `recyclable`, `residual`, `special`, `calm`, `pending`, `highlight`, `mist`, `card`). Use them; don't hard-code hex values in screens.
- The Appearance setting (System / Light / Dark) must work.

**Typography: Roboto.** Regular Roboto for body text, and heavier weights of the same family (Medium, SemiBold, Bold) for titles, labels, and big numbers. It's bundled as the variable font `res/font/roboto.ttf` (OFL, license in `docs/design/essentials/Roboto-OFL.txt`), so it renders the same on every phone and offline. Use `MaterialTheme.typography`, which is already set to Roboto. Hierarchy: screen titles `headlineMedium`/`headlineSmall` bold, section titles `titleMedium`, big numbers `headlineLarge`/`displaySmall`, body `bodyLarge`/`bodyMedium`, captions `bodySmall`/`labelSmall`.

**Logo and illustrations** (`docs/design/essentials/`):
- `Logo.svg` is the two-leaf mark: a Moss leaf behind a Sage leaf. In the app it's `@drawable/ic_menosan_logo` (`MenosanLogo()`), and the launcher icon uses the same mark on Paper, with a themed monochrome version.
- The wordmark is lowercase **menosan** plus the tagline "Less Waste, Better Habits" (`MenosanWordmark()`).
- `signup-image.png` → `drawable-nodpi/illustration_welcome.png` (Welcome), and `post-signup-image.png` → `illustration_ready.png` ("You're in").
- The illustrations are small (~340 px) with an opaque Paper background, so they're drawn on a Paper Light rounded card, in dark mode too. Ask the designer for 3× exports (≥1000 px) or SVGs, ideally with a transparent background.

**Shared components already built** (`core/ui/components/Brand.kt`, `navigation/MainBottomBar.kt`): `MenosanLogo`, `MenosanWordmark`, `BackButton`, `ScreenHeader`, `MessageBanner` (calm/error), `BrandLoading`, `GoogleButton`, `Pill`, `PlaceholderScreen`, `MainBottomBar` (tabs plus the raised "+"), and `AddEntrySheet`. Reuse them.

## 3. Components and patterns
- **App shell:** a bottom navigation bar with **Home · Audit · (+) · Insights · Profile**. The center **+ FAB** (Moss circle, Paper ring) opens the "Add New Entry" bottom sheet: *Scan with Photo* / *Log Waste Manually*, as large outlined rows with an icon.
- **Screen header:** a circular outlined back button (Moss outline) on the left and a centered bold title. Top-level tabs use a left-aligned big title and a subtitle ("Audit · Track your waste, build better habits.").
- **Buttons:** primary is filled Moss with white bold text. Secondary is outlined in Moss with ink text. Destructive is filled Rust. Full-width buttons are ~48–56 dp tall.
- **Cards:** white, rounded, thin outline, with a title row and a trailing `›` chevron when the card opens a detail screen.
- **Chips:** status pills, "Synced" (Sage + check icon) and "To Sync" (Sand + hourglass). Highlight pills, "Top Hotspot" and "New" (Ochre, white text). Tag pills are Sand.
- **Banners:** full-width rounded Sage blocks with an icon: offline notice, entries waiting to sync, a lightbulb tip, "Editable until …". Errors use Rust container with Rust text and a warning icon.
- **Category tiles** (entry form): three square tiles with an icon, a label, and an example line. Selected is Moss with white text; unselected is Sage.
- **Segmented tabs:** *New | Adopted*. Selected is Moss; unselected is Sage Light.
- **Entry row:** a category icon, a bold name, "Category • date" in muted text, a sync chip, and a `⋮` overflow menu (View full details / Edit entry / Delete entry).
- **Dialogs:** a centered white card with an icon and title (Rust for destructive), a short body, and **Cancel** (outlined) plus **Delete/Proceed** (filled) side by side.
- **Bottom sheets:** Paper, rounded top, a bold title, and a full-width **Done** button (Appearance radio list, Other Preferences).
- **Charts:** a weekly bar strip Sun–Sat (Sage bars, the current day highlighted), a category donut with a legend (count and %), and a hotspot donut with the top item in the center. Draw them with Compose `Canvas`; don't add a chart library.
- **Loading screens:** the two-leaf mark centered on Paper with a bold title and a one-line subtitle ("Analyzing your Photo", "Creating your account"). The plain logo-only version is the splash.
- **Empty and helper text** is supportive and never blaming.

## 4. Screen map (mockup → plan screen → workstream)

| Mockup(s) | Plan §10 screen | WS |
|---|---|---|
| `LoadingScreen General` | Splash / app start | AN-0 |
| `Welcome Page` | Entry point before sign-in | AN-0 |
| `Login`, `Create Account`, `Loading Screen-AccountCreation`, `LoadingScreen AfterAccountCreation` | Sign-in + Create account (consent) + "You're in" | AN-0 |
| `Home` | Dashboard | AN-4 |
| `WasteLog PopUp` | "+" FAB sheet (shell) | AN-0 shell, AN-1/AN-2 targets |
| `Manual Waste Entry`, `Entry Correction` | Waste Logging (create/edit) | AN-1 |
| `Waste Audit Dashboard`, `Waste Audit History`, `… (Pending Entries)`, `More Options Pop up`, `More Options DeletePop-up`, `Waste Entry Details` | Waste Entries (current week) + details, edit, delete | AN-1 |
| `Upload Photo`, `Camera CapturePhoto`, `Loading Screen (After Scanning)` | Photo logging | AN-2 |
| `Insights Dashboard` | History + Weekly Summary entry point | AN-3 |
| `Waste Hotspot` | Waste Hotspot | AN-3 |
| `Prevention Recommendation (New)`, `(Adopted)` | Intervention Results + adopt | AN-3 |
| `Profile`, `Profile LightDark`, `Profile PreferencesSettings`, `Profile ExportPopUp`, `Profile DeletePopUp` | Account Settings & Privacy | AN-4 |

## 5. Where the mockups differ from the plan (the plan wins unless the team decides otherwise)
1. **Taxonomy labels.** The mockups use generic names ("Food Waste", "Plastic", "Paper", "Compostable", "Cardboard"). Use the real taxonomy: main categories Biodegradable/Recyclable/Residual/**Special** and subcategory labels from `taxonomy.json`. Hotspots are **subcategories** (I2).
2. **Special category is missing** from the entry form. Add a fourth tile (Special) and the "logged, not included in analysis" line in summaries (I3).
3. **Entries vs pieces.** The mockups count "entries". The plan tracks both frequency (entries) and quantity (pieces). Hotspot scoring uses both, and comparisons and impact use **pieces**. Show both where relevant ("12 entries · 40 pcs"). Item name max length is **60**, not 50.
4. **Hotspots, comparisons, and baseline exist only for closed weeks** (reports). On Home and Insights, label them as last week's report ("From your Sep 20–26 report"). Don't show a live "↑12%" or a hotspot for the week in progress. The "Baseline 8 → 5 / 38% decrease" card maps to **impact** (§5.4): baseline pieces in the adoption week → pieces the following week, Decreased/Same/Increased. There are no multi-week averages.
5. **Insights week picker** lists report weeks (closed weeks from `GET /v1/reports`), not "This Week". Adopt/Un-adopt is shown only when `isLatest` (I7). A provisional offline report shows the "Offline summary" banner and hides recommendations (§5.7).
6. **Recommendation cards:** show type (Prevent/Reduce/Reuse), cost ("Free", "Saves money", "Small one-time cost"), and effort badges from the contract, not freeform tags. On the detail sheet, *What to do* = `description`, *Steps* = `howTo`, and the personal `note` (when present) replaces *Why this helps*. Keep "continued/Keep it up" for pinned items.
7. **Photo review must mark AI fields** (tint plus an "AI suggestion" chip per field until edited or confirmed) and needs a required "I checked these details" confirmation plus the server `warning` (NFR10, SFR9.2–9.5). The mockups don't show this, so add it. The custom camera viewfinder (flash, flip) is optional. The system camera (`TakePicture`) plus the Upload Photo screen is enough.
8. **Audit history filters:** the entries list is the current week (UFR10). Past weeks kept locally are read-only (no Edit/Delete in `⋮`). "Editable until" = the Saturday 11:59 pm PHT that ends the entry's week.
9. **Sign-in flow (built):** Welcome → *Create Account* shows the privacy card and consent checkbox first, then Google sign-in, then `POST /v1/account`. `201` → "Creating your account" → "You're in"; `200` (the account already existed) → Home. Welcome → *Log in* runs Google sign-in, then `GET /v1/me`: `200` → Home, `404` → Create Account (already signed in, so the button reads "Create my account"). There are no Terms of Use yet, so the consent refers to the privacy notice. The mockup's "Getting Started / Personalize the app" card was replaced by the short privacy card the plan requires.
10. **Other Preferences** (Notifications, Units, Language) are **low priority** (team, 2026-09-24): not in v1. Hide them. The UI is English only, and v1 has no notifications.
11. **Profile:** the button at the bottom must be **Log out** (the mockup says "Log in"). Account deletion uses the plan's **type-DELETE** confirmation (team, 2026-09-24): the user types DELETE before the Delete button is enabled. Style the dialog like the mockup (Rust trash icon and title, Cancel and Delete buttons), but don't use its one-tap Proceed behavior.
12. The Home "Online" pill and the sync chips are good. Also add a "Couldn't sync" state for entries with `lastError`.
