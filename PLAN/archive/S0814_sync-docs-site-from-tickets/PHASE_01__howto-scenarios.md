# Phase 01 - HOW_TO scenarios for newly shipped capabilities

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 7 / 7
**Started:** 2026-07-05
**Completed:** 2026-07-05

---

## Objective

Add scenario / Core-Task-Reference sections to `HOW_TO.md` (EN/RU/UK) for the four owner-approved capability clusters: Screen Capture & recording, Statistics & cleanup, Camera & photo metadata, Streams & shortcuts.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (they are - see INDEX Blockers).
- [ ] Read `research/01__doc-freshness-reconciliation.md` sections A + B in full.
- [ ] Read `docs/COMMUNICATION_POLICY.md` §2 (message formula) + §6 (tone checklist) before writing prose.
- [ ] Read the existing `HOW_TO.md` "Scenario Groups" and "Core Task Reference" structure so new sections match tone and heading style.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO.md` | Modified | +180 |
| `docs/HOW_TO_RU.md` | Modified | +180 |
| `docs/HOW_TO_UK.md` | Modified | +180 |

> All three locale files edited in lockstep per step. Documentation prose style (CLAUDE.md §1): `..` not `...`, plain hyphen `-`, Russian Ё/ё where grammatical. Use real interface icons only if the surrounding section already does; do not invent icon markup.
>
> **Settings-path gate.** Any new line using `→` to describe a Settings navigation path is validated by `howto-settings-paths-gate` (S0558) against the settings manifest. Keep such paths exact, or describe the entry point without a `→` path. Only `→`+anchor lines require EN/RU/UK parity for the gate.

---

## Steps

### Step 01.1 - Edge-gesture capture strip and its action menu

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new scenario under "Power-user and mixed media workflows" describing the left-edge gesture strip and its action menu: swipe from the left edge to open the capture menu; actions are screenshot, take-photo (with send / edit / OCR-translate follow-ups), start video / audio / screen recording, open an app or panel, and crop-and-share. Note that enabling the strip changes the left-edge swipe (page-swipe is suppressed there). State how to enable it (entry point in Settings; keep any `→` path exact per the settings gate). Mirror the section into RU and UK. Prose per COMMUNICATION_POLICY §2/§6.

**Verification:**

- `Grep` - the new EN scenario heading exists once in `HOW_TO.md`; the mirrored heading exists once in each of `HOW_TO_RU.md` and `HOW_TO_UK.md` (heading is the parity anchor - note "OCR" already exists in the separate OCR-translate scenario, so do not use it as the anchor).
- `Grep -n "crop-and-share"` (or the exact localised action wording) present within the new edge-gesture section in all three files - a term new to this section.
- If a `→` settings path was added: `howto-settings-paths-gate` (via `scripts/post-change.ps1`) is green.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Added "Capture the screen with edge gestures" scenario before Core Task Reference in HOW_TO.md / _RU / _UK (trilingual). No `→` settings path added (gate N/A). Dev log batched to Phase 06.

---

### Step 01.2 - Recording flows: screen, voice/audio, in-app video

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a Core-Task-Reference section covering the three recording flows: (1) screen recording via OS consent, saved to the Movies folder; (2) voice / audio note started from the overflow menu, a widget, or the edge-gesture strip, with a floating timer + Stop indicator that stays visible over other apps; (3) in-app video recording straight to the browsed folder or Movies. One cohesive "How to record the screen, a voice note, or a video" section. Mirror into RU and UK.

**Verification:**

- `Grep -n "Movies"` matches within the new recording section in all three locale files (invariant folder token).
- `Grep` - the new EN heading exists once in `HOW_TO.md`; mirrored heading once in each of RU and UK.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). "Movies": EN L1168, RU L1153, UK L1162. Headings mirrored once each: "How to Record Your Screen" (EN L1154 / RU L1139 / UK L1148) + "How to Record a Voice Note" (EN L1174 / RU L1159 / UK L1168). Structure deviation: two sections instead of one cohesive section; in-app video recording covered in the camera section (per its step-5 Record video + edge-gesture tip). Predicates satisfied.

---

### Step 01.3 - Usage Statistics dashboard

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a "How to view usage statistics" reference section: an opt-in, local-only dashboard with summary cards, a media-type distribution bar, collapsible metric sections, and a send/export report action. Emphasise opt-in and local-only (ties to the privacy story). Mirror into RU and UK.

**Verification:**

- `Grep` - a new statistics/dashboard heading exists once in `HOW_TO.md`; mirrored heading once in each of RU and UK.
- `Grep -n "export"` (or the localised equivalent already used elsewhere) present in the new section across all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). Heading once each: "How to View Your Usage Statistics" EN L1236 / RU L1221 / UK L1230. Export token: EN "Export" L1252, RU "Экспорт" L1237, UK "Експорт" L1246. Opt-in + local-only emphasised in the closing note.

---

### Step 01.4 - Duplicate finder and delete-by-size cleanup

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a cleanup reference section: a three-phase duplicate scan (size -> quick-hash -> SHA-256) with automatic or manual deletion, plus a size-threshold sweep to remove files above a chosen size. Mirror into RU and UK.

**Verification:**

- `Grep -n "SHA-256"` matches in all three locale files within the new cleanup section (invariant token).
- `Grep` - the new EN heading exists once in `HOW_TO.md`; mirrored heading once in each of RU and UK.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Cleanup section pre-existed ("How to Find and Delete Duplicate Files" EN L1214 / RU L1199 / UK L1208); this run added the three-pass (size -> quick hash -> SHA-256) wording to the Notes bullet in all three locales. SHA-256: EN L1231, RU L1216, UK L1225.

---

### Step 01.5 - In-app camera detail, File Info (EXIF/GPS), photo geotagging

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add a section on the in-app camera (fixed controls: zoom presets/slider, night mode, photo/video switch) and on File Info: capture date plus GPS shown as a tappable maps/browser link, and opt-in GPS geotagging embedded into captured JPEGs. Mirror into RU and UK.

**Verification:**

- `Grep -n "EXIF"` and `Grep -n "GPS"` match in all three locale files within the new section (invariant tokens).
- `Grep` - the new EN heading exists once in `HOW_TO.md`; mirrored heading once in each of RU and UK.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Camera section pre-existed ("How to Use the In-App Camera" EN L1193 / RU L1178 / UK L1187, zoom presets + night mode + photo/video switch covered); this run expanded the geotag tip with File Info detail (capture date + EXIF GPS tappable maps/browser link, opt-in JPEG embedding). EXIF+GPS: EN L1208, RU L1193, UK L1202.

---

### Step 01.6 - Streams grid/tile mode and Chromecast casting

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.5

**Prompt for developer:**

> Extend the Internet-streams guidance with grid/tile mode (channels shown as tiles with a captured current-frame thumbnail that persists across relaunch) and with casting a live stream to Chromecast. Attach near "How to Add or Import an Internet Stream". Mirror into RU and UK.

**Verification:**

- `Grep -n "Chromecast"` matches in all three locale files (invariant token).
- `Grep` - the new EN grid/tile subsection heading exists once in `HOW_TO.md`; mirrored once in each of RU and UK.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). Chromecast: EN L219, RU L219, UK L1127. Grid/tile: EN L218, RU L218, UK L1126. Placement deviation: added as Scenario Walkthrough bullets inside the internet-radio/streams scenario rather than a separate subsection heading near the stream-add reference - grid + cast content mirrored once per locale, predicate intent satisfied.

---

### Step 01.7 - Quick Sort digit / TV-remote shortcuts

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 01.6

**Prompt for developer:**

> Extend "How to Set Up Quick Sort Folders" (or add an adjacent note) describing digit shortcuts: press a number key (or TV-remote digit) to copy/move to that destination slot; slot badges appear when a hardware keyboard is present. Mirror into RU and UK.

**Verification:**

- `Grep` - the new digit-shortcut note/heading exists once in `HOW_TO.md`; mirrored once in each of RU and UK.
- `Grep -n "1-9"` (or the digit range as written) present in the new content across all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). Keyboard/TV-remote digit note in "How to Set Up Quick Sort Folders": EN L641, RU L616, UK L586. Digit range as written "(0-9)" in the same section: EN L635, RU L610, UK L580.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Each new section exists in `HOW_TO.md`, `HOW_TO_RU.md`, and `HOW_TO_UK.md` (trilingual parity).
- [x] `howto-settings-paths-gate` green (run `scripts/post-change.ps1 -ChangeType Doc` for the HOW_TO files, or the gate directly) - or no `→` settings path was added. 2026-07-05: expected exit 0 | actual exit 0 (17 recipes/locale, parity OK). Fixed 12 stale-path violations inherited from the untracked session: non-existent "Management" tab -> "Operations" (gestures / voice recorder / photography), and reshaped the two prose-in-path lines (Gesture overlay, Statistics collection) so the leaf is the last path segment.
- [x] Strings/prose pass COMMUNICATION_POLICY §6 tone checklist.
- [x] `Grep` for `TODO(phase-01)` returns zero hits (expected 0 | actual 0).
- [x] Dev log entry added (batched at Phase 06 acceptable, or per-file now) - batched to Phase 06.

---

## Handoff Notes to Next Phase

New HOW_TO scenarios exist for all four clusters in EN/RU/UK. FAQ (Phase 02) may cross-reference them by section name. DOCS_MAP date bump (Phase 04) must reflect that HOW_TO was edited today.

---

## Rollback Plan

Revert the HOW_TO.md / _RU / _UK edits - no data migration or user-facing app surface changed; docs-only.
