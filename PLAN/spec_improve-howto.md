# Specification: HOWTO Improvement — Dummy-Level Step-by-Step Scenario Guides

**Status:** Draft
**Date:** 2026-04-15
**Tier:** 2 — Easy (content-making, low risk)
**Scope:** Documentation + in-app link + homepage integration. No code changes except one string-resources update for the in-app Help link.

---

## 1. Problem Statement

The existing HOW_TO guides (`docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`) are a reference list of feature descriptions, not scenario walkthroughs. A first-time user trying to set up a "digital photo frame" or "car music player" has no single page to follow — they must piece together steps from Quick Start, HOW_TO, and FAQ. This creates confusion for non-technical users ("dummies"). Additionally, the home pages link to `docs/HOW_TO.html` generically; no deep links guide the user to the relevant scenario.

---

## 2. Goals

1. Create **6 detailed scenario guides** (EN/RU/UK each), covering the most compelling use cases shown on the homepage.
2. Each guide: self-contained, ~10–15 numbered steps with "dummy-level" clarity — no assumed knowledge.
3. Each guide: annotated screenshot slots (`[SCREENSHOT: ...]`) so the author can drop in real screenshots later.
4. Add a **"Scenarios" / "Пошаговые сценарии"** section to all three home pages (`index.html`, `index-ru.html`, `index-uk.html`) with direct card links to each scenario guide.
5. Update the **in-app Help URL** in `GeneralSettingsFragment.kt` to point to the dedicated HOW_TO page (currently it opens the homepage; should open the scenarios index page).
6. Add **string resources** for "How-To Guides" label (EN/RU/UK) used by the new in-app link button.

**Non-goals for this spec:**
- No changes to existing `.html` or `.md` documentation files (per user request — we only create new files).
- No video tutorials, no interactive guides, no offline bundling of guides.
- No changes to app logic beyond the in-app help URL and one new string resource.
- No changes to the Quick Start, FAQ, or Troubleshooting docs.

---

## 3. Scenario Selection

Six scenarios chosen based on the use-case cards already on the homepage (maximum discoverability):

| # | ID | EN Title | RU Title |
|---|-----|---------|---------|
| 1 | `scenario-photo-frame` | Digital Photo Frame on Tablet | Цифровая фоторамка на планшете |
| 2 | `scenario-car-music` | In-Car Music Player (Android Head Unit) | Музыка в автомобиле (Android-магнитола) |
| 3 | `scenario-home-cinema` | Home Cinema & VR Streaming | Домашний кинотеатр и VR-стриминг |
| 4 | `scenario-download-organizer` | Download Organizer (Quick Sort) | Порядок в загрузках (Quick Sort) |
| 5 | `scenario-camera-backup` | Scheduled Camera Backup to PC | Автобэкап фото на ПК по расписанию |
| 6 | `scenario-smb-setup` | Connect to Home NAS / Windows Share (SMB) | Подключение к домашнему NAS / Windows (SMB) |

Scenario 6 (SMB Setup) replaces "Auto-Clean Downloads" from homepage because it is the most common support question and the existing `docs/SMB_SETUP_GUIDE.md` proves demand.

---

## 4. File Structure

All new files go under `docs/howto/`:

```
docs/
  howto/
    index.md                      ← Scenarios index (EN) — linked from homepage + in-app
    index-ru.md                   ← RU mirror
    index-uk.md                   ← UK mirror
    scenario-photo-frame.md       ← EN guide
    scenario-photo-frame-ru.md    ← RU guide
    scenario-photo-frame-uk.md    ← UK guide
    scenario-car-music.md
    scenario-car-music-ru.md
    scenario-car-music-uk.md
    scenario-home-cinema.md
    scenario-home-cinema-ru.md
    scenario-home-cinema-uk.md
    scenario-download-organizer.md
    scenario-download-organizer-ru.md
    scenario-download-organizer-uk.md
    scenario-camera-backup.md
    scenario-camera-backup-ru.md
    scenario-camera-backup-uk.md
    scenario-smb-setup.md
    scenario-smb-setup-ru.md
    scenario-smb-setup-uk.md
    screenshots/                  ← Author drops PNG files here; referenced from guides
      .gitkeep
```

