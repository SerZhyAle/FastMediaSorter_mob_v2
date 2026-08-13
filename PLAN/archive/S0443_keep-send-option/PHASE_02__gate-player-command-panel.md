# Phase 02 - Gate the player command panel

**Strategic spec:** [`../S0443_keep-send-option.md`](../S0443_keep-send-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03 (establishes the threading pattern reused there)
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Make the player command panel's "Send to Keep" command (`PlayerCommand.SEND_TEXT_TO_KEEP`, text files only) honor the `keep` toggle in addition to the existing installed-check. This phase also establishes the host-activity injection pattern (`IsShareTargetEnabledUseCase` reached from the manually-wired updater) that Phase 03 reuses for the sibling surfaces.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (Keep target registered; `KEEP_TARGET_ID` available).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | (no hard cap; +ctor wiring only) |

---

## Steps

### Step 02.1 - Thread `IsShareTargetEnabledUseCase` to the availability updater

**Files:** `ui/player/PlayerManagerInitializer.kt`, `ui/player/CommandPanelController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The Keep command visibility is computed in `CommandPanelAvailabilityUpdater` (built lazily inside `CommandPanelController`). Thread the effective-flag seam down to it. First confirm the host `PlayerActivity` (and the standalone player host) is `@AndroidEntryPoint` and can `@Inject lateinit var isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase` (the use-case's deps - `ShareTargetRegistry`, `ShareTargetAvailabilityResolver` - are `@Singleton`, so injection is free). Pass the use-case from the host through `CommandPanelController`'s constructor (`PlayerManagerInitializer` builds it at `activity.commandPanelController = CommandPanelController(...)`, reading `activity.<dep>` fields) and on into the `CommandPanelAvailabilityUpdater(...)` lazy builder, as a new constructor parameter. Do not construct the use-case manually - inject it on the host and pass the reference. Document the chosen thread (host -> controller -> updater) in a one-line code comment explaining WHY a manual pass is used (these are non-Hilt view managers).

**Verification:**

- `Grep` - `isShareTargetEnabledUseCase` referenced in `CommandPanelController.kt`.
- `Grep` - `IsShareTargetEnabledUseCase` passed into the `CommandPanelAvailabilityUpdater(` builder in `CommandPanelController.kt`.
- `Grep` - the host activity (`PlayerActivity` / standalone player) declares `@Inject` of `IsShareTargetEnabledUseCase`.
- Project compiles (`.\a.ps1 fk`).

**Status:** `[ ]` not done

---

### Step 02.2 - Combine `keep` flag with the installed-check

**Files:** `ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 02.1, Phase 01

**Prompt for developer:**

> In `CommandPanelAvailabilityUpdater`, the value `keepInstalled = isKeepInstalled()` is passed to `planner.buildActiveCommands(...)` at three call sites (big-buttons, portrait, landscape-overflow). Change the value fed into `keepInstalled = ...` to the combined predicate `isKeepEnabled() && isKeepInstalled()`, where `isKeepEnabled()` reads the current `AppSettings` and calls `isShareTargetEnabledUseCase(KEEP_TARGET_ID, settings)`. The updater already loads settings asynchronously (`settingsRepository.getSettings().first()` inside the `effectiveShowCommandPanel` coroutine) - reuse a cached current-settings value the same way other settings-derived visibility (favorite, separate-window) is cached and re-triggered via `reTriggerUpdate(state)`, so the gate updates reactively when the toggle changes. Keep `isKeepInstalled()` (the `GoogleKeepAvailabilityChecker` probe) for the launch-time guarantee; only visibility gains the extra flag condition. Reference the id via `KEEP_TARGET_ID`, not a string literal. Do not change the planner's `buildActiveCommands` signature - only the boolean fed to its `keepInstalled` parameter.

**Verification:**

- `Grep` - `isShareTargetEnabledUseCase` referenced in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `KEEP_TARGET_ID` referenced (no inline `"keep"` literal added).
- `Grep` - `keepInstalled =` still present at the `buildActiveCommands(` call sites (planner contract unchanged).
- `Grep -n "Log\.d\("` - zero hits in the file.
- Project compiles (`.\a.ps1 fk`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` (touches resources/manifest indirectly via the player) or `assembleStandardDebug`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] No new hardcoded `"keep"` literal outside `KEEP_TARGET_ID`.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- The host-injection pattern (`@Inject IsShareTargetEnabledUseCase` on the `@AndroidEntryPoint` host, passed into the manually-wired manager) is the template for Phase 03's editor / draw / standalone surfaces.
- The combined predicate `keepEnabled && keepInstalled` is the canonical gate shape - reuse it verbatim per surface.

---

## Rollback Plan

Revert phase commit(s). The `keepInstalled` value reverts to installed-only; the Keep command shows whenever Keep is installed regardless of the toggle. No data migration.
