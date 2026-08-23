# Phase 03 - Artwork and delivery

**Strategic spec:** [`../S1840_stream-publisher-script-oversized-untested.md`](../S1840_stream-publisher-script-oversized-untested.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

## Objective

Extract artwork, atlas, tile-pack, archive and publication responsibilities while retaining all external asset contracts and safety guards.

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Probe and discovery tests pass.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | <= 1,500 |
| `scripts/streams/modules/StreamPublisher.Artwork.ps1` | New | <= 1,400 |
| `scripts/streams/modules/StreamPublisher.Delivery.ps1` | New | <= 900 |
| `scripts/streams.tests/StreamPublisher.Artwork.Tests.ps1` | New | <= 500 |
| `scripts/streams.tests/StreamPublisher.Delivery.Tests.ps1` | New | <= 400 |

## Steps

### Step 03.1 - Extract artwork and atlas builders

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Artwork.ps1`
**Depends on:** Phase 02

**Prompt for developer:**

> Move favicon cache/fetch, favicon atlas, channel-preview, stream-logo and tile-pack construction into the artwork module. Preserve geometry, cache reuse, rollback and byte/dimension budgets.

**Why:**

The publisher owns three separate artwork families with consumer-visible geometry contracts, and their current co-location makes a change in one family difficult to isolate or test.

**Verification:**

- Each artwork function is declared exactly once.
- Favicon geometry remains 32x32 with 16 columns; preview and logo constants remain unchanged.
- PowerShell parse check passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Artwork builders extracted into a 1229-line module; favicon, preview and logo geometry constants remain present and all modules parse.

### Step 03.2 - Extract archive and publication orchestration

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Delivery.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Move catalog maintenance, ZIP assembly, entry-order checks, faviconless-publication refusal, release upload and mode dispatch helpers into the delivery module. Keep the current CLI as the only executable entry point.

**Why:**

The published ZIP and external atlases are compatibility boundaries, so the refactor must keep every safety guard at the point where artifacts are assembled and before upload.

**Verification:**

- `streams.csv` remains the first ZIP entry in the implementation.
- The favicon-index-without-atlas guard remains active unless explicitly overridden.
- The default release tag, asset names and `--clobber` upload behavior remain unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Delivery safeguards and mode dispatch are isolated; entry remains the sole CLI and the revision gate now scans extracted modules.

### Step 03.3 - Add artwork and delivery tests

**Files:** `scripts/streams.tests/StreamPublisher.Artwork.Tests.ps1`, `scripts/streams.tests/StreamPublisher.Delivery.Tests.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add deterministic tests for atlas geometry/budget predicates and ZIP publication guards using temporary synthetic files and mocked upload boundaries. Do not invoke GDI+, ffmpeg, network services or a real GitHub release.

**Why:**

The external consumer can receive wrong icons or a malformed catalog while the publisher reports success; local artifact predicates are the cheapest reliable regression evidence.

**Verification:**

- Both Pester files pass under the installed Pester version.
- Tests cover ZIP entry order, asset names, atlas byte ceilings and favicon-index pairing.
- Temporary artifacts are written under the test temp directory and cleaned after each test.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Artwork and delivery Pester suites pass 6 tests covering budgets, favicon pairing and ZIP contracts.

### Step 03.4 - Enforce all module budgets and CLI smoke

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Artwork.ps1`, `scripts/streams/modules/StreamPublisher.Delivery.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Remove duplicate moved declarations, enforce <=1,500 lines for every publisher file, and run a no-network parameter/help smoke check through the original entry script.

**Why:**

The strategic acceptance requires both a line-budget fix and unchanged operator behavior; one without the other would leave either maintainability or compatibility unproven.

**Verification:**

- Entry script and every module are <= 1,500 lines.
- Help/parameter parsing succeeds without network calls.
- All publisher Pester tests pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Entry is 504 lines and all publisher modules are <=1500; help smoke, full 15-test suite and asset revision gate pass.

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Parse, Pester and no-network CLI smoke checks pass.
- [ ] Existing stream asset revision gate passes.
- [ ] Dev log entries exist for each touched file.
- [ ] Phase-boundary audit finds no unresolved P0/P1 issue.

## Handoff Notes to Next Phase

The entry point and all extracted modules preserve the external contract and have deterministic test coverage; final phase records documentation and closure evidence.

## Rollback Plan

Restore the pre-extraction script and remove new modules/tests if publication or CLI smoke checks fail.