Jekyll builds `.md` → `.html` automatically via the existing `_config.yml`.

---

## 5. Scenarios Index Page (`docs/howto/index.md`)

### Structure

```markdown
---
layout: default
title: "📖 Step-by-Step Guides — FastMediaSorter v2"
permalink: /docs/howto/
---
# 📖 Step-by-Step Guides

Practical walkthroughs for real-world use cases. No prior experience needed.

[Русский](index-ru.md) | [Українська](index-uk.md)

| Guide | What you get |
|-------|-------------|
| [Digital Photo Frame](scenario-photo-frame.md) | Tablet on a stand showing your photos all day from home NAS |
| [Car Music Player](scenario-car-music.md) | Android head unit playing your music collection hands-free |
| [Home Cinema / VR Streaming](scenario-home-cinema.md) | Watch series from PC directly on phone or VR headset |
| [Download Organizer](scenario-download-organizer.md) | Sort files into folders with one tap using Quick Sort |
| [Camera Backup to PC](scenario-camera-backup.md) | Automatic nightly photo backup over Wi-Fi to your computer |
| [Connect to NAS / Windows Share](scenario-smb-setup.md) | Step-by-step SMB connection with screenshots |
```

RU (`index-ru.md`) and UK (`index-uk.md`) mirrors follow the same structure.

---

## 6. Scenario Guide Template

Each scenario `.md` file uses this template. The author fills real content; screenshot slots are clearly marked:

```markdown
---
layout: default
title: "<Scenario Title>"
permalink: /docs/howto/<scenario-id>.html
---
# <Icon> <Scenario Title>

> **Level:** Beginner • **Time:** ~10 minutes • **Flavor required:** <Standard / Any>

[Русский](<scenario-id>-ru.md) | [Українська](<scenario-id>-uk.md)

---

## What You Will Need

- <prerequisite 1>
- <prerequisite 2>
..

---

## Step 1 — <Action verb + object>

<2–3 sentences of plain-language explanation. What to do, where to tap, what to expect.>

[SCREENSHOT: <description of what the screenshot should show>]
`suggested filename: screenshots/<scenario-id>-step1.png`

---

## Step 2 — ...

(repeat for all steps)

---

## Done! What You Can Do Next

- <optional next step / feature to explore>
- <link to relevant HOW_TO section for advanced config>

---

## Troubleshooting

| Problem | Solution |
|---------|---------|
| <common issue> | <one-line fix> |

→ Full troubleshooting: [TROUBLESHOOTING.md](../TROUBLESHOOTING.md)
```

---

## 7. Detailed Scenario Outlines

### 7.1 Scenario 1 — Digital Photo Frame

**Prerequisites:** Android tablet, home Wi-Fi, PC or NAS with a shared folder (or Google Drive).
**Flavor:** Standard (for SMB/Cloud) or any (for local photos).

Steps outline:
1. Install the app (link to Downloads page)
2. Open the app — first-launch overview
3. Tap **"+"** → choose source type (Local / SMB / Google Drive)
4. For SMB: Tap **"Scan Network"** → select your PC → fill credentials
   - `[SCREENSHOT: Scan Network dialog with devices found]`
5. Tap **"Test Connection"** → **"Save"**
6. Open the newly added folder → verify photos appear
   - `[SCREENSHOT: Browse grid with photo thumbnails]`
7. Long-press the folder → **Edit** → set **Slideshow Interval** (e.g., 5 sec)
8. Enable **"Include Subfolders"** if photos are in nested folders
9. Open Settings → Audio → Enable **"Slideshow Background Music"** → pick a music source
   - `[SCREENSHOT: Audio settings with Slideshow Music enabled]`
10. Open the folder → tap the first photo → tap **"Play"** (bottom-right zone)
    - `[SCREENSHOT: Slideshow running with touch-zone overlay]`
11. (Optional) Enable **"Keep screen on"** in Android Display Settings
12. (Optional) Add a Home Screen widget: long-press home → Widgets → FastMediaSorter → **Resource Shortcut**
    - `[SCREENSHOT: Widget picker showing FastMediaSorter widgets]`

