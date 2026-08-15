# Спецификация (compact): S0979 - Кнопка выхода в диалоге «Быстрый запуск»

**Ticket:** S0979
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-10
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-10 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-10

**Текст:**

ещё драфт чтобы добавить кнопку выхода перед заголовком диалога "быстрый запуск"

---

## 1. Цель

У диалога быстрого запуска (`AppLaunchPanelDialogFragment`, заголовок `app_launch_panel_title` = «Быстрый запуск») нет видимой кнопки закрытия - выйти можно только системным back или тапом вне области. Добавить видимую иконку-кнопку закрытия в начале строки заголовка (перед `tvPanelTitle`), закрывающую панель. Хост-активность (`AppLaunchPanelActivity`) сама завершается при detach диалога, поэтому кнопке достаточно `dismiss()`.

Область: `ui/applaunchpanel`, layout `dialog_app_launch_panel.xml`. Standard flavor, `src/main`. У layout нет land-варианта (окно масштабируется программно в `onStart`), Rule 11 неприменим.

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI placement:** иконка-кнопка закрытия - первый элемент строки заголовка, слева от `tvPanelTitle` (кнопка Edit остаётся справа).
- **Локализация:** переиспользуется существующий ключ `close` (EN/RU/UK уже есть), новых строк нет.
- **Доступность:** `contentDescription=@string/close`, `focusable=true`, D-pad-достижимость.

---

## Фазы

### Фаза 01 - layout: кнопка закрытия перед заголовком

- Файл: `app_v2/src/main/res/layout/dialog_app_launch_panel.xml`.
- В горизонтальный header-`LinearLayout` первым дочерним элементом (перед `tvPanelTitle`) добавить `MaterialButton` `btnClosePanel`: icon-only стиль, `app:icon="@drawable/ic_clear"`, тинт `?attr/colorOnSurface`, `contentDescription="@string/close"`, `focusable=true`.
- Без хардкод-hex (Rule 19). Заголовок сохраняет `layout_weight=1`.
- Verification: layout инфлейтится; `assembleStandardDebug` компилируется.

### Фаза 02 - fragment: обработчик закрытия

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelDialogFragment.kt`.
- В `onViewCreated` привязать `binding.btnClosePanel.setOnClickListener { dismiss() }`. Хост завершится сам через `onFragmentDetached`.
- Verification: `assembleStandardDebug` компилируется; detekt-гейт зелёный.

---

## 4. Проверка

On-device (эмулятор, standard debug): открыть панель быстрого запуска (жест/виджет) -> в строке заголовка слева видна иконка «X» -> тап закрывает панель (диалог и прозрачный хост исчезают), без запуска плиток. Кнопка достижима с D-pad.

---

## Last Audit

### Manual (device) - 2026-07-19

- **Verdict:** PASS
- **Device:** emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35)
- **Build:** com.sza.fastmediasorter.debug v2.60.7182.317-DEBUG (pre-installed, not rebuilt)
- **Entry point:** MainActivity -> "More actions" dropdown -> "Quick launch" -> AppLaunchPanelActivity.

Criteria:

1. X icon at start of title row, left of title (Edit stays right).
   - expected: close button is the first element in the header row, left of `tvPanelTitle`; Edit control on the right.
   - actual: PASS. `btnClosePanel` bounds `[74,342][199,468]` (leftmost); `tvPanelTitle` starts at x=210; `btnEditPanel` at x=774 (right). Confirmed visually in `01_panel_open.png`.
2. Tapping X closes panel (dialog + transparent host dismiss), no tile launched.
   - expected: dialog and `AppLaunchPanelActivity` both dismiss; no tile activity started.
   - actual: PASS. topResumedActivity went `AppLaunchPanelActivity` -> `MainActivity` after the tap; host record finishing (`t-1 f`); no Calculator/Streams/Settings tile activity present (`02_after_close.png`).
3. X is D-pad reachable.
   - expected: close button focusable and reachable via D-pad.
   - actual: PASS. `focusable="true"`; DPAD_UP navigation lands focus on `btnClosePanel` (content-desc "Close").
4. Probe marker.
   - actual: logcat captured `S0979: quick-launch panel close tapped` at tap time (source `AppLaunchPanelDialogFragment.kt:61`).

Evidence: `temp/S0979/01_panel_open.png`, `temp/S0979/02_after_close.png`, `temp/S0979/logcat_S0979.txt`.
