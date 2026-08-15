# Phase 04 - Prose sweep, reference docs

**Strategic spec:** [`../S1392_bugfix-flavor-matrix-docs-contradict-gates.md`](../S1392_bugfix-flavor-matrix-docs-contradict-gates.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Correct every free-prose flavor claim in the reference and troubleshooting documents against the Phase 01 snapshot, in all three locales.

---

## Prerequisites

- [x] Phase 03 is ✅ Done and `assert-flavor-matrix-docs.ps1 -Gate` is green.
- [x] `docs/FLAVOR_MATRIX.md` open as the target values.
- [x] `research/01__flavor-matrix-surface-inventory.md` §3 open as the work list - work from it, not from memory.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md` | Modified | ≤ 60 delta each |
| `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md` | Modified | ≤ 25 delta each |
| `docs/LIMITATIONS.md` + RU/UK | Modified | ≤ 15 delta each |
| `docs/TROUBLESHOOTING.md` + RU/UK | Modified | ≤ 25 delta each |
| `docs/MODULE_SELECTION.md` | Modified | ≤ 15 delta |
| `docs/COMMUNICATION_POLICY.md` + RU/UK | Modified | ≤ 5 delta each |
| `docs/V2_architecture_overview.md`, `docs/V2_Specification.md` | Modified | ≤ 6 delta each |
| `docs/RECEIVING_LINKS_RU.md` | Modified | ≤ 15 delta |

---

## Steps

### Step 04.1 - Correct the "Available in:" lines in the user guide

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`

**Depends on:** - start of phase

**Prompt for developer:**

> Walk every `Available in:` line in the EN file and check its flavor list against `docs/FLAVOR_MATRIX.md`, then mirror each correction in RU and UK. The known-wrong ones are the documents line (claims local reading in Lite and Photos, both `SUPPORT_DOCUMENTS` false), the cloud-reading line and the two note-flow cloud lines (they deny cloud to Photos and Legacy, both `SUPPORT_CLOUD` true), and the streams lines that grant Lite progressive playback. Also fix the two inline "Do not use / Do not expect" warnings that repeat those claims. Where a feature depends on two flags, name both conditions rather than compressing.

**Why:**

Research items §3.3 through §3.5 confirm four wrong lines in this file, and strategic §2 goal 3 covers every surface that misstates `lite`, `photos` or `legacy`; strategic §3.2 makes the three locales one edit.

**Verification:**

- `Grep` - no `Available in:` line in the three files lists Lite for documents, for cloud, or for Streams.
- `Grep` - `Do not expect cloud reading in Lite, Photos, or Legacy` (and its RU/UK equivalents) absent.
- `Grep` - count of `Available in:` lines is equal across the three files.

**Status:** `[x]` done

---

### Step 04.2 - Correct the FAQ

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`

**Depends on:** Step 04.1

**Prompt for developer:**

> Replace "The Lite flavor does not include audio features" with the true statement: local audio playback is present, background playback is not. Replace "Lite supports progressive-audio only" with "the Streams feature is not present in Lite". Keep the Photos statement - Streams really is absent there. Mirror in RU and UK.

**Why:**

Research item §3.11 shows the FAQ carries both halves of the originally reported inversion, and strategic §11 criterion 4 requires a reader in any locale to learn that `lite` has audio and has no Streams.

**Verification:**

- `Grep` - `does not include audio` absent from the three FAQ files.
- `Grep` - `progressive` absent from the three FAQ files, or present only in a sentence about a flavor that really has Streams.
- `Grep` - a statement that Lite has audio without background playback present in each of the three.

**Status:** `[x]` done

---

### Step 04.3 - Correct limitations and troubleshooting

**Files:** `docs/LIMITATIONS.md`, `docs/TROUBLESHOOTING.md` and their RU/UK siblings

**Depends on:** Step 04.2

**Prompt for developer:**

> In limitations, replace the Lite progressive-streams entry with the real limitation: no Streams feature, no network sources, no cloud, no documents, no animations, no background audio. Keep the Photos entry. In troubleshooting, rewrite the per-flavor Streams instructions so the Lite branch says the feature is absent rather than protocol-limited, and check the cloud-availability sentence does not imply cloud is flavor-limited beyond `lite`. Mirror both in RU and UK.

**Why:**

Research items §3.12 and §3.13 show both files instruct a `lite` user to expect a screen that has no entry point, which is the user-visible consequence strategic §1 describes.

**Verification:**

- `Grep` - `progressive http/https` absent from all six files.
- `Grep` - the Lite troubleshooting branch states the feature is unavailable in that flavor.
- `Grep` - `ENABLE_PERSISTENT_AUDIO_PLAYBACK` consequence (no background audio) stated in the limitations entry for Lite.

**Status:** `[x]` done

---

### Step 04.4 - Correct the module-selection guide

**Files:** `docs/MODULE_SELECTION.md`

**Depends on:** Step 04.3

**Prompt for developer:**

> Fix the Streams bullet - Lite is absent, not progressive-only. Fix the Wear OS bullet - it claims all flavors except `vr`, while `lite` and `photos` both declare `SUPPORT_WEAR_COMPANION` false. Keep the VR bullet, which is correct about the two flavors that compile the VR source set, but state that only one of them declares the VR player flag.

**Why:**

Research item §3.14 records two false bullets here, one of them (Wear) not present in the original report - strategic §1 called for a sweep of every row rather than only the two noticed rows.

**Verification:**

- `Grep` - `progressive` absent from the file.
- `Grep` - the Wear bullet names `lite` and `photos` as excluded.
- Cross-check the VR bullet against `docs/FLAVOR_MATRIX.md`: `SUPPORT_VR_PLAYER` true only for `noLegal`.

**Status:** `[x]` done

---

### Step 04.5 - Complete the flavor enumerations

**Files:** `docs/COMMUNICATION_POLICY.md` + RU/UK, `docs/V2_architecture_overview.md`, `docs/V2_Specification.md`

**Depends on:** Step 04.4

**Prompt for developer:**

> The communication-policy scope line enumerates four flavors and silently excludes `vr` and `noLegal` from the string policy - add them. The two V2 documents each carry a Streams sentence that omits `vr` from the enumerated set while `docs/ARCHITECTURE.md` includes it; align them with the architecture wording, which is already correct.

**Why:**

Strategic §2 goal 6 requires `vr` and `noLegal` to appear wherever a matrix or enumeration currently just drops the column, and research §3.18 shows the policy omission silently narrows which builds the string rules cover.

**Verification:**

- `Grep` - `vr` and `noLegal` present in the communication-policy scope line in all three locales.
- `Grep` - the Streams sentence in both V2 documents names the same flavor set as `docs/ARCHITECTURE.md:247`.

**Status:** `[x]` done

---

### Step 04.6 - Resolve the link-receiving matrix column

**Files:** `docs/RECEIVING_LINKS_RU.md`

**Depends on:** Step 04.5

**Prompt for developer:**

> Determine whether link receiving differs between `noLegal` and `standard` by comparing the flags this feature reads. If they are identical, add a `noLegal` column as a copy of `standard` so the table stops being the only matrix in the repo without one. If they differ, add the column with the real values. Either way, verify the existing `lite` and `photos` cells against the snapshot while the file is open, and record the decision in the dev log.

**Why:**

Strategic §6.2 is the one Open research item in this ticket and the tactical index lists it as a Phase 04 blocker; leaving the column out without a recorded reason is what made it look like an oversight in the first place.

**Verification:**

- Read the file: either a `noLegal` column exists, or a one-line note states why it does not, naming the flags compared.
- Cross-check the `lite` and `photos` cells against `docs/FLAVOR_MATRIX.md` - streaming-related rows are absent for `lite`.
- Strategic §6.2 `Статус:` flipped to `Resolved` with the decision recorded.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Strategic §6.2 shows `Resolved`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `Grep` across `docs/` for the stale phrase family (`progressive only`, `progressive-audio only`, `progressive http/https`) returns zero hits outside `dev/handoff/**` and `dev/CHANGELOG.md`, both out of scope per strategic Non-goals.
- [x] Dev log entry added for the phase.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The reference layer now matches the snapshot. Phase 06 needs to know whether any RU/UK sibling was missing for a corrected EN file, so record any such gap in the dev log rather than creating a new translated document inside this ticket.

---

## Rollback Plan

Revert the document edits. The Phase 02 gate stays green either way - it does not read prose, which is exactly why this phase is manual.