**Troubleshooting row examples:**
- "Photos not updating" → "Disable file list caching: folder Edit → disable Cache"
- "Screen goes dark" → "Enable 'Keep screen on' in Android Settings → Display"

---

### 7.2 Scenario 2 — Car Music Player

**Prerequisites:** Android head unit / phone, music files on SD card or home PC (SMB), optional steering wheel buttons.
**Flavor:** Standard or Legacy.

Steps outline:
1. Install app on head unit
2. Add music source: Tap **"+"** → choose **Local Folder** (SD card) OR **SMB** (home PC)
3. For local SD: navigate to `/sdcard/Music` or the SD card path
4. Long-press folder → Edit → set Profile to **"Audio Library"** (auto-configures audio-only filter, sort by title)
   - `[SCREENSHOT: Folder Edit screen with Profile dropdown showing "Audio Library"]`
5. (Optional) Add the folder as **"Predefined Virtual Resource: All Music"** so it shows at top of list
6. Open folder → tap any track → music starts in full-screen audio player
   - `[SCREENSHOT: Audio player with album art / photo background]`
7. Test steering wheel buttons: press Next/Prev — should skip tracks (no setup needed, works out of the box)
8. Enable **"Background Audio Service"**: Settings → Audio → **"Keep playing when app is in background"**
   - `[SCREENSHOT: Audio settings showing background service toggle]`
9. (Optional) Lock screen controls appear automatically once background service is active
10. (Optional) Set up a **Home Screen widget** (Resource Shortcut) pointing to the music folder for one-tap launch

---

### 7.3 Scenario 3 — Home Cinema / VR Streaming

**Prerequisites:** Android phone or VR headset (Meta Quest, Pico), home PC with video series, SMB share enabled on PC.
**Flavor:** Standard or Legacy.

Steps outline:
1. Enable SMB sharing on PC (Windows: File Explorer → share folder → note IP address)
   - `[SCREENSHOT: Windows sharing dialog]` *(external — user provides)*
2. Install app on phone/headset
3. Add SMB resource: Tap **"+"** → **"Network folder SMB"**
4. Tap **"Scan Network"** → select PC → enter share path and credentials
   - `[SCREENSHOT: SMB add-resource screen filled in]`
5. Tap **"Test Connection"** → **"Save"**
6. Open the folder → verify series episodes appear as thumbnails
7. Tap first episode → video plays full-screen with ExoPlayer
   - `[SCREENSHOT: Video player showing episode]`
8. Configure **"Auto-next"**: long-press folder → Edit → enable **"Auto-next file after playback"**
9. (Optional) Adjust video quality via player → Options → Decode mode
10. For VR: open in standalone browser/launcher, the video fills the virtual screen automatically
11. Use hardware buttons on headset/controller to Pause, Skip, Back

---

### 7.4 Scenario 4 — Download Organizer (Quick Sort)

**Prerequisites:** Android phone, files accumulating in Downloads or any folder.
**Flavor:** Any.

Steps outline:
1. Add your Downloads folder: Tap **"+"** → **Local Folder** → navigate to `Downloads`
2. Create destination folders (e.g., "Work", "Personal", "Trash"): use any file manager or do it inside app
3. Add destinations to Quick Sort: Settings → **Quick Sort** tab → Tap **"Add to Quick Sort"**
   - `[SCREENSHOT: Quick Sort settings showing 3 folders with colors and numbers]`
4. Each folder gets a number **1–9** and a color badge
5. Open Downloads folder → tap first file to open full-screen viewer
   - `[SCREENSHOT: File viewer with command panel at bottom]`
6. Look at the bottom command panel: numbered color buttons are your Quick Sort destinations
7. Tap **"1"** → file is **copied** to folder #1 instantly
8. Tap **bottom-left zone** (COPY) → same effect
9. Tap **bottom-center zone** (MOVE) → file is **moved** (deleted from source)
   - `[SCREENSHOT: Touch zone diagram with COPY/MOVE highlighted]`
10. Enable **Safe Mode** (Settings → General) if you want a confirmation before each delete/move
11. After sorting: Settings → Quick Sort → Tap **"Clear Trash"** to permanently delete files you sent to trash

