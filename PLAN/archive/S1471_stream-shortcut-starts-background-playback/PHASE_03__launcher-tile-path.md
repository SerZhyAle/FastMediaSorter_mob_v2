# Phase 03 - Launcher tile path

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Route the app's own launcher desktop stream tile through the same trampoline, so both surfaces the owner named behave identically.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 260 |

> No flavor source set is involved: this use case lives in `src/main` and `SUPPORT_LAUNCHER` gates the surface at runtime, not at compile time.

---

## Steps

### Step 03.1 - Send `LauncherCellCommand.Stream` to the trampoline

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `launchStream`, replace `startIntent(StreamsActivity.createPlayIntent(context, source.url))` with `startIntent(StreamPlayLaunchActivity.createIntent(context, source.url))`. Add a one-line comment naming S1471 and stating that this covers both the desktop tile and the Streams gadget row, which share this command. Remove the `StreamsActivity` import if nothing else in the file uses it.

**Why:**

Strategic §3.1 records the owner's own wording in §0, which names the launcher surface alongside the system home screen, so both must get the screen-less behaviour.

**Verification:**

- `Grep` - `StreamPlayLaunchActivity.createIntent` present in `ExecuteLauncherCommandUseCase.kt`.
- `Grep` - `StreamsActivity.createPlayIntent` returns zero hits in `ExecuteLauncherCommandUseCase.kt`.
- `Grep` - `StreamsActivity.createPlayIntent` still matches at least one call site elsewhere in `app_v2/src/main` (the main-window streams panel, which must keep opening the screen).
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Result (2026-08-09):** `ExecuteLauncherCommandUseCase.kt:106` now builds `StreamPlayLaunchActivity.createIntent`; the `StreamsActivity` import was dropped because nothing else in the file used it. `StreamsActivity.createPlayIntent` = 0 hits here, still 4 hits elsewhere in `src/main` (`MainActivity` x2, `BrowseEventHandler`, `MainResumePlaybackHelper`) - the in-app screen navigation strategic §3.1 preserves. `.\a.ps1 fk` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, plus the full `standard debug` build run at Phase 04 close.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` - batched with Phase 04/05 through `post-change.ps1` in Step 05.3.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Both live entry points now build their intent through `StreamPlayLaunchActivity.createIntent`. Only shortcuts pinned before this change still carry the old intent, which Phase 04 repairs.

---

## Rollback Plan

Revert phase commit(s) - a one-line intent-factory swap with no data migration and no persisted state.
