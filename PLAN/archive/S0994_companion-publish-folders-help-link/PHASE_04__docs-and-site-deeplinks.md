# Phase 04 - Docs and site deep-links

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - documentation phase (parallel to UI phases)
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-07-11
**Completed:** 2026-07-11 (grep: 13/13 doc+site files carry the deep-link)

---

## Objective

Add a direct deep-link to `publish-folders-android.html` in the companion sections of HOW_TO, README, a new FAQ entry, and the landing cards - EN/RU/UK.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md` | Modified | - |
| `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`, `README.md` | Modified | - |
| `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md` | Modified | - |
| `index.html`, `index-ru.html`, `index-uk.html` | Modified | - |

---

## Steps

### Step 04.1 - HOW_TO companion section deep-link (EN/RU/UK)

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Import a Windows Companion Share" section (EN) and its RU/UK counterparts, add a sentence linking `https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html` as the PC-side "how to publish folders" guide - the counterpart to the phone-side import steps. Prose style: `..` not `...`, plain hyphen, `ё` where grammatical.

**Verification:**

- `Grep` - `publish-folders-android.html` present in all three HOW_TO files (three matches).

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Added a "Publishing folders (guide)" bullet to the "Get FastMediaSorter LITE" list in HOW_TO EN/RU/UK.

---

### Step 04.2 - README companion mention deep-link (EN/RU/UK + root)

**Files:** `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`, `README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Where each README mentions FastMediaSorter LITE / the companion, add the publish-folders guide deep-link alongside the existing LITE root/GitHub link.

**Verification:**

- `Grep` - `publish-folders-android.html` present in all four README files (four matches).

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Added a deep-link line under the LITE mention in docs/README EN/RU/UK + root README.md.

---

### Step 04.3 - FAQ companion-share entry (EN/RU/UK)

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> FAQ currently only has a Wear OS "companion" entry - do NOT edit that. Add one concise new Q&A ("Can I share PC folders to my phone?" / RU / UK) whose answer points to the publish-folders guide deep-link and mentions the in-app companion import / QR scan as the phone side. Keep it short; match surrounding FAQ style.

**Verification:**

- `Grep` - `publish-folders-android.html` present in all three FAQ files (three matches).

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Added a new "Can I share PC folders with the app?" Q&A after the SFTP/FTP entry in FAQ EN/RU/UK (Wear OS entry untouched).

---

### Step 04.4 - Landing companion card deep-link (EN/RU/UK)

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** - start of phase

**Prompt for developer:**

> In the companion / LITE card that currently links only to the LITE site root, add an anchor to `publish-folders-android.html` ("How to publish folders for Android" + RU/UK). Match the card's existing markup and `target="_blank"` convention. Keep the page body non-scrolling horizontally.

**Verification:**

- `Grep` - `publish-folders-android.html` present in all three `index*.html` files (three matches).

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Added a guide `<li>` to the companion card detail list in index EN/RU/UK.

---

## Phase Done Criteria

- [ ] All four steps are `[x] done`.
- [ ] `Grep` - `publish-folders-android.html` appears in every targeted doc/site file (13 files).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added (batched) via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Docs and site carry the deep-link in all three languages. Phase 05 records the user-facing capability in `docs/ALL_FEATURES.jsonl`.

---

## Rollback Plan

Revert the phase commit - documentation-only, no code or data surface changed.
