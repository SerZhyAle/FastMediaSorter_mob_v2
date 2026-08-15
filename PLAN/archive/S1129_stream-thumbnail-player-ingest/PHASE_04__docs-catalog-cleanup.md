# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S1129_stream-thumbnail-player-ingest.md`](../S1129_stream-thumbnail-player-ingest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phases 01-03
**Blocks:** final audit
**Steps done:** 4 / 4
**Started:** 2026-07-20
**Completed:** -

---

## Objective

Close inventory, catalog, validation, and device-test handoff for the implemented feature.

---

## Prerequisites

- [x] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified through script | n/a |
| `docs/ARCHITECTURE.md` | Modified | <= 500 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Modified through script | n/a |
| `PLAN/S1129_stream-thumbnail-player-ingest.md` | Modified through catalog CLI | <= 260 |

---

## Steps

### Step 04.1 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`

**Prompt for developer:**

> Add one CHANGE/ADD inventory record for player-ingested stream thumbnails with exact shipping flavors `standard,legacy,vr,noLegal` and spec `S1129`. Do not edit curated `docs/FEATURES*.md` before release.

**Verification:**

- `scripts/all_features/validate.ps1` exits 0.
- Exactly one record references `S1129`.

**Status:** `[x]` done

### Step 04.2 - Regenerate the Kotlin catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Run the one-shot app_v2 catalog sync after all Kotlin changes.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- New public classes are queryable.

**Status:** `[x]` done

### Step 04.3 - Run mechanical closure and build proof

**Files:** all ticket-touched files

**Prompt for developer:**

> Run `post-change.ps1 -ScopeToFile` for the logical change, then the cheapest matching compile/test gates. Record expected and actual outcomes.

**Verification:**

- `./a.ps1 fk` exits 0.
- Targeted ingest tests exit 0.
- Fast quality gates report no new ticket-local failure.

**Status:** `[x]` done

### Step 04.4 - Finalize the device-test handoff

**Files:** strategic spec and catalog status via CLI

**Prompt for developer:**

> Mark implementation complete, then transition to `BlockNeedUserTest` with an exact note: open a VIDEO/RTSP channel, wait for picture, return to GRID, verify the real frame appears immediately, restart the app, and verify it persists. Keep the S1129 probe until the ticket leaves this status.

**Verification:**

- Catalog status is `BlockNeedUserTest` with a non-empty status note.
- Exactly one S1129 debug probe remains.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 04.* is `[x] done`.
- [ ] INDEX shows 4 / 4 phases done.
- [x] Fresh compile, targeted tests, and ticket-local gates pass.
- [ ] `/spec-check S1129` records the device-only residual gate.

---

## Handoff Notes to Next Phase

Debug APK `2.60.7200.347-DEBUG` built, installed, launched on `emulator-5554`, and passed the
launch-window crash scan. Evidence screenshot:
`temp/scratch/emulator-5554_20260720_035006.png`.

The full unit suite was not used as ticket evidence: it hit an unrelated
`CameraCaptureSaverTest` failure and its worker exhausted memory. The focused
`RealStreamFrameIngestorTest` run passed (`expected: 0 | actual: 0`).

Final closure remains device-gated. `/spec-test-device S1129` could not start because its hard
requirement is unavailable: `mobile-mcp not configured - enable the MCP server first`. Keep the
ticket in `BlockNeedUserTest`, retain the single S1129 probe, then run `/spec-test-device S1129`
and `/spec-check S1129` after mobile-mcp is enabled.

---

## Rollback Plan

Remove the inventory record through the feature script and revert the S1129 implementation files.
