# Стратегическая спецификация: S1738 - Кнопка сброса настроек лаунчера в группе «Система»

**Ticket:** S1738
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-02)
**Tactical spec:** inline - раздел «Тактический план (compact)» ниже (Simple path, /spec-all)

<!-- auto-approved by /spec-all - 2026-08-17 -->

---

## 1. Проблема

Кнопка сброса настроек лаунчера стоит вне логичной группы и выглядит так, что её назначение непонятно до нажатия. Владелец: «по ее изображению и ее расположению я не понял, что она там делает, пока не нажал».

---

## 2. Цели

1. Кнопка сброса настроек лаунчера перенесена в группу «Система» окна настроек лаунчера.
2. Кнопка оформлена как явная кнопка с текстовой надписью, а не как неподписанный элемент.

**Non-goals:**

- Изменение самой механики сброса (что именно сбрасывается) - только размещение и подача.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Именно группа «Система» и именно кнопка с надписью - формулировка владельца.

### 3.2 Жёсткие ограничения

- **Flavor:** по `docs/FLAVOR_MATRIX.md` (лаунчер доступен не во всех сборках).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** EN/RU/UK для надписи кнопки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-007); S1088 (диалог настроек лаунчера).
- **UI placement contract:** кнопка перемещается в группу «Система», текстовая надпись обязательна.
- **Validation level:** визуальная проверка окна настроек лаунчера.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

---

## 11. Критерии готовности (strategic-level)

1. Кнопка сброса видна в группе «Система» с понятной надписью без нажатия.

---

---

## Тактический план (compact - Simple path, /spec-all)

> Scope: tactical, English, developer handoff. Every step carries a verification predicate. Rationale lives in §1-§3 above.

# Phase 01 - Reset action into the System group as a labeled button

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 4 / 4

## Objective

Relocate the launcher reset action from the dialog header icon button into the System group as a labeled outlined button. Reset mechanics, the destructive confirmation dialog, and the fragment wiring (`binding.btnResetLauncher.setOnClickListener { confirmReset() }`) stay untouched - the id and view type do not change, so no `.kt` edit is needed.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 310 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 305 |
| `app_v2/src/main/res/values*/strings.xml` (13 locale files) | Modified | one key removed per file |
| `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json` | Regenerated | - |

## Steps

### Step 01.1 - Move `btnResetLauncher` into the System group (portrait)

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `btnResetLauncher` MaterialButton from the header LinearLayout. Re-add it as the last child of `containerLauncherSystem`, after `rowLauncherOpenHomeSettings`, keeping the same id: style `@style/Widget.FastMediaSorter.SettingsButton.Outlined`, `android:text="@string/launcher_settings_reset_title"`, `app:icon="@drawable/ic_restore_defaults"`, `android:textAllCaps="false"`, `android:clickable="true"`, `android:focusable="true"`, and no `android:contentDescription`. Update the header comment so it no longer lists reset as a header control.

**Why:**

> The owner could not tell what the unlabeled header icon did until pressing it (§1), and the §3.3 placement contract fixes the button in the System group with a mandatory text label (§2.2).

**Verification:**

- `Grep` - `btnResetLauncher` matches exactly once in the file, after `containerLauncherSystem`.
- `Grep` - `launcher_settings_reset_title` present in the file.
- `Grep` - `launcher_settings_reset_button_description` absent from the file.

**Status:** `[x]` done

### Step 01.2 - Mirror the move in the landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Apply the Step 01.1 edit verbatim to `layout-land/dialog_launcher_settings.xml`: remove the header icon button, add the identical labeled button as the last child of `containerLauncherSystem`, update the header comment.

**Why:**

> §2.1 moves the button in the launcher settings window as such, and that window ships as two orientation layouts sharing one ViewBinding, so the move is only complete when the landscape copy carries the identical change (house Rule 11).

**Verification:**

- `Grep` - `btnResetLauncher` matches exactly once in the landscape file, after `containerLauncherSystem`.
- `Grep` - `launcher_settings_reset_button_description` absent from the landscape file.

**Status:** `[x]` done

### Step 01.3 - Delete the orphaned description string from all locales

**Files:** `app_v2/src/main/res/values/strings.xml` and the 12 `values-<locale>/strings.xml` files
**Depends on:** Steps 01.1, 01.2 (the key is orphaned only once both layouts stop referencing it)

**Prompt for developer:**

> Remove the `launcher_settings_reset_button_description` string line from every locale's `strings.xml` (13 files). Add no replacement key: the button's visible text reuses the existing `launcher_settings_reset_title`, which already ships in all thirteen locales.

**Why:**

> With a visible label the button no longer needs a spoken description (§2.2), and the key it leaves behind is dead weight that house Rule 20 requires deleting in the same change that orphans it.

**Verification:**

- `Grep` - `launcher_settings_reset_button_description` returns zero hits across `app_v2/src/main/res/`.

**Status:** `[x]` done

