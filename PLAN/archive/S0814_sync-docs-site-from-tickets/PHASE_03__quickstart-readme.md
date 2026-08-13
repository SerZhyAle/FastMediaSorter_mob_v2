# Phase 03 - QUICK_START camera detail and README feature summary

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01/02 (different files)
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-05
**Completed:** 2026-07-05

---

## Objective

Expand the QUICK_START "Camera Capture" entry with in-app camera detail, and refresh the README "Key Features" summary with the biggest new capability clusters. Both mirrored EN/RU/UK.

---

## Prerequisites

- [ ] Read `research/01__doc-freshness-reconciliation.md` section A (camera-detail item + capture cluster).
- [ ] Read `docs/COMMUNICATION_POLICY.md` §2 + §6.
- [ ] Read the current QUICK_START "Step 5: Advanced Features" list and README "Key Features" section to match tone and bullet density.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/QUICK_START.md` | Modified | +30 |
| `docs/QUICK_START_RU.md` | Modified | +30 |
| `docs/QUICK_START_UK.md` | Modified | +30 |
| `docs/README.md` | Modified | +18 |
| `docs/README_RU.md` | Modified | +18 |
| `docs/README_UK.md` | Modified | +18 |

> Keep QUICK_START terse (it is a minutes-to-first-use guide); keep README "Key Features" to concise one-line bullets. Style + tone per CLAUDE.md §1 and COMMUNICATION_POLICY §2/§6.

---

## Steps

### Step 03.1 - Expand QUICK_START "Camera Capture" + add edge-gesture bullet

**Files:** `docs/QUICK_START.md`, `docs/QUICK_START_RU.md`, `docs/QUICK_START_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Under "Step 5: Advanced Features", expand the existing "Camera Capture" entry to mention the in-app camera controls: zoom presets/slider, night mode, and photo/video switch. Add one short "Screen Capture / Edge Gestures" advanced bullet pointing readers to the HOW_TO screen-capture scenario for detail. Keep both terse. Mirror into RU and UK.

**Verification:**

- `Grep -n "night mode"` (or the localised equivalent) present in the expanded Camera entry across all three QUICK_START files.
- `Grep` - an edge-gesture / screen-capture advanced bullet exists once in each of the three QUICK_START files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Camera entry expansion (zoom presets/slider, night mode, photo/video switch) pre-existed from a prior untracked session (night mode = 1 hit per locale); this run added the "Screen Capture & Edge Gestures" advanced block (heading + one bullet with the exact Operations settings path and a HOW_TO pointer) in all three locales (edge bullet = 1 hit per locale).

---

### Step 03.2 - README "Key Features" summary refresh

**Files:** `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the "Key Features" section, add concise one-line bullets for the three highest-impact new clusters: the Screen Capture & recording suite (edge-gesture strip, screenshot, screen/voice/video recording), the opt-in Usage Statistics dashboard, and the duplicate-file finder with size-threshold cleanup. Do not touch "What's New in vXXX" (release-owned) - only the evergreen Key Features summary. Mirror into RU and UK.

**Verification:**

- `Grep -n "Usage Statistics"` (or the localised phrase) present in the Key Features section across all three README files.
- `Grep` - a duplicate-finder bullet and a screen-capture bullet each exist in the Key Features section of all three README files.
- `Grep -n "What's New"` - the README "What's New in vXXX" heading is unchanged (edit did not touch it).

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Appended three Key Features one-liners (Screen Capture & Recording; Usage Statistics opt-in; Duplicate Finder & Size Cleanup) at the list tail in EN/RU/UK. Tokens: statistics=1, capture=1, duplicate>=1 per locale. "What's New" heading: zero occurrences in the current READMEs (predicate trivially holds - nothing release-owned touched).

---

## Phase Done Criteria

- [x] Both `Step 03.*` are `[x] done`.
- [x] Camera detail present in all three QUICK_START files; feature bullets present in all three README files (trilingual parity).
- [x] README "What's New in vXXX" heading untouched (release-owned) - heading absent in current READMEs, nothing touched.
- [x] Prose passes COMMUNICATION_POLICY §6 tone checklist.
- [x] `Grep` for `TODO(phase-03)` returns zero hits (expected 0 | actual 0).
- [x] Dev log entry added (batched at Phase 06 acceptable) - batched to Phase 06.

---

## Handoff Notes to Next Phase

QUICK_START and README now reflect the camera/capture/statistics/duplicate additions. DOCS_MAP date bump (Phase 04) must reflect that README and QUICK_START were edited today.

---

## Rollback Plan

Revert the QUICK_START / README edits (all six locale files) - docs-only.
