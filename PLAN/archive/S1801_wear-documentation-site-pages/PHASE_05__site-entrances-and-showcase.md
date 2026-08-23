# Phase 05 - Entrances from the landing and the reference docs

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Make the two Wear guides reachable without knowing their address: from the landing page in each of its three locales, from the long reference guide, and from the user-facing Wear documents that already exist.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - both guides exist on three locales.

> Phase 01 is not a dependency: the entrances added here are hand-written links between existing files and need nothing the registry declares.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `index.html` | Modified | ≤ 8 added lines |
| `index-ru.html` | Modified | ≤ 8 added lines |
| `index-uk.html` | Modified | ≤ 8 added lines |
| `docs/HOW_TO.md` | Modified | ≤ 4 added lines |
| `docs/HOW_TO_RU.md` | Modified | ≤ 4 added lines |
| `docs/HOW_TO_UK.md` | Modified | ≤ 4 added lines |
| `docs/WEAR_OS_SMB_SETUP.md` | Modified | ≤ 3 added lines |
| `docs/WEAR_OS_SMB_QUICK_REF.md` | Modified | ≤ 3 added lines |

> These are site pages and Markdown documents. No Android layout, no Activity, no settings surface is touched, so no UI placement decision is required.

---

## Steps

### Step 05.1 - Link both guides from the three landing pages

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the two Wear guides to each landing page following the pattern the existing scenario links already use there - the same markup shape and the same analytics label call as `docs/howto/scenario-car-music.html` and its neighbours, pointing at `docs/howto/scenario-watch-music.html` and `docs/howto/scenario-watch-network.html`. In `index-ru.html` and `index-uk.html` point at the `-ru` and `-uk` addresses and write the labels in that locale.

**Why:**

Strategic §1 records that the landing carries no link to Wear documentation at all, and §11.1 requires the user to reach a Wear page from the landing in one click; the landing already links individual scenario pages, so the Wear guides being absent from it is the gap rather than a missing mechanism.

**Verification:**

- `Grep` - `scenario-watch-music.html` matches in `index.html`.
- `Grep` - `scenario-watch-network.html` matches in `index.html`.
- `Grep` - `scenario-watch-music-ru.html` matches in `index-ru.html`, and the `-uk` equivalent in `index-uk.html`.
- The count of added links is equal across the three landing files.

**Status:** `[x]` done

---

### Step 05.2 - Link both guides from the long reference guide

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a short Wear entry to each locale of the long reference guide, pointing at the two scenario pages of that locale, in the same position in all three files. Write the pointer as prose with an ordinary link - do not introduce a settings navigation route here.

**Why:**

Strategic §5.1 states that without entrances the pages exist but are unreachable, and the long reference guide already links into the scenario genre, so a reader who starts there rather than at the landing has no path to the Wear guides otherwise.

**Verification:**

- `Grep` - `scenario-watch-music` matches in each of the three `docs/HOW_TO*.md` files.
- `Grep` - the arrow character U+2192 count is unchanged in all three files from before the edit.

**Status:** `[x]` done

---

### Step 05.3 - Cross-link from the existing user-facing Wear documents

**Files:** `docs/WEAR_OS_SMB_SETUP.md`, `docs/WEAR_OS_SMB_QUICK_REF.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a pointer from each of these two documents to the new network guide, and from the SMB setup document to the music guide. Add nothing else - do not restructure or rewrite either document.

**Why:**

Strategic ADR-2 keeps these two documents published because they are written for a watch owner, and §4 explains that they were the only user-facing Wear pages the site had; a reader who lands on them from search must be able to reach the guides rather than stopping at a reference page.

**Verification:**

- `Grep` - `scenario-watch-network` matches in both files.
- `Grep` - `scenario-watch-music` matches in `docs/WEAR_OS_SMB_SETUP.md`.
- The diff for both files adds lines only - no line is removed or reworded.

**Status:** `[x]` done

---

### Step 05.4 - Confirm every new entrance resolves

**Files:** `evidence/entrance-check.txt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Collect every link added in Steps 05.1 to 05.3, resolve each to the Markdown file that produces the address it names, and record the result in `evidence/entrance-check.txt`. Treat a link whose target file does not exist as a failure of this step, not of a later one.

**Why:**

Strategic §2.3 requires that a switcher or pointer lead to an existing page rather than into nothing, and the genre has no generated navigation - every cross-link here is hand-written, so nothing but an explicit check catches a mistyped address before it ships.

**Verification:**

- `Glob` - `evidence/entrance-check.txt` exists.
- Every link listed in that file maps to an existing file under `docs/howto/`.
- Zero entries in that file are marked unresolved.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this phase.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every entrance the strategic criteria name is in place and verified to resolve. Phase 06 records the shipped capability, regenerates the registry artifacts once for the whole ticket, and runs the closing gates.

---

## Rollback Plan

Revert the phase commit. Only added lines are involved in every touched file, so reverting leaves each document exactly as it was.
