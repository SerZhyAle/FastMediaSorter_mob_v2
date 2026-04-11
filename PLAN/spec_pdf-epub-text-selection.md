# Specification: NEW.1 — Unified Text Selection Menu for TXT, PDF & EPUB

**Status:** Draft  
**Date:** 2026-04-11  
**Tier:** 3 — Moderate (4–8h, medium risk)  
**Roadmap entry:** *(New item — not yet in IMPROVEMENT_ROADMAP.md. Proposed for TIER 3.)*  
Добавить единое плавающее меню над выделенным текстом для всех форматов документов
(TXT, EPUB, PDF). Меню содержит стандартные системные пункты плюс «Перевести»
(если перевод включён в настройках) и «Найти в Google». TXT уже имеет нативное
выделение — нужно лишь расширить его меню. EPUB и PDF приводятся к тому же поведению.

---

## 1. Problem Statement

### TXT (базовый сценарий — уже работает частично)
`TextViewerManager` отображает текст в `tvTextContent` (`TextView` с `textIsSelectable=true`).
Стандартное Android-выделение уже работает: долгий тап → рукоятки выделения → плавающий
`ActionMode` с пунктами «Копировать», «Поделиться», «Выбрать всё».
**Чего не хватает:** пунктов «Перевести» и «Найти в Google» — они не входят в системный ActionMode.
Кнопка `btnTranslateTextCmd` переводит весь текст файла, а не выделенный фрагмент.
Этот готовый UX-паттерн (плавающий ActionMode над выделением) является **эталоном**
для EPUB и PDF.

### EPUB
`EpubViewerManager` отображает главы в `WebView`. Флаги `isLongClickable = true`
и возврат `false` из `setOnTouchListener` теоретически разрешают стандартное выделение
WebView, однако контекстное меню содержит только системные пункты («Копировать»,
«Выбрать всё» и т. д.). Пунктов «Перевести» и «Найти в Google» нет.

### PDF
`PdfViewerManager` рендерит каждую страницу PDF как `Bitmap` в `PhotoView`.
Выделение текста на растровом изображении системой Android не поддерживается.
Текущая работа с текстом: полностраничное OCR (ML Kit) через `extractTextFromCurrentPage()`
и `copyPageTextToClipboard()` — без возможности выбрать только нужный фрагмент.

В standalone-режиме (`StandaloneViewManager`) все три вьюера создаются идентично —
все точки входа затронуты.

**Итог:** во всех трёх форматах документов отсутствует единый UX выделения с пунктами
«Перевести» и «Найти в Google». TXT ближе всего к цели — нужно только добавить пункты.
EPUB и PDF нужно привести к тому же паттерну.

---

## 2. Goals

1. **Единый UX**: плавающий Android `ActionMode` над выделенным текстом — единственный
   и одинаковый способ взаимодействия для TXT, EPUB и PDF.
2. **TXT**: расширить существующий ActionMode двумя новыми пунктами —
   **«Перевести»** и **«Найти в Google»** — поверх стандартных («Копировать», «Поделиться», «Выбрать всё»).
3. **EPUB**: при долгом нажатии на слово появляются рукоятки выделения WebView
   и тот же набор пунктов, что у TXT.
4. **PDF**: кнопка «Текст» в панели PDF открывает оверлей с `SelectableTextView`,
   дающий идентичный выделению и ActionMode UX.
5. **«Перевести»** — видим только если `BuildConfig.ENABLE_TRANSLATION = true` и в настройках
   включён переводчик; передаёт выделенный фрагмент в `TranslationManager.translateTextDirect()`.
6. **«Найти в Google»**: открывает браузер по умолчанию —
   `https://www.google.com/search?q=<encoded_text>`.
7. **«Копировать»** и остальные системные пункты: не изменяются, остаются штатными.
8. Работает и в `PlayerActivity`, и в `StandalonePlayerActivity`
   (одни и те же менеджеры — одна реализация).
9. Функция доступна в `standard` и `legacy` флейворах (`SUPPORT_DOCUMENTS = true`);
   «Перевести» дополнительно управляется `ENABLE_TRANSLATION`.

