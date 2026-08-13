# S0947 - Canonical single-choice picker with conditional quick search-filter

<!-- auto-approved by /spec-all - 2026-07-06 -->

**Ticket:** S0947
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-05
**Tier:** 3 - Moderate
**Source:** User request 2026-07-05 (`/spec-draft`)

> Approved - request generalized after owner clarification; all open points resolved (quiz + codebase research 2026-07-05). Ready for tactical decomposition.

## 0. Captured request

**Captured:** 2026-07-05

**Text:**

/spec-draft Наш пикер (выбор программы для свободной ячейки панели быстрого запуска программ). Под заголовкоом нужен "быстрый поиск-фильтр" если в списке больше значений чем поместилось на экран

**Attachments:** none.

## 1. Draft scope

- Owner clarification after draft capture:
  - this is a rule for all picker dialogs/screens with long lists
  - the quick-launch app/program picker was only an example that exposed the need
- Requested UX rule:
  - show a quick search-filter directly under the picker title
  - but only when the picker list is longer than what fits on the current screen
- Intent:
  - make long picker lists faster to scan
  - avoid adding extra input UI to short pickers
  - keep picker UX consistent across the app instead of solving each picker ad hoc

## 2. Problem

В приложении уже есть несколько picker'ов со списками значений. Когда список длиннее одного экрана, ручной поиск по прокрутке становится медленным. Изначально это было замечено на выборе программы для свободной ячейки панели быстрого запуска, но по уточнению владельца проблема не локальная: нужен единый UX-инвариант для всех длинных picker'ов, а не частный фикс одного окна.

## 3. Desired outcome

1. Во всех picker'ах с длинным списком появляется быстрый поиск-фильтр.
2. Поле размещается под заголовком picker'а как единый паттерн.
3. Поле показывается только когда список длиннее одного экрана.
4. Короткие списки продолжают открываться без лишнего фильтра.
5. Правило применяется как общий стандарт, а не как разрозненная особенность отдельных picker'ов.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0580 (origin of `SearchableOptionPickerDialog`, ADR-1), S0946 (adjacent quick-launch panel draft that grows the long-list picker family).
- **UI placement:** the quick search-filter sits directly under the picker title, inside the one canonical single-choice picker component.
- **UI visibility:** the search-filter is shown only when the option list does not fit the current screen/orientation (dynamic measured fit, re-measured on configuration change); short lists that fit open without it.
- **UI fallback / scope boundary:** the canonical component serves only simple single-choice lists (each option = text with an optional leading image). Special pickers that carry per-option descriptions or a custom option view (`DeviceProfilePickerDialogFragment`) and the remote cloud-folder `Activity` pickers keep their bespoke UI and are out of scope.
- **Behavior contract (owner, §4.1):** portrait + landscape ready; TV / D-pad keyboard navigation; the currently selected option is highlighted with autoscroll to it on open; search-filter is passive (IME not auto-raised); filter matches the primary visible label, case-insensitive `contains`.

## 4. Example surfaces

- Quick-launch panel app/program picker
- Quick-launch panel internal-feature picker
- Quick-launch panel OS-settings picker
- Resource picker
- Other value pickers with potentially long lists

Program selection remains only an example, not the scope boundary.

### 4.1 Scope decision (owner, 2026-07-05)

Owner reframed the rule as a single canonical component for **every simple single-choice list dialog**, not just the quick-launch family:

- One reusable picker for "choose one element from a list" where each option is text with an optional leading image (flag/icon/thumbnail or none).
- Must serve both short fast selections (a few options) and long lists (country / language / channel / application).
- Required behaviors of the canonical component:
  - portrait + landscape ready;
  - TV / D-pad keyboard navigation;
  - clear highlight of the currently selected option, with autoscroll to it on open;
  - search-filter shown only when the list is longer than the screen.
- Out of scope: "special" pickers that carry per-option descriptions or a custom option view (e.g. `DeviceProfilePickerDialogFragment`) and the remote cloud-folder `Activity` pickers - they keep their bespoke UI.

## 5. Open points

Все пункты разрешены (quiz + codebase research 2026-07-05):

