# Phase 04 - Gating existing surfaces

**Strategic spec:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped - delegated to target tickets (owner decision 2026-06-16)
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 0 / 3 (skipped)
**Started:** -
**Completed:** -

---

## Scope decision (2026-06-16)

Owner chose to delegate consumer-gating to the target tickets rather than wire it speculatively inside the foundation. Reasons: `CommandPanelAvailabilityUpdater` / `CommandPanelController` are manually-wired hot classes (30+ ctor params) and clean injection of `IsShareTargetEnabledUseCase` per ADR-2 would thread a new dependency through `PlayerActivity -> CommandPanelController -> CommandPanelAvailabilityUpdater` (+ Browse); Telegram/messenger visibility is owned by S0446.

Therefore this phase is **not implemented in S0452**. Each target ticket (S0443 Keep, S0444 Email, S0445 system Share, S0446 Telegram/messengers) will:

- register its `ShareTarget` via `@Provides @IntoSet` / `@Binds @IntoSet` (seam from Phase 01), and
- gate its command's visibility in its own surfaces via `IsShareTargetEnabledUseCase(id) && ShareTargetAvailabilityResolver.isAvailable(target)`.

The foundation ships with an empty registry; the settings group is hidden while empty (added to Phase 03).

## Objective (original - superseded)

Seed the registry with the existing Telegram target as proof-of-mechanism, and route the two primary visibility gates (player command panel + browse overflow) through `enabled AND available`, so the new setting actually controls command visibility end to end.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | Modified | ≤ 280 |

---

## Steps

### Step 04.1 - Seed the Telegram target via multibinding

**Files:** `core/share/di/ShareTargetModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Contribute a `ShareTarget` for Telegram via `@Provides @IntoSet`: `id = "telegram"`, `titleRes` = existing Telegram send label, `availability = PACKAGE_INSTALLED` over `TelegramShareTargets` package list, `defaultEnabled = ON_IF_INTERNET` (matches S0445/S0446 intent; final messenger default is owned by S0446). This makes the Telegram toggle appear in the settings group and become the gating proof. Do not duplicate the Telegram package list - reference `TelegramShareTargets`.

**Verification:**

- `Grep` - `@IntoSet` present in `ShareTargetModule.kt`.
- `Grep` - `"telegram"` id literal present.
- `Grep` - `TelegramShareTargets` referenced (no duplicated package list).

**Status:** `[ ]` not done

---

### Step 04.2 - Gate the player command panel through the registry

**Files:** `ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 04.1, Phase 02 (`IsShareTargetEnabledUseCase`)

**Prompt for developer:**

> Where the Telegram command visibility is currently decided by `isTelegramInstalled()`, change the gate to `IsShareTargetEnabledUseCase("telegram") && availabilityResolver.isAvailable(telegramTarget)`. Inject the use-case + resolver + registry. Keep the existing layout-planner contract (`buildActiveCommands(..., telegramInstalled = ...)`) but feed it the combined enabled-AND-available value. Do not regress Keep/Lens gating - leave those paths unchanged in this phase (they migrate when S0443 registers Keep).

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `isAvailable` referenced (resolver consulted).
- Project compiles.

**Status:** `[ ]` not done

---

### Step 04.3 - Gate the browse overflow menu through the registry

**Files:** `ui/browse/helpers/BrowseFileOverflowMenuManager.kt`
**Depends on:** Step 04.1, Phase 02

**Prompt for developer:**

> Where the Telegram item visibility is decided by `TelegramShareTargets.firstInstalledPackage()` at menu construction, additionally require `IsShareTargetEnabledUseCase("telegram")`. Inject the use-case (constructor or via the manager's existing dependency seam). The installed-package check stays for actual launch; visibility now also honors the setting.

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `BrowseFileOverflowMenuManager.kt`.
- Project compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- The mechanism is proven end to end with Telegram: toggle in settings -> visibility in player + browse. S0443/S0444/S0445/S0446 register their targets the same way (`@IntoSet ShareTarget`) and add their gate point if it is a new surface.

---

## Rollback Plan

Revert phase commit(s). Gating reverts to installed-only checks; no data migration.