**Pro tip box:**
> Enable **"Always show touch zones overlay"** (Settings → Playback) — a semi-transparent grid shows exactly where to tap.

---

### 7.5 Scenario 5 — Scheduled Camera Backup to PC

**Prerequisites:** Android phone with camera, Windows PC on same Wi-Fi, SMB share on PC.
**Flavor:** Standard.

Steps outline:
1. Enable SMB sharing on Windows PC — share a folder named e.g. `PhoneBackup`
2. Open app → Tap **"+"** → **"Network folder SMB"** → fill in PC address and backup share
3. Test connection and save
4. Go to **Settings** → **Scheduled Operations** (or the background service section)
5. Tap **"Add Schedule"**
   - `[SCREENSHOT: Scheduled Operations screen with "Add Schedule" button]`
6. Set **Source**: select your Camera folder (DCIM/Camera or use "Camera Photos" virtual resource)
7. Set **Destination**: select the SMB resource (PhoneBackup folder)
8. Set **Operation**: **Copy new files only** (or "Move" if you want to free phone storage)
9. Set **Schedule**: e.g. every day at 02:00
   - `[SCREENSHOT: Schedule configuration dialog showing time picker]`
10. Tap **Save** — the schedule is now active
11. Connect phone to charger at night — backup runs automatically in background
12. (Verify) Next morning: open the SMB folder → photos from yesterday should be there
    - `[SCREENSHOT: Browse showing backed-up photos in SMB folder]`

---

### 7.6 Scenario 6 — Connect to Home NAS / Windows Share (SMB)

This is the most common first-time setup question. Extra detail warranted.

**Prerequisites:** NAS (Synology, QNAP, etc.) or Windows PC, Wi-Fi network.
**Flavor:** Standard, Lite, Photos, Legacy (all support SMB).

Steps outline:
1. **Find your NAS/PC IP address**
   - Windows: `Win + R` → `cmd` → `ipconfig` → look for IPv4 Address (e.g., `192.168.1.100`)
   - NAS: open NAS web panel → find IP in Network settings
   - `[SCREENSHOT: Windows ipconfig output highlighting the IPv4 address]` *(user provides)*
2. **Find the share name**
   - Windows: File Explorer → right-click folder → Properties → Sharing tab → note the share name
   - `[SCREENSHOT: Windows share properties dialog]` *(user provides)*
3. **Open app** → main screen → tap **"+"** button
   - `[SCREENSHOT: Main screen with "+" button highlighted]`
4. Select **"Network folder SMB"** from the list
   - `[SCREENSHOT: Add Resource type picker]`
5. **Try Auto-Discovery first**: tap **"Scan Network"**
   - `[SCREENSHOT: Scan Network in progress — spinning indicator]`
   - `[SCREENSHOT: Scan Network results — list of found devices]`
6. Tap your PC/NAS in the list → IP fills automatically
7. Fill in the remaining fields:
   ```
   Server/Path:  \\192.168.1.100\Photos
   Username:     john
   Password:     ••••
   Display Name: Home NAS (optional)
   ```
   - `[SCREENSHOT: SMB form filled in with example data]`
8. Tap **"Test Connection"**
   - `[SCREENSHOT: "Connection successful" green toast]`
   - If red: see Troubleshooting section below
9. Tap **"Save"** — the folder appears on the main screen
   - `[SCREENSHOT: Main screen with new SMB resource card]`
10. Tap the folder to browse its contents
    - `[SCREENSHOT: Browse grid showing NAS photo thumbnails]`

**Server address format reference:**

| Format | Example |
|--------|---------|
| Windows share | `\\192.168.1.100\Photos` |
| Linux/macOS style | `smb://192.168.1.100/Photos` |
| With custom port | `smb://192.168.1.100:445/Photos` |
| Subfolder | `\\192.168.1.100\Media\Movies` |

**Troubleshooting:**

| Problem | Solution |
|---------|---------|
| "Connection refused" | Check PC firewall — allow TCP port 445 |
| "Wrong password" | Try leaving username blank (guest access) |
| "Host not found" | Use IP address, not hostname; make sure both devices on same Wi-Fi |
| Scan finds nothing | Disable VPN; try manual IP entry |
| Slow browse speed | Tap the speed-test button in resource Edit to see actual throughput |

