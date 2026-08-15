# PHASE_06 - Debug tags, build gate, device-test handoff

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending
**Depends on:** PHASE_05

## Goal

Insert the on-device verification probes, run the final build that validates code + tags in one pass, hand off to device test.

## Steps

### Step 6.1 - Insert debug verification tags

Insert one `Timber.d("S0374: <entry-point>")` at each changed flow entry (CLAUDE.md "Debug Verification Tags"), as the FINAL code edits before the build:
- `BrowseCommandOverflowManager.recompute()` entry: `Timber.d("S0374: overflow recompute - available width measured, priority partition applied")`.
- `ResourceOpsMenuManager.showMenu()` overflow-item build block: `Timber.d("S0374: overflow menu surfaced overflowed commands")`.

These tags exist only while the spec is `BlockNeedUserTest`; `/spec-check` removes them on the transition out.

### Step 6.2 - Final build

`.\a.ps1 dq` → standardDebug assemble. One build validates feature code + tags.

**Verification:**
- `Grep` `Timber.d("S0374:` across `app_v2/src/main` → expected: 2 | actual: record.
- `.\a.ps1 dq` → expected: BUILD SUCCESSFUL | actual: record.

### Step 6.3 - Status handoff

- Set status `BlockNeedUserTest` (via `update.ps1 -Status BlockNeedUserTest`).
- Device-test checklist (portrait + landscape, narrow phone):
  - No clipped / off-screen command buttons; no horizontal scroll.
  - Overflowed commands (mic, slideshow, etc.) appear in the "⋮" menu and work.
  - Rotating re-partitions correctly (landscape labels widen buttons → more overflow).
  - Selecting a resource that shows/hides mic/create-* re-partitions without leftover ghosts.
  - D-pad / keyboard focus traverses only visible bar buttons; overflowed commands reachable via "⋮".

## Phase Done Criteria

- [ ] Exactly 2 `Timber.d("S0374:` tags at the flow entries.
- [ ] `standardDebug` assembles green with tags in place.
- [ ] Status `BlockNeedUserTest`; device-test checklist recorded in the spec Manual block.
- [ ] Functionality log entry (CHANGE) appended on `Implemented`.
