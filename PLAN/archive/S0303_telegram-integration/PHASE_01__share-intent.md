# Phase 01 - Send to Telegram via share-intent

**Strategic spec:** [`../S0303_telegram-integration.md`](../S0303_telegram-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Add a "Send to Telegram" action to the existing share surfaces in Browse multi-select and in the player, routing the selected file(s) to an installed Telegram client through the existing `ACTION_SEND` path, with a chooser fallback when no Telegram client is installed. Available in `standard` and all inheriting flavors. No content download, no network code.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Telegram client package detection list agreed (official + Telegram X variants).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/TelegramShareTargets.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/SystemShareInvoker.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShareOperationsHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | - |

> **Files Touched expanded 2026-05-30 (spec-dev):** the original 2-code-file list under-modeled the wiring. A dedicated "Send to Telegram" action needs the player overflow-command infrastructure and the Browse per-file overflow menu. Owner UI decision (2026-05-30): **light surface** - player overflow-only menu item (no command-bar button → no layout/landscape change) + Browse per-file overflow menu (`ListPopupWindow`, no layout-land). **Scope decision:** the Browse multi-select toolbar button is intentionally NOT added (it is a fixed toolbar surface requiring `res/layout-land` parity that this plan never modeled); multi-select coverage is deferred. Icon reuses `ic_share` (no Telegram-specific asset exists). Visibility gate: the entry appears only when a Telegram client is installed (`TelegramShareTargets.firstInstalledPackage(packageManager) != null`); all media types.
>
> "Send to Telegram" is available in every flavor (no flavor differentiation), so it lives in `src/main` - this is not a `BuildConfig`-gated path and Rule 15 does not apply. The share-target abstraction for the Bot API path was scoped to Phase 03, now ⏭️ Skipped.

---

## Steps

### Step 01.1 - Add Telegram client package catalogue

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/share/TelegramShareTargets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an object holding the ordered list of known Telegram client package ids (official `org.telegram.messenger`, Telegram X `org.thunderdog.challegram`, and the web/plus forks) and a helper that returns the first installed package id via `PackageManager`, or `null` when none is installed. No UI here.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/share/TelegramShareTargets.kt` exists.
- `Grep` - `object TelegramShareTargets` matches exactly once.
- `Grep` - `org.telegram.messenger` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. expected `object TelegramShareTargets`:1 | actual:1; `org.telegram.messenger` present | actual:3; `Log.d(` 0 | actual:0. Files: core/share/TelegramShareTargets.kt (+47 LOC, new). Dev log recorded.

---

### Step 01.2 - Support multi-file image/binary payload + Telegram targeting in the invoker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/share/SystemShareInvoker.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend `SystemShareInvoker` so a caller can send one or more file URIs targeted at a resolved package id. When the preferred package is unavailable, fall back to the system chooser (do not silently fail). Reuse the existing `FLAG_GRANT_READ_URI_PERMISSION` handling. Keep `Timber` only.

**Verification:**

- `Grep` - `preferredPackage` present in the file.
- `Grep` - `createChooser` present (fallback path retained).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. expected `preferredPackage`>=1 | actual:10; `createChooser`>=1 | actual:2; `Log.d(` 0 | actual:0. Added `invokeFiles(...)` multi-URI targeted send with chooser fallback + `buildFilesIntent`. Files: core/share/SystemShareInvoker.kt (+~50 LOC). Dev log recorded.

---

### Step 01.3 - Wire "Send to Telegram" into Browse multi-select and player share

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShareOperationsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a "Send to Telegram" entry to the existing share action surface in Browse multi-select and in the player. Resolve the installed Telegram package via `TelegramShareTargets`; if present, send the prepared file URI(s) directly to it; if absent, fall back to the generic chooser. Remote files reuse the existing cache-staging path before sharing. The new control follows the surrounding visual design and is reachable by keyboard, D-pad, and mouse in the same focus order as the existing share action (CLAUDE.md Rule 17). Surface progress/result via the existing toast/snackbar style.

**Verification:**

- `Grep` - `TelegramShareTargets` referenced in both modified files.
- `Grep` - the new action string key (`share_to_telegram`) referenced in both UI files.
- `/build` - `standardDebug` assembles.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. `TelegramShareTargets` in both touched files (PlayerShareManager + BrowseShareOperationsHelper) | actual:2/2; `share_to_telegram` key referenced in both | actual:2/2; standardDebug assembles | actual: BUILD SUCCESSFUL (v2.60.5301.522, log build_debug_20260530_153522.log). Light surface implemented: player overflow command `SEND_TO_TELEGRAM` (no bar button/layout) + Browse per-file overflow menu entry; both gated on Telegram-installed. Files: TelegramShareTargets is reused; edits in CommandPanelLayoutPlanner, CommandPanelAvailabilityUpdater, CommandPanelController, PlayerCommandPanelCallbackImpl, PlayerShareManager, overflow_menu_player.xml, BrowseFileOverflowMenuManager, BrowseFileOperationsManager, BrowseManagerInitializer, BrowseShareOperationsHelper. Multi-select toolbar button intentionally deferred (layout-land scope). Dev log recorded.

---

### Step 01.4 - Add trilingual strings for the Telegram send action

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add the action label and any toast/result strings (`share_to_telegram`, plus failure/no-client messages) to all three string files. Author per `docs/COMMUNICATION_POLICY.md` §2 (message formula) and pass the §6 tone checklist. Use `..` not `...`; use `ё`/`Ё` in Russian where correct.

**Verification:**

- `Grep` - `share_to_telegram` present in `values/strings.xml`.
- `Grep` - `share_to_telegram` present in `values-ru/strings.xml`.
- `Grep` - `share_to_telegram` present in `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "share_to_telegram"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification PASS. `share_to_telegram` + `share_to_telegram_failed` present in values/values-ru/values-uk (check_strings_localized.ps1 exit 0). Tone: action label is a plain verb phrase; failure message is factual with a concrete next step ("Try the standard share instead.") - passes COMMUNICATION_POLICY §6. `..`/`«»`/`ё` honoured in RU/UK. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml (+2 keys each). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL (v2.60.5301.522).
- [x] `Grep` for `TODO(phase-01)` returns zero hits (actual: 0).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - authoritative regen batched into Phase 04 Step 04.3 (covers new classes across both phases).

---

## Handoff Notes to Next Phase

Telegram client detection (`TelegramShareTargets`) and the multi-file targeted `SystemShareInvoker` are reusable by the Phase 03 Bot API path's fallback and by any future Telegram action.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent state changed; the new action only adds a share entry point.
