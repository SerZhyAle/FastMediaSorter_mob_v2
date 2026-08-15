# Phase 04 - Command Panel Parity

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped - spun off to a separate ticket (2026-06-09)
**Depends on:** Phase 01, Phase 03
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

> **Skipped (2026-06-09).** Research found standalone hosts do NOT use the in-app panel engine (`CommandPanelController`/`CommandPanelAvailabilityUpdater`) - each has its own compact panel. Only crop/draw/compress/rotate have reusable generic helpers; the rest (OCR/Lens/Print/Translate/SaveFrame/Sleep/Lyrics) are bound to `ActivityPlayerUnifiedBinding`/`PlayerActivity` and need a per-helper generic-seam refactor. Owner-requested "priority bar↔overflow" placement also needs a planner generalization. Owner decision: ship S0389 with folder paging + Open-in-FMS; move panel parity (Group A first) to a dedicated follow-up ticket. The original steps below are kept for reference but are NOT executed under S0389.

---

## Objective

Show, in standalone mode, every in-app command-panel action that is applicable to a single external file, driven by the host-capability seam and the per-type availability logic - with real implementations replacing the current empty standalone callbacks. Owner scope: all applicable buttons at once.

---

## Prerequisites

- [ ] Phase 01 and Phase 03 are ✅ Done.
- [ ] `supportsTypeSpecificActions` capability available from Phase 01.
- [ ] Strategic §6.1 reviewed: owner wants all applicable type-specific buttons; only buttons that need list/folder context beyond Phase 03 paging are deferred to separate tickets.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ 640 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 480 |

> `CommandPanelController.kt` (888 LOC) and `PlayerActivity.kt` (1098 LOC) must NOT be edited in this phase; if availability logic needs new branching, keep it inside `CommandPanelAvailabilityUpdater.kt`. If that file would exceed 500 LOC after edits, back it up to `temp/` first; if it would exceed the maintainability ceiling, extract a helper rather than inflating it.

---

## Steps

### Step 04.1 - Gate availability by host capability, not host identity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Make per-type button availability read `supportsTypeSpecificActions` and `supportsFolderPaging` from the host capabilities rather than assuming an in-app host. Single-file actions (rotate, crop, compress, print, OCR, translate, lens, lyrics, info, favorite, share, rename, delete, cast) become available to standalone when the type matches and the action does not require resource-list context beyond folder paging. List-only actions remain gated by `supportsFolderPaging`. No `if standalone` branches.

**Verification:**

- `Grep` - `supportsTypeSpecificActions` referenced in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - no new `isStandalone` / `if (standalone` literal branch added in the file.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 04.2 - Replace empty standalone action callbacks with real implementations

**Files:** the four `*StandaloneActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> For each newly-shown type-specific button, wire the same action the in-app host invokes for a single file (delegate to the shared handlers; do not reimplement). Remove the empty placeholder callbacks currently stubbed in the document/text hosts. A button must not be shown unless its action is fully wired - hide buttons whose action genuinely cannot work on a single external file in this flavor/type. No broad/empty `catch` blocks: handle failures with a user-visible result or a correctly-leveled Timber log.

**Verification:**

- `Grep` - the prior empty-lambda placeholders (e.g. `{ }` OCR/translate/settings callbacks) are gone from `DocumentStandaloneActivity.kt` and `TextStandaloneActivity.kt`.
- `Grep` - each activity routes its type-specific buttons to a shared handler call.
- `Grep -n "catch (.*: Exception)" ` edited blocks include a log or recovery, not an empty body.

**Status:** `[ ]` not done

---

### Step 04.3 - Flavor/type gating for absent capabilities

**Files:** the four `*StandaloneActivity.kt`, `CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure buttons for media types unsupported in the running flavor are hidden via the existing `SUPPORT_*` abstractions (e.g. photos flavor: no audio/docs actions). Do not add `BuildConfig.IS_*` flavor guards in `src/main`. Use the existing media-type support gates already consumed by the panel.

**Verification:**

- `Grep` - no `BuildConfig.IS_` literal added in any `src/main` file edited by this step.
- `Grep` - media-type support is checked through the existing support gate, not a flavor-name check.
- Run `/build` for the `photos` flavor target to confirm gating compiles (`assemblePhotosDebug`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `scripts/quality/assert-neuroslop.ps1` (via `post-change.ps1`) passes for edited files.

---

## Handoff Notes to Next Phase

Standalone command panel now reaches type-specific parity. Open-in-FMS routing (Phases 05–06) is independent of this phase and may proceed in parallel where source files do not overlap.

---

## Rollback Plan

Revert phase commit(s). Buttons revert to the S0380 minimal set; no data or schema change.
