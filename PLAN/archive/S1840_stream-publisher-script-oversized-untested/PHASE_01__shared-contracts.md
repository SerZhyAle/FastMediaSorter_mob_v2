# Phase 01 - Shared contracts

**Strategic spec:** [`../S1840_stream-publisher-script-oversized-untested.md`](../S1840_stream-publisher-script-oversized-untested.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

## Objective

Extract shared schema, pure helpers and common contract state while preserving the current entry script behavior.

## Prerequisites

- [ ] Working tree is clean or on the feature branch.
- [ ] Research artifact has been read.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | <= 1,500 |
| `scripts/streams/modules/StreamPublisher.Common.ps1` | New | <= 500 |
| `scripts/streams.tests/StreamPublisher.Common.Tests.ps1` | New | <= 350 |

## Steps

### Step 01.1 - Add the shared module loader

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Common.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Move shared schema initialization, common constants and pure helper declarations into the common module. Dot-source it from the existing entry script in dependency order and preserve the current parameter names and defaults.

**Why:**

The single script combines shared contract state with every publishing concern, so later extraction cannot be tested independently without first establishing one stable loading boundary.

**Verification:**

- `Test-Path` confirms both files exist.
- `rg` finds exactly one declaration for each moved helper.
- A PowerShell parse check returns no errors.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Common module loaded by the entry script; both files parse and shared declarations are unique.

### Step 01.2 - Preserve CSV and URL contracts

**Files:** `scripts/streams/modules/StreamPublisher.Common.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Keep the 19-column schema order, header-name compatibility and existing URL format/protocol classification behavior in the shared module. Do not change published field names or default values.

**Why:**

The delivery contract requires the 19-column header-named CSV and stable URL classification; changing either would break consumers independently of the refactor.

**Verification:**

- `rg` shows the 19 expected schema names in the existing order.
- Synthetic URL cases return the existing `m3u8`, `mpd`, `rtsp` and default outcomes.
- No new field is added to the schema.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Shared module preserves 19-column schema order and URL format/protocol classifications for m3u8, mpd, rtsp and default URLs.

### Step 01.3 - Add deterministic common-helper tests

**Files:** `scripts/streams.tests/StreamPublisher.Common.Tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add Pester 3.4 tests for URL format/protocol classification, canonical topic mapping, prune-status normalization and CSV schema order. Load only the common module or a test fixture and use no network, GDI+, ffmpeg or GitHub CLI.

**Why:**

The current publisher has zero dedicated tests, while these deterministic rules control catalog rows and can be protected without external services.

**Verification:**

- `Invoke-Pester` runs the new test file and exits 0.
- The test file contains no network command, `gh`, ffmpeg or GDI+ invocation.
- At least one assertion covers each named rule group.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Pester 3.4 common-helper suite passes 5 tests without network, media tools or GitHub access.

### Step 01.4 - Enforce the first line-budget boundary

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Common.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Remove duplicate shared declarations from the entry script and record the first extraction reduction. Keep the entry script executable with its existing parameter surface; the final <=1,500-line check runs after all extraction phases.

**Why:**

The ticket's primary defect is the 1,500-line ceiling violation, and a measured boundary prevents the first extraction from merely moving duplication around.

**Verification:**

- The entry script is shorter than its pre-phase-01 baseline and the common module is <= 500 lines.
- Shared function declarations occur only in the common module.
- PowerShell parse check succeeds.
- Existing help/parameter metadata remains present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Entry reduced from 3501 to 3296 lines; common module is 210 lines, shared declarations are unique, and staged budget checks pass.

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] PowerShell parse check passes.
- [ ] Pester common-helper tests pass.
- [ ] Dev log entries exist for each touched file.
- [ ] Phase-boundary audit finds no unresolved P0/P1 issue.

## Handoff Notes to Next Phase

The entry script loads shared contract helpers; discovery and probe modules may consume the established schema and common state.

## Rollback Plan

Revert the phase changes and restore the original entry script if parameter binding or parse validation fails.
