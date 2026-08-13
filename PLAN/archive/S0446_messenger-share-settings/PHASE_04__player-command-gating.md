# Phase 04 - Player command-panel gating

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02 (targets/ids), Phase 03 (send methods the callbacks invoke)
**Blocks:** -
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Make the three messenger commands appear in the player command panel (overflow) gated by "flag enabled AND client installed", and route their clicks to the Phase 03 send methods. Migrate the existing Telegram command from installed-only (`isTelegramInstalled()`) to the same `IsShareTargetEnabledUseCase("telegram") && resolver.isAvailable(target)` gate. Add WhatsApp/Instagram as new overflow-only commands.

This is the invasive seam S0452 Phase 04 deliberately deferred: `CommandPanelAvailabilityUpdater` and `CommandPanelController` are manually-wired hot classes. The design threads the gating dependencies (use-case + registry + resolver) from `CommandPanelController` (which the host constructs) into the updater, and computes the three enabled-AND-available booleans inside the updater's existing async settings block (it already reads `settingsRepository.getSettings().first()`), so the planner contract stays "feed it booleans".

---

## Architecture / wiring decision

- `CommandPanelLayoutPlanner.buildActiveCommands(...)` currently takes `telegramInstalled: Boolean`. Replace/extend with three per-messenger booleans: `telegramEnabled`, `whatsappEnabled`, `instagramEnabled` (each already = flag AND available; the planner stays dumb). Keep `keepInstalled` unchanged (Keep is S0443's concern).
- `CommandPanelAvailabilityUpdater` gains three injected collaborators: `IsShareTargetEnabledUseCase`, `ShareTargetRegistry`, `ShareTargetAvailabilityResolver`. It computes each messenger boolean as `useCase(id, settings) && registry.byId(id)?.let(resolver::isAvailable) == true`, using the `settings` it already fetches in the async block. The current `isTelegramInstalled()` private helper is removed in favor of this.
- `CommandPanelController` is the construction point: it already receives `settingsRepository` and is created by the host. Add the three collaborators to its constructor and forward them into the `CommandPanelAvailabilityUpdater` it builds in its `by lazy` block. The host (`PlayerActivity`) supplies them - they are `@Singleton` and Hilt-available; if the host already field-injects (it constructs many Hilt deps), inject there and pass in. Do not widen the planner's responsibility.
- New overflow-only `PlayerCommand`s `SEND_TO_WHATSAPP` / `SEND_TO_INSTAGRAM` mirror `SEND_TO_TELEGRAM` (priority near 35, `barCapable = false`, `menuItemId` = new ids, `titleResId` = the Phase 01 strings, icon `ic_share`). Add matching `menu_send_to_whatsapp` / `menu_send_to_instagram` ids.

---

## Prerequisites

- [ ] Phase 02 ✅ Done; Phase 03 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | + 2 `<item>` ids |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 480 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 200 |

---

## Steps

### Step 04.1 - Declare new commands + menu ids in the planner

**Files:** `CommandPanelLayoutPlanner.kt`, `res/menu/overflow_menu_player.xml`
**Depends on:** start of phase

**Prompt for developer:**

> Add `<item android:id="@+id/menu_send_to_whatsapp" .../>` and `menu_send_to_instagram` to `res/menu/overflow_menu_player.xml` (where `menu_send_to_telegram` is declared). In `PlayerCommand`, add `SEND_TO_WHATSAPP` and `SEND_TO_INSTAGRAM` mirroring `SEND_TO_TELEGRAM` (overflow-only, `barCapable = false`, priority adjacent to 35 e.g. 36/37, `titleResId = R.string.share_to_whatsapp` / `share_to_instagram`, `iconRes = R.drawable.ic_share`). In `buildActiveCommands`, replace the `telegramInstalled: Boolean = false` parameter with `telegramEnabled`, `whatsappEnabled`, `instagramEnabled` (all `Boolean = false`); gate `SEND_TO_TELEGRAM` on `telegramEnabled`, and add `if (whatsappEnabled) add(PlayerCommand.SEND_TO_WHATSAPP)` / `if (instagramEnabled) add(PlayerCommand.SEND_TO_INSTAGRAM)`. Leave `keepInstalled` as-is.

**Verification:**

- `Grep` - `SEND_TO_WHATSAPP` and `SEND_TO_INSTAGRAM` present in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `whatsappEnabled` and `instagramEnabled` present (params threaded).
- `Grep` - `menu_send_to_whatsapp` / `menu_send_to_instagram` present in `res/menu/overflow_menu_player.xml`.
- `.\a.ps1 fk` - compiles (callers updated in 04.2).

**Status:** `[ ]` not done

---

### Step 04.2 - Compute enabled-AND-available in the updater

**Files:** `CommandPanelAvailabilityUpdater.kt`, `CommandPanelController.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Thread `IsShareTargetEnabledUseCase`, `ShareTargetRegistry`, `ShareTargetAvailabilityResolver` from `CommandPanelController`'s constructor into the `CommandPanelAvailabilityUpdater` it builds. In the updater, define a private helper `messengerEnabled(id, settings) = useCase(id, settings) && registry.byId(id)?.let(resolver::isAvailable) == true`. The updater already fetches `settings` in the `effectiveShowCommandPanel` async block - compute the three booleans there and pass them into every `buildActiveCommands(...)` call (big-buttons, portrait, landscape-overflow). Remove the old `isTelegramInstalled()` helper. Because the booleans require `settings`, the `buildActiveCommands` calls that currently run synchronously outside the async block must read cached values: cache the three messenger booleans as fields updated from the async block and `reTriggerUpdate(state)` when they change (same pattern already used for `favorite`/`separateWindow`). Do not block the main thread on settings.

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `isAvailable` referenced (resolver consulted).
- `Grep` - `isTelegramInstalled` no longer present in the updater (migrated).
- `Grep` - the three new fields/params reach `buildActiveCommands` (`whatsappEnabled`/`instagramEnabled` passed at call sites).
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 04.3 - Callbacks + overflow dispatch for the two new commands

**Files:** `CommandPanelController.kt`, `PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 04.1, Phase 03

**Prompt for developer:**

> Add `onSendToWhatsappClicked()` and `onSendToInstagramClicked()` to `CommandPanelController.CommandPanelCallback`. In `handleOverflowCommand`, map `R.id.menu_send_to_whatsapp -> callback.onSendToWhatsappClicked()` and `R.id.menu_send_to_instagram -> callback.onSendToInstagramClicked()` (next to the existing `menu_send_to_telegram` branch). In `PlayerCommandPanelCallbackImpl`, implement the two new methods routing to `activity.shareManager.sendCurrentFileToWhatsapp()` / `sendCurrentFileToInstagram()` (mirroring `onSendToTelegramClicked`).

**Verification:**

- `Grep` - `onSendToWhatsappClicked` and `onSendToInstagramClicked` present in both the interface (`CommandPanelController.kt`) and `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` - `menu_send_to_whatsapp` / `menu_send_to_instagram` mapped in `handleOverflowCommand`.
- `Grep` - `sendCurrentFileToWhatsapp` / `sendCurrentFileToInstagram` invoked in `PlayerCommandPanelCallbackImpl.kt`.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 04.4 - Full code+resource build

**Files:** - (no new edits)
**Depends on:** Step 04.1, Step 04.2, Step 04.3

**Prompt for developer:**

> Run `.\a.ps1 fc`. The player overflow now shows Telegram/WhatsApp/Instagram only when each flag is on and the client installed, and clicks reach the Phase 03 send. Verify no neuroslop regressions in touched files (`scripts/quality/assert-neuroslop.ps1` via post-change).

**Verification:**

- `.\a.ps1 fc` - PASS (record exit code).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` passes.
- [ ] Telegram migrated to the registry gate (no `isTelegramInstalled()` left in the updater).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Player surface fully gated and wired. Browse surface (Phase 05) follows the same gating rule with its own callbacks.

---

## Rollback Plan

Revert phase commit(s): player gating reverts to Telegram-installed-only and WhatsApp/Instagram commands disappear. The send methods (Phase 03) and registrations (Phase 02) remain but become unreachable from the player; browse still owns its own gate.
