# Phase 02 - Gate the player command panel

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Make the system-Share command in the main player obey the `system_share` flag. Two show-points exist: the adaptive command set builder adds `PlayerCommand.SHARE` unconditionally, and the landscape layout sets the Share bar button visible directly (bypassing the planner). Both must consult the flag. The mechanism mirrors the existing `telegramInstalled` / `keepInstalled` parameters already threaded into the planner, so this is an additive boolean, not a new architecture.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (target registered, id `"system_share"`).
- [ ] `IsShareTargetEnabledUseCase` is constructor-injectable (plain `@Inject` class - confirmed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 400 |

---

## Steps

### Step 02.1 - Add a `shareEnabled` parameter to the command builder

**Files:** `ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `buildActiveCommands(...)`, add a parameter `shareEnabled: Boolean = true` (mirror the existing `telegramInstalled` / `keepInstalled` defaulted booleans). Change `add(PlayerCommand.SHARE)` to `if (shareEnabled) add(PlayerCommand.SHARE)`. Default `true` keeps every other caller unchanged. Do not touch the SEND_TO_TELEGRAM / Keep lines.

**Verification:**

- `Grep` - `shareEnabled` parameter present in `buildActiveCommands` signature.
- `Grep` - `if (shareEnabled) add(PlayerCommand.SHARE)` present (no remaining unconditional `add(PlayerCommand.SHARE)` in this file).
- Compiles via Step 02.3.

**Status:** `[ ]` not done

---

### Step 02.2 - Compute the flag in the availability updater and feed both show-points

**Files:** `ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> The updater already loads settings asynchronously (`settingsRepository.getSettings().first()`) and already calls `buildActiveCommands(...)` in three places (big-buttons, portrait, landscape) plus sets `binding.btnShareCmd.isVisible = true` directly in `applyLandscapeLayout`. Thread a `system_share` enabled value through:
>
> 1. Inject `IsShareTargetEnabledUseCase` into this class (add a constructor parameter; the host `CommandPanelController` / `PlayerActivity` wiring passes it - see Step 02.2a wiring note below). Do NOT widen the param list unnecessarily: a single use-case param is enough; the registry/resolver are not needed because availability is constant.
> 2. Resolve `shareEnabled = isShareTargetEnabledUseCase("system_share", settings)` from the settings already read in the `effectiveShowCommandPanel` coroutine, and cache it the same way `lastKnownFavoriteVisible` is cached (a `getShareEnabled()/setShareEnabled()` pair driven through the existing `reTriggerUpdate` path), so the synchronous layout methods can read it without re-reading settings.
> 3. Pass `shareEnabled = <cached>` to all three `buildActiveCommands(...)` calls.
> 4. In `applyLandscapeLayout`, replace `binding.btnShareCmd.isVisible = true` with `binding.btnShareCmd.isVisible = <cached shareEnabled>`.
>
> Keep the change minimal and consistent with the existing favorite/separate-window caching pattern; do not introduce a new lifecycle-unsafe collector - reuse the existing settings read.

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `"system_share"` literal referenced.
- `Grep` - no remaining `binding.btnShareCmd.isVisible = true` (literal `true`) in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - all `buildActiveCommands(` calls pass `shareEnabled =`.
- Compiles via Step 02.3.

**Status:** `[ ]` not done

---

### Step 02.2a - Wire the use-case from the host (only if a new ctor param was added)

**Files:** `ui/player/CommandPanelAvailabilityUpdater.kt` construction site (its `CommandPanelController` host and `PlayerActivity` / `PlayerManagerInitializer` injection chain).
**Depends on:** Step 02.2

**Prompt for developer:**

> `CommandPanelAvailabilityUpdater` is manually constructed (30+ params). Find its single construction site and pass the injected `IsShareTargetEnabledUseCase`. The use-case is `@Inject`-constructible, so the nearest `@AndroidEntryPoint` host (`PlayerActivity`) can `@Inject lateinit var` it and forward it. Add the field where the host already injects similar use-cases; forward through the existing builder/initializer call. Do not introduce a Hilt entry point - constructor injection on the host suffices.

**Verification:**

- `Grep` - the construction call of `CommandPanelAvailabilityUpdater` passes the use-case argument.
- `Grep` - host (`PlayerActivity` or its initializer) has an `IsShareTargetEnabledUseCase` injection.
- Compiles via Step 02.3.

**Status:** `[ ]` not done

---

### Step 02.3 - Compile the player gating

**Files:** - (build only)
**Depends on:** Step 02.1, Step 02.2, Step 02.2a

**Prompt for developer:**

> Run `.\a.ps1 fk`. The player command panel now hides the Share bar button + overflow item when `system_share` is OFF, in portrait, big-buttons, and landscape. Other commands unaffected.

**Verification:**

- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` exits 0.
- [ ] No literal `binding.btnShareCmd.isVisible = true` remains in the player updater (both show-points gated).
- [ ] Telegram / Keep / Lens gating unchanged (no edits to their lines).

---

## Handoff Notes to Next Phase

- The `shareEnabled` builder parameter pattern is the template for the browse single-file overflow (Phase 03 reuses `buildExtendedCommands`).
- The caching-through-`reTriggerUpdate` detail is player-specific; browse and standalone read settings on their own existing flows.

---

## Rollback Plan

Revert the two-file change. Builder param defaults to `true`, so reverting the updater alone restores unconditional Share. No data migration.
