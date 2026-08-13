# Спецификация (compact bugfix): S1062 - Сетка трансляций: пункт «Закрепить/Открепить» в меню + бейдж «закреплён»

**Ticket:** S1062
**Status:** Archived
**Priority:** 95
**Date:** 2026-07-15
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-15 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-15

**Текст:**

трансляции плитками (сеткой) у нас нет кнопки "закрепитьЪоткрепить" которая определяет порядок в списке. Нужно в меню три кнопки (дропдаун) которое есть у плитки самым верхним (первым ) пунктом добавить "Закрепить" или "Открепить" в зависимости от текущего знаычения. А в у тех кототекущее значение "закреплён" в левом верхнем углу над самой картинкой миниатюры (мы всё ещ только про сетку трансляций) нарисовать меленькую красную кнопку "закреплен", но она не нажимается - управление только через меню которое я описал выше.
это баг его нужно решить сейчас до публикации релиза

---

## Goal (RU)

В режиме сетки трансляций (плитки) нет явной команды «Закрепить/Открепить» и нет визуального признака закреплённой плитки - закрепить можно только неочевидным долгим нажатием (S0695), а результат виден лишь после пересортировки. Добавить в трёхточечное меню плитки самым первым пунктом «Закрепить»/«Открепить» (лейбл зависит от текущего состояния) и рисовать в левом верхнем углу поверх миниатюры маленький красный не-нажимаемый бейдж «Закреплён» у закреплённых плиток. Управление - только через меню; бейдж чисто индикатор.

## 1. Проблема / симптом

- Экран трансляций в режиме сетки (`StreamGridAdapter`, `item_stream_grid_cell.xml`) не имеет команды закрепления в трёхточечном меню плитки, в отличие от строки списка (у которой есть кнопка `btnPin`).
- У плитки нет визуального индикатора закреплённого состояния - пользователь не понимает, закреплена ли трансляция.
- Затронуто на flavors, где есть Streams: standard, noLegal, legacy, vr (`SUPPORT_STREAMS=true`). Не затрагивает lite/photos (там Streams отсутствует по gate).

## 2. Корневая причина

- Доменная логика закрепления существует полностью: `StreamSourceEntity.pinned: Boolean`, toggle `StreamsViewModel.onPin(source)` (unpin если pinned, иначе pin) уже прокинут в `StreamGridAdapter(onPin = { viewModel.onPin(it) })`.
- Пробел чисто UI-обнаружимости: в программно строящемся `PopupMenu` плитки (`StreamGridAdapter.VH.bind`) нет пункта pin/unpin, а в layout плитки нет overlay-бейджа закрепления. Новые UseCase/Repository/ViewModel-методы не требуются.

## 3. Исправление

Три фазы. Все правки в `app_v2`, feature-area `ui/streams`. Landscape-варианта у `item_stream_grid_cell.xml` нет - правится только `res/layout/`.

### Phase 1 - Resources (strings + color + drawable)

Step 1.1. Add red badge color token in `app_v2/src/main/res/values/colors.xml`: `stream_pin_badge_bg` = `#FFC62828` (Red 800; distinct element from the play-status dots, top-left text pill vs bottom-left status disc).
- Verification: `Select-String app_v2/src/main/res/values/colors.xml -Pattern 'stream_pin_badge_bg'` returns 1 line.

Step 1.2. Add rounded-pill badge drawable `app_v2/src/main/res/drawable/bg_stream_pin_badge.xml`: `<shape rectangle>` with `corners radius` and `solid @color/stream_pin_badge_bg`. No hardcoded hex in the drawable (references the color token).
- Verification: file exists and references `@color/stream_pin_badge_bg`.

