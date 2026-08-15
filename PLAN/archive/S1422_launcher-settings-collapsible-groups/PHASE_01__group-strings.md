# Phase 01 - Group title strings

**Strategic spec:** [`../S1422_launcher-settings-collapsible-groups.md`](../S1422_launcher-settings-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Add the four group-title strings the collapsible headers will reference, in EN, RU and UK.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 4 added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 4 added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 4 added |

---

## Steps

### Step 01.1 - Add the four group titles across EN / RU / UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Add four string keys with one lockstep call each, using the byte-preserving tool rather than editing the
> three files by hand. The other `launcher_settings_*` keys live in `strings.xml`, which is the tool's default
> file, so no `-File` argument is needed.
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_settings_group_taskbar -En "Taskbar" -Ru "Панель задач" -Uk "Панель задач"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_settings_group_top_bar -En "Top bar" -Ru "Верхняя полоса" -Uk "Верхня смуга"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_settings_group_desktop -En "Desktop" -Ru "Рабочий стол" -Uk "Робочий стіл"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_settings_group_system -En "System" -Ru "Система" -Uk "Система"
> ```
>
> These are group headings, not messages, so `docs/COMMUNICATION_POLICY.md` §2 message formulas do not apply;
> the §6 tone checklist still does - each title is a plain noun phrase with no punctuation and no ellipsis.

**Why:**

Strategic §3.4 names the four groups and §2 records the owner accepting those names without edits, so the
header text has to exist as a localized resource before any layout can reference it.

**Verification:**

- `Grep` - each of `launcher_settings_group_taskbar`, `launcher_settings_group_top_bar`, `launcher_settings_group_desktop`, `launcher_settings_group_system` matches exactly once in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - the same four keys match exactly once each in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - the same four keys match exactly once each in `app_v2/src/main/res/values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_group_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 01.1 done. Four keys added with one `set-android-string.ps1 -Action add` call each; each call reported `[EN]/[RU]/[UK] added to strings.xml`. Verification 5/5 PASS: the four keys grep exactly once in `values/`, `values-ru/` and `values-uk/strings.xml` (4 matches per file), `check_strings_localized.ps1 -KeyPrefix "launcher_settings_group_"` exit 0 with `all 5 key(s) present in en/ru/uk` - five because the pre-existing `launcher_settings_group_title` shares the prefix. Best-effort locales (de/es/fr/..) report untranslated, which the script itself marks not fatal.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fr` exit 0, `BUILD SUCCESSFUL in 12s`, `Fast check passed`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the change via `.\scripts\add_to_dev_log.ps1` - one row per locale file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Files Touched are resource-only (four `<string>` keys, no logic, no lifecycle, no coroutine, no Room surface), so only Layer 1 applies: keys follow the file's `launcher_settings_*` prefix and carry no format arguments, and `post-change.ps1` returned a bare `PASS` with the string-format and neuroslop gates clean.

---

## Handoff Notes to Next Phase

Four localized header titles exist; Phase 02 references them from both orientations of the dialog layout.

---

## Rollback Plan

Revert phase commit - four added string keys, no user-facing surface changed until Phase 02 lands.