**Non-goals:**
- Синхронизация выделения с position-saving (закладки на фрагмент).
- Аннотации / постоянная подсветка (Highlight-as-annotation).
- Нативное in-place выделение текста прямо по PDF-растру (требует API 35 + custom renderer).
- Форматы `.djvu` — не поддерживается приложением.
- Wear OS — документы в `wear/` не отображаются.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor     | Affected? | Примечание |
|------------|:---------:|-----------|
| `standard` | ✅        | `SUPPORT_DOCUMENTS = true`, `ENABLE_EPUB = true`, `ENABLE_TRANSLATION = true` |
| `lite`     | ❌        | `SUPPORT_DOCUMENTS = false` — документы не компилируются в этот флейвор |
| `photos`   | ❌        | `SUPPORT_DOCUMENTS = false` |
| `legacy`   | ✅        | `minSdk = 23`; TXT/EPUB — без ограничений; PDF text extraction fallback через OCR (см. § 3.2) |

Никакого нового `BuildConfig`-флага не нужно: фича управляется существующим
`SUPPORT_DOCUMENTS` + `ENABLE_TRANSLATION`.

### 3.2 Android API Level Forks

| API level | Поведение |
|-----------|-----------|
| 23–25 (`legacy`) | PDF text extraction: только OCR via `TranslationManager.extractTextOnly()`.  `PdfRenderer.Page.getTextContents()` недоступен. |
| 26–34 (`standard` default) | Аналогично: OCR-fallback для PDF; WebView ActionMode для EPUB работает корректно на API 26+. |
| 35+ (Android 15) | PDF: используем `@RequiresApi(35) PdfRenderer.Page.getTextContents()` для точного извлечения текста без OCR; OCR — только если `getTextContents()` вернул пустой результат. |

### 3.3 Wear OS Impact

No Wear OS changes required. Документы не поддерживаются в `wear/` модуле.

---

## 4. Current Architecture (Relevant Parts)

