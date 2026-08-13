# S0309 - Rethink Features and Landing Pages

- Ticket: `S0309`
- Status: Verified
- Priority: 50
- Roadmap: UX / Redesign
- Origin: User Request

## Goal

Rethink, restructure, and visually transform the main landing pages (`index-ru.html`, `index.html`, `index-uk.html`) and the canonical features list files (`docs/FEATURES_RU.md`, `docs/FEATURES.md`, `docs/FEATURES_UK.md`). The primary objective is to present high-value, actionable capability information, eliminate self-evident or trivial entries, provide smooth interactive filtering by target platforms (Standard vs VR), connect features directly to real-world usage scenarios, and strictly hide any mention of the `noLegal` sideload-only flavor features.

## Affected Surface

- `index-ru.html` (Main Russian landing page)
- `index.html` (Main English landing page)
- `index-uk.html` (Main Ukrainian landing page)
- `styles.css` (Shared styling sheet)
- `docs/FEATURES_RU.md` (Canonical Russian features list)
- `docs/FEATURES.md` (Canonical English features list)
- `docs/FEATURES_UK.md` (Canonical Ukrainian features list)

## Non-Goals

- No modification of functional Kotlin/Java application code or assets.
- No modification of local-only `docs/FEATURES_noLegal*.md` files (they must remain intact).
- No modification of actual scenario tutorial pages in `docs/howto/` (only their linking and categorization in the landing page will be updated).
- No removal of valid, core user-visible features from the canonical lists (only consolidation, removal of fluff, and clear classification of Standard vs VR).

## Requirements & Design Decisions

### 1. Aesthetic Upgrades (Modern Ambient Dark UI)
- The landing pages will be redesigned with a premium, media-hub ambient dark theme using an HSL-harmonized color palette.
- Background: Deep slate/dark space `#080616` with smooth radial-gradient glows (`radial-gradient(circle at 50% -20%, rgba(102,126,234,0.15), transparent 60%)`).
- Glassmorphic panels: Use `background: rgba(255, 255, 255, 0.03)` with `backdrop-filter: blur(16px)` and a subtle `border: 1px solid rgba(255, 255, 255, 0.05)`.
- Palette: 
  - Primary (Standard): Violet/Blue `#667eea`
  - Secondary (VR Only): Cyan/Aqua `#00e5ff`
  - Accent: Magenta/Purple `#d946ef`
- Typography: Import and apply Google Fonts 'Outfit' for headers and 'Inter' for highly readable body copy.
- Custom Animations: Smooth scale-up and floating shadows for interactive cards; custom neon-border hover state for active buttons.

### 2. Interactive Feature Explorer Dashboard
- Replace the huge, dry `<details>` text block with an interactive **Feature Explorer** directly inside the landing page.
- Implement a three-tab filter system:
  - 📱 **Standard Edition** (Phones, Tablets, Android TV, Car Head Units)
  - 🥽 **VR Edition** (Meta Quest, Pico, Android XR)
  - 🌟 **All Features** (Unified view)
- Include a real-time text-filter search bar (`#featureSearch`) for instant responsive filtering of feature cards.
- Each feature card will display a platform badge: `[Standard]`, `[VR Only]`, or `[Standard / VR]`.
- Feature cards will display **Scenario Quick Links** (e.g. `📖 Сценарий: Фоторамка`) linking them to detailed how-to scenarios, enhancing logical navigation.

### 3. Redesigning Features Lists (Canonical `.md` & JSON mirrors)
- Clean up `docs/FEATURES*.md` files by consolidating minor items and removing trivial descriptions (e.g., "Language selection on welcome screen").
- Categorize every feature under standard Markdown headers with clear tags:
  - `[Standard / VR]` - available across all platforms.
  - `[VR Only]` - available exclusively in OpenXR-based Quest/XR builds.
  - `[Standard Only]` - available on phones/tablets/TVs but disabled or not applicable in VR (e.g., Wear OS companion).
- Strictly omit all `noLegal` sideload capabilities (such as universal link downloads with yt-dlp, native extractors, on-device PaddleOCR, or local APK installing).

### 4. Scenario-Based Navigation
- Restructure the "Use Scenarios" section into high-impact cards with concrete value propositions.
- Clearly tag scenarios with target platforms (e.g. `🍿 Home Theater [Standard / VR]`, `🚗 Car Music [Standard Only]`).
- Embed direct actions (`Посмотреть инструкцию →`) that lead users directly to `docs/howto/scenario-*.html` pages.

---

## Phase 01 - Canonical Features List Consolidation

### Step 01.01 - Update `docs/FEATURES_RU.md`
- Consolidate 24 sections into 16 simplified, punchy categories.
- Tag each list item with `[Standard / VR]`, `[VR Only]`, or `[Standard Only]`.
- Remove trivial entries (like window auto-scrolling, basic welcome layout properties).
- Double check that yt-dlp, PaddleOCR, local APK installation, and internal Office document viewer are NOT present in the file.

Verification (PowerShell, repo root):
```powershell
Select-String -Path 'docs/FEATURES_RU.md' -Pattern 'yt-dlp', 'PaddleOCR', 'ytdlp', 'Chaquopy', 'install'
```
Expected: 0 matches.

### Step 01.02 - Update `docs/FEATURES.md` (English) & `docs/FEATURES_UK.md` (Ukrainian)
- Replicate the exact consolidated structure and item classification in the English and Ukrainian mirrors to maintain full parity.

---

## Phase 02 - Main Landing Pages Redesign

### Step 02.01 - Revamp `styles.css`
- Add dark-ambient system color variables inside `:root`.
- Style the glassmorphic panels, search input, platform-filter tabs, and beautiful layout badges.
- Ensure responsive break-points for fluid grid adjustments on mobile, tablets, and landscape desktop screens.

### Step 02.02 - Implement Interactive Explorer in `index-ru.html`
- Replace `<details class="full-features-panel">` with a gorgeous HTML template for the Interactive Feature Explorer.
- Write robust, vanilla JavaScript in the `<script>` tag of `index-ru.html` to:
  - Parse the dynamic features list and categorize them by tags.
  - Listen to tab-filtering events and update the card layout dynamically.
  - Listen to search inputs and filter the list instantly with fade animations.
- Connect feature cards directly with how-to scenario links.
- Revamp the Use Scenarios section, styling them with high-fidelity badges and explicit links.

### Step 02.03 - Replicate Redesign in English `index.html` & Ukrainian `index-uk.html`
- Apply the same template structure, CSS integration, and interactive filtering script to the English and Ukrainian mirrors, keeping translations perfectly aligned.

---

## Phase 03 - Verification & Logs

### Step 03.01 - Build and Local Server Verification
- Serve the landing page locally (or perform automated visual check) to verify:
  - 100% responsive styling in both portrait and landscape viewports.
  - Perfect dynamic filtering when clicking "Standard", "VR Only", or "All Features" tabs.
  - Real-time instant search works correctly.
  - Links to how-to scenarios, FAQ, downloads, and documentation map to correct relative paths.
  - Absolutely NO mentions of noLegal features.

### Step 03.02 - Catalog Sync & Dev Logging
- Synchronize catalog files.
- Record development log entries via `add_to_dev_log.ps1` for each modified public HTML/CSS/Markdown file.
