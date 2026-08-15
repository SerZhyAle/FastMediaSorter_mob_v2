# Phase 03 - Structured tables fix

**Strategic spec:** [`../S1392_bugfix-flavor-matrix-docs-contradict-gates.md`](../S1392_bugfix-flavor-matrix-docs-contradict-gates.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Correct every structured flavor table the Phase 02 gate checks until that gate is green, without touching the gate itself.

---

## Prerequisites

- [x] Phase 02 is ✅ Done and its gate reports the expected mismatches.
- [x] `docs/FLAVOR_MATRIX.md` regenerated and current.
- [x] `research/01__flavor-matrix-surface-inventory.md` §1 open as the target values.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 40 delta |
| `docs/HOW_TO.md` | Modified | ≤ 20 delta |
| `docs/HOW_TO_RU.md` | Modified | ≤ 20 delta |
| `docs/HOW_TO_UK.md` | Modified | ≤ 20 delta |
| `docs/QUICK_START.md` | Modified | ≤ 15 delta |
| `docs/QUICK_START_RU.md` | Modified | ≤ 15 delta |
| `docs/QUICK_START_UK.md` | Modified | ≤ 15 delta |
| `dev/TECH_REQUIREMENTS.md` | Modified | ≤ 20 delta |

---

## Steps

### Step 03.1 - Fix the operations matrices

**Files:** `docs/DEV_OPS.md`

**Depends on:** - start of phase

**Prompt for developer:**

> In the core feature matrix add a `STREAMS` row and a `NETWORK` row (`SUPPORT_STREAMS`, `SUPPORT_LOCAL_NETWORK`) so `lite`'s two headline restrictions are visible. In the extended table correct `SUPPORT_VR_PLAYER` to false for `vr` and true only for `noLegal`, and correct `VR_UI_COMPOSITION_LAYER_ENABLED` to false for `vr` and true for `noLegal`, keeping the not-declared glyph for the four flavors that do not declare it. Update the prose note under the tables so it no longer implies the `vr` flavor renders through the VR player, and state which flavor actually carries immersive rendering.

**Why:**

Research item §3.8 shows the extended table marks both VR flags enabled for the `vr` flavor while the build declares them false there, and §3.9 shows the primary developer matrix omits the two flags that define `lite`; strategic ADR-3 requires the not-declared state to stay visible rather than collapse into a false.

**Verification:**

- `Grep` - `SUPPORT_STREAMS` and `SUPPORT_LOCAL_NETWORK` present in `docs/DEV_OPS.md` matrix rows.
- Run `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1` - zero mismatches reported for `docs/DEV_OPS.md`.
- `Grep` - the prose note no longer contains a claim that the `vr` flavor has `SUPPORT_VR_PLAYER` enabled.

**Status:** `[x]` done

---

### Step 03.2 - Fix the availability table in the user guide, all three locales

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`

**Depends on:** Step 03.1

**Prompt for developer:**

> Set the audio row for `lite` to available, and add a footnote or a separate row stating background playback is off in `lite` (`ENABLE_PERSISTENT_AUDIO_PLAYBACK` false) so one cell never carries two flags. Set the Internet Streams row for `lite` to unavailable and delete the "progressive only" qualifier. Add a `vr`/`noLegal` distinction only where the two differ; keep the existing combined column otherwise. Align the row order across the three locales - RU and UK currently place the Streams row last while EN places it fourth. Apply the same change in all three files in this step, not in follow-ups.

**Why:**

Research items §3.1 and §3.2 are the originally reported inversion, and strategic §6.1 resolved the audio wording as available-plus-a-separate-background-row precisely because gluing two flags into one cell produced the error; strategic §3.2 makes EN/RU/UK a single-edit requirement.

**Verification:**

- `Grep` - `progressive only` absent from all three `HOW_TO*` files.
- Run `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1` - zero mismatches for the three `HOW_TO*` files.
- Row labels in the three tables appear in the same order (compare the three extracted table blocks).
- `Grep` - a background-playback statement exists in each of the three files.

**Status:** `[x]` done

---

### Step 03.3 - Fix the flavor-choice table in quick start, all three locales

**Files:** `docs/QUICK_START.md`, `docs/QUICK_START_RU.md`, `docs/QUICK_START_UK.md`

**Depends on:** Step 03.2

**Prompt for developer:**

> Rewrite the Lite row: audio is present, cloud and network sources are absent, Streams is absent, documents and translation are absent, animations are off. Remove "Streams supports progressive audio streams only". Correct the flavor count sentence above the table to the number the snapshot actually declares, describing `vr` and `noLegal` as the separate distribution surface rather than dropping them from the count. Mirror in RU and UK.

**Why:**

Research item §3.10 shows this cell carries two false claims at once and §3.20 shows the flavor count disagrees between two READMEs and this file; strategic §6.3 resolved the canonical count as the number of `productFlavors` in the snapshot.

**Verification:**

- `Grep` - `progressive audio` absent from all three `QUICK_START*` files.
- `Grep` - `no audio` absent from the Lite row in all three.
- Run `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1` - zero mismatches for the three files.

**Status:** `[x]` done

---

### Step 03.4 - Fix the minimum-requirements table

**Files:** `dev/TECH_REQUIREMENTS.md`

**Depends on:** Step 03.3

**Prompt for developer:**

> The table groups Standard, Lite and Photos in one column, which asserts SMB/SFTP/FTP support and Play-Services-for-cloud for `lite`. Split `lite` out or add a per-cell qualifier so the network-protocols row and the Google-Play-Services row read correctly for it. Keep the Legacy column as is - its API 23 value is already correct.

**Why:**

Research item §3.7 shows the grouping silently grants `lite` two capabilities the build denies it (`SUPPORT_LOCAL_NETWORK` and `SUPPORT_CLOUD` both false), and strategic §2 goal 3 covers every surface that misstates `lite`.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1` - zero mismatches for `dev/TECH_REQUIREMENTS.md`.
- `Grep` - the network-protocols row no longer places `lite` in a cell asserting SMB support.
- `Grep` - `minSdk 23` / API 23 claim for `legacy` unchanged.

**Status:** `[x]` done

---

### Step 03.5 - Turn the gate green and record it

**Files:** none - verification only

**Depends on:** Step 03.4

**Prompt for developer:**

> Run the gate with `-Gate` over the whole project and confirm exit 0. If a mismatch remains, fix the document, never the manifest or the comparison logic - the only legitimate manifest change here is a mapping that was wrong about which flag a documented row means. Then run the fast-gate batch and record its verdict.

**Why:**

Strategic §11 criterion 2 requires the gate green on the corrected tree, and the Phase 02 handoff note forbids reaching green by weakening the gate - that would restore the silent-drift condition this ticket exists to remove.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1 -Gate` - exit 0 recorded.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - the new gate reports PASS in the summary.
- `git diff --stat` on `scripts/quality/assert-flavor-matrix-docs.ps1` shows no logic change in this phase (mapping-only edits allowed and, if made, justified in the dev log).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] No `.kt` touched - validation ladder rung is Doc (grep) plus the gate run.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase, naming all eight files.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every corrected table now agrees with the snapshot, so Phases 04 and 05 have a citable target for prose: when a sentence disagrees with `docs/FLAVOR_MATRIX.md`, the sentence is wrong. The two prose phases are independent of each other and may run in either order.

---

## Rollback Plan

Revert the eight document edits. The gate then reports the original mismatches again; nothing else depends on this phase.

