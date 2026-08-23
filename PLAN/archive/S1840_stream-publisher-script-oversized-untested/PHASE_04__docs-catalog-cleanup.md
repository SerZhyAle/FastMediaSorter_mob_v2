# Phase 04 - Documentation and catalog cleanup

**Strategic spec:** [`../S1840_stream-publisher-script-oversized-untested.md`](../S1840_stream-publisher-script-oversized-untested.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

## Objective

Record the new module layout, validate maintained stream documentation and close the tooling quality gates.

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] All publisher tests and smoke checks pass.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/handoff/streams-source-spec/08_build_publish_pipeline.md` | Modified | <= 800 |
| `docs/STREAM_CATALOG_CONSUMERS.md` | Modified | <= 700 |
| `scripts/streams.tests/README.md` | New | <= 200 |
| `scripts/streams/collect-stream-candidates.ps1` | Modified | <= 1,500 |

## Steps

### Step 04.1 - Update producer handoff paths

**Files:** `dev/handoff/streams-source-spec/08_build_publish_pipeline.md`, `docs/STREAM_CATALOG_CONSUMERS.md`
**Depends on:** Phase 03

**Prompt for developer:**

> Update the maintained producer and consumer handoff documents to describe the module directory, the unchanged entry point and the new Pester test location. Preserve all contract numbers and consumer warnings.

**Why:**

The registry records these documents as the source and consumer handoff set, so stale paths would make the refactor undiscoverable and weaken future contract maintenance.

**Verification:**

- Both documents mention the new module/test paths.
- Existing asset names, revisions, byte ceilings and ZIP invariants remain present.
- No generated docs file is hand-edited.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Registered stream handoff documents now describe the modular publisher and point contract checks to the delivery module.

### Step 04.2 - Add test-runner instructions

**Files:** `scripts/streams.tests/README.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Document the supported PowerShell/Pester command, the no-network test boundary and the temporary-artifact policy in concise English.

**Why:**

A test suite that depends on undocumented local invocation will regress into an unrun suite, which is the current coverage failure this ticket addresses.

**Verification:**

- The README names the installed Pester-compatible invocation.
- It explicitly states that network, media decoders and GitHub upload are excluded.
- It references only existing paths.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Publisher test README documents the Pester command and no-network boundary.

### Step 04.3 - Run tooling quality gates

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/*.ps1`, `scripts/streams.tests/*.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the publisher Pester suite, PowerShell parse checks, line-count checks and the existing stream asset revision gate. Fix only findings in the touched publisher files.

**Why:**

The acceptance criteria require executable evidence that the split preserves behavior and that the existing external-consumer revision guard still passes.

**Verification:**

- All checks return exit code 0.
- No touched publisher file exceeds 1,500 lines.
- The revision gate returns PASS.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Pester 15/15 passed; 10 publisher .ps1 files parse clean; all modules and entry point under the 1500-line budget (max 1279); assert-stream-asset-revisions PASS (4 pinned still published, 2 frozen untouched).

### Step 04.4 - Close the ticket with registry validation

**Files:** `PLAN/S1840_stream-publisher-script-oversized-untested.md`, `PLAN/S1840_stream-publisher-script-oversized-untested/INDEX.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run document-registry validation/generation checks and the S1840 audit, then record the final evidence through the script-owned changelog and catalog tools. Keep the strategic spec status transition script-owned.

**Why:**

The document-registry loop is mandatory for the affected maintained handoff records, and script-owned status/catalog files must close with executable evidence rather than hand edits.

**Verification:**

- `scripts/document_registry/validate.ps1`, `generate.ps1` and `generate.ps1 -Check` return 0.
- `scripts/spec_catalog/check-open-items-carried.ps1 -Id S1840` returns 0.
- `/spec-check S1840` equivalent audit returns Verified.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - document_registry validate/generate/generate -Check all exit 0 (36 records, generated views current); check-open-items-carried -Id S1840 exit 0; parameter surface re-verified: 69 parameters exposed, all 8 mode switches present, all 7 published artifact names present.

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Registry validation and generated-output check pass.
- [ ] Dev log entries exist for every modified file.
- [ ] No unresolved P0/P1 audit finding remains.

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

## Rollback Plan

Revert only documentation and generated-output changes if registry validation fails; preserve the tested source split for diagnosis.