Step 1.3. Add three string keys across EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`:
- `streams_pin` = Pin / Закрепить / Закріпити
- `streams_unpin` = Unpin / Открепить / Відкріпити
- `streams_pinned_badge` = Pinned / Закреплён / Закріплено
- Verification: `scripts/check_strings_localized.ps1 -KeyPrefix "streams_pin"` exits 0 (parity EN/RU/UK).

### Phase 2 - Grid tile badge view

Step 2.1. In `app_v2/src/main/res/layout/item_stream_grid_cell.xml` add a non-clickable `TextView` `@+id/tvPinBadge` anchored top-left of `@id/ivFrame`:
- `background="@drawable/bg_stream_pin_badge"`, small padding, `textColor="@android:color/white"`, `textSize="10sp"`, `textStyle="bold"`, `maxLines="1"`, `text="@string/streams_pinned_badge"`, `android:visibility="gone"` (shown at bind time only when pinned).
- Not clickable / not focusable (pure indicator). Top-left is free (overflow is top-right, status dot + title are bottom).
- Verification: layout inflates; `ItemStreamGridCellBinding` regenerates a `tvPinBadge` field (proven by Phase 3 compile).

### Phase 3 - Adapter wiring (`StreamGridAdapter.kt`)

Step 3.1. Add companion const `ID_TOGGLE_PIN = 9`.

Step 3.2. In the `PopupMenu` builder, add the pin/unpin item as the FIRST `menu.add` (before the S0938 reorder block), so it is the topmost item unconditionally:
- Label = `if (source.pinned) R.string.streams_unpin else R.string.streams_pin`.
- `menu.add(Menu.NONE, ID_TOGGLE_PIN, Menu.NONE, <label>)`.

Step 3.3. Add dispatch branch `ID_TOGGLE_PIN -> { onPin(source); true }` in `setOnMenuItemClickListener` (reuses the already-injected toggle).

Step 3.4. In `VH.bind`, set `binding.tvPinBadge.isVisible = source.pinned` (add `import androidx.core.view.isVisible`).
- Verification: `.\a.ps1 fk` (standard Kotlin compile) PASS; menu shows Закрепить/Открепить as first item; pinned tile shows red badge top-left; unpinning hides badge on next bind.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0695 (long-press pin toggle on tile), S0783 (grid favorites toggle - menu pattern), S0938 (pinned reorder menu block), S1061 (empty streams panel hint - sibling ad-hoc)
- **UI scope:** streams grid tile menu + tile overlay badge; no home-panel (`MainStreamsPanelManager`) change.
- **Flavor scope:** standard, noLegal, legacy, vr (Streams-enabled); lite/photos unaffected (feature gated out).
- **Data scope:** no schema change - `StreamSourceEntity.pinned` already exists.

---

## 4. Проверка

- Build: `.\a.ps1 fk` (standard Kotlin compile) PASS after Phase 3.
- String parity: `scripts/check_strings_localized.ps1 -KeyPrefix "streams_pin"` exit 0.
- On-device (Streams-enabled flavor, grid mode): open a stream tile's three-dot menu -> first item is «Закрепить» (unpinned) / «Открепить» (pinned); tapping toggles state and re-sorts; a pinned tile shows a small red «Закреплён» badge top-left over the thumbnail; the badge is not tappable (only the menu changes state).

---

## Last Audit

**Date:** 2026-07-15
**Status:** Verified
**Method:** build + on-device verification (emulator-5554, standard debug, English locale).

Delivered files (this pipeline run):
- `app_v2/src/main/res/values/colors.xml` - added `stream_pin_badge_bg` (#FFC62828).
- `app_v2/src/main/res/drawable/bg_stream_pin_badge.xml` - new rounded red pill referencing the color token.
- `app_v2/src/main/res/values*/strings.xml` (EN/RU/UK) - `streams_pin`, `streams_unpin`, `streams_pinned_badge`.
- `app_v2/src/main/res/layout/item_stream_grid_cell.xml` - added non-clickable `tvPinBadge` (top-left of `ivFrame`, gone by default).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` - `ID_TOGGLE_PIN` const, topmost Pin/Unpin menu item (label per `source.pinned`), dispatch to existing `onPin`, `tvPinBadge.isVisible = source.pinned`.

Evidence:
- `.\a.ps1 fc` (code + resources) PASS; full debug APK build PASS; `.\a.ps1 fk` after debug-tag removal PASS.
- String parity `check_strings_localized.ps1 -KeyPrefix "streams_pin"` exit 0 (EN/RU/UK).
- Device: grid-mode tile menu first item = "Pin" when unpinned; after tap the tile shows a red "Pinned" badge top-left and the menu first item flips to "Unpin"; tapping "Unpin" removes the badge. Debug probe logged both toggles (`pinned=false` -> pin, `pinned=true` -> unpin), then removed on transition out of BlockNeedUserTest.

No residual gaps. Reorder items (S0938) still lead only when >1 channel is pinned; Pin/Unpin sits above them - matches "topmost" requirement.
