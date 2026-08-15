# Phase 02 - FAQ entries for newly shipped capabilities

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01 (different files)
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-07-05
**Completed:** 2026-07-05

---

## Objective

Add Q&A entries to `FAQ.md` (EN/RU/UK) for the four owner-approved clusters, placed in the existing FAQ sections (Screen Capture, Security & Privacy, File Operations, Internet Streams, Quick Sort) per the reconciliation report's best-fit mapping.

---

## Prerequisites

- [ ] Read `research/01__doc-freshness-reconciliation.md` sections A + B.
- [ ] Read `docs/COMMUNICATION_POLICY.md` §2 + §6.
- [ ] Read the existing `FAQ.md` section layout (General / File Operations / Network & Cloud / Quick Sort / Touch Zones / Security & Privacy / Internet Streams).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FAQ.md` | Modified | +120 |
| `docs/FAQ_RU.md` | Modified | +120 |
| `docs/FAQ_UK.md` | Modified | +120 |

> All three locale files edited in lockstep per step. Prose style + tone per CLAUDE.md §1 and COMMUNICATION_POLICY §2/§6. FAQ questions are `### ` headings; keep that level.

---

## Steps

### Step 02.1 - Screen Capture FAQ section

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new "Screen Capture" FAQ section with Q&A: what the left-edge gesture strip is and how to enable it; how to record the screen; how to record a voice note (with the floating Stop indicator over other apps). Keep answers short and action-first. Mirror into RU and UK.

**Verification:**

- `Grep` - a new "Screen Capture" (localised) `## ` section heading exists once in each of the three files (heading is the parity anchor; "OCR" already appears in the translation FAQ, so do not use it as the anchor).
- `Grep -n "edge"` (or the exact localised term for the edge strip) present within the new section in all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). Section "Screen & Voice Capture" / "Захват экрана и голоса" / "Захоплення екрана та голосу" once each (EN L163 / RU L204 / UK L163); edge term present (EN "left-edge" L165-166, RU "у левого края" L206, UK "біля лівого краю" L165). Screen-recording how-to deferred to HOW_TO links inside the edge-strip answer (deviation: no standalone screen-record Q). This run fixed the stale settings path: non-existent "Management/Управление/Управління" tab -> Operations/Операции/Операції in all three locales.

---

### Step 02.2 - Usage Statistics FAQ (next to "Is my data collected?")

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the "Security & Privacy" section, next to "Is my data collected?", add a Q&A explaining the Usage Statistics dashboard: opt-in, computed and stored locally, never sent unless the user explicitly exports/sends a report. This closes the gap where the privacy FAQ never mentioned the feature. Mirror into RU and UK.

**Verification:**

- `Grep` - a new statistics Q (`### `) exists once in each of the three files, inside the Security & Privacy section.
- `Grep -n "opt-in"` (or the localised phrasing used elsewhere in the doc) present in the new answer across all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Q pre-existed in Security & Privacy ("Can I see how I use the app?" EN L232 / RU L273 / UK L232), local-only + explicit-send already stated; this run added the opt-in wording ("it's opt-in and off by default" / "по умолчанию сбор выключен" / "типово збір вимкнено").

---

### Step 02.3 - Duplicate finder FAQ (File Operations)

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> In "File Operations", add a Q&A about the duplicate finder: a three-phase scan (size -> quick-hash -> SHA-256) with auto or manual delete, plus the delete-by-size-threshold sweep. Distinguish it from the existing "Copy creates a duplicate" wording. Mirror into RU and UK.

**Verification:**

- `Grep -n "SHA-256"` present in the new Q&A across all three files (invariant token).
- `Grep` - a new duplicate-finder Q (`### `) exists once in each of the three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Q pre-existed in File Operations ("How do I find and remove duplicate files?" EN L48 / RU L48 / UK L48, incl. delete-by-size sweep); this run appended the content-based three-pass sentence (size -> quick hash -> SHA-256) in all three locales. Inline UK typo fix in the adjacent File-Manager-Mode answer ("операции" -> "операції").

---

### Step 02.4 - Photo metadata / geotagging FAQ

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a Q&A on photo geotagging and File Info: GPS embedding is opt-in; File Info shows capture date and a tappable maps link built from the photo's EXIF GPS. Place near the privacy/camera questions. Mirror into RU and UK.

**Verification:**

- `Grep -n "GPS"` and `Grep -n "EXIF"` present in the new Q&A across all three files (invariant tokens).
- `Grep` - a new geotag/File-Info Q (`### `) exists once in each of the three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Q pre-existed near privacy/camera questions ("Does the app save GPS location in my photos?" EN L229 / RU L270 / UK L229, opt-in framing already present); this run added capture date + EXIF wording to the File Info sentence and fixed the stale "Management/Управление/Управління" tab -> Operations/Операции/Операції.

---

### Step 02.5 - Internet Streams FAQ additions (grid + casting)

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 02.4

**Prompt for developer:**

> In the existing "Internet Streams" section, add two Q&A: grid/tile view with last-frame thumbnails that persist across relaunch; and casting a live stream to Chromecast. Mirror into RU and UK.

**Verification:**

- `Grep -n "Chromecast"` present in the new Q&A across all three files (invariant token).
- `Grep` - two new stream Q entries (`### `) added in each of the three files within the Internet Streams section.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). Two Q&A in the streams section of each locale: live tiles/grid (EN L305 / RU L148 / UK L315) + Chromecast casting (EN L308 / RU L151 / UK L318); persistence across relaunch and the RTSP no-cast caveat covered.

---

### Step 02.6 - Quick Sort digit-shortcut FAQ

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 02.5

**Prompt for developer:**

> In "Quick Sort & Destinations", add a Q&A on digit shortcuts: press a number key or TV-remote digit to send the current file to that destination slot; slot badges show when a hardware keyboard is attached. Mirror into RU and UK.

**Verification:**

- `Grep` - a new digit-shortcut Q (`### `) exists once in each of the three files, inside the Quick Sort section.
- `Grep -n "1-9"` (or the digit range as written) present in the new answer across all three files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Q pre-existed in the Quick Sort section ("Can I use number keys instead of tapping?" EN L129 / RU L170 / UK L129, HW keyboard/TV-remote + auto badges covered); this run added the digit range "(0-9)" to the answer in all three locales.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Each new Q&A exists in `FAQ.md`, `FAQ_RU.md`, and `FAQ_UK.md` (trilingual parity). Token check 2026-07-05: SHA-256=1, EXIF=1, Chromecast>=2, (0-9)=2 in each locale; no stale "Management/Управление/Управління" settings-path residue.
- [x] Prose passes COMMUNICATION_POLICY §6 tone checklist.
- [x] `Grep` for `TODO(phase-02)` returns zero hits (expected 0 | actual 0).
- [x] Dev log entry added (batched at Phase 06 acceptable) - batched to Phase 06.

---

## Handoff Notes to Next Phase

FAQ now covers all four clusters in EN/RU/UK. DOCS_MAP date bump (Phase 04) must reflect that FAQ was edited today.

---

## Rollback Plan

Revert the FAQ.md / _RU / _UK edits - docs-only, no app surface changed.
