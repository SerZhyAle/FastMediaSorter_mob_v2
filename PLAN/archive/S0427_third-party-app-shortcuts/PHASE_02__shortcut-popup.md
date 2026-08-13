# Phase 02 - Shortcut popup surface

**Strategic spec:** [`../S0427_third-party-app-shortcuts.md`](../S0427_third-party-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Build the popup that lists an app's quick actions and starts the chosen one - row layout, trilingual failure string, adapter, and the manager that owns the window. Nothing calls it yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `QueryAppShortcutsUseCase` and `StartAppShortcutUseCase` exist and compile.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_app_shortcut.xml` | New | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 key |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutAdapter.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutMenuManager.kt` | New | ≤ 180 |

> The popup row has no landscape counterpart: it is a list item inside a floating window, and `src/launcherEnabled/res/layout-land/` holds only `activity_launcher_home.xml`. No `layout-land` variant is needed.
>
> Flavor placement: the popup is part of the launcher home surface, so it lives in `src/launcherEnabled/` - the same source set as `LauncherEditModeManager` and `LauncherTaskbarManager`. No `BuildConfig` guard anywhere.

---

## Steps

### Step 02.1 - Add the shortcut row layout

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_app_shortcut.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a horizontal row: an `ImageView` (`@+id/shortcutIcon`, fixed square sized from a new or existing dimen, `scaleType="fitCenter"`) followed by a single-line ellipsizing `TextView` (`@+id/shortcutLabel`). Colours come from `?attr/` theme attributes only - no literal `#hex`. Make the row `focusable` so the popup is walkable with a D-pad, keyboard and mouse (CLAUDE.md Rule 16). Match the paddings already used by `item_launcher_app_grid_cell.xml` rather than inventing new spacing.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/item_launcher_app_shortcut.xml` exists.
- `Grep` - `@+id/shortcutIcon` and `@+id/shortcutLabel` each match once.
- `Grep` - `="#` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 3/3 PASS. Files: res/layout/item_launcher_app_shortcut.xml (+40 LOC). Paddings mirror `item_launcher_app_grid_cell.xml`; row is a plain focusable `LinearLayout` because a popup row needs no card treatment.

---

### Step 02.2 - Add the launch-failure string in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one key, `launcher_app_shortcut_start_failed`, in a single lockstep call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_app_shortcut_start_failed -En "<en>" -Ru "<ru>" -Uk "<uk>"
> ```
>
> Wording: state what happened from the user's side ("This action is not available any more"), not the API failure. Check the text against `docs/COMMUNICATION_POLICY.md` §2 (message formula for an error/unavailable state) and §6 (tone checklist) before adding it. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_app_shortcut"` and fix anything it reports.

**Verification:**

- `Grep` - `launcher_app_shortcut_start_failed` matches once in each of the three `strings.xml` files.
- `scripts/check_strings_localized.ps1 -KeyPrefix "launcher_app_shortcut"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 3/3 PASS. `launcher_app_shortcut_start_failed` added in EN/RU/UK via `set-android-string.ps1 -Action add`; `check_strings_localized.ps1 -KeyPrefix "launcher_app_shortcut"` exit 0. Copy follows §2.1 (toast = one short thought), not the API failure.

---

### Step 02.3 - Add `LauncherAppShortcutAdapter`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutAdapter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create a `BaseAdapter` over `List<AppShortcut>` that inflates `item_launcher_app_shortcut.xml`, sets the icon and the label, and dims a row whose `isEnabled` is false (reuse `LauncherCellViewBinder.UNAVAILABLE_ALPHA` so a disabled shortcut looks like an unavailable cell). Set each row's `contentDescription` to the shortcut label. Keep it a plain adapter - no coroutines, no data loading.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class LauncherAppShortcutAdapter` matches exactly once.
- `Grep` - `UNAVAILABLE_ALPHA` present.
- `Grep` - `LauncherApps` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 4/4 PASS. Files: helpers/LauncherAppShortcutAdapter.kt (+49 LOC). The `LauncherApps` predicate must be run case-sensitively - `LauncherAppShortcut*` is a case-insensitive substring match and reports false hits.

---

### Step 02.4 - Add `LauncherAppShortcutMenuManager`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutMenuManager.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Create `class LauncherAppShortcutMenuManager(private val scope: CoroutineScope, private val queryAppShortcuts: QueryAppShortcutsUseCase, private val startAppShortcut: StartAppShortcutUseCase)` with one entry point: `fun show(anchor: View, packageName: String)`.
>
> `show` launches on `scope`, queries the shortcuts, and returns without any UI when the list is empty - a long press on an app with no quick actions does nothing visible (strategic §3.3). With items, build a `ListPopupWindow` anchored to `anchor`, feed it a `LauncherAppShortcutAdapter`, set `isModal = true` so D-pad and keyboard focus enter the list, size the width from the anchor, and show it. On item click: dismiss, then start the shortcut with the anchor's screen bounds as `sourceBounds` (the system uses them for the launch animation); if the start returns false, show `launcher_app_shortcut_start_failed` as a toast.
>
> Hold the window in a nullable field and expose `fun dismiss()`; the host calls it when the surface goes away so a popup cannot outlive its anchor. Guard re-entry: a second `show` while a query is in flight or a window is open dismisses the old one first, so two long presses cannot stack two windows. Do not inject anything here - the host owns construction and passes its own use cases in.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class LauncherAppShortcutMenuManager` matches exactly once.
- `Grep` - `fun show(anchor: View, packageName: String)` present.
- `Grep` - `fun dismiss()` present.
- `Grep` - `isModal = true` present.
- `Grep` - `GlobalScope` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 6/6 PASS. Files: helpers/LauncherAppShortcutMenuManager.kt (+80 LOC), res/values/dimens.xml (+1 dimen `launcher_shortcut_popup_width`, added here because a desktop cell is far narrower than a readable action label).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 23s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1` (PASS, all gates, strings audited with `-KeyPrefix launcher_app_shortcut`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade.
- [x] Phase-boundary audit run - no P0/P1. Layer 2: the query runs on the host's scope and is cancelled by `dismiss()`; a result arriving after the anchor detached is dropped via `isAttachedToWindow`. Layer 3: exactly one window reference, nulled both by `dismiss()` and by the window's own dismiss listener, so no `PopupWindow` can retain a dead anchor.

---

## Handoff Notes to Next Phase

`LauncherAppShortcutMenuManager.show(anchor, packageName)` is the only call the wiring phase needs; it self-suppresses when there is nothing to show, so callers do not pre-check anything. Hosts must call `dismiss()` on their teardown edge.

---

## Rollback Plan

Revert phase commit(s) and remove the three string entries with `set-android-string.ps1 -Action remove`. Nothing calls the popup yet, so no user-facing surface regresses.