| Компонент | Файл | Роль |
|-----------|------|------|
| `TextViewerManager` | `ui/player/helpers/TextViewerManager.kt` (1 764 строки) | TXT-файлы в `tvTextContent` (`TextView`); нативное выделение уже работает; перевод кнопкой (весь текст) |
| `EpubViewerManager` | `ui/player/helpers/EpubViewerManager.kt` (2 062 строки) | HTML-рендер в `WebView`; выделение WebView работает; кастомного ActionMode нет |
| `PdfViewerManager` | `ui/player/helpers/PdfViewerManager.kt` (1 568 строк) | Рендер страниц в `PhotoView` как `Bitmap`; нет выделения текста; полностраничный OCR через кнопки |
| `TranslationManager` | `ui/player/helpers/TranslationManager.kt` | ML Kit OCR + перевод; `translateTextDirect(text, src, tgt)` — прямой перевод строки без OCR |
| `PlayerCommandPanelCallbackImpl` | `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Обработка команд кнопок всех документных типов |
| `StandaloneViewManager` | `ui/player/helpers/StandaloneViewManager.kt` | Создаёт все три менеджера для standalone-пути |

**Ключевая идея**: TXT уже реализует эталонный UX («Копировать», «Поделиться», «Выбрать всё»
через нативный Android ActionMode на `TextView`). Задача для EPUB и PDF — достичь того же паттерна.

**Ограничение PDF**: `PhotoView` рендерит `Bitmap` — выделение текста на изображении в Android
недоступно. Решение: оверлей с `SelectableTextView`, куда загружается извлечённый текст страницы
(OCR для API < 35; `PdfRenderer.Page.getTextContents()` на API 35+). Пользователь выделяет текст
в оверлее — получает тот же ActionMode, что у TXT.

---

## 5. Proposed Architecture

### 5.1 Единый подход: `DocumentSelectionActionModeCallback`

Один общий класс реализует `ActionMode.Callback` для всех трёх форматов.
В `onCreateActionMode` добавляем только наши новые пункты — системные
(«Копировать», «Поделиться», «Выбрать всё») добавляются платформой автоматически.

```kotlin
// ui/player/helpers/DocumentSelectionActionModeCallback.kt
class DocumentSelectionActionModeCallback(
    private val showTranslate: Boolean,           // BuildConfig.ENABLE_TRANSLATION
    private val getSelectedText: () -> String,    // зависит от вида: TextView.selectedText vs JS eval
    private val onTranslate: (String) -> Unit,
    private val onSearchGoogle: (String) -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.document_selection_menu, menu)
        menu.findItem(R.id.action_translate_selection)?.isVisible = showTranslate
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val text = getSelectedText()
        if (text.isBlank()) return false
        return when (item.itemId) {
            R.id.action_translate_selection -> { onTranslate(text); mode.finish(); true }
            R.id.action_search_google       -> { onSearchGoogle(text); mode.finish(); true }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
```

**Общий ресурс меню** `res/menu/document_selection_menu.xml`  
_(одно меню для TXT, EPUB и PDF-оверлея):_
```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/action_translate_selection"
          android:title="@string/action_translate"
          android:showAsAction="ifRoom" />
    <item android:id="@+id/action_search_google"
          android:title="@string/action_search_google"
          android:showAsAction="ifRoom" />
</menu>
```

**Общий хелпер для Intent «Найти в Google»** — статическая функция в companion или top-level:
```kotlin
fun openGoogleSearch(context: Context, text: String) {
    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
```

---

### 5.2 TXT — расширение существующего ActionMode

`tvTextContent` — обычный `TextView` с `textIsSelectable=true`. Android-платформа
управляет ActionMode сама. Чтобы добавить кастомные пункты, нужно
`tvTextContent.customSelectionActionModeCallback = DocumentSelectionActionModeCallback(…)`.

Изменения в `TextViewerManager.setupControls()` (≤ 15 новых строк):
```kotlin
safeViews.tvTextContent.customSelectionActionModeCallback =
    DocumentSelectionActionModeCallback(
        showTranslate  = BuildConfig.ENABLE_TRANSLATION,
        getSelectedText = {
            val start = safeViews.tvTextContent.selectionStart.coerceAtLeast(0)
            val end   = safeViews.tvTextContent.selectionEnd.coerceAtLeast(0)
            safeViews.tvTextContent.text?.substring(
                minOf(start, end), maxOf(start, end)
            ) ?: ""
        },
        onTranslate    = ::translateSelectedText,
        onSearchGoogle = { openGoogleSearch(context, it) }
    )
```

`translateSelectedText(text: String)` — новый приватный метод в `TextViewerManager`:
вызывает `TranslationManager.translateTextDirect()` и показывает результат в
существующем `translationOverlay` (тот же overlay, что используется для перевода всего текста).

---

### 5.3 EPUB — кастомный ActionMode (WebView)

WebView нативно поддерживает выделение. Устанавливаем
`webView.setCustomSelectionActionModeCallback(DocumentSelectionActionModeCallback(…))`
в `EpubViewerManager` после инициализации WebView.

> **Проблема async**: `getSelectedText` для WebView требует `evaluateJavascript()` —
> асинхронная операция. Решение: установить JS-интерфейс `EpubSelectionBridge`
> (`addJavascriptInterface`), который слушает событие `selectionchange` и сохраняет
> последнее выделение в `@Volatile var lastSelectedText: String`. `getSelectedText`
> просто читает это поле.

```kotlin
// Инжектируемый JS в HTML главы (добавляется при loadData):
document.addEventListener('selectionchange', () => {
    EpubSelectionBridge.onSelectionChanged(window.getSelection().toString());
});
```

```kotlin
inner class EpubSelectionBridge {
    @Volatile var lastSelectedText: String = ""
    @JavascriptInterface
    fun onSelectionChanged(text: String) { lastSelectedText = text }
}
```

Изменения в `EpubViewerManager` (≤ 40 новых строк):
```kotlin
private val selectionBridge = EpubSelectionBridge()

private fun initWebView() {
    webView?.addJavascriptInterface(selectionBridge, "EpubSelectionBridge")
    webView?.setCustomSelectionActionModeCallback(
        DocumentSelectionActionModeCallback(
            showTranslate  = BuildConfig.ENABLE_TRANSLATION,
            getSelectedText = { selectionBridge.lastSelectedText },
            onTranslate    = ::handleTranslateSelection,
            onSearchGoogle = { openGoogleSearch(binding.root.context, it) }
        )
    )
}

private fun handleTranslateSelection(text: String) {
    coroutineScope.launch(Dispatchers.IO) {
        val settings = settingsRepository.getSettings().first()
        val translated = translationManager.translateTextDirect(
            text,
            TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage),
            TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        )
        withContext(Dispatchers.Main) {
            if (translated != null) callback.displayTranslatedText(translated)
            else callback.showError(binding.root.context.getString(R.string.translation_failed))
        }
    }
}
```

---

### 5.4 PDF — «режим выделения текста» через оверлей

Тот же `DocumentSelectionActionModeCallback`, только `getSelectedText` читает
выделение из `SelectableTextView` в оверлее (как у TXT — синхронно).

**UX-поток:**
1. В панели PDF появляется кнопка `btnSelectTextMode` (иконка «T»).
2. Нажатие → `PdfTextSelectionManager.enterTextSelectionMode()`:
   - запускает извлечение текста (OCR или нативно на API 35);
   - показывает `pdfTextSelectionOverlay` (полупрозрачный `FrameLayout`) поверх `PhotoView`;
   - внутри — `SelectableTextView` с полным текстом страницы;
   - `SelectableTextView.customSelectionActionModeCallback = DocumentSelectionActionModeCallback(…)`;
   - кнопка «✕» закрывает оверлей.
3. Пользователь выделяет фрагмент → ActivityMode → выбирает пункт → действие.

**Новый класс `PdfTextSelectionManager`** (≤ 200 строк):

| Метод | Описание |
|-------|----------|
| `enterTextSelectionMode()` | Запускает извлечение текста, показывает оверлей |
| `exitTextSelectionMode()` | Скрывает оверлей |
| `isInTextSelectionMode(): Boolean` | Состояние |

**Логика извлечения текста (внутри `PdfTextSelectionManager`):**
```kotlin
suspend fun extractPageText(pageIndex: Int, bitmap: Bitmap): String =
    if (Build.VERSION.SDK_INT >= 35) extractTextNative(pageIndex)
    else                             extractTextOcr(bitmap)

@RequiresApi(35)
private suspend fun extractTextNative(pageIndex: Int): String {
    return withContext(pdfDispatcher) {
        val page = pdfRenderer?.openPage(pageIndex) ?: return@withContext ""
        try { page.getTextContents().joinToString(" ") { it.value } }
        finally { page.close() }
    }
}

private suspend fun extractTextOcr(bitmap: Bitmap): String {
    val settings = settingsRepository.getSettings().first()
    val lang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
    return translationManager.extractTextOnly(bitmap, lang) ?: ""
}
```

---

### 5.5 Новые классы / файлы

| Класс / Файл | Путь | Бюджет |
|-------------|------|--------|
| `DocumentSelectionActionModeCallback.kt` | `ui/player/helpers/` | ≤ 80 строк |
| `PdfTextSelectionManager.kt` | `ui/player/helpers/` | ≤ 200 строк |
| `document_selection_menu.xml` | `res/menu/` | — |
| `layout_pdf_text_selection_overlay.xml` | `res/layout/` | ≤ 30 строк XML |
| String resources (EN/RU/UK) | `values/strings.xml` x3 | ~4 строки |

### 5.6 Строки для локализации

| Key | EN | RU | UK |
|-----|----|----|----|
| `action_translate` | "Translate" | "Перевести" | "Перекласти" |
| `action_search_google` | "Search in Google" | "Найти в Google" | "Шукати в Google" |
| `pdf_text_mode_enter` | "Text selection" | "Режим выделения текста" | "Режим виділення тексту" |
| `pdf_text_extracting` | "Extracting text…" | "Извлечение текста…" | "Витягування тексту…" |
| `pdf_text_empty` | "No text found on this page" | "Текст на странице не найден" | "Текст на сторінці не знайдено" |

### 5.7 Architecture Compliance

| Правило | Статус | Примечание |
|---------|:------:|----------|
| Нет бизнес-логики в Activity / Fragment | ✅ | Вся логика в `PdfTextSelectionManager`, `DocumentSelectionActionModeCallback`, менеджерах |
| Именование классов | ✅ | `DocumentNounCallback` (Callback), `PdfNounManager` (Manager) |
| Data flow: `UI → ViewModel → UseCase → Repository` | ✅ / N/A | «Перевести» → `TranslationManager` — уже инстанциирован во всех трёх менеджерах |
| Только `Timber`, нет `Log.d()` | ✅ | |
| Room schema version | N/A | Нет изменений в БД |
| Hilt DI | ✅ / N/A | `DocumentSelectionActionModeCallback` — data class, инстанциируется в менеджерах; не нужно в DI-графе |

---

## 6. Data Flow

Общая схема одинакова для всех трёх форматов. Разница — только в источнике выделенного текста.

```
┌─────────────────────────────────────────────────────────────────┐
│                 Пользователь выделяет текст                     │
│                                                                 │
│  TXT           │  EPUB              │  PDF                      │
│  долгий тап    │  долгий тап        │  нажать «T» (Select Text) │
│  на tvTextContent│  на WebView       │  → PdfTextSelectionManager│
│                │                    │  → ocrOverlay             │
└────────────────────────────────────────────────────────────────┘
                      │
                      ▼
       Плавающий ActionMode (системные пункты платформы)
         + DocumentSelectionActionModeCallback добавляет:

    ┌─────────────────────┬────────────────────────┐
    │  «Перевести»        │  «Найти в Google»       │
    │  (если ENABLE_TRANS)│                        │
    └─────────┬───────────┴──────────┬─────────────┘
              │                      │
              ▼                      ▼
  getSelectedText()           getSelectedText()
    TXT: TextView.selectedText   → openGoogleSearch(context, text)
    EPUB: selectionBridge.last       │
    PDF: SelectableTextView.sel      ▼
              │             Intent(ACTION_VIEW,
              ▼             "https://www.google.com/search?q=...")
   XxxManager.handleTranslateSelection(text)        │
              │                               Браузер по умолчанию
              ▼
   TranslationManager.translateTextDirect(text, src, tgt)
              │   (Dispatchers.IO, ML Kit)
              ▼
   callback.displayTranslatedText(translated)
              │
              ▼
   translationOverlay.show() — существующий overlay
```

### PDF — вход в режим выделения текста (дополнительный шаг)

```
Пользователь: нажимает «T» в PDF-панели
    │
    ▼
PdfViewerManager → PdfTextSelectionManager.enterTextSelectionMode()
    │
    ▼
extractPageText(pageIndex, currentPageBitmap)
    ├── [API 35+]  PdfRenderer.Page.getTextContents() → String
    └── [API 23–34] TranslationManager.extractTextOnly(bitmap, lang) → String (OCR)
    │
    ▼
pdfTextSelectionOverlay.show() → SelectableTextView.text = extractedText
Selectable​TextView.customSelectionActionModeCallback = DocumentSelectionActionModeCallback(…)
    │
    ▼  (далее общий поток выше)
```

---

## 7. Files to Modify

| Файл | Изменение | Оценка после |
|------|-----------|:------------:|
| `TextViewerManager.kt` (1 764 строки) | В `setupControls()`: установить `tvTextContent.customSelectionActionModeCallback`; добавить `translateSelectedText(text)` ≤ 20 строк | ~1 785 строк |
| `EpubViewerManager.kt` (2 062 строки) | В `initWebView()`: добавить `EpubSelectionBridge` + JS-сниппет + `setCustomSelectionActionModeCallback()`; добавить `handleTranslateSelection()` ≤ 40 строк | ~2 100 строк |
| `PdfViewerManager.kt` (1 568 строк) | Добавить делегат к `PdfTextSelectionManager`; подключить кнопку `btnSelectTextMode` | ~1 590 строк |
| `PlayerManagerInitializer.kt` | `btnSelectTextMode.setOnClickListener` для PDF | ~+5 строк |
| `PlayerCommandPanelCallbackImpl.kt` | Обработчик `onSelectTextMode()` для PDF | ~+10 строк |
| `activity_player_unified.xml` | `btnSelectTextMode` в PDF-панель; `pdfTextSelectionOverlay` FrameLayout | ~+30 строк XML |
| `values/strings.xml` + RU/UK | 5 новых строк | — |

> **Backup-правило**: `TextViewerManager.kt` (1 764 строки), `PdfViewerManager.kt` (1 568 строк),
> `EpubViewerManager.kt` (2 062 строки) — все > 500 строк → timestamped-бэкапы в `temp/` перед изменением.

---

## 8. Risk Analysis

| Риск | Вероятность | Митигация |
|------|:-----------:|-----------|
| EPUB: JS `selectionchange` не срабатывает при выборе через стрелки доступности | Низкая | Вторичный fallback: `evaluateJavascript("window.getSelection().toString()", …)` вызывать в `onPrepareActionMode` и снова обновлять `lastSelectedText` |
| EPUB: `addJavascriptInterface` сохраняется при `loadData` новой главы — мост живёт пока жив WebView | Низкая | Мост регистрируется один раз в `initWebView()`; `lastSelectedText` сбрасывается в `""` в `loadChapter()` |
| EPUB: `setCustomSelectionActionModeCallback` может конфликтовать с жестами свайпа | Низкая | `setOnTouchListener` возвращает `false` — WebView получает все события; ActionMode активируется независимо |
| PDF: `PdfRenderer` не thread-safe; `getTextContents()` (API 35) на другом диспетчере | Средняя | Использовать существующий `pdfDispatcher` (`limitedParallelism(1)`) |
| PDF: OCR на слабых устройствах — задержка 1–3 сек при входе в text mode | Средняя | Прогресс-бар + строка «Extracting text…»; оверлей показывается только после завершения |
| `EpubViewerManager` (2 062 строки) + `TextViewerManager` (1 764 строки) растут | Высокая | Вся новая логика в `DocumentSelectionActionModeCallback.kt` (отдельный файл); изменения в менеджерах ≤ 40 строк каждый |
| Пункт «Перевести» виден даже при `ENABLE_TRANSLATION = false` | Низкая | `menu.findItem(R.id.action_translate_selection)?.isVisible = showTranslate` в `onCreateActionMode` |

---

## 9. Testing Plan

### 9.1 Unit Tests

**`DocumentSelectionActionModeCallbackTest`** (`test/ui/player/helpers/`):
- `onCreateActionMode_translationEnabled_translateItemVisible`.
- `onCreateActionMode_translationDisabled_translateItemHidden`.
- `onActionItemClicked_translateItem_invokesOnTranslate`.
- `onActionItemClicked_searchGoogleItem_invokesOnSearchGoogle`.
- `onActionItemClicked_emptyText_returnsFalse` — не вызывать callback на пустой строке.
- `onActionItemClicked_unknownItem_returnsFalse`.

**`PdfTextSelectionManagerTest`** (`test/ui/player/helpers/`):
- `extractPageText_api35_returnsNativeText`.
- `extractPageText_belowApi35_fallsBackToOcr`.
- `extractPageText_emptyOcrResult_returnsEmptyString`.
- `onActionItemClicked_unknownItem_returnsFalse`.

### 9.2 Manual Test Cases

**TXT — оба хоста:**
1. Открыть TXT-файл; долго нажать на слово → рукоятки + плавающий ActionMode.
2. Убедиться: пункты «Копировать», «Поделиться», «Выбрать всё» — на месте (системные).
3. Расширить выделение → «Перевести» и «Найти в Google» видны в меню.
4. «Перевести» → overlay показывает перевод выбранного фрагмента *(не всего текста)*.
5. «Найти в Google» → браузер открывается с правильным запросом.
6. `ENABLE_TRANSLATION = false` flavor → пункт «Перевести» отсутствует.

**EPUB — оба хоста (PlayerActivity и StandalonePlayerActivity):**
1. Открыть EPUB-файл; долго нажать на слово → рукоятки WebView + ActionMode.
2. Те же пункты, что у TXT: системные + «Перевести» + «Найти в Google».
3. «Перевести» → overlay с переводом фрагмента (не целой главы).
4. «Найти в Google» → браузер с правильным запросом.
5. Высокая скорость набора символов в поиске не ломает `lastSelectedText`.
6. Свайп для смены главы работает после выделения.
7. `ENABLE_TRANSLATION = false` → пункт «Перевести» скрыт.

**PDF — оба хоста:**
1. Открыть PDF; нажать «T» (Select Text) → прогресс-бар → затем оверлей с текстом страницы.
2. API 35: текст извлекается нативно (>100ms быстрее OCR), без артефактов распознавания.
3. API 23–34: OCR работает; на сложных страницах могут быть ошибки — это ожидаемо.
4. Долго нажать на слово в оверлее → рукоятки + тот же ActionMode.
5. «Перевести» / «Найти в Google» → аналогично TXT и EPUB.
6. «✕» или повторное нажатие «T» → оверлей закрывается, PDF в нормальном режиме.
7. Страница с только изображениями → сообщение «Текст на странице не найден».
8. Landscape: оверлей не перекрывает кнопки навигации.
9. Standalone open-with (открыть PDF из Files/Gmail) → идентичное поведение.

**API compatibility:**
1. Эмулятор API 26 (standard minSdk) — OCR-путь PDF работает; TXT/EPUB — без проблем.
2. Эмулятор API 23 (legacy minSdk) — аналогично.
3. Устройство / эмулятор API 35 — нативное извлечение текста PDF.

### 9.3 Maestro E2E

Добавить три файла в `maestro/smoke/`:

`txt_text_selection.yaml`:
```yaml
- launchApp
- tapOn: "Test.txt"               # открыть тестовый TXT из fixtures
- longPressOn:
    text: "Lorem"                  # первое слово в файле
- tapOn: "Search in Google"
- assertVisible:                   # браузер открылся (заголовок системного браузера)
    id: "com.android.chrome:id/url_bar"
```

`epub_text_selection.yaml` — аналогично с открытием EPUB.

`pdf_text_selection.yaml`:
```yaml
- launchApp
- tapOn: "Test.pdf"
- tapOn: "T"                      # кнопка Select Text
- longPressOn:
    text: "Lorem"
- tapOn: "Search in Google"
- assertVisible:
    id: "com.android.chrome:id/url_bar"
```

---

## 10. Accessibility

- **TXT**: `customSelectionActionModeCallback` устанавливается на стандартный `TextView` —
  все нативные a11y-механизмы сохраняются; новые пункты меню объявлены через `android:title`
  — TalkBack их читает автоматически.
- **EPUB**: WebView ActionMode — аналогично; JS-мост не влияет на a11y.
- **PDF**: кнопка `btnSelectTextMode` — `contentDescription = @string/pdf_text_mode_enter`;
  размер touch target 48dp. `SelectableTextView` в оверлее: нативный View — TalkBack и
  свитч-доступ поддерживаются из коробки. Кнопка «✕» оверлея — 48dp с `contentDescription`.
- Дополнительных изменений для a11y не требуется.

---

## 11. Implementation Order (рекомендуемая последовательность)

```
### Фаза 1 — Общая инфраструктура (нулевой риск)
[ ] 0. Создать бэкапы:
       temp/backup_TextViewerManager_<date>.kt
       temp/backup_EpubViewerManager_<date>.kt
       temp/backup_PdfViewerManager_<date>.kt
[ ] 1. Добавить string resources EN/RU/UK (5 строк)
[ ] 2. Создать res/menu/document_selection_menu.xml
[ ] 3. Создать DocumentSelectionActionModeCallback.kt
[ ] 4. Unit-тесты DocumentSelectionActionModeCallbackTest

### Фаза 2 — TXT (самый простой, верифицирует паттерн)
[ ] 5. TextViewerManager: установить customSelectionActionModeCallback + translateSelectedText()
[ ] 6. Ручное тестирование TXT (§9.2)
[ ] 7. .\scripts\add_to_dev_log.ps1 TextViewerManager.kt

### Фаза 3 — EPUB
[ ] 8.  EpubViewerManager: EpubSelectionBridge + JS-сниппет + setCustomSelectionActionModeCallback()
[ ] 9.  EpubViewerManager: handleTranslateSelection()
[10] Ручное тестирование EPUB — оба хоста (§9.2)
[11] .\scripts\add_to_dev_log.ps1 EpubViewerManager.kt

### Фаза 4 — PDF
[12] Создать layout_pdf_text_selection_overlay.xml
[13] Создать PdfTextSelectionManager.kt + unit-тесты PdfTextSelectionManagerTest
[14] Добавить btnSelectTextMode в activity_player_unified.xml
[15] PdfViewerManager: делегат к PdfTextSelectionManager
[16] PlayerManagerInitializer + PlayerCommandPanelCallbackImpl: обработчик кнопки
[17] Ручное тестирование PDF — API 26, 35, landscape, standalone (§9.2)
[18] .\scripts\add_to_dev_log.ps1 для PdfViewerManager, PlayerManagerInitializer, xml

### Фаза 5 — Finalization
[19] Maestro smoke tests (txt/epub/pdf_text_selection.yaml)
[20] Обновить docs/FEATURES.md, FEATURES_RU.md, FEATURES_UK.md
```
