# S0560 - Краш MaterialButton в диалогах share-флоу (потеря Material3-базы у прозрачной активити)

**Status:** Archived
**Priority:** 72
**Date:** 2026-06-20
**Tier:** 1 - Crash fix (release-affecting)
**Origin:** краш-репорт noLegal DEBUG 2026-06-20 04:54 (Samsung SM-S731B, Android 16, dark mode + dynamic colors)

---

## 1. Проблема

`android.view.InflateException` при инфляции `MaterialButton` в `layout/dialog_link_autodownload_progress` (строка 42), вызов из `LinkAutoDownloadProgressDialog.show()` через `ReceiveShareActivity.processLinkAutoDownload`.

Корневая ошибка: `java.lang.UnsupportedOperationException: Failed to resolve attribute at index 37` - неразрешённый `?attr` (TypedValue t=0x2).

Падающий атрибут - **собственный** атрибут приложения `?attr/dialogActionButtonMinHeight` (S0538), заданный через `android:minHeight` в стиле `Widget.FastMediaSorter.Button.DialogCancel`.

---

## 2. Корневая причина

- `ReceiveShareActivity` объявлена с `Theme.FastMediaSorter.Transparent` (полупрозрачная), а наследует `AppCompatActivity` напрямую, минуя `BaseActivity`.
- В рантайме AppCompat DayNight-делегат уплощает оконную тему на базу `Theme.AppCompat.Empty` / `Theme.DeviceDefault` через `applyStyle(force=false)`, не переразрешая полную Material3-цепочку (`.Transparent` → `.FastMediaSorter` → `.Base` → `.App` → `Theme.Material3.DayNight.NoActionBar`).
- `DynamicColors.applyToActivitiesIfAvailable` сверху накладывает только цветовой overlay, не восстанавливая базу.
- `?attr/dialogActionButtonMinHeight` задан только на двух узлах: `Theme.FastMediaSorter.App` (выпал при уплощении) и BaseActivity-only overlay `CompactDialogButtons` (не применяется, т.к. активити минует BaseActivity). Оба источника отсутствуют → атрибут не резолвится → краш.
- Условие триггера: dark mode + dynamic colors + свежий Android (без них уплощение может не воспроизводиться).

---

## 3. Масштаб (флейворы)

- Весь путь в `src/main`, без flavor-гейтинга; `Material 1.14.0` и тема одинаковы во всех флейворах.
- noLegal воспроизвёл лишь потому, что на нём тестировали; standard достижим и воспроизводит при тех же условиях.
- **Вывод: нужен фикс-релиз standard** (краш до запуска воркера, не зависит от noLegal-бэкенда загрузки).

---

## 4. Похожие сайты (тот же класс бага)

Все достижимы из `ReceiveShareActivity` (единственная прозрачная активити, инфлейтящая Material-виджеты):

- `LinkAutoDownloadProgressDialog` - `dialog_link_autodownload_progress` (репортнутый краш).
- `FileOperationDestinationDialog` - `dialog_copy_to` (основной share-to-folder путь).
- `FileOperationProgressDialog` - `dialog_file_operation_progress` (после выбора назначения).
- `ScrollableTextDialog` - `dialog_error_detail` (на ошибке/частичном успехе копирования).
- `WebViewAuthDialogFragment` - `dialog_webview_auth` (ветка предложения авторизации).

Прочие прозрачные активити (`ResourceImportActivity`, `StandalonePlayerDispatcherActivity`, `QuickAudioRecorderActivity`, `CameraQuickCaptureActivity`, noLegal `ScreenCaptureConsentActivity`) Material-виджеты под сырым контекстом не инфлейтят - не затронуты.

---

## 5. Решение

Инфлейтить под гарантированно-Material3 контекстом `ContextThemeWrapper(ctx, R.style.Theme_FastMediaSorter)`:

- `ReceiveShareActivity`: введён `dialogContext` (lazy `ContextThemeWrapper`), проброшен в `FileOperationDestinationDialog`, оба `MaterialAlertDialogBuilder` (auth-offer, CCT-unavailable).
- Цепочка `FileOperationDestinationDialog` → `FileOperationProgressDialog` → `ScrollableTextDialog` получает тему через проброшенный `context` - сами классы править не понадобилось.
- `LinkAutoDownloadProgressDialog`: оборачивает инфлейтер и `AlertDialog.Builder` (берёт `AppCompatActivity`, контекст не пробросить).
- `WebViewAuthDialogFragment`: `inflater.cloneInContext(themed)` в `onCreateView` (DialogFragment).

---

## 6. Верификация

- `compileStandardDebugKotlin` - PASS.
- Рекомендуется on-device проверка на repro-условиях (dark mode + dynamic colors): поделиться http(s)-ссылкой при включённых «Accept shared files» + «Link auto-download»; убедиться, что диалог прогресса и диалог копирования открываются без краша.

---

## 7. Следующий шаг

Включить в ближайший фикс-релиз standard; при наличии устройства прогнать `/spec-test-device S0560`.

## Last Audit

**Date:** 2026-06-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Все 5 сайтов из §4 покрыты каноничным паттерном Material3-темизации:

- `ReceiveShareActivity`: `dialogContext` (lazy `ContextThemeWrapper(this, R.style.Theme_FastMediaSorter)`); оба `MaterialAlertDialogBuilder(dialogContext)`; `context = dialogContext` проброшен в `FileOperationDestinationDialog` (цепочка к `FileOperationProgressDialog`/`ScrollableTextDialog`).
- `LinkAutoDownloadProgressDialog`: `LayoutInflater.from(themedContext)` + `AlertDialog.Builder(themedContext)`.
- `WebViewAuthDialogFragment`: `inflater.cloneInContext(ContextThemeWrapper(..))`.
- `R.style.Theme_FastMediaSorter` присутствует во всех вариантах themes.xml (day/night/v31/v35).
- Debug-tag инвариант: 0 тегов `Timber.d("S0560:` при статусе Implemented.

### Manual / on-device

- [ ] Repro-условия (dark mode + dynamic colors, свежий Android): поделиться http(s)-ссылкой при «Accept shared files» + «Link auto-download»; диалог прогресса и диалог копирования открываются без `InflateException`. Не воспроизводится на стандартном AVD - нужен реальный девайс (репорт: Samsung Android 16).
