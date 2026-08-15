# S0887 - Function icons for "Allow rename" / "Allow delete" toggles

**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

> Настройки - Плеер . У тогглеров "Разрешить переименования" и "Разрешить удаление" по аналогии с прочими добавить символ этих функции (как они приняты у нас в интерфейсе)

---

## Goal (RU)

В разделе настроек Playback (группа "File Operations") у тогглеров "Разрешить переименование" и "Разрешить удаление" сейчас нет ведущей иконки, тогда как многие соседние строки настроек её показывают. Добавить каждому из двух тогглеров узнаваемый значок функции - тот же, что уже используется для этих действий в интерфейсе (rename / delete в оверфлей-меню плеера), через уже существующий атрибут `app:str_icon` виджета `SettingsToggleRow` (введён S0776). Правка декоративная: только два значка в двух ориентациях, без изменения логики, строк или новых ресурсов.

---

## 1. Проблема

Тогглеры "Allow rename" и "Allow delete" (Settings > Playback > File Operations) отображаются без ведущей иконки, в отличие от прочих строк настроек, где иконка функции задаёт быстрый визуальный якорь. Несогласованность делает раздел менее сканируемым.

## 2. Scope

- Только два ряда: `rowAllowRename`, `rowAllowDelete`.
- Обе ориентации: `res/layout/fragment_settings_playback.xml` и `res/layout-land/fragment_settings_playback.xml`.

**Non-goals:**

- `rowConfirmDelete` и прочие тогглеры раздела - вне запроса владельца.
- Новые drawables, строки, изменение логики виджета.

## 3. Constraints / owner inputs

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0885, S0886 (аналогичные добавления иконок тогглерам в том же разделе).
- **UI decision:** размещения нет - используется существующий ведущий слот иконки `SettingsToggleRow` (`app:str_icon`), идентично 30+ действующим применениям; иконки канонические (`ic_rename` / `ic_delete`, те же, что в оверфлей-меню плеера). Неоднозначности размещения/видимости нет.

---

## Phases

### Phase 1 - Add leading icons (portrait + landscape)

- [ ] Step 1.1: In `app_v2/src/main/res/layout/fragment_settings_playback.xml`, add `app:str_icon="@drawable/ic_rename"` to `@id/rowAllowRename` and `app:str_icon="@drawable/ic_delete"` to `@id/rowAllowDelete`.
  - Verification: `Grep str_icon` in the file returns both new lines alongside the existing `ic_audio` usage.
- [ ] Step 1.2: In `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`, add the same two `app:str_icon` attributes to `@id/rowAllowRename` and `@id/rowAllowDelete` (Rule 11 orientation parity).
  - Verification: `Grep str_icon` in the land file returns both new lines.
- [ ] Step 1.3: Build gate - resources compile (`.\a.ps1 fr` or `fc`).
  - Verification: BUILD SUCCESSFUL; no unresolved `@drawable/ic_rename` / `@drawable/ic_delete`.

---

## 11. Критерии готовности

1. Тогглер "Allow rename" показывает значок `ic_rename` слева от заголовка в обеих ориентациях.
2. Тогглер "Allow delete" показывает значок `ic_delete` слева от заголовка в обеих ориентациях.
3. Иконки совпадают с теми, что приняты для этих действий в интерфейсе (оверфлей-меню плеера).

---

## Last Audit

**Date:** 2026-07-04 - Verified (static evidence, P3 cosmetic).

Change: added `app:str_icon` to two `SettingsToggleRow` rows in both orientations.

- `res/layout/fragment_settings_playback.xml` - `rowAllowRename` -> `ic_rename`, `rowAllowDelete` -> `ic_delete`.
- `res/layout-land/fragment_settings_playback.xml` - same two rows (Rule 11 orientation parity).

Evidence:
- Attr wired: `SettingsToggleRow.kt` reads `R.styleable.SettingsToggleRow_str_icon` and calls `setIcon(iconRes)`, which sets the drawable on `R.id.str_icon` and flips it to `VISIBLE` (the same path 30+ shipping `str_icon` usages rely on; introduced by S0776).
- Canonical icons: `ic_rename` / `ic_delete` are the exact drawables the player overflow menu (`overflow_menu_player.xml`) uses for the rename/delete actions - matches "как они приняты у нас в интерфейсе".
- Build: `a.ps1 fr` BUILD SUCCESSFUL - resources merge, both `@drawable` refs resolve.

Scope note: `rowConfirmDelete` and other toggles intentionally left iconless (owner requested only rename + delete). No strings, no new drawables, no widget-logic change. `docs/FEATURES` unaffected (cosmetic parity), so no `ALL_FEATURES` record.