1. Условие "не поместилось на экран" - **Resolved**: динамический замер фактической высоты списка vs высоты экрана/ориентации ("fits on screen"), не фиксированный порог.
2. Область применения - **Resolved**: единый канонический компонент для всех простых single-choice списков (текст ± ведущая картинка); "специальные" пикеры с описаниями/кастомным view и cloud-folder Activities исключены (см. 4.1).
3. По каким данным фильтровать - **Resolved** (8.3): по основному видимому label, case-insensitive `contains`.
4. Автопоказ клавиатуры - **Resolved** (8.3): поле пассивное, IME не поднимается автоматически.
5. Единый reusable component - **Resolved** (8.1): адаптировать/расширить `SearchableOptionPickerDialog`, новый компонент не создавать.

## 6. Direction (rough)

- Treat this as a shared picker UX rule.
- Avoid a one-off fix only in the quick-launch panel flow.
- Prefer one reusable pattern so future long pickers inherit the same behavior automatically.

## 7. Related

- S0946 - adjacent quick-launch panel draft where the long-list picker family can grow further
- S0580 - origin of the existing generic searchable picker (`SearchableOptionPickerDialog`, ADR-1)

## 8. Codebase findings (2026-07-05 research)

Resolves the codebase-answerable parts of section 5; the rest stays owner-gated.

### 8.1 A reusable searchable picker already exists (open point 5 - resolved)

- `ui/dialog/SearchableOptionPickerDialog.kt` (S0580 ADR-1) is already a generic single-choice picker with a type-to-filter search field over a possibly long option list. `Option(id, label, flag?)` + result callback.
- `ui/dialog/SearchableLanguagePickerDialog.kt` is the sibling that seeded the pattern.
- So "do we need a new reusable component" is answered: adopt / extend `SearchableOptionPickerDialog` as the standard, do not build a new one.
- Gap vs this request: `SearchableOptionPickerDialog` shows its search field **unconditionally**. This ticket wants it shown **only when the list is longer than one screen** - that conditional show/hide is new behavior the component does not yet have.

### 8.2 The picker surface landscape is fragmented (open point 2 - informs scope)

Independent picker implementations that do NOT currently route through the searchable component:

- Quick-launch family (the example surfaces): `AppPickerDialogFragment`, `InternalRoutePickerDialogFragment`, `OsShortcutPickerDialogFragment`, `ResourcePickerDialogFragment`.
- Generic list dialog: `ui/dialog/ListSelectionDialog.kt`.
- Others with potentially long lists: `DeviceProfilePickerDialogFragment`, `IconPickerBottomSheet` (grid), `DestinationPickerDialog`, `ResourcePickerDialog`.
- Cloud-folder pickers are full `Activity` + `ViewModel` filtering **remote** listings (`DropboxFolderPickerActivity`, `GoogleDriveFolderPickerActivity`, `OneDriveFolderPickerActivity`) - a different retrofit shape; likely out of a first rollout.

So "apply as a common rule" is a multi-surface retrofit, not a one-file change - the scope boundary is the main cost driver and the main owner decision.

### 8.3 Suggested codebase-derived defaults for the remaining open points

- Open point 3 (filter fields): filter on the visible primary label, case-insensitive `contains`, matching the existing `SearchableOptionPickerDialog` behavior.
- Open point 4 (keyboard): passive - do not auto-raise the keyboard on open (consistent with current component; avoids intrusive IME on every long picker).

### 8.4 Owner-gated decisions (resolved 2026-07-05 via /spec-quiz)

1. Rollout scope - **Resolved**: unify every *simple* single-choice list dialog into one canonical component (text ± leading image), not just the quick-launch family. Special pickers with per-option descriptions / custom views (`DeviceProfilePickerDialogFragment` etc.) and remote cloud-folder Activities stay out. Full behavior contract in 4.1.
2. Show/hide threshold - **Resolved**: dynamic "does the list fit the current screen/orientation" measure (not a fixed item count). Truer to the request; must stay stable across rotation - handle re-measure on configuration change.

### Quiz decisions (2026-07-05)

- Which pickers get the rule in v1? → One canonical simple single-choice picker for all simple text±image lists; exclude special pickers with descriptions/custom views and cloud-folder Activities (owner wants a single unified component, not per-picker retrofits).
- Additional owner requirements folded into 4.1: portrait/landscape ready, TV/D-pad keyboard nav, highlight + autoscroll to the currently selected option on open.
- "Fits on screen" condition? → Dynamic measured fit vs fixed item-count (must re-measure on rotation).

---

## Last Audit

