# Phase 05 - Prose sweep, scenarios and showcase

**Strategic spec:** [`../S1392_bugfix-flavor-matrix-docs-contradict-gates.md`](../S1392_bugfix-flavor-matrix-docs-contradict-gates.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Correct the per-scenario flavor front-matter, the showcase flavor tags and the generated settings reference's flavor rendering, in all three locales.

---

## Prerequisites

- [x] Phase 03 is ✅ Done and the conformance gate is green.
- [x] `docs/FLAVOR_MATRIX.md` open as the target values.
- [x] `research/01__flavor-matrix-surface-inventory.md` §3 items 6, 15, 19, 20 open as the work list.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/howto/scenario-*.md` + `-ru`/`-uk` siblings | Modified | ≤ 8 delta each |
| `docs/howto/index.md` + `-ru`/`-uk` | Modified | ≤ 15 delta each |
| `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` | Modified | ≤ 20 delta each |
| `docs/README.md` + RU/UK | Modified | ≤ 20 delta each |
| `README.md` | Modified | ≤ 6 delta |
| `scripts/docs/render-settings-reference.ps1` | Modified | ≤ 8 delta |
| `docs/SETTINGS_REFERENCE*.md` | Modified (generated) | n/a |

---

## Steps

### Step 05.1 - Correct the scenario front-matter

**Files:** `docs/howto/scenario-*.md` and every `-ru` / `-uk` sibling

**Depends on:** - start of phase

**Prompt for developer:**

> Check the `Flavor:` line of every scenario page against `docs/FLAVOR_MATRIX.md`. The SMB scenario is the confirmed error - it says "Standard, Lite, Photos, Legacy (all support SMB)" while `lite` declares `SUPPORT_LOCAL_NETWORK` false. Also correct the internet-radio and car-music scenarios, which offer Lite a progressive-audio fallback that does not exist. Enumerate flavors consistently: use the same names and the same order as the snapshot. Apply each correction to the EN page and both siblings in the same edit.

**Why:**

Research item §3.6 shows a scenario page instructing a `lite` user through an SMB setup the build makes impossible, and strategic §7 flags an incomplete locale sweep as the highest-probability risk in this ticket.

**Verification:**

- `Grep` - `all support SMB` absent from `docs/howto/`.
- `Grep` - no `Flavor:` line under `docs/howto/` lists Lite together with SMB, cloud, documents or Streams.
- For each corrected EN page, its `-ru` and `-uk` siblings carry the same flavor list (compare the extracted `Flavor:` lines).

**Status:** `[x]` done

---

### Step 05.2 - Correct the scenario index table

**Files:** `docs/howto/index.md`, `docs/howto/index-ru.md`, `docs/howto/index-uk.md`

**Depends on:** Step 05.1

**Prompt for developer:**

> The index carries a flavor column per scenario. Reconcile every cell with the `Flavor:` line the scenario page now states, including the rows that say "All flavors" or "Any" - those are only true for scenarios needing nothing beyond images. Mirror in RU and UK.

**Why:**

Strategic §5.1 requires surfaces to defer to one answer rather than restate it, and an index disagreeing with the page it links to reproduces the same contradiction one level up.

**Verification:**

- For every row, the index cell equals the `Flavor:` line of the linked page.
- `Grep` - `All flavors` / `Any` remain only on rows whose scenario needs images alone.
- Row count and order identical across the three index files.

**Status:** `[x]` done

---

### Step 05.3 - Correct the showcase flavor tags

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `docs/README.md` + RU/UK, `README.md`

**Depends on:** Step 05.2

**Prompt for developer:**

> Fix the bracket tags on the Streams entries: drop `Lite (progressive-audio only)` from the dedicated-Streams entry and drop `Lite` from the inline-radio entry, since `SUPPORT_STREAMS` is false there. Check the remaining bracket tags across these files against the snapshot, including the mini-game entry and the launcher entries. Correct the flavor-count sentences - root `README.md` says four flavors, `docs/README.md` says five plus a surface - to the count the snapshot declares. Do not add any new showcase entry; strategic §8 records that this ticket delivers no new capability.

**Why:**

Research items §3.15 and §3.20 show the public showcase promising a `lite` user a Streams screen and the two READMEs disagreeing on the flavor count; strategic §8 explicitly scopes this to correcting existing entries, because `docs/FEATURES*.md` is otherwise owned by the release pipeline.

**Verification:**

- `Grep` - `progressive-audio only` absent from `docs/FEATURES*.md`, `docs/README*.md`, `README.md`.
- `Grep` - no bracket tag on a Streams entry lists Lite.
- `Grep` - the flavor-count sentence agrees between `README.md` and `docs/README.md` and matches `docs/FLAVOR_MATRIX.md`.
- Entry count in `docs/FEATURES.md` unchanged from before the phase.

**Status:** `[x]` done

---

### Step 05.4 - Teach the settings-reference renderer about `vr`

**Files:** `scripts/docs/render-settings-reference.ps1`, `docs/SETTINGS_REFERENCE*.md`

**Depends on:** Step 05.3

**Prompt for developer:**

> The renderer's flavor display-name map has no `vr` key, so a `vr`-scoped setting cannot render its flavor name in the generated reference. Add `vr` with the display name the rest of the documentation uses. Regenerate the reference in all three locales and confirm the `Available in:` lines are unchanged except where a `vr` entry now appears. Do not hand-edit the generated files.

**Why:**

Research item §3.19 records the missing key, and strategic §2 goal 6 requires `vr` to be present wherever flavors are enumerated; canon invariant 16 forbids fixing this in the render target instead of its source.

**Verification:**

- `Grep` - `vr` present in the `$flavorName` map in `scripts/docs/render-settings-reference.ps1`.
- Run the renderer - exit 0 for all three locales.
- Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -Gate` - exit 0.
- `git diff` on `docs/SETTINGS_REFERENCE*.md` shows only flavor-name additions.

**Status:** `[x]` done

---

### Step 05.5 - Sweep for residue

**Files:** none - verification only

**Depends on:** Step 05.4

**Prompt for developer:**

> Grep the whole in-scope tree for the stale phrase family and for every enumeration that names four flavors: `progressive only`, `progressive-audio only`, `progressive http/https`, `standard, lite, photos, legacy`, `Standard, Lite, Photos, Legacy`. Every surviving hit must be either out of scope per strategic Non-goals (`dev/handoff/**`, `dev/CHANGELOG.md`, `nolegal*.html`, `docs/ALL_FEATURES.jsonl`) or genuinely correct in context. List each survivor and its reason in the dev log.

**Why:**

Strategic §7 rates an incomplete sweep as the highest-probability risk, and strategic §11 criterion 6 requires that no surface promises progressive streams in `lite` - a claim that can only be checked by looking for the phrase, not by reasoning about it.

**Verification:**

- Each of the five greps run and its hit list recorded.
- Every hit classified out-of-scope or correct-in-context, with the reason.
- Zero unclassified hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1` not required - no `strings.xml` touched in this phase.
- [x] Dev log entry added for the phase.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Phase 06 inherits the residue list from Step 05.5. Anything on it that is neither out of scope nor correct is a Phase 06 item or a `/spec-draft` candidate, not a silent omission.

---

## Rollback Plan

Revert the document edits and the one renderer line, then re-run the settings renderer. No data migration and no app behaviour involved.

