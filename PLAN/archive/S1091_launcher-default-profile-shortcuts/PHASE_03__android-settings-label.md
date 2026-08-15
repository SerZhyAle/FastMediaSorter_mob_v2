# Phase 03 - Android Settings Label

**Strategic spec:** [`../S1091_launcher-default-profile-shortcuts.md`](../S1091_launcher-default-profile-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01/02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 03.1 grep-verified: osVisual special-cases KEY_SETTINGS to launcher_menu_android_settings; OsShortcutCatalog unchanged (app_launch_panel_os_settings still there). fk compile SUCCESSFUL.

---

## Objective

Label the launcher desktop's OS-Settings cell "Android settings" (reusing the existing launcher-scoped string) without renaming the shared `OsShortcutCatalog` label that the app-launch panel uses.

---

## Prerequisites

- [ ] `R.string.launcher_menu_android_settings` exists in EN/RU/UK (already present - Grep to confirm).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 150 |

---

## Steps

### Step 03.1 - Special-case KEY_SETTINGS in osVisual

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `osVisual(targetKey)`, after resolving `target = OsShortcutCatalog.byKey(targetKey)`, choose the label resource: when `targetKey == OsShortcutCatalog.KEY_SETTINGS` use `R.string.launcher_menu_android_settings`, otherwise `target.labelRes`. Use that for `context.getString(...)`; keep `iconRes = target.iconRes`. Add one WHY comment: this resolver is launcher-only, so the shared `OsShortcutCatalog` label (app-launch panel) is untouched. Do not modify `OsShortcutCatalog`.

**Verification:**

- `Grep` - `R.string.launcher_menu_android_settings` referenced in `ResolveLauncherCommandLabelUseCase.kt`.
- `Grep` - `OsShortcutCatalog.KEY_SETTINGS` compared in `osVisual`.
- `Grep` (negative) - `app_launch_panel_os_settings` NOT introduced/renamed in `OsShortcutCatalog.kt` (unchanged).
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 03.1 is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Launcher OS-Settings cells now read "Android settings"; app-launch panel wording unchanged. Phase 04 records + regenerates.

---

## Rollback Plan

Revert the one-method change - no data or resource impact.
