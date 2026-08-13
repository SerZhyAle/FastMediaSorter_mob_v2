# Стратегическая спецификация: S0881 - Развести тогглеры в портрете на две строки

**Ticket:** S0881
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-02
**Tactical spec:** compact (inline phases below, отдельная папка не создаётся)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Настройки - Управление - Взаимодействие с операционной системой. Портрет только. Тогглеры "Системный обрботчик медиа" и "Принимать общие файлы" -сейчас находятся в одной строке - развести в две строки (только портрет)

---

## 1. Проблема

В разделе Настройки -> Управление -> Взаимодействие с ОС тогглеры "Системный обработчик медиа" (`rowPrimaryMediaPlayer`) и "Принимать общие файлы" (`rowAcceptSharedFiles`) в портретной ориентации размещены рядом в одной горизонтальной строке (`layoutDefaultPlayerToggles`, по 50% ширины на каждый). В узкой портретной ширине заголовки/подписи тогглеров теснятся. Нужно развести их в две отдельные строки только в портретной раскладке.

---

## 2. Цели

1. В портретной ориентации оба тогглера отображаются каждый на своей строке, во всю доступную ширину, без горизонтального сжатия.
2. Альбомная раскладка (`layout-land`) не меняется - там ширины достаточно для одной строки.

**Non-goals:**

- Изменение текста, логики или поведения самих тогглеров.
- Изменение других строк/подгрупп в этой секции настроек.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Нет дополнительных пожеланий сверх текста инбокса.

### 3.2 Жёсткие ограничения

- **Flavor:** все (`standard`, `lite`, `photos`, `legacy`) - общий `src/main` layout, не flavor-specific.
- **API level:** без API-специфики - чистая XML-раскладка.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично - статичная XML-раскладка.
- **Совместимость данных:** нет изменений данных/настроек, только визуальное размещение.
- **Локализация:** без изменений строк - ключи `settings_primary_media_player`/`setting_primary_media_player_desc`/`settings_accept_shared_files`/`setting_accept_shared_files_desc` не трогаются.
- **Доступность:** touch target каждого тогглера не уменьшается (полная ширина строки вместо половины - только увеличивается).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **Scope:** UI-only, layout XML, portrait variant only (`res/layout/fragment_settings_destinations.xml`); `res/layout-land/` intentionally untouched (landscape has enough width already, confirmed by reading the counterpart file).

---

## 4. Контекст текущей архитектуры

Экран настроек `fragment_settings_destinations.xml` - чистая XML-раскладка без бизнес-логики в разметке; `SettingsToggleRow` - переиспользуемый custom view для тогглера с заголовком/подзаголовком. Текущий блок `layoutDefaultPlayerToggles` - `LinearLayout` с `orientation="horizontal"`, два `SettingsToggleRow` с `layout_width="0dp"` + `layout_weight="1"`. В портрете это даёт по половине ширины экрана на тогглер; в `layout-land` тот же блок остаётся горизонтальным (шире экран).

---

## 5. Предлагаемый подход

Изменить только портретный вариант блока `layoutDefaultPlayerToggles`: сменить `orientation` контейнера на `vertical`, убрать `layout_weight`/`0dp`-ширину у обоих `SettingsToggleRow`, дать каждому `layout_width="match_parent"` и вертикальный отступ между строками (по образцу соседних одиночных строк в этом же файле, например `layoutFollowSystemRotation`). Альбомный файл не трогается.

### 5.1 Основные столпы / модули

Один layout-файл (`res/layout/fragment_settings_destinations.xml`), один контейнер `LinearLayout` перестраивается из горизонтального в вертикальный.

### 5.2 Потоки данных и событий

Нет изменений в потоках данных - чисто визуальное перестроение статичной XML-иерархии, ViewModel/UseCase/Repository не участвуют.

### 5.3 Точки расширяемости

Не применимо - точечное изменение расположения.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Изменение контейнера ломает существующие margin-констрейнты соседних блоков | Низкая | Визуальный разрыв в секции "Системные приложения" | Проверить сборкой + чтением итогового XML, сверить с соседними одиночными строками |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - чисто визуальная правка раскладки, не новая возможность.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (тот же паттерн, что у `layoutFollowSystemRotation`/`layoutPreventSleep` - одиночная вертикальная строка на тогглер).

---

## 10. Связи с другими спеками

Связей нет. Блок изначально перемещён в этот раздел в рамках S0435 (сама раскладка внутри блока - предмет этого тикета).

---

## 11. Критерии готовности (strategic-level)

1. В портретной ориентации тогглеры "Системный обработчик медиа" и "Принимать общие файлы" отображаются каждый на своей строке, во всю ширину.
2. В альбомной ориентации расположение не изменилось (одна строка, как раньше).
3. Сборка `standard debug` проходит без ошибок.

---

## 12. Tactical Phases (compact)

### Phase 1 - Split toggle row into two vertical rows (portrait only)

- [x] Step 1: In `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, change the `layoutDefaultPlayerToggles` `LinearLayout` (currently `orientation="horizontal"`) to `orientation="vertical"`. Remove `layout_weight="1"` and `layout_width="0dp"` from both `rowPrimaryMediaPlayer` and `rowAcceptSharedFiles`; set `layout_width="match_parent"` on each. Replace the horizontal `layout_marginEnd`/`layout_marginStart` pairing with a single `layout_marginBottom="@dimen/margin_small"` on the first row (`rowPrimaryMediaPlayer`), matching the spacing pattern used by `layoutFollowSystemRotation` above.
  - Verification: read the edited XML block - both rows `match_parent` width, no `layout_weight`, vertical stacking order preserved (`rowPrimaryMediaPlayer` then `rowAcceptSharedFiles`). Done.
- [x] Step 2: Confirm `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` counterpart is unchanged (already read - stays horizontal, weight-1 each) - intentional per Rule 9 (portrait-only request, landscape has sufficient width).
  - Verification: re-read land file, `layoutDefaultPlayerToggles` still `orientation="horizontal"` with both rows `layout_weight="1"`. Confirmed unchanged.
- [x] Step 3: Build `standard debug` via `.\a.ps1 dq`.
  - Verification: `BUILD SUCCESSFUL` (2026-07-04 03:31, `assembleStandardDebug`, exit 0).

---

## Last Audit

**Date:** 2026-07-04
**Mode:** strategic (no tactical folder - compact spec)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Checks:
- Portrait block (`layout/fragment_settings_destinations.xml:848`) - `layoutDefaultPlayerToggles` is `orientation="vertical"`, both `rowPrimaryMediaPlayer`/`rowAcceptSharedFiles` `match_parent`, no `layout_weight` - PASS (Goal 1 / Criterion 1).
- Landscape block (`layout-land/fragment_settings_destinations.xml:1078`) - unchanged, still `orientation="horizontal"`, both rows `layout_weight="1"` - PASS (Goal 2 / Criterion 2).
- Build - `BUILD SUCCESSFUL` recorded Step 3 - PASS (Criterion 3).
- String keys (`settings_primary_media_player`, `setting_primary_media_player_desc`, `settings_accept_shared_files`, `setting_accept_shared_files_desc`) - untouched, still referenced - PASS.
- `docs/FEATURES*` - §8 states "Без изменений" - EXEMPT.
- Debug-tag invariant - zero `Timber.d("S0881:` hits in `.kt` (spec never entered `BlockNeedUserTest`) - PASS.
- Dev log entries present for spec + layout change - PASS.

### Manual / on-device

- [ ] Optional visual sanity on a portrait device/emulator (static XML attributes already prove the layout; not required to close).