---

## 8. Screenshots Plan

The author shoots these on-device. Guidelines for consistency:

| Requirement | Value |
|-------------|-------|
| Device | Tablet preferred (wider layout) or phone portrait |
| Resolution | At least 1080×1920 |
| Theme | Follow what the app ships with (default dark or light) |
| Language | Match guide language (EN screenshots for EN guides, etc.) |
| Annotations | None — screenshots are raw; arrows/callouts can be added in a later pass |
| Naming convention | `<scenario-id>-step<N>.png` (e.g., `scenario-smb-setup-step5.png`) |
| Location | `docs/howto/screenshots/` |

**Screenshot count per scenario (estimated):**

| Scenario | Min screenshots | Priority |
|----------|:--------------:|---------|
| SMB Setup | 8 | High — most FAQ |
| Download Organizer | 4 | High — core feature |
| Photo Frame | 5 | High — headline use case |
| Camera Backup | 4 | Medium |
| Car Music | 4 | Medium |
| Home Cinema | 3 | Medium (VR is niche) |

Total: ~28 screenshots. The author can start with just "High" priority (17 screenshots) for a first publish.

**The new screenshots from 2026-04-15 session** (`store_assets/screenshots/Screenshot_20260415_*.png`) may already cover several needed states — author should audit them first before shooting new ones.

---

## 9. Homepage Integration

Three cards to add to the **"User Guides"** section of each homepage, linking to the new scenarios index.

### 9.1 `index.html` — new card (EN)

```html
<a href="docs/howto/" class="card" target="_blank">
    <div class="card-icon">🗺️</div>
    <div>
        <h3>Scenario Guides</h3>
        <p>6 detailed walkthroughs for real use cases: photo frame, car music, home cinema, file organizer, camera backup, NAS setup. Beginner-friendly, step by step.</p>
    </div>
    <div class="card-footer">Explore →</div>
</a>
```

### 9.2 `index-ru.html` — new card (RU)

```html
<a href="docs/howto/index-ru.html" class="card" target="_blank">
    <div class="card-icon">🗺️</div>
    <div>
        <h3>Пошаговые сценарии</h3>
        <p>6 подробных руководств для реальных случаев: фоторамка, музыка в авто, домашний кинотеатр, сортировка загрузок, бэкап фото, подключение NAS. Для новичков.</p>
    </div>
    <div class="card-footer">Смотреть →</div>
</a>
```

### 9.3 `index-uk.html` — new card (UK)

```html
<a href="docs/howto/index-uk.html" class="card" target="_blank">
    <div class="card-icon">🗺️</div>
    <div>
        <h3>Покрокові сценарії</h3>
        <p>6 детальних посібників для реальних випадків: фоторамка, музика в авто, домашній кінотеатр, сортування завантажень, бекап фото, підключення NAS. Для початківців.</p>
    </div>
    <div class="card-footer">Дивитись →</div>
</a>
```

---

## 10. In-App Help Link Update

Currently `GeneralSettingsFragment.kt:692–695` opens the homepage. After this change it should open the new scenarios index page.

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`

**Change:**

```kotlin
// Before (opens homepage)
val guideUrl = when (currentLanguage) {
    "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/index-ru.html"
    "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/index-uk.html"
    else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/"
}

