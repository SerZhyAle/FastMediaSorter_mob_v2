# Phase 03 - Player-menu command and gating

**Strategic spec:** [`../S0444_player-send-email.md`](../S0444_player-send-email.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Surface a "Send to Email" command in the player overflow menu wherever Share/Keep appear, gated by `IsShareTargetEnabledUseCase("email", settings) && ShareTargetAvailabilityResolver.isAvailable(emailTarget)`. This phase owns the consumer-gating that S0452 delegated to target tickets. Follow the existing Telegram (`SEND_TO_TELEGRAM`, S0303) / Keep (`SEND_TEXT_TO_KEEP`, S0431) overflow-command pattern exactly; do NOT touch the layout planner's width math.

---

## Gating seam - how the use-case threads into the manually-wired command panel

The command panel is not Hilt-injected; `CommandPanelController` is constructed by `PlayerActivity` and `CommandPanelAvailabilityUpdater` is built lazily inside the controller. The Telegram/Keep gates (`isTelegramInstalled()`/`isKeepInstalled()`) are computed inside `CommandPanelAvailabilityUpdater` from `binding.root.context.packageManager` and passed into `planner.buildActiveCommands(.., telegramInstalled = .., keepInstalled = ..)`. Email follows the same shape, with two differences the developer must handle:

- The gate needs `AppSettings` (`IsShareTargetEnabledUseCase(targetId, settings)`), which is read asynchronously. `CommandPanelAvailabilityUpdater.update()` already runs `coroutineScope.launch { val settings = settingsRepository.getSettings().first(); .. }` for the favorite/separate-window flags and calls `reTriggerUpdate(state)` when a cached value changed. Add the email-enabled boolean to that same cached-async pattern: compute `emailEnabled = isShareTargetEnabledUseCase("email", settings) && resolver.isAvailable(emailTarget)` in that block, cache it (a `lastKnownEmailCommandVisible` getter/setter pair threaded from `CommandPanelController`, mirroring `getLastKnownFavoriteVisible`/`setLastKnownFavoriteVisible`), and re-trigger the update when it flips. The synchronous layout pass then reads the cached boolean.
- `IsShareTargetEnabledUseCase`, `ShareTargetRegistry`, and `ShareTargetAvailabilityResolver` must reach the updater. Inject all three into `PlayerActivity` (`@Inject lateinit var`, as `PlaybackSettingsFragment` already does), pass them into the `CommandPanelController` constructor, and forward them into the `CommandPanelAvailabilityUpdater` constructor. Resolve the email `ShareTarget` once via `registry.byId("email")`.

Do NOT call `getSettings().first()` on the synchronous layout path - keep the existing "cache async, re-trigger" contract so the hot path stays non-blocking (strategic §3.2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified (add item) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified (enum entry + gate param) | ≤ +15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified (compute + pass gate) | ≤ +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified (constructor deps + dispatch + callback) | ≤ +25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified (callback impl) | ≤ +10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified (inject + pass deps) | ≤ +15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` | Modified (send-to-email action) | ≤ +30 |

> No `res/layout-land/*` counterpart: the command lives in the programmatic overflow `PopupMenu` built from `overflow_menu_player.xml` (one menu, orientation-independent). There is no per-orientation player command-panel layout to mirror for this item.

---

## Steps

### Step 03.1 - Send-to-email action in the share manager

**Files:** `ui/player/helpers/PlayerShareManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `sendCurrentFileToEmail()` to `PlayerShareManager`, mirroring `sendCurrentFileToTelegram()`: read `activity.viewModel.state.value.currentFile`; for a local path build the `FileProvider` uri directly, for a network path (`contains("://")`) `prepareFileForRead` first (same try/catch + `toast_failed_to_prepare_file` fallback). Launch via `SystemShareInvoker.invokeFiles(activity, listOf(uri), mime = "message/rfc822", chooserTitle = activity.getString(R.string.share_to_email))` (no `preferredPackage` - chooser picks the mail client). On `false` show a short failure toast. Do NOT use `ACTION_SENDTO` (strategic ADR-1). If a separate `EmailShareInvoker` was created in Phase 01, call it here instead.

**Verification:**

- `Grep -n "fun sendCurrentFileToEmail"` over `PlayerShareManager.kt` - present.
- `Grep -n "message/rfc822"` over `PlayerShareManager.kt` - present.
- `Grep -n "ACTION_SENDTO"` over `PlayerShareManager.kt` - zero hits.

**Status:** `[ ]` not done

---

### Step 03.2 - `SEND_TO_EMAIL` enum entry + menu item

**Files:** `helpers/CommandPanelLayoutPlanner.kt`, `res/menu/overflow_menu_player.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `CommandPanelLayoutPlanner.PlayerCommand`, add `SEND_TO_EMAIL` next to `SEND_TO_TELEGRAM`: `SEND_TO_EMAIL(36, R.id.menu_send_to_email, false, R.string.share_to_email, <icon>)` - overflow-only (`barCapable = false`), priority just after Telegram (35). For `<icon>`: reuse `R.drawable.ic_share` (Telegram precedent) unless a new envelope vector is added - resolve the INDEX Pre-Implementation icon decision here. In `buildActiveCommands(..)` add a new gate parameter `emailEnabled: Boolean = false` and `if (emailEnabled) add(PlayerCommand.SEND_TO_EMAIL)` in Group 1 next to the Telegram line. Add the matching `<item android:id="@+id/menu_send_to_email" android:icon="@drawable/ic_share" android:title="@string/share_to_email" app:showAsAction="never" />` to `overflow_menu_player.xml` (the `@+id` here is what generates `R.id.menu_send_to_email`).

**Verification:**

- `Grep -n "SEND_TO_EMAIL"` over `CommandPanelLayoutPlanner.kt` - present (enum + buildActiveCommands).
- `Grep -n "emailEnabled"` over `CommandPanelLayoutPlanner.kt` - present as a `buildActiveCommands` param.
- `Grep -n "menu_send_to_email"` over `overflow_menu_player.xml` - present.

**Status:** `[ ]` not done

---

### Step 03.3 - Compute + thread the gate (consumer-gating)

**Files:** `CommandPanelAvailabilityUpdater.kt`, `CommandPanelController.kt`, `PlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement the gating seam described in the "Gating seam" section above. (1) In `PlayerActivity`, add `@Inject lateinit var isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase`, `@Inject lateinit var shareTargetRegistry: ShareTargetRegistry`, `@Inject lateinit var shareTargetAvailabilityResolver: ShareTargetAvailabilityResolver`; pass them where it constructs `CommandPanelController`. (2) `CommandPanelController` takes the three as constructor params and forwards them into the lazily-built `CommandPanelAvailabilityUpdater`, plus a `getLastKnownEmailCommandVisible`/`setLastKnownEmailCommandVisible` cached-flag pair (mirror the favorite pair). (3) In `CommandPanelAvailabilityUpdater.update()`'s existing `coroutineScope.launch { settings = .. }` block, compute `emailEnabled = registry.byId("email")?.let { isShareTargetEnabledUseCase("email", settings) && resolver.isAvailable(it) } ?: false`, cache it, and add it to the favorite/separate-window "changed -> reTriggerUpdate" check. Pass the cached `emailEnabled` into every `planner.buildActiveCommands(..)` call (big-buttons, portrait, landscape-overflow branches) alongside `telegramInstalled`/`keepInstalled`.

**Verification:**

- `Grep -n "isShareTargetEnabledUseCase|shareTargetAvailabilityResolver|shareTargetRegistry"` over `PlayerActivity.kt` - all three injected.
- `Grep -n "emailEnabled = "` over `CommandPanelAvailabilityUpdater.kt` - present (computed in the async settings block).
- `Grep -n "getSettings().first()"` over `CommandPanelAvailabilityUpdater.kt` - still only in the existing async block (no new synchronous settings read on the layout path).
- `Grep -n "emailEnabled =" ` count over `CommandPanelAvailabilityUpdater.kt` matches the number of `buildActiveCommands` call sites (big-buttons + portrait + landscape).

**Status:** `[ ]` not done

---

### Step 03.4 - Dispatch + callback

**Files:** `CommandPanelController.kt`, `callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add `fun onSendToEmailClicked()` to `CommandPanelController.CommandPanelCallback`. In `CommandPanelController.handleOverflowCommand(..)` add `R.id.menu_send_to_email -> callback.onSendToEmailClicked()` next to the Telegram case. Implement `onSendToEmailClicked()` in `PlayerCommandPanelCallbackImpl` as `activity.shareManager.sendCurrentFileToEmail()` (mirror `onSendToTelegramClicked`). No `Timber.d("S0444:` tag - this spec is not entering `BlockNeedUserTest` at authoring time (CLAUDE.md §2; tags are inserted only on the transition into that status).

**Verification:**

- `Grep -n "onSendToEmailClicked"` - present in callback interface, `handleOverflowCommand`, and `PlayerCommandPanelCallbackImpl`.
- `Grep -n "menu_send_to_email ->"` over `CommandPanelController.kt` - present.
- `.\a.ps1 fc` exit 0 (compile + resources; the new `R.id`/`R.string` resolve). Record PASS/FAIL + log path.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` PASS (compile + resources; record log path).
- [ ] `Grep` for `ACTION_SENDTO` across touched files - zero hits.
- [ ] Layout planner width math (`planLayout`/`planBigButtonsLayout`) is unchanged (only `buildActiveCommands` gained a param + one `add`).
- [ ] No new `getSettings().first()` on the synchronous command-availability path.
- [ ] No `Timber.d("S0444:` tag present (spec not in `BlockNeedUserTest`).

---

## Handoff Notes to Next Phase

- Email command is fully wired and gated end-to-end; remaining work is docs + catalog + dev-log.
- On-device proof (toggle on -> command appears -> opens mail compose with attachment; toggle off -> command gone) is the natural `BlockNeedUserTest` candidate the parent may set after build - debug tags get inserted only at that transition, not now.

---

## Rollback Plan

Revert phase commit(s). The enum entry, menu item, callback method, and injected deps are additive; removing them restores the prior menu. Phase 02's registration is independent (settings toggle would remain).
