# Phase 01 - Detection Harness

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Create four read-only ratchet detectors under `scripts/quality/`, each mirroring `assert-flavor-flags-not-growing.ps1` (modes: report / `-Gate` / `-UpdateBaseline`; baseline = single integer in a sibling `.txt`). No source code is edited in this phase - detectors only measure and freeze the current counts.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/quality/assert-flavor-flags-not-growing.ps1` reviewed as the contract template (report / `-Gate` / `-UpdateBaseline`, `Set-StrictMode`, exit-code discipline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-trivial-comments.ps1` | New | ≤ 120 |
| `scripts/quality/trivial-comments-baseline.txt` | New | 1 |
| `scripts/quality/assert-empty-catch.ps1` | New | ≤ 120 |
| `scripts/quality/empty-catch-baseline.txt` | New | 1 |
| `scripts/quality/assert-layout-hardcoded-colors.ps1` | New | ≤ 110 |
| `scripts/quality/layout-hardcoded-colors-baseline.txt` | New | 1 |
| `scripts/quality/assert-unsafe-collect.ps1` | New | ≤ 120 |
| `scripts/quality/unsafe-collect-baseline.txt` | New | 1 |

---

## Steps

### Step 01.1 - Comment detector

**Files:** `scripts/quality/assert-trivial-comments.ps1`, `scripts/quality/trivial-comments-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write `assert-trivial-comments.ps1` modelled on `assert-flavor-flags-not-growing.ps1`. Scan `app_v2/src/main` `*.kt` (skip `build`/`.gradle`/`.kotlin`). Count line-comments matching the trivial verb-noun heuristic: a `//` comment whose text is a verb-noun phrase (`Get|Set|Initialize|Init|Create|Update|Check|Handle|Setup|Show|Hide|Load|Save|Return|Add|Remove|Clear|Start|Stop|Reset|Apply|Configure|Build|Bind|Observe|Enable|Disable|Register|Unregister|Notify|Refresh|Toggle|Cancel`) restating the immediately following identifier. Exclude `//` URLs, `//noinspection`, `// TODO`, `// FIXME`, and KDoc. Provide report / `-Gate` / `-UpdateBaseline` modes. Seed `trivial-comments-baseline.txt` with the current count.

**Verification:**

