# Menosan UI design reference

The mockups in `docs/design/*.png` (28 screens) are the **visual inspiration** for the app: palette, type, layout, components, and tone. They are mockups, not specs. **Behavior, data, and copy rules still come from `docs/DEVELOPMENT_PLAN.md` and `docs/api-contract.md`.** When a mockup conflicts with them, the plan wins (see §5). If you deviate from either, log it in `docs/DECISIONS.md`.

Before building a screen, open its mockup(s) from the table in §4.

## 1. Look and feel
- Warm, calm, and natural: a cream background, deep forest green as the main color, sage for soft surfaces and banners, and amber/sand for highlights. Nothing loud or neon.
- Rounded shapes everywhere: cards and inputs are ~8–12 dp, buttons ~8 dp, chips are pills, and tiles are ~8 dp.
- White cards with a thin forest or grey-green outline (1 dp) on the cream background. Flat, with almost no shadows.
- Friendly bold headings and simple line icons in forest green or near-black. Illustrations (Welcome, Ready) are flat, with sage and forest leaves and amber accents.
- Copy is short and encouraging ("A cleaner home starts with small steps", "Let's work on reducing it first!").

## 2. Design tokens (sampled from the mockups)

| Token | Hex | Used for |
|---|---|---|
| `cream` (background) | `#F6F3EA` | App background, bottom sheets, footer bars |
| `creamLight` | `#FCF9F1` | Welcome and splash background |
| `surface` | `#FFFFFF` | Cards, list surface, inputs |
| `forest` (primary) | `#2F5D50` | Primary buttons, FAB, hero card, selected tile/tab, icons, links, Biodegradable |
| `sage` (secondary) | `#8FA98F` | Offline/pending/tip banners, "Synced" chip, unselected category tiles, avatar, chart bars, secondary leaf |
| `sageLight` | `#C7D1C0` | Unselected segmented tab, check-circle background |
| `sageMist` | `#E6EEDD` | Soft info card ("You're in" feature card) |
| `amber` | `#C98A3B` | "Top Hotspot" and "New" chips, Recyclable in charts |
| `sand` | `#DCB786` | "To Sync" chip, intervention tag chips |
| `taupe` | `#8C8272` | Residual in charts |
| `terracotta` | `#B6462E` | Destructive buttons and text (Delete), error icon |
| `errorContainer` | `#DFBBAE` | Error banner ("Sign-in didn't go through") |
| `ink` | `#22271F` | Main text |
| Muted text | ~`#5F6360` | Subtitles, captions |

Suggested Material 3 mapping (`core/ui/theme`): `primary = forest`, `onPrimary = white`, `secondary = sage`, `secondaryContainer = sageLight`, `tertiary = amber`, `tertiaryContainer = sand`, `background/surface = cream`, `surfaceContainerLowest = white`, `error = terracotta`, `errorContainer = errorContainer`, `onBackground/onSurface = ink`, `outline ≈ forest @ 60%`. Chart colors per main category: Biodegradable `forest`, Recyclable `amber`, Residual `taupe`, plus `terracotta` and `sage` for extra series. The mockups are light only; derive a dark scheme with a dark forest-tinted background, `sage` as the dark primary, and the same accents. The Appearance setting (System / Light / Dark) must work.

**Typography.** A rounded geometric sans that looks like **Lexend**. Confirm with the designer. Bundle the font files in `res/font` (the app works offline); don't use downloadable fonts. Hierarchy: screen titles ~28 sp bold, section titles ~18 sp semibold, big numbers ("23 entries", "10 pcs") ~28–32 sp bold, body 14–16 sp, captions 11–12 sp.

**Logo.** Two overlapping leaves: a large `forest` leaf and a smaller `sage` leaf in front. The wordmark is lowercase **menosan** in bold `ink`/grey, and the tagline is "Less Waste, Better Habits". The launcher icon and the splash/loading mark use the two-leaf mark. Ask the designer for SVG exports of the logo and the Welcome and Ready illustrations. They aren't in the repo yet.

## 3. Components and patterns
- **App shell:** a bottom navigation bar with **Home · Audit · (+) · Insights · Profile**. The center **+ FAB** (forest circle, cream ring) opens the "Add New Entry" bottom sheet: *Scan with Photo* / *Log Waste Manually*, as large outlined rows with an icon.
- **Screen header:** a circular outlined back button (forest outline) on the left and a centered bold title. Top-level tabs use a left-aligned big title and a subtitle ("Audit · Track your waste, build better habits.").
- **Buttons:** primary is filled `forest` with white bold text. Secondary is outlined in forest with ink text. Destructive is filled `terracotta`. Full-width buttons are ~48–56 dp tall.
- **Cards:** white, rounded, thin outline, with a title row and a trailing `›` chevron when the card opens a detail screen.
- **Chips:** status pills, "Synced" (`sage` + check icon) and "To Sync" (`sand` + hourglass). Highlight pills, "Top Hotspot" and "New" (`amber`, white text). Tag pills are `sand`.
- **Banners:** full-width rounded `sage` blocks with an icon: offline notice, entries waiting to sync, a lightbulb tip, "Editable until …". Errors use `errorContainer` with `terracotta` text and a warning icon.
- **Category tiles** (entry form): three square tiles with an icon, a label, and an example line. Selected is `forest` with white text; unselected is `sage`.
- **Segmented tabs:** *New | Adopted*. Selected is `forest`; unselected is `sageLight`.
- **Entry row:** a category icon, a bold name, "Category • date" in muted text, a sync chip, and a `⋮` overflow menu (View full details / Edit entry / Delete entry).
- **Dialogs:** a centered white card with an icon and title (terracotta for destructive), a short body, and **Cancel** (outlined) plus **Delete/Proceed** (filled) side by side.
- **Bottom sheets:** cream, rounded top, a bold title, and a full-width **Done** button (Appearance radio list, Other Preferences).
- **Charts:** a weekly bar strip Sun–Sat (sage bars, the current day highlighted), a category donut with a legend (count and %), and a hotspot donut with the top item in the center. Draw them with Compose `Canvas`; don't add a chart library.
- **Loading screens:** the two-leaf mark centered on cream with a bold title and a one-line subtitle ("Analyzing your Photo", "Creating your account"). The plain logo-only version is the splash.
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
9. **Sign-in flow:** Welcome's *Create Account* and *Log in* both run Google sign-in. Then `GET /v1/me` decides: 404 → consent + `POST /v1/account`. The Terms/Privacy consent checkbox is required before `POST /v1/account`. The Getting Started step "Personalize the app (theme, notifications, units)" is out of scope except for Light/Dark.
10. **Other Preferences** (Notifications, Units, Language) are not in the plan. The UI is English only, and there are no notifications in v1. Hide them unless the team adds them to the plan.
11. **Profile:** the button at the bottom must be **Log out** (the mockup says "Log in"). Account deletion: the plan asks for typing DELETE to confirm. The mockup shows a simple Proceed dialog. Keep the type-DELETE step unless the team decides otherwise (log it in DECISIONS).
12. The Home "Online" pill and the sync chips are good. Also add a "Couldn't sync" state for entries with `lastError`.