// After (opens HOW-TO scenarios index)
val guideUrl = when (currentLanguage) {
    "ru" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-ru.html"
    "uk" -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/index-uk.html"
    else -> "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/"
}
```

This is a 3-line change with zero risk — it only changes a URL string.

---

## 11. String Resources (optional, for future in-app "Open Guides" button)

If the developer later adds a dedicated "Open Guides" button (separate from the existing "User Guide" button), these strings are ready:

| Key | EN | RU | UK |
|-----|----|----|-----|
| `how_to_scenarios_title` | `Step-by-Step Guides` | `Пошаговые сценарии` | `Покрокові сценарії` |
| `how_to_scenarios_subtitle` | `Real-world walkthroughs for common tasks` | `Практические руководства для частых задач` | `Практичні посібники для частих завдань` |

Files:
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

Adding string resources is optional for Phase 1 — the URL change in Section 10 is sufficient.

---

## 12. Implementation Steps (Phase 1 — Content)

> Note: This is primarily a content task. Steps are ordered for a single author working alone.

### Phase 1A — File scaffold (30 min)

1. Create `docs/howto/` directory
2. Create `docs/howto/screenshots/.gitkeep`
3. Write `docs/howto/index.md` (EN scenarios index)
4. Write `docs/howto/index-ru.md` (RU mirror)
5. Write `docs/howto/index-uk.md` (UK mirror)

### Phase 1B — Scenario guides: SMB Setup (highest priority)

6. Write `docs/howto/scenario-smb-setup.md` (EN, all 10 steps, screenshot slots filled)
7. Write `docs/howto/scenario-smb-setup-ru.md` (RU)
8. Write `docs/howto/scenario-smb-setup-uk.md` (UK)

### Phase 1C — Scenario guides: Download Organizer

9. Write `docs/howto/scenario-download-organizer.md` (EN)
10. Write `docs/howto/scenario-download-organizer-ru.md` (RU)
11. Write `docs/howto/scenario-download-organizer-uk.md` (UK)

### Phase 1D — Scenario guides: Photo Frame

12. Write `docs/howto/scenario-photo-frame.md` (EN)
13. Write `docs/howto/scenario-photo-frame-ru.md` (RU)
14. Write `docs/howto/scenario-photo-frame-uk.md` (UK)

### Phase 1E — Remaining 3 scenarios

15. Write Car Music trio (EN/RU/UK)
16. Write Home Cinema trio (EN/RU/UK)
17. Write Camera Backup trio (EN/RU/UK)

### Phase 2 — Screenshots

18. Audit `store_assets/screenshots/Screenshot_20260415_*.png` — identify which steps they cover
19. Shoot missing screenshots on device (see Section 8 priority list)
20. Copy approved screenshots to `docs/howto/screenshots/`
21. Replace `[SCREENSHOT: ...]` slots in each `.md` file with actual `![alt](screenshots/filename.png)`

### Phase 3 — Integration

22. Add "Scenario Guides" card to `index.html` user guides section (see Section 9.1)
23. Add "Пошаговые сценарии" card to `index-ru.html` (Section 9.2)
24. Add "Покрокові сценарії" card to `index-uk.html` (Section 9.3)
25. Update `GeneralSettingsFragment.kt` in-app help URL (Section 10, 3-line change)
26. Run dev log: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../GeneralSettingsFragment.kt" "GeneralSettingsFragment" "Update in-app Help URL to point to HOW-TO scenarios index"`
27. (Optional) Add string resources for `how_to_scenarios_title` / `how_to_scenarios_subtitle` in EN/RU/UK

### Phase 4 — Review & Publish

28. Open `https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/howto/` in browser — verify Jekyll rendered all pages
29. Check all internal links resolve (no 404s)
30. Test in-app Help button on device — confirm it opens the scenarios index

---

## 13. Checklist

- [ ] All 6 scenarios written in EN
- [ ] All 6 scenarios written in RU
- [ ] All 6 scenarios written in UK
- [ ] Screenshot slots filled (or at minimum annotated with `[SCREENSHOT: ...]` placeholders)
- [ ] `docs/howto/index.md` + RU + UK written
- [ ] Homepage cards added (index.html, index-ru.html, index-uk.html)
- [ ] In-app Help URL updated in `GeneralSettingsFragment.kt`
- [ ] Dev log entry added for `GeneralSettingsFragment.kt`
- [ ] (Optional) String resources added in EN/RU/UK
- [ ] Jekyll builds successfully — no broken links
- [ ] Verified on mobile browser: pages are readable at 375px width

---

## 14. Out of Scope

- Video walkthroughs or animated GIFs
- Deep linking from specific app screens to specific scenario guides
- In-app embedded guide viewer (WebView inside settings)
- Localization beyond EN/RU/UK
- Updating existing HOW_TO.md / HOW_TO_RU.md / HOW_TO_UK.md content
- SEO optimization of the new pages (meta descriptions, etc.) — can be done as a follow-up
- Wear OS companion scenario guide
