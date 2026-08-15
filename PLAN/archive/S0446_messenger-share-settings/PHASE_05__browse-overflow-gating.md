# Phase 05 - Browse overflow gating

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02 (targets/ids), Phase 03 (send methods)
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Gate the three messenger commands in the browse single-file overflow menu by "flag enabled AND client installed", and route WhatsApp/Instagram clicks to the Phase 03 browse send methods. Migrate the existing Telegram item from installed-only to the same registry gate.

`BrowseFileOverflowMenuManager` is `@ActivityScoped @Inject` and already receives `appSettings` in `showFor(...)` and builds messenger items in `buildExtendedCommands(...)`, so the registry gate injects cleanly (no hot-path threading like the player).

---

## Architecture / wiring decision

- Inject `IsShareTargetEnabledUseCase` + `ShareTargetRegistry` + `ShareTargetAvailabilityResolver` into `BrowseFileOverflowMenuManager`'s constructor (already `@Inject`).
- In `buildExtendedCommands`, replace the inline `TelegramShareTargets.firstInstalledPackage(...) != null` gate with `messengerEnabled("telegram", appSettings)`, and add the same for `"whatsapp"` / `"instagram"` adding `SEND_TO_WHATSAPP` / `SEND_TO_INSTAGRAM` (the enum entries from Phase 04). `messengerEnabled(id, s) = useCase(id, s) && registry.byId(id)?.let(resolver::isAvailable) == true`.
- Add `onSendToWhatsapp` / `onSendToInstagram` callback params to `showFor(...)`, dispatch them in the `when (cmd)` mapping (next to `SEND_TO_TELEGRAM`).
- Wire the new callbacks in `BrowseManagerInitializer` to `fileOperationsManager.sendSelectedFilesToWhatsapp(listOf(f), resource)` / `...Instagram(...)`, mirroring the existing `onSendToTelegram` wiring; add the two pass-through methods on `BrowseFileOperationsManager` if it fronts `BrowseShareOperationsHelper`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done; Phase 03 ✅ Done; Phase 04 ✅ Done (enum entries exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ +20 |

---

## Steps

### Step 05.1 - Gate the three messenger items via the registry

**Files:** `ui/browse/helpers/BrowseFileOverflowMenuManager.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Inject `IsShareTargetEnabledUseCase`, `ShareTargetRegistry`, `ShareTargetAvailabilityResolver` into the constructor. Add a private `messengerEnabled(id: String, s: AppSettings): Boolean = isShareTargetEnabledUseCase(id, s) && shareTargetRegistry.byId(id)?.let(resolver::isAvailable) == true`. In `buildExtendedCommands`, replace the Telegram inline `firstInstalledPackage` gate with `if (messengerEnabled("telegram", appSettings)) add(PlayerCommand.SEND_TO_TELEGRAM)`, and add `if (messengerEnabled("whatsapp", appSettings)) add(PlayerCommand.SEND_TO_WHATSAPP)` / `instagram` likewise. Remove the now-unused `TelegramShareTargets` import if nothing else uses it.

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `BrowseFileOverflowMenuManager.kt`.
- `Grep` - `SEND_TO_WHATSAPP` and `SEND_TO_INSTAGRAM` added in `buildExtendedCommands`.
- `Grep` - `firstInstalledPackage` no longer present in this file (migrated to registry gate).
- `.\a.ps1 fk` - compiles (callbacks added in 05.2).

**Status:** `[ ]` not done

---

### Step 05.2 - New callbacks in `showFor` + dispatch

**Files:** `ui/browse/helpers/BrowseFileOverflowMenuManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `onSendToWhatsapp: ((MediaFile) -> Unit)? = null` and `onSendToInstagram: ((MediaFile) -> Unit)? = null` params to `showFor(...)` next to `onSendToTelegram`. In the `when (cmd)` action mapping, add `PlayerCommand.SEND_TO_WHATSAPP -> { { onSendToWhatsapp?.invoke(file) } }` and the Instagram branch.

**Verification:**

- `Grep` - `onSendToWhatsapp` and `onSendToInstagram` present as `showFor` params and in the `when (cmd)` mapping.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 05.3 - Wire callbacks to the browse send methods

**Files:** `ui/browse/managers/BrowseFileOperationsManager.kt`, `ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 05.2, Phase 03

**Prompt for developer:**

> If `BrowseFileOperationsManager` fronts `BrowseShareOperationsHelper` (it exposes `sendSelectedFilesToTelegram`), add pass-throughs `sendSelectedFilesToWhatsapp(files, resource)` / `...Instagram(...)` delegating to the Phase 03 helper methods. In `BrowseManagerInitializer`, add `onSendToWhatsapp = { f -> viewModel.state.value.resource?.let { fileOperationsManager.sendSelectedFilesToWhatsapp(listOf(f), it) } }` and the Instagram equivalent to the `showFor` call, mirroring `onSendToTelegram`.

**Verification:**

- `Grep` - `sendSelectedFilesToWhatsapp` / `sendSelectedFilesToInstagram` referenced in `BrowseManagerInitializer.kt`.
- `Grep` - `onSendToWhatsapp` / `onSendToInstagram` passed in the `showFor(...)` call.
- `.\a.ps1 fc` - code + resources build passes (record exit code).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` passes.
- [ ] Telegram migrated to the registry gate in browse (no `firstInstalledPackage` left in the overflow manager).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Both surfaces (player + browse) gated and wired end to end. Phase 06 syncs docs/catalog and prepares device verification.

---

## Rollback Plan

Revert phase commit(s): browse messenger items revert to Telegram-installed-only; WhatsApp/Instagram disappear from the overflow. Send methods and registrations remain.
