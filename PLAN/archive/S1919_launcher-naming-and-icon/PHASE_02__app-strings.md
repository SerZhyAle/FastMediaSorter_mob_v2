# Phase 02 - App strings

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Put the word "лаунчер" into the five RU and UK strings that introduce the feature, and prove the strings that mean the cell screen were left alone.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | 5 values |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | 5 values |

> Both files are far over 500 LOC, but the edits go through `scripts/utils/set-android-string.ps1`, which is byte-preserving and rewrites one value at a time. No whole-file rewrite, so no backup step.

---

## Steps

### Step 02.1 - Rewrite the five introducing values in RU and UK

**Files:** `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change exactly these five keys, RU and UK only, one `scripts/utils/set-android-string.ps1 -Action set` call per key and locale with `-ExpectedOldValue` so a concurrent edit cannot be overwritten silently. Leave `values/strings.xml` and the other ten locales untouched.
>
> `launcher_settings_enable_title` - RU: `Лаунчер: сделать приложение домашним экраном`; UK: `Лаунчер: зробити застосунок домашнім екраном`.
> `launcher_settings_enable_desc` - RU: `Кнопка «Домой» открывает лаунчер - ваш рабочий стол с ярлыками и гаджетами. Выключите в любой момент, чтобы вернуть прежний домашний экран.`; UK: `Кнопка «Додому» відкриває лаунчер - ваш робочий стіл з ярликами та гаджетами. Вимкніть будь-коли, щоб повернути попередній домашній екран.`
> `launcher_settings_enable_help_title` - RU: `Режим лаунчера`; UK: `Режим лаунчера`.
> `launcher_settings_enable_help_message` - RU: keep the existing sentence but open it with `После включения лаунчера Android спросит,` in place of `После включения Android спросит,`; UK: likewise `Після ввімкнення лаунчера Android запитає,`.
> `launcher_settings_group_desktop` - RU: `Рабочий стол лаунчера`; UK: `Робочий стіл лаунчера`.
>
> Check the five new values against `docs/COMMUNICATION_POLICY.md` §2 message formula and the §6 tone checklist before writing them.

**Why:**

Strategic §1 records that "рабочий стол" already means the Android home screen to the reader, so the strings that introduce the feature do not tell the user that a whole launcher is being switched on; these five are the ones a person reads before the launcher is enabled.

**Verification:**

- `Grep` - `лаунчер` (case-insensitive) matches in the RU values of all five keys.
- `Grep` - `лаунчера` (case-insensitive) matches in the UK values of all five keys.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - Five introducing keys rewritten in RU and UK via set-android-string (byte-preserving); check_strings_localized -KeyPrefix launcher_settings_ exit 0, all 78 keys present in en/ru/uk. Nine cell-screen keys re-read from both locale files: all still say rabochiy stol / robochiy stil, none gained the word launcher.

---

### Step 02.2 - Prove the cell-screen strings were not touched

**Files:** `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Confirm by grep that these keys still read "рабочий стол" / "робочий стіл" with no "лаунчер" substituted into them: `launcher_edit_remove_cell`, `launcher_edit_add_cell_title`, `launcher_edit_remove_cell_named`, `launcher_edit_enter`, `launcher_settings_lock_desktop_title`, `launcher_settings_wallpaper_title`, `launcher_app_action_to_desktop`, `launcher_app_action_desktop_full`, `launcher_home_cell_too_wide`.
>
> Record the grep output in the step result. If any of them changed, revert that key to its previous value.

**Why:**

Strategic ADR-1 keeps both words and §7 names the wholesale replacement as the highest-probability risk of this ticket, because "убрать с лаунчера" would be wrong by meaning where the cell screen, not the mode, is intended.

**Verification:**

- `Grep` - each of the nine keys listed above matches `рабоч` in `values-ru/strings.xml` and `робоч` in `values-uk/strings.xml`.
- `Grep` - none of the nine values contains `лаунчер` in either locale, except `launcher_settings_screen_timeout_subtitle`, which already carried it before this ticket and is not in the list.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - Five introducing keys rewritten in RU and UK via set-android-string (byte-preserving); check_strings_localized -KeyPrefix launcher_settings_ exit 0, all 78 keys present in en/ru/uk. Nine cell-screen keys re-read from both locale files: all still say rabochiy stol / robochiy stil, none gained the word launcher.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The settings titles now differ from what `docs/settings/settings-manifest.json` records. Phase 03 regenerates that manifest, so it must not be regenerated before this phase lands.

---

## Rollback Plan

Revert the phase commit - string values only, no schema and no persisted user data.