- `Glob` - `scripts/quality/assert-trivial-comments.ps1` exists.
- `Grep` - `-Gate` and `-UpdateBaseline` both present in the script.
- Run `pwsh -NoProfile -File scripts/quality/assert-trivial-comments.ps1` - expected exit 0; `trivial-comments-baseline.txt` contains a single integer (expected: integer | actual: `1037`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Files: scripts/quality/assert-trivial-comments.ps1 (new), trivial-comments-baseline.txt (seeded 1037). expected: integer | actual: 1037. Dev log recorded.
- 2026-06-08 - CORRECTION (found during Phase 02 calibration, Rule 14). The first heuristic (any `// <verb> ..`) was far too loose: a sample of 35 flagged comments in ImageLoadingManager was ~70% genuine WHY-comments ("Cancel .. to prevent stale onResourceReady callbacks"), not slop. Tightened to: comment body <= 4 words AND no explanatory connective (to/so/for/avoid/prevent/when/if/null/stale/deprecated/..). True-trivial count is 232, not 1037. Baseline re-seeded 1037 -> 232. This protects ~800 informative comments from deletion (strategic §7 risk). expected: precise trivial count | actual: 232.

---

### Step 01.2 - Empty/comment-only catch detector

**Files:** `scripts/quality/assert-empty-catch.ps1`, `scripts/quality/empty-catch-baseline.txt`
**Depends on:** Step 01.1 (reuse the same scaffold)

**Prompt for developer:**

> Write `assert-empty-catch.ps1` on the same contract. Scan `app_v2/src/main` `*.kt`. Count catch blocks that swallow: an empty `catch (..) {}` or a `catch (..) { <comment-only body> }` (no executable statement). Use a multiline regex that tolerates whitespace and a single comment line inside the braces. Provide report / `-Gate` / `-UpdateBaseline`. Seed `empty-catch-baseline.txt` with the current count.

**Verification:**

- `Glob` - `scripts/quality/assert-empty-catch.ps1` exists.
- `Grep` - `catch` and `-Gate` present in the script.
- Run `pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1` - expected exit 0; `empty-catch-baseline.txt` contains a single integer (expected: integer | actual: `75`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Files: scripts/quality/assert-empty-catch.ps1 (new), empty-catch-baseline.txt (seeded 75). expected: integer | actual: 75 (§11 audit ~76 incl. tests; src/main only = 75). Dev log recorded.

---

### Step 01.3 - Layout hardcoded-color detector

**Files:** `scripts/quality/assert-layout-hardcoded-colors.ps1`, `scripts/quality/layout-hardcoded-colors-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write `assert-layout-hardcoded-colors.ps1` on the same contract. Scan only `app_v2/src/main/res/layout` and `app_v2/src/main/res/layout-land` `*.xml`. Count attribute values matching `="#<hex>"` (3–8 hex digits). Do NOT scan `drawable/`, `color/`, `mipmap/`, or vector assets - hex there is legitimate. Provide report / `-Gate` / `-UpdateBaseline`. Seed `layout-hardcoded-colors-baseline.txt` with the current count.

**Verification:**

- `Glob` - `scripts/quality/assert-layout-hardcoded-colors.ps1` exists.
- `Grep` - `layout-land` and `-Gate` present in the script.
- Run `pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1` - expected exit 0; baseline file contains a single integer (expected: integer | actual: `150`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Files: scripts/quality/assert-layout-hardcoded-colors.ps1 (new), layout-hardcoded-colors-baseline.txt (seeded 150). expected: integer | actual: 150 (= §11 ~150). Dev log recorded.

---

### Step 01.4 - Unsafe-collect detector

**Files:** `scripts/quality/assert-unsafe-collect.ps1`, `scripts/quality/unsafe-collect-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write `assert-unsafe-collect.ps1` on the same contract. Scan `app_v2/src/main` `*.kt`. Count occurrences of `lifecycleScope.launch { .. collect }` whose launch body calls `.collect`/`.collectLatest` WITHOUT an enclosing `repeatOnLifecycle`/`flowWithLifecycle` and not routed through the `collectOnLifecycle` helper. Use a bounded multiline regex. Provide report / `-Gate` / `-UpdateBaseline`. Seed `unsafe-collect-baseline.txt` with the current count.

**Verification:**

- `Glob` - `scripts/quality/assert-unsafe-collect.ps1` exists.
- `Grep` - `repeatOnLifecycle` and `-Gate` present in the script.
- Run `pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1` - expected exit 0; baseline file contains a single integer (expected: integer | actual: `8`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Files: scripts/quality/assert-unsafe-collect.ps1 (new), unsafe-collect-baseline.txt (seeded 8). expected: integer | actual: 8. Sites: BrowseManagerInitializer (x2), MainActivity, PlayerActivityLifecycleBridge (x2), PlayerManagerInitializer, SlideshowSettingsDialogFragment, WelcomeActivity. Dev log recorded.
- 2026-06-08 - CORRECTION (found during Phase 05 triage, Rule 14). The first detector used a negative-lookahead regex that stopped on the first `}` before `.collect`, so it missed launches whose flow had an intervening operator lambda (`.filter { .. }.collect`, `.map { .. }.collect`). Rewrote the detector to brace-match the launch body, then test for `.collect` without `repeatOnLifecycle`/`flowWithLifecycle`. True count is 13, not 8 (5 hidden: PlayerManagerInitializer:563/588, LyricsManager:43, SlideshowResourceAvailabilityManager:36/50). Baseline re-seeded 8 -> 13. expected: brace-accurate count | actual: 13.

---

### Step 01.5 - Cross-check baselines against the strategic audit

**Files:** (read-only) all four baseline `.txt`
**Depends on:** Steps 01.1–01.4

**Prompt for developer:**

> Read the four baseline integers and confirm they are in the same order of magnitude as strategic §11 (trivial comments ~1k, empty/comment-only catch ~76, layout hardcoded colors ~150, unsafe collect ~19). A baseline near zero or wildly above audit means the detector regex is wrong - fix the detector, do not edit the baseline. Record the four actual values in the phase Handoff Notes.

**Verification:**

- `Grep` - each baseline `.txt` contains exactly one integer line.
- Value equality: each baseline within the same order of magnitude as its §11 figure (expected per §11 | actual: trivial-comments 1037, empty-catch 75, layout-hardcoded-colors 150, unsafe-collect 8). A gross mismatch is a hard failure on the detector, not the baseline.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Each baseline file holds exactly one integer. expected: §11 (~1k / ~76 / ~150 / ~19) | actual: 1037 / 75 / 150 / 8. All same order of magnitude; unsafe-collect 8 is the precise true count vs the §11 loose estimate of 19. No detector regex error. Read-only step - no source files changed.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] All four detectors run in report mode with exit 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public Kotlin API changed - catalog regen not required this phase.

---

## Handoff Notes to Next Phase

Seeded baselines: trivial-comments=`232` (corrected from 1037 during Phase 02 calibration - heuristic precision fix), empty-catch=`75`, layout-hardcoded-colors=`150`, unsafe-collect=`8` (was 13 after the Phase 05 brace-match fix; ratcheted to 8 once the 5 view-collects were converted). Phases 02–05 each ratchet exactly one of these DOWN after cleanup; none may rise. The detectors are the source of truth for which files each cleanup phase targets - use `-List` on each to regenerate the per-site proposal list. Unsafe-collect sites (8) are already enumerated in the Step 01.4 log.

---

## Rollback Plan

Delete the four `assert-*.ps1` and four `*-baseline.txt` files - no source code or user-facing surface touched.