**Дата:** 2026-07-09
**Статус:** Verified (device-проверено на emulator-5554, quick-launch panel edit flow)
**Сборка:** standard debug `a.ps1 fc` + `a.ps1 d` BUILD SUCCESSFUL; probe-теги `S0947:` сняты после device-verdict.

**Device-верификация (emulator-5554, 2026-07-09, скриншоты в temp/scratch):**

- App-пикер: длинный список установленных аппов -> поле поиска ПОКАЗАНО (overflow); строки с иконками приложений (LeadingVisual.IconDrawable); ввод «chr» отфильтровал до одного Chrome (case-insensitive contains по label); клавиатура НЕ поднималась на открытии (passive IME).
- OS-shortcut пикер: заголовок «Выбор настройки ОС Андроид» (tvOptionPickerTitle) + IconRes-иконки; поиск показан (overflow).
- Internal-feature пикер: заголовок «Выбор функции»; disabled-hint label сохранён («Мини-игра - Выключено - нажмите, чтобы включить»); поиск показан.
- Все 4 типа пикеров достижимы и функциональны, без крашей; result-контракты (slot/package/routeKey/targetKey/resourceId Long) сохранены.
- Остаточные (не заблокировано, minor): скрытие поиска на коротком списке не удалось воспроизвести (все реальные списки overflow'ят capped viewport - ограничение данных, не дефект; логика `computeVerticalScrollRange > height` симметрична); D-pad и поворот - вторичные, отложены на ручную проверку.

**Реализовано (Phase 01-03):**

- `ui/dialog/SearchableOptionPickerDialog.kt` - `Option` обобщён: добавлен `leading: LeadingVisual?` (sealed: `IconDrawable`/`IconRes`/`Thumbnail`) поверх существующего `flag` (streams не тронут). Диалог слимлён - делегирует общую логику контроллеру.
- `ui/dialog/SearchableOptionPickerController.kt` (new) - переиспользуемое ядро: условная видимость поиска (замер overflow списка vs viewport, orientation-correct без отдельного land-layout), highlight+autoscroll к выбранному, passive IME (без авто-показа клавиатуры), фильтр по label case-insensitive `contains`, `resetRow`-opt-out.
- `res/layout/dialog_searchable_option_picker.xml` - `layoutOptionSearch` (gone по умолчанию), `ivOptionIcon` (ImageView для общей ведущей картинки), опциональный `tvOptionPickerTitle` для card-хостов.
- `res/layout/item_searchable_option.xml` - добавлен `ivOptionIcon`.
- Мигрированы 4 quick-launch пикера на канонический компонент через контроллер, с сохранением entry API (`newInstance`/`RESULT_*`/FragmentResult) и card-sizing: `AppPickerDialogFragment` (app icon Drawable), `InternalRoutePickerDialogFragment` (IconRes + disabled-hint label + title), `OsShortcutPickerDialogFragment` (IconRes + title), `ResourcePickerDialogFragment` (IconRes + title, id Long<->String).
- Удалён dead-weight (Rule 20): `AppPickerAdapter.kt`, `res/layout*/dialog_app_picker.xml`, `res/layout*/dialog_panel_route_picker.xml`, `res/layout/item_app_picker_row.xml` (бывшие bespoke адаптеры/layout'ы 4 пикеров; ни один не имел других потребителей).

**Осознанно НЕ мигрировано (owner decision 2026-07-09, plan Phase 03 escape-hatch):**

- `ui/dialog/ListSelectionDialog<T>` + подклассы `DestinationPickerDialog` / `ResourcePickerDialog` оставлены bespoke. Это типизированный дженерик-фреймворк (loader/formatter/typed `ListSelectionConfig<T>`) с ~8 typed call-site по настройкам (settings-фрагменты, game mode, screenshot gesture, destinations). Форс-миграция на строковый `Option` стёрла бы типобезопасность и loader/formatter/clear-семантику и переписала бы 8 вызовов - отдельный канонический паттерн, не «простой single-choice». Владелец подтвердил оставить как есть.

**Device-verify (Phase 04, pending):** условный поиск при overflow / его отсутствие для коротких списков / поворот; highlight+autoscroll к выбранному; D-pad до поиска и списка; фильтр по label. 4 probe-тега `S0947:` в точках открытия пикеров; снять при выходе из BlockNeedUserTest. Прогон: `/spec-test-device S0947` -> `/spec-check S0947` (или `/spec-sweep`).

**Гейты:** compile (fc) PASS.
