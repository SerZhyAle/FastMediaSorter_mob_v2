# Phase 04 - Docs, Catalog, Cleanup

**Strategic spec:** [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Close the research-only tactical plan cleanly: cross-link the strategic and tactical artefacts, record dev-log coverage, verify that no production files changed, and prepare S0267 for `/spec-check --strategic`.

---

## Prerequisites

- [ ] Phases 01..03 are ✅ Done.
- [ ] `CHILD_SPECS.md`, `PROMPTS.md`, and `ROLLOUT_ORDER.md` exist.
- [ ] No production file outside `PLAN/` was modified during S0267 tactical execution.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0267_cloud-auth-unified-storage-research.md` | Modified | as-is |
| `PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md` | Modified | as-is |
| `dev/CHANGELOG.md` | Modified (via post-change or add_to_dev_log) | auto |

---

## Steps

### Step 04.1 - Verify cross-links between strategic and tactical artefacts

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research.md`, `PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Confirm the strategic spec frontmatter contains both:
>
> - `**Tactical spec:** PLAN/S0267_cloud-auth-unified-storage-research/`
> - `**Tactical plan:** PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md`
>
> Confirm `INDEX.md` points back to the strategic spec via the relative markdown link in its header. Patch only if missing.

**Verification:**

- `Grep` - `**Tactical plan:** ` present in `PLAN/S0267_cloud-auth-unified-storage-research.md`.
- `Grep` - `../S0267_cloud-auth-unified-storage-research.md` present in `INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS without edits. Strategic spec L10: `**Tactical plan:** \`PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md\``. INDEX L3: `**Strategic spec:** [\`../S0267_cloud-auth-unified-storage-research.md\`](../S0267_cloud-auth-unified-storage-research.md)`. Strategic L9 also contains `**Tactical spec:**`. No patching required.

---

### Step 04.2 - Record doc-only closure in the dev log

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1 -ChangeType Doc` once per tactical or strategic spec file changed by S0267 tactical work:
>
> - `PLAN/S0267_cloud-auth-unified-storage-research.md`
> - `PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md`
> - `PLAN/S0267_cloud-auth-unified-storage-research/PHASE_01__child-spec-matrix.md`
> - `PLAN/S0267_cloud-auth-unified-storage-research/PHASE_02__prompt-pack.md`
> - `PLAN/S0267_cloud-auth-unified-storage-research/PHASE_03__rollout-sequencing.md`
> - `PLAN/S0267_cloud-auth-unified-storage-research/PHASE_04__docs-catalog-cleanup.md`
> - plus `CHILD_SPECS.md`, `PROMPTS.md`, and `ROLLOUT_ORDER.md` if Phases 01..03 were executed
>
> Do not edit `dev/CHANGELOG.md` manually.

**Verification:**

- `Grep` - `S0267` present in `dev/CHANGELOG.md` at least 6 times.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 1/1 PASS (expected: `S0267` ≥6 in dev/CHANGELOG.md | actual: 22). Logged via `scripts/post-change.ps1 -ChangeType Doc` for CHILD_SPECS.md ×3, PROMPTS.md ×3, ROLLOUT_ORDER.md ×3, INDEX.md ×1, PHASE_01..04 ×1 each. Strategic spec content was not modified by tactical execution, so no log entry for it.

---

### Step 04.3 - Prove that S0267 stayed research-only

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run a structural check that no production file outside `PLAN/` was changed by S0267 tactical execution. Record the expected-vs-actual result inline in the phase Step Log when you execute this step. If any non-PLAN file changed, stop and treat it as a failure because S0267 must remain research-only.

**Verification:**

- `Grep` - `No production file outside \`PLAN/\` was changed by this tactical plan.` present in `INDEX.md` OR the execution Step Log records `expected: PLAN-only | actual: PLAN-only`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 1/1 PASS (expected: PLAN-only | actual: PLAN-only). S0267 tactical execution touched: `PLAN/S0267_cloud-auth-unified-storage-research/{INDEX,PHASE_01..04,CHILD_SPECS,PROMPTS,ROLLOUT_ORDER}.md` + `dev/CHANGELOG.md` (via `scripts/post-change.ps1`) + `PLAN/spec-catalog.jsonl` (via `scripts/spec_catalog/update.ps1` for `Tactical -> In Progress` status flip). Pre-existing dirty working-tree files (`app_v2/build.gradle.kts`, `app_v2/src/vr/...`) are unrelated to this ticket and predate the session; none were modified by S0267 work.

---

### Step 04.4 - Close via strategic audit

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run `/spec-check S0267 --strategic`. The expected outcome is `Verified` once all doc-only decomposition artefacts exist and the research-only boundary is preserved. If the audit returns `Partial` or `Broken`, fix only the doc-level findings within S0267.

**Verification:**

- `Grep` - `## Last Audit` present in `PLAN/S0267_cloud-auth-unified-storage-research.md`.
- `Grep` - `**Outcome:** Verified` present in the audit block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS (expected: `## Last Audit`=1 + `**Outcome:** Verified`=1 | actual: 1, 1). `/spec-check S0267 --strategic` returned `Verified` with counts PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1 (FEATURES trilingual exempt - strategic §8 says "Без изменений"). Journal status flipped In Progress -> Verified via `close-and-log.ps1`; strategic spec frontmatter `**Status:**` aligned Tactical -> Verified.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits in deliverable docs (the single match in this phase file is the criterion's self-reference).
- [x] S0267 strategic status is `Verified` (journal + frontmatter aligned).
- [x] No stale `Timber.d("S0267:` debug tag exists in the repo (expected: 0 hits | actual: 0 hits).

---

## Handoff Notes to Next Phase

Final phase - after completion, open the child strategic specs using `PROMPTS.md` and `ROLLOUT_ORDER.md`.

---

## Rollback Plan

Revert this phase commit. This phase is documentation-only and must not affect production code or runtime state.
