# S0886 - Function icons for "Black-screen button" / "Picture-in-Picture" toggles

**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

> Настройки - Плеер . У тогглеров "Показывать кнопку Черный экран" и "Включить картинку в картинке" по аналогии с прочими добавить символ этих функции (как они приняты у нас в интерфейсе)

---

## Goal (RU)

В разделе настроек Playback (группа "Player UI") у тогглеров "Показывать кнопку Чёрный экран" и "Включить картинку в картинке" нет ведущей иконки, тогда как соседние строки её показывают. Добавить каждому узнаваемый значок функции - тот же, что уже используется в интерфейсе (`ic_black_screen` у команды чёрного экрана плеера, `ic_picture_in_picture` у PiP-кнопки плеера), через существующий атрибут `app:str_icon` виджета `SettingsToggleRow` (S0776). Правка декоративная: два значка в двух ориентациях.

---

## 1. Проблема

Тогглеры "Show Black-screen button" и "Enable Picture-in-Picture" (Settings > Playback > Player UI) отображаются без ведущей иконки, в отличие от прочих строк настроек. Несогласованность снижает сканируемость раздела.

## 2. Scope

- Только два ряда: `rowShowBlackScreenButton`, `rowEnablePip`.
- Обе ориентации: `res/layout/fragment_settings_playback.xml` и `res/layout-land/fragment_settings_playback.xml`.

**Non-goals:**

- Прочие тогглеры раздела.
- Новые drawables, строки, изменение логики виджета или PiP-гейта видимости.

## 3. Constraints / owner inputs

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0885 (аналогичное добавление иконок тогглерам в том же разделе).
- **UI decision:** размещения нет - существующий ведущий слот иконки `SettingsToggleRow` (`app:str_icon`); иконки канонические (`ic_black_screen` / `ic_picture_in_picture`, те же, что у соответствующих кнопок плеера).

---

## Phases

### Phase 1 - Add leading icons (portrait + landscape)

- [ ] Step 1.1: In `app_v2/src/main/res/layout/fragment_settings_playback.xml`, add `app:str_icon="@drawable/ic_black_screen"` to `@id/rowShowBlackScreenButton` and `app:str_icon="@drawable/ic_picture_in_picture"` to `@id/rowEnablePip`.
  - Verification: `Grep str_icon` returns both new lines.
- [ ] Step 1.2: In `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`, add the same two attributes to the same ids (Rule 11 parity).
  - Verification: `Grep str_icon` returns both new lines in the land file.
- [ ] Step 1.3: Build gate - resources compile (`.\a.ps1 fr`).
  - Verification: BUILD SUCCESSFUL; both `@drawable` refs resolve.

---

## 11. Критерии готовности

1. Тогглер "Show Black-screen button" показывает `ic_black_screen` слева от заголовка в обеих ориентациях.
2. Тогглер "Enable Picture-in-Picture" показывает `ic_picture_in_picture` слева от заголовка в обеих ориентациях.
3. Иконки совпадают с теми, что приняты для этих функций в интерфейсе (кнопки плеера).

---

## Last Audit

**Date:** 2026-07-04 - Verified (static evidence, P3 cosmetic).

Change: added `app:str_icon` to two `SettingsToggleRow` rows in both orientations.

- `res/layout/fragment_settings_playback.xml` - `rowShowBlackScreenButton` -> `ic_black_screen`, `rowEnablePip` -> `ic_picture_in_picture`.
- `res/layout-land/fragment_settings_playback.xml` - same two rows (Rule 11 parity).

Evidence:
- Attr wired: `SettingsToggleRow.kt` reads `str_icon` and calls `setIcon(iconRes)` -> drawable set on `R.id.str_icon`, `VISIBLE` (S0776 path, 30+ shipping usages).
- Canonical icons: `ic_black_screen` is the player black-screen command button icon (`overflow_menu_player.xml`, `activity_player_unified.xml`); `ic_picture_in_picture` is the player PiP control icon (`custom_player_controls*.xml`). Matches "как они приняты у нас в интерфейсе".
- Build: `a.ps1 fr` BUILD SUCCESSFUL - both `@drawable` refs resolve.

Scope note: PiP visibility gate (`layoutPip`, API 31+) unchanged; no strings/new drawables/logic. `docs/FEATURES` unaffected (cosmetic parity), no `ALL_FEATURES` record.
