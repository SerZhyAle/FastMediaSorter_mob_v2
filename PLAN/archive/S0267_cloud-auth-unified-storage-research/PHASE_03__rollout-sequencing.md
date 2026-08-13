# Phase 03 - Rollout Sequencing

**Strategic spec:** [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Turn the child-ticket set into a concrete rollout order that states which specs are sequential, which may run in parallel, and which verification gates must pass before the next wave may start.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] `CHILD_SPECS.md` and `PROMPTS.md` are complete.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md` | New | ≤ 350 |

---

## Steps

### Step 03.1 - Define the wave-by-wave execution order

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ROLLOUT_ORDER.md`. Add a `## Execution waves` section with four subsections: `Wave 1`, `Wave 2`, `Wave 3`, `Wave 4`. Use the same wave contents as Phase 01. For each wave, list `Entry criteria`, `Outputs`, and `Exit gate`.

**Verification:**

- `Glob` - `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md` exists.
- `Grep` - `## Execution waves` present.
- `Grep` - `Wave 4` present exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: file present + section + `Wave 4`=1 | actual: file ok, section=1, Wave 4=1).

---

### Step 03.2 - Mark parallelism and serialization rules

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `## Parallelism rules` section. State explicitly:
>
> - `cloud-auth-storage-foundation` must complete first.
> - `google-drive-auth-mirror`, `dropbox-auth-mirror`, and `onedrive-auth-mirror` may run in parallel after Wave 1 is Verified.
> - `settings-authorizations-unified-sources` starts only after all three provider tickets are at least Implemented.
> - `settings-authorizations-unified-ui` starts only after the sources ticket is Verified and `/ui-clarify` is complete.
> - `cloud-auth-auditor-extension` is deferred until after the first unified-authorizations release.

**Verification:**

- `Grep` - `may run in parallel` present.
- `Grep` - `/ui-clarify` present.
- `Grep` - `deferred until after the first unified-authorizations release` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: each phrase present | actual: 1, 2, 1).

---

### Step 03.3 - Add stop-go checkpoints and validation gates

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a final `## Stop-go checkpoints` section with one checkpoint after each wave. Each checkpoint must name the concrete pass condition before the next wave may open:
>
> - Wave 1 -> Room migration builds on `standardDebug`
> - Wave 2 -> all three provider tickets pass compile and token-path validation
> - Wave 3 -> unified UI builds and passes orientation parity review
> - Wave 4 -> optional post-release only
>
> Close the document with `## Suggested operator sequence`, listing the recommended order of future commands: child `/spec`, then child `/spec-tech`, then child `/spec-dev`, then child `/spec-check`.

**Verification:**

- `Grep` - `## Stop-go checkpoints` present.
- `Grep` - `orientation parity review` present.
- `Grep` - `## Suggested operator sequence` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: each section/phrase present | actual: 1, 1, 1).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `ROLLOUT_ORDER.md` names all four waves and all four stop-go checkpoints (expected: 4 waves + 4 checkpoints | actual: Wave 1..4 sections + Checkpoint after Wave 1..4 bullets).
- [x] `Grep` for `TODO(phase-03)` returns zero hits in `ROLLOUT_ORDER.md` (self-reference in this phase file excluded).
- [x] Dev log entry added for `ROLLOUT_ORDER.md` via `scripts/post-change.ps1` (3 entries: Phase 03.1, 03.2, 03.3).

---

## Handoff Notes to Next Phase

Phase 04 closes the tactical plan, records the doc-only change set, and prepares S0267 for strategic `/spec-check`.

---

## Rollback Plan

Revert this phase commit. This phase adds planning docs only and does not touch production code or spec-catalog records.
