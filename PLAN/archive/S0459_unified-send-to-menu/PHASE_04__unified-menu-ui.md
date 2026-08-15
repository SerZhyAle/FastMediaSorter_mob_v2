# Phase 04 - Unified «Send to..» menu (command + bottom sheet + overflow submenu)

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05, 06, 07
**Steps done:** 6 / 6
**Started:** 2026-06-15
**Completed:** 2026-06-16

---

## Objective

Build the single UI consumer of the registry: a use-case that yields the gated, ordered receiver list for given content, a bottom-sheet presenter (bar press) and a native `addSubMenu` builder (overflow), wired as one high-priority `PlayerCommand`. This is the menu; surfaces are routed to it in Phases 05-07.

---

## Prerequisites

- [x] Phase 01 ✅ (`appliesTo`, `ShareableContent`, `ShareTargetIconResolver`).
- [x] Phase 03 ✅ (registry populated, `Map<String, ShareTargetHandler>` bound).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildSendToReceiverListUseCase.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToBottomSheet.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt` | New | ≤ 220 |
| `app_v2/src/main/res/layout/sheet_send_to.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout/item_send_to_receiver.xml` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | - |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> No `res/layout-land/sheet_send_to.xml` needed - a bottom sheet spans width in both orientations; if a landscape height cap is required it is handled in-sheet, not via a land layout variant. Note recorded per landscape-parity rule.

---

## Steps

### Step 04.1 - BuildSendToReceiverListUseCase (the 3-gate list)

**Files:** `domain/usecase/BuildSendToReceiverListUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BuildSendToReceiverListUseCase @Inject constructor(registry, resolver, isEnabled: IsShareTargetEnabledUseCase)`. `operator fun invoke(content: ShareableContent, settings: AppSettings): List<ShareTarget>` returns targets where `isEnabled(id, settings)` AND `resolver.isAvailable(target)` AND `target.appliesTo(content.mediaType)`, ordered by usage-frequency rank with System Share last as catch-all (ADR-9). For a multi-file selection, single-only receivers gate on the first file's type (ADR-4). Pure domain - no Android UI.

**Verification:**

- `Glob` - `domain/usecase/BuildSendToReceiverListUseCase.kt` exists.
- `Grep` - `class BuildSendToReceiverListUseCase` matches once.
- `Grep` - `appliesTo(` and `isAvailable(` both referenced.

**Status:** `[x]` done

---

### Step 04.2 - Receiver row layouts (icon + label, accessible)

**Files:** `res/layout/sheet_send_to.xml`, `res/layout/item_send_to_receiver.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the sheet container (title + `RecyclerView`) and a receiver row (icon `ImageView` + label `TextView` + optional "Не установлено" subtitle). Rows `focusable=true` with a visible focus selector and touch-target `minHeight`; colours via `?attr`/`@color` only (no hex). Icon supplements the label, never replaces it (accessibility). Mirror an existing project sheet layout (e.g. `IconPickerBottomSheet`) for TV focus consistency, per research 03.

**Verification:**

- `Glob` - both layout files exist.
- `Grep` - `?attr` present and `="#` NOT present (zero hex) in both layouts.
- `Grep` - `android:focusable="true"` present in `item_send_to_receiver.xml`.

**Status:** `[x]` done

---

### Step 04.3 - SendToBottomSheet (bar press presentation)

**Files:** `ui/share/SendToBottomSheet.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Create `SendToBottomSheet : BottomSheetDialogFragment` rendering the Step 04.1 list with Step 04.2 rows and `ShareTargetIconResolver` icons. On show, request focus on the first enabled row (D-pad/TV, research 03). On row click, invoke the bound `ShareTargetHandler` from the injected `Map<String, ShareTargetHandler>` with the passed `ShareableContent`, then dismiss. Disabled (unavailable) rows show the non-colour "Не установлено" subtitle. Collect view-bound flows with `collectOnLifecycle` (no bare `lifecycleScope.launch{collect}`).
>
> **Row label (S0459 ADR-5, compliance):** for a package-backed receiver (non-empty `ShareTarget.packages` whose `titleRes` is the neutral `share_target_title_app`) resolve the displayed label from the installed app via `PackageManager.getApplicationLabel` - mirror `ShareTargetIconResolver` (add a `resolveLabel(target): CharSequence?` there or a sibling resolver). This keeps forbidden brand literals (e.g. Instagram, denied by `verifyNoPlatformNames`) out of the codebase; fall back to `getString(titleRes)` when no app resolves. Logical receivers always use `titleRes`.

**Verification:**

- `Glob` - `ui/share/SendToBottomSheet.kt` exists.
- `Grep` - `class SendToBottomSheet` and `BottomSheetDialogFragment` present.
- `Grep` - `Map<String, ShareTargetHandler>` injected; `collectOnLifecycle` used if a Flow is collected.

**Status:** `[x]` done

---

### Step 04.4 - SendToMenuManager (bar-direct / overflow submenu / one-receiver rule)

**Files:** `ui/share/SendToMenuManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Create `SendToMenuManager` exposing `show(activity, content, settings)` and `buildOverflowSubMenu(menu, content, settings)`. `show`: build the list; if exactly one applicable receiver, invoke it directly (ADR-8); else open `SendToBottomSheet`. `buildOverflowSubMenu`: `menu.addSubMenu(...)` populated with one item per gated receiver (ADR-2/ADR-8 overflow always submenu), each item dispatching its handler. Add a multi-file hint ("применится к первому файлу") for single-only receivers when `content.uris.size > 1` (ADR-4). This is the single point both bar and overflow call.

**Verification:**

- `Glob` - `ui/share/SendToMenuManager.kt` exists.
- `Grep` - `class SendToMenuManager` matches once.
- `Grep` - `addSubMenu` present; `SendToBottomSheet` referenced.

**Status:** `[x]` done

---

### Step 04.5 - Add the «Send to..» PlayerCommand + strings

**Files:** `ui/player/helpers/CommandPanelLayoutPlanner.kt`, `res/menu/overflow_menu_player.xml`, `res/values/strings.xml` (+ ru/uk)
**Depends on:** Step 04.4

**Prompt for developer:**

> Add `PlayerCommand.SEND_TO` (high priority so it reaches the bar when space allows, `barCapable = true`, menu id `menu_send_to`, title `share_to_menu_title` «Отправить в..», a neutral send glyph). Add it in `buildActiveCommands` for any shareable file. Add `menu_send_to` to `overflow_menu_player.xml` as the overflow anchor for the submenu. Add trilingual strings (`share_to_menu_title`, `share_to_first_file_hint`) via `set-android-string.ps1 -Action add`. Do NOT yet remove the old share/telegram/lens/keep commands - removal is Phase 05 (keeps this phase non-breaking).

**Verification:**

- `Grep` - `SEND_TO(` present in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `menu_send_to` present in `overflow_menu_player.xml`.
- `scripts/utils/set-android-string.ps1 -Action get -Key share_to_menu_title` exits 0.

**Status:** `[x]` done

---

### Step 04.6 - Wire the command callback to SendToMenuManager

**Files:** `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`, `ui/player/CommandPanelController.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Route the `SEND_TO` command: bar press → `SendToMenuManager.show(activity, content, settings)`; overflow → `SendToMenuManager.buildOverflowSubMenu`. Build `ShareableContent` from the current player file (uris, mime, `MediaType`, text for text surfaces). This proves the menu end-to-end on the player before consolidation. Keep existing commands working alongside (removed in Phase 05).

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `PlayerCommandPanelCallbackImpl.kt` or `CommandPanelController.kt`.
- `Grep` - `ShareableContent(` constructed at the call site.
- Project compiles - run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL 2026-06-16.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `Grep -n "Log\.d\("` in every touched `.kt` returns zero hits.
- [ ] «Отправить в..» appears on the player and opens the gated receiver list (bottom sheet) / overflow submenu - on-device smoke check. **MANUAL-REQUIRED** (device verification is acceptance gate → BlockNeedUserTest).
- [x] Strings pass COMMUNICATION_POLICY §6; `check_strings_localized.ps1 -KeyPrefix share_to` exits 0.
- [x] Dev log + `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Step Log

- 2026-06-16 - Steps 04.1–04.6 all PASS. Phase Done Criteria 6/7 mechanical ✓; on-device criterion → BlockNeedUserTest. BUILD SUCCESSFUL. Debug tag `Timber.d("S0459: player SEND_TO command triggered")` inserted in `PlayerCommandPanelCallbackImpl.onSendToClicked`. Files: BuildSendToReceiverListUseCase.kt (new), SendToBottomSheet.kt (new), SendToMenuManager.kt (new), sheet_send_to.xml (new), item_send_to_receiver.xml (new), CommandPanelLayoutPlanner.kt (+SEND_TO enum), overflow_menu_player.xml (+menu_send_to), strings.xml×3 (+3 keys), CommandPanelController.kt (+onSendToClicked), PlayerCommandPanelCallbackImpl.kt (+wiring), PlayerActivity.kt (+sendToMenuManager injection).

---

## Handoff Notes to Next Phase

The unified menu works on the player surface (old commands still present in parallel). Phases 05-07 route each remaining surface to `SendToMenuManager` and remove the duplicate ad-hoc commands. `ShareableContent` construction is the per-surface adapter point.

---

## Rollback Plan

Revert phase commit(s) - removes the `SEND_TO` command and menu UI; the old commands (untouched this phase) keep working, so no functional loss.