### Step 01.4 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/settings/settings-annotations.json`
**Depends on:** Steps 01.1-01.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` to regenerate the manifest and reference. If the gate refuses an unannotated or stale entry for the moved row, update `docs/settings/settings-annotations.json` first, then re-run. Finish with `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exiting 0.

**Why:**

> not stated in strategic spec - house Rule 22 makes manifest regeneration mandatory for any change to a dialog-hosted setting's placement.

**Verification:**

- `assert-settings-doc-sync.ps1` exits 0.
- The manifest entry for the reset row names the System group / `dialog_launcher_settings` scope, not the header.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x]` done.
- [x] Project compiles - `.\a.ps1 fr` (resources-only change; no `.kt` touched).
- [x] Dev log entry added for the changed-file set via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (`docs/CODE_AUDIT_PROTOCOL.md`).

## Handoff Notes to Next Phase

Final phase. Invariant established: `btnResetLauncher` keeps its id and MaterialButton type in both orientations, so `LauncherSettingsDialogFragment` binds unchanged; the destructive confirmation remains the only gate before `ResetLauncherToDefaultsUseCase` runs.

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing behavior changed beyond button placement.

---

## Приложение. Записи инбокса (дословно)

- **L-007** - «Кнопку Reset "Сбросить настройки для лаунчера" лучше разместить вот в этой группе "система".как кнопку с надписью, потому что по ее изображению и ее расположению я не понял, что она там делает, пока не нажал.»

---

## Last Audit

**Date:** 2026-08-18 - `/spec-all` review mode (drift-check reported DRIFT: the phase was already in the tree).

**Verdict:** Implemented - all four steps of Phase 01 verified against the working tree.

**Evidence:**

- `app_v2/src/main/res/layout/dialog_launcher_settings.xml` - `btnResetLauncher` appears exactly once, as the last child of `containerLauncherSystem` (line 323, container opens at 288), styled `Widget.FastMediaSorter.SettingsButton.Outlined` with `android:text="@string/launcher_settings_reset_title"`, `app:icon="@drawable/ic_restore_defaults"`, `textAllCaps="false"`, `clickable`/`focusable` true, no `contentDescription`. The header comment now reads "title + close .. Close is the only action" and no longer lists reset.
- `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` - identical block at line 340, same container, same attributes. Both orientation variants enumerated: a scan of `app_v2/src` returns exactly these two `dialog_launcher_settings*.xml` files, so no third variant is stale.
- `launcher_settings_reset_button_description` - zero hits across `app_v2/src/` and `docs/`; `launcher_settings_reset_title` present in all 13 locale `strings.xml` files, matching the 13 locales declared in `locales_config.xml`.
- `LauncherSettingsDialogFragment.kt:216` - `binding.btnResetLauncher.setOnClickListener { confirmReset() }` unchanged, so the id/view-type invariant holds and the destructive confirmation is still the only gate before `ResetLauncherToDefaultsUseCase`.
- `docs/settings/settings-manifest.json` - the `btnResetLauncher` entry carries `layout: dialog_launcher_settings`, `kind: BUTTON`, `sectionId: launcher` and the EN/RU/UK titles of `launcher_settings_reset_title`; `docs/settings/settings-annotations.json` annotates the key; `docs/SETTINGS_REFERENCE.md` renders the row under the Launcher section. Regeneration produced no delta for this key, so the mirror is fresh with respect to this ticket.
- `.\a.ps1 fr` - `BUILD SUCCESSFUL`, "Fast check passed" (resources + manifest; no `.kt` touched by this ticket).

**Audit result (P0/P1):** none. The change is a resource-only row relocation: no lifecycle, coroutine, Room, DI, player or build surface is involved, so no Layer 2-7 trigger of `docs/CODE_AUDIT_PROTOCOL.md` fires. Focus handling matches its two sibling buttons in the same vertical `LinearLayout` (`rowLauncherOpenHomeSettings`, `btnImportSystemShortcuts`), which likewise rely on natural top-to-bottom focus order.

**Foreign-WIP note (not charged to S1738):** a full `reindex-settings.ps1` run fails its annotations stage on `btnReleasedTickets`, a key introduced by in-flight S1783 (`debug-tools-released-tickets-listing`, status Tactical) in `fragment_settings_general.xml`. That row is unrelated to this ticket and is S1783's closure obligation; the regenerated files were reverted so this ticket does not commit another ticket's half-written manifest entry.

**Residual gap:** none in code. Visual confirmation of the button in the launcher settings window on a device is the owner-level validation named in §3.3.

**Collapse convention (pre-existing, not introduced here):** `LauncherSettingsDialogFragment.setupCollapsibleSections()` registers the System group with `defaultExpanded = false` - by the deliberate S1422 rule only the Top bar group starts expanded. The reset button is therefore revealed by expanding "Система", exactly like every other row of the Taskbar, Desktop and System groups. §11's "без нажатия" is read against inbox entry L-007 ("не понял, что она там делает, пока не нажал"): the label now states the button's purpose without pressing the button itself, which is the criterion this ticket owns. Changing the group's default expanded state would be a change to S1422's rule and belongs to that ticket, not this one.
