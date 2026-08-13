# Спецификация (compact): S0983 - Иконка лирики (аудио) на «книжечку»

**Ticket:** S0983
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-10
**Захвачено во время:** S0421 (windows-sftp-folder-share-companion)

**Текст:**
изменить иконку лирики для аудио на книжечку. сейчас схожа с диктофоном

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation
- **Goal / expected outcome:** Provided by user - изменить иконку лирики для аудио на книжечку, так как сейчас она схожа с диктофоном.
- **Local anchor:** Delegated by user - /spec-all auto-approval - Использование ресурса `R.drawable.ic_microphone` для лирики.
- **Scope boundaries / forbidden areas:** Delegated by user - /spec-all auto-approval - Только замена иконки лирики для аудио; другие иконки микрофона (запись голоса, TTS) не изменяются.
- **Done / success signal:** Delegated by user - /spec-all auto-approval - Кнопка лирики в плеере и пункт меню используют иконку книжки `ic_book`.
- **Autonomy rule:** Delegated by user - /spec-all auto-approval - agent may decide with explicit assumptions
- **UI decisions / delegation:** Delegated by user - /spec-all auto-approval - Иконка меняется на `ic_book` во всех ориентациях (portrait, landscape).

---

## 1. Цель

Изменить иконку кнопки отображения лирики и пункта меню лирики для аудиофайлов с микрофона (`ic_microphone`) на книжку (`ic_book`), чтобы избежать визуального сходства с диктофоном.

---

## 2. Подход

Изменить ссылки на ресурс `ic_microphone` на `ic_book` в файлах разметки плеера, меню overflow плеера и в классе планирования командной панели.

- В `app_v2/src/main/res/layout/activity_player_unified.xml` и `app_v2/src/main/res/layout-land/activity_player_unified.xml` заменить `android:src="@drawable/ic_microphone"` у ImageButton `btnLyricsCmd` на `android:src="@drawable/ic_book"`.
- В `app_v2/src/main/res/menu/overflow_menu_player.xml` заменить `android:icon="@drawable/ic_microphone"` у item `menu_lyrics` на `android:icon="@drawable/ic_book"`.
- В `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` в элементе enum `LYRICS` заменить `R.drawable.ic_microphone` на `R.drawable.ic_book`.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Открытых пожеланий нет.

### 3.2 Жёсткие ограничения

- **Flavor:** Все варианты сборки.
- **API level:** Без API-специфики.
- **Wear OS:** Не затрагивается.
- **Производительность:** Без влияния.
- **Совместимость данных:** Не требуется миграция.
- **Локализация:** Без изменения строк.
- **Доступность:** Сохранить существующие contentDescription (`@string/lyrics`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## Фазы

### Фаза 01 - обновление ресурсов разметки плеера

- Обновить `app_v2/src/main/res/layout/activity_player_unified.xml` для `btnLyricsCmd`.
- Обновить `app_v2/src/main/res/layout-land/activity_player_unified.xml` для `btnLyricsCmd`.
- Verification: Запустить быструю проверку ресурсов `.\a.ps1 fc`.

### Фаза 02 - обновление ресурса меню плеера

- Обновить `app_v2/src/main/res/menu/overflow_menu_player.xml` для `menu_lyrics`.
- Verification: Запустить быструю проверку ресурсов `.\a.ps1 fc`.

### Фаза 03 - обновление планировщика панели команд

- Обновить `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`.
- Verification: Запустить быструю проверку компиляции кода `.\a.ps1 fk`.

---

## 4. Проверка

После внесения изменений выполнить компиляцию проекта:
- `.\a.ps1 fc` (ресурсы)
- `.\a.ps1 fk` (Kotlin код)

---

## Last Audit

**Date:** 2026-07-11
**Outcome:** Verified
**Method:** Static audit - all four `ic_microphone` -> `ic_book` references confirmed in working tree; `res/drawable/ic_book.xml` present; `standard debug` fast-check (fc) BUILD SUCCESSFUL.

- `res/layout/activity_player_unified.xml` `btnLyricsCmd` -> `@drawable/ic_book`.
- `res/layout-land/activity_player_unified.xml` `btnLyricsCmd` -> `@drawable/ic_book`.
- `res/menu/overflow_menu_player.xml` `menu_lyrics` -> `@drawable/ic_book`.
- `CommandPanelLayoutPlanner.kt` `LYRICS` -> `R.drawable.ic_book`.

Pure resource-reference swap; verifiable statically, no device gate required.
