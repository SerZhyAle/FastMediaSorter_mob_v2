# Specification: NEW.3 — Standalone Player Document Search Parity

**Status:** Draft  
**Date:** 2026-04-11  
**Tier:** 3 — Moderate (4–8h, medium risk)  
**Roadmap entry:** *(New item — not yet in IMPROVEMENT_ROADMAP.md. Proposed for TIER 3.)*  
В StandalonePlayerActivity кнопки поиска для PDF (`btnSearchPdfCmd`) и TXT (`btnSearchTextCmd`)
видны, но click-listener не зарегистрирован — нажатие игнорируется. Для EPUB
(`btnSearchEpubCmd`) кнопка подключена к `showCrossChapterSearch()` (BottomSheet),
тогда как в нормальном PlayerActivity та же кнопка открывает inline search panel — разное
поведение для одной кнопки. Цель: одинаковое поведение и внешний вид поиска во всех
типах документов (EPUB, PDF, TXT) и во всех режимах.

---

## 1. Problem Statement

`StandalonePlayerActivity` использует `StandaloneViewManager` как координатор просмотрщиков,
но не инстанциирует `SearchControlsManager` — компонент, который в нормальном `PlayerActivity`
отвечает за всю логику inline search panel.

**PDF** (`PdfViewerManager`): выставляет `btnSearchPdfCmd.isVisible = isLandscape && ...`
(строка 307), но click-listener на кнопку в standalone не назначается → тап игнорируется.
Метод `PdfViewerManager.searchInPdf()` (строка 1380) и `nextSearchResult()`/`previousSearchResult()`
(строки 1416/1426) полностью реализованы и доступны.

**TXT** (`TextViewerManager`): выставляет `btnSearchTextCmd.isVisible = true` (строка 542),
но click-listener отсутствует → тап игнорируется. Методы `searchText()` (строка 1692)
и `highlightSearchMatch()` доступны.

**EPUB** (`EpubViewerManager`): `setupEpubButtons()` (строка 502) подключает `btnSearchEpubCmd`
к `viewManager.showEpubCrossSearch()` → `EpubViewerManager.showCrossChapterSearch()` (строка 1540).
Это открывает BottomSheet для поиска по всем главам. В нормальном `PlayerActivity`
`SearchControlsManager.setupSearchControls()` (строка 66) подключает ту же кнопку
к `showSearchPanel()` — это inline search panel в текущей главе WebView (`searchInEpub()`,
строка 1493). Поведение **разное**: BottomSheet vs. inline panel. Результат: пользователь
видит разный UX в зависимости от источника запуска файла.

Inline search panel (`searchPanel`, `etSearchQuery`, `btnSearchPrev`, `btnSearchNext`,
`btnCloseSearch`) включена в оба варианта `activity_player_unified.xml` через
`player_search_panel_content.xml`. Все виды доступны в standalone через `ActivityPlayerUnifiedBinding`.
`PlayerBindingSafeViews` обращается к ним через `requiredFromRoot(R.id.searchPanel)` (строка 108,
191 в `PlayerBindingSafeViews.kt`) — так что инфраструктура полностью доступна без изменений layout.

---

## 2. Goals

1. `btnSearchPdfCmd` в standalone открывает inline search panel → пользователь вводит запрос → `PdfViewerManager.searchInPdf()` выделяет совпадения.
2. `btnSearchTextCmd` в standalone открывает inline search panel → `TextViewerManager.searchText()` выделяет совпадения.
3. `btnSearchEpubCmd` в standalone открывает inline search panel → `EpubViewerManager.searchInEpub()` ищет в текущей главе (WebView `findAllAsync`).
4. Кнопки Prev/Next (`btnSearchPrev`, `btnSearchNext`) в inline search panel работают для всех трёх типов в standalone.
5. Кнопка «Закрыть» (`btnCloseSearch`) скрывает панель и очищает запрос.
6. Поведение идентично нормальному Player визуально и функционально (та же панель, тот же UX).

**Non-goals for this spec:**
- `showCrossChapterSearch()` BottomSheet из `btnSearchEpubCmd` в standalone (перенесено в NEW.3.future — cross-chapter search доступен через другую точку входа).
- Long-press на кнопку поиска для выбора режима (inline vs. cross-chapter).
- `SearchControlsManager.showTranslationSettingsDialog()` в standalone (вне scope, no-op).
- Поиск в изображениях, GIF, аудио, видео.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | ENABLE_EPUB + SUPPORT_DOCUMENTS = true |
| `lite`     | ❌ | SUPPORT_DOCUMENTS=false, нет PDF/EPUB/TXT просмотрщиков |
| `photos`   | ❌ | SUPPORT_DOCUMENTS=false |
| `legacy`   | ✅ | ENABLE_EPUB=true, SUPPORT_DOCUMENTS=true |

Все флаги уже проверяются внутри менеджеров просмотрщиков (видимость кнопок). Новый код в standalone не нуждается в дополнительных `BuildConfig` проверках.

### 3.2 Android API Level Forks

| API level | Поведение |
|-----------|-----------------------|
| 26+ (standard minSdk) | Основной путь |
| 23+ (legacy minSdk) | Тот же путь через legacy flavor |

`WebView.findAllAsync()` задокументирован как `@Deprecated` с API 16+, но функционирует на всех поддерживаемых уровнях. Специфических API-ветвлений нет.

### 3.3 Wear OS Impact

No Wear OS changes required.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `SearchControlsManager` | `ui/player/helpers/SearchControlsManager.kt` (~700 LOC) | Управляет всей inline search panel в нормальном Player; принимает провайдеры `() -> *ViewerManager`; НЕ инстанциируется в standalone |
| `SearchControlsManager.SearchControlsCallback` | внутри `SearchControlsManager.kt` | Интерфейс: `getCurrentMediaFile()`, `scheduleHideControls()`, `onEpubTranslate()`, `showTranslationSettingsDialog()` |
| `StandaloneViewManager` | `ui/player/helpers/StandaloneViewManager.kt` (~411 LOC) | Координатор просмотрщиков; `_epubViewerManager`, `_pdfViewerManager`, `_textViewerManager` — приватные ленивые поля |
| `StandalonePlayerActivity` | `ui/player/StandalonePlayerActivity.kt` (~767 LOC) | "Open With" Activity; `setupEpubButtons()` (строка 494) содержит конфликтующий listener для `btnSearchEpubCmd`; `setupPdfButtons()` (строка 486) без search |
| `PlayerBindingSafeViews` | `ui/player/helpers/PlayerBindingSafeViews.kt` | Предоставляет `searchPanel`, `etSearchQuery`, `btnSearchPrev/Next/Close` через `requiredFromRoot` — работает в любой Activity с `ActivityPlayerUnifiedBinding` |
| `EpubViewerManager` | `ui/player/helpers/EpubViewerManager.kt` (~2000+ LOC) | `searchInEpub()` (строка 1493), `nextSearchMatch()` (1522), `previousSearchMatch()` (1530), `showCrossChapterSearch()` (1540) |
| `PdfViewerManager` | `ui/player/helpers/PdfViewerManager.kt` | `searchInPdf()` (1380, suspend), `nextSearchResult()` (1416), `previousSearchResult()` (1426) |
| `TextViewerManager` | `ui/player/helpers/TextViewerManager.kt` | `searchText()` (1692), `highlightSearchMatch()` |

**Ключевые пробелы:**
1. `SearchControlsManager` не инстанциируется в standalone → `btnSearchPdfCmd` и `btnSearchTextCmd` без listener.
2. Конфликтующий `btnSearchEpubCmd` listener в `setupEpubButtons()` открывает BottomSheet вместо inline panel.
3. `StandaloneViewManager` не экспонирует менеджеры просмотрщиков как провайдеры — `SearchControlsManager` не может к ним обратиться.

---

## 5. Proposed Architecture

### 5.1 Экспонировать провайдеры менеджеров из StandaloneViewManager

Добавить три публичных метода-провайдера в `StandaloneViewManager`, аналогичных паттерну ленивого доступа, уже используемому повсюду:

```kotlin
// Для SearchControlsManager и теоретически StandaloneTranslatorCallback
fun epubViewerManagerProvider(): EpubViewerManager = epubViewerManager
fun pdfViewerManagerProvider(): PdfViewerManager   = pdfViewerManager
fun textViewerManagerProvider(): TextViewerManager = textViewerManager
```

> **Внимание**: вызов этих методов lazy-инициализирует соответствующий менеджер. Это безопасно — поведение идентично `get()` внутри класса.

### 5.2 Инстанциировать SearchControlsManager в StandalonePlayerActivity

```kotlin
private var searchControlsManager: SearchControlsManager? = null

private fun setupSearchControls() {
    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    searchControlsManager = SearchControlsManager(
        binding           = binding,
        textViewerManagerProvider = { viewManager.textViewerManagerProvider() },
        pdfViewerManagerProvider  = { viewManager.pdfViewerManagerProvider() },
        epubViewerManagerProvider = { viewManager.epubViewerManagerProvider() },
        lifecycleScope    = lifecycleScope,
        inputMethodManager = imm,
        callback          = object : SearchControlsManager.SearchControlsCallback {
            override fun getCurrentMediaFile() = viewModel.state.value.mediaFile
            override fun scheduleHideControls() { /* no auto-hide in standalone */ }
            override fun onEpubTranslate()          { viewManager.toggleEpubTranslation() }
            override fun showTranslationSettingsDialog() { /* out of scope — no-op */ }
        }
    )
    searchControlsManager?.setupSearchControls()
}
```

Вызвать `setupSearchControls()` в `onCreate()` **после** вызова `setupEpubButtons()`, `setupPdfButtons()`.

### 5.3 Удалить конфликтующий listener в setupEpubButtons()

Убрать из `setupEpubButtons()` строку:
```kotlin
// УДАЛИТЬ:
binding.btnSearchEpubCmd.setOnClickListener { viewManager.showEpubCrossSearch() }
```
> После этого `SearchControlsManager.setupSearchControls()` перепишет listener на inline panel. Запись BO после SearchControlsManager выигрывает — порядок вызовов гарантирует правильный listener.

### 5.4 Новые классы / файлы

Новых файлов не создаётся.

| Файл | Изменение | Строк после |
|------|-----------|-------------|
| `StandaloneViewManager.kt` | +3 метода-провайдера (3 строки) | ~414 |
| `StandalonePlayerActivity.kt` | +метод `setupSearchControls()` (~20 строк) + вызов в `onCreate()`, удалить 1 строку listener в `setupEpubButtons()` | ~787 |

> `StandalonePlayerActivity.kt` > 500 строк → **создать backup** перед правкой.

### 5.5 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Вся логика поиска остаётся в `SearchControlsManager` и `*ViewerManager` |
| Naming convention | ✅ | `setupSearchControls()`, `searchControlsManager` |
| Data flow `UI → SearchControlsManager → *ViewerManager.search*()` | ✅ | Полностью в рамках Clean Architecture |
| No `Log.d()` — Timber only | ✅ | `SearchControlsManager` уже использует Timber |
| Room schema: без изменений | N/A | |
| Hilt DI: без новых bindings | N/A | `SearchControlsManager` не является Hilt-инжектируемым в нормальном Player тоже |

---

## 6. Data Flow

```
[User taps btnSearchPdfCmd / btnSearchTextCmd / btnSearchEpubCmd]
        │
        ▼
SearchControlsManager.setupSearchControls() listener
    └── showSearchPanel()
            │
            ├── safeViews.searchPanel.isVisible = true
            ├── safeViews.etSearchQuery.requestFocus()
            └── inputMethodManager.showSoftInput(...)
                        │ (user types)
                        ▼
               afterTextChanged() → performSearch()
                        │
                        ├── MediaType.PDF  → pdfViewerManagerProvider().searchInPdf(query)    [suspend]
                        │                    PdfViewerManager → renders highlights on PDF canvas
                        │
                        ├── MediaType.TEXT → textViewerManagerProvider().searchText(query)
                        │                    TextViewerManager → Spannable highlights in TextView
                        │
                        └── MediaType.EPUB → epubViewerManagerProvider().searchInEpub(query)
                                             EpubViewerManager → WebView.findAllAsync(query)

[User taps btnSearchNext / btnSearchPrev]
        │
        ▼
performSearchNavigation(forward)
        │
        ├── PDF  → pdfViewerManager.nextSearchResult() / previousSearchResult()
        ├── TEXT → textViewerManager.highlightSearchMatch(query, index)
        └── EPUB → epubViewerManager.nextSearchMatch() / previousSearchMatch()

[User taps btnCloseSearch]
        │
        ▼
hideSearchPanel()
        ├── searchPanel.isVisible = false
        ├── etSearchQuery.text.clear()
        └── clearSearch() — снимает highlights во всех менеджерах
```

---

## 7. Files to Modify

| Файл | Изменение | Ожид. размер |
|------|-----------|-------------|
| `ui/player/helpers/StandaloneViewManager.kt` | +3 метода-провайдера: `epubViewerManagerProvider()`, `pdfViewerManagerProvider()`, `textViewerManagerProvider()` | ~414 строк |
| `ui/player/StandalonePlayerActivity.kt` | +`setupSearchControls()` метод, вызов в `onCreate`, удалить `btnSearchEpubCmd` listener из `setupEpubButtons()` | ~787 строк |

> `StandalonePlayerActivity.kt` > 500 строк → создать timestamped backup в `temp/` перед правкой.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Lazy init провайдеров в `SearchControlsManager` вызывает инициализацию не того менеджера (e.g., при открытии PDF создаётся EpubViewerManager) | Low | `performSearch()` вызывает провайдер только в ветке `when (currentFile.type)` → тип известен, wrong manager нет |
| Конфликт `btnSearchEpubCmd` listeners: `setupEpubButtons()` записывает listener, потом `setupSearchControls()` перезаписывает | Low | Android View позволяет переопределять listener; `setupSearchControls()` всегда вызывается ПОСЛЕ `setupEpubButtons()` → гарантирует правильный итоговый listener |
| `SearchControlsManager.onEpubTranslate()` требует `viewManager.toggleEpubTranslation()` из spec NEW.2; если NEW.2 не реализован, NullPointerException | Med | `StandaloneViewManager.kt` изменяется в обоих specs; реализовывать NEW.2 ПЕРЕД NEW.3, или добавить guard `_epubViewerManager?.toggleTranslation()` в callback |
| `getCurrentMediaFile()` возвращает `null` пока файл загружается → `performSearch()` тихо не выполняется | Low | Допустимо — кнопка поиска не должна быть нажимаемой пока файл не загружен; visibility controlled by viewer managers |
| `PdfViewerManager.searchInPdf()` — `suspend` функция, `SearchControlsManager` вызывает через `lifecycleScope.launch` | Low | Уже правильно — `performSearch()` в SearchControlsManager обёрнут в `lifecycleScope.launch` |
| Cross-chapter EPUB BottomSheet search недоступен после удаления listener | Low | User story: inline search (current chapter) заменяет BottomSheet как основной search UX. If future demand exists, add dedicated cross-chapter button separately |

---

## 9. Testing Plan

### 9.1 Unit Tests

Без новых unit tests. Вся логика уже покрыта `SearchControlsManager`; изменения — только wiring в Activity. Ручное тестирование + Maestro достаточны.

### 9.2 Manual Test Cases

#### PDF в StandalonePlayerActivity

1. Открыть `.pdf` через «Открыть с помощью» → `StandalonePlayerActivity`.
2. (Landscape) Нажать `btnSearchPdfCmd` → `searchPanel` появляется, клавиатура открывается.
3. Ввести запрос из 3+ символов → PDF рендерит выделение совпадений, счётчик `1/N`.
4. Нажать `btnSearchNext` → переход к следующему совпадению.
5. Нажать `btnSearchPrev` → переход к предыдущему.
6. Нажать `btnCloseSearch` → панель скрывается, выделения сняты.
7. Строка без совпадений → счётчик `0/0`.

#### TXT в StandalonePlayerActivity

1. Открыть `.txt` через «Открыть с помощью».
2. Нажать `btnSearchTextCmd` → панель появляется.
3. Ввести запрос → совпадения подсвечиваются в `tvTextContent`.
4. `btnSearchNext/Prev` → навигация по совпадениям.
5. `btnCloseSearch` → панель закрыта, выделения сняты.

#### EPUB в StandalonePlayerActivity

1. Открыть `.epub` → перейти на главу с известным текстом.
2. Нажать `btnSearchEpubCmd` → появляется inline search panel (НЕ BottomSheet).
3. Ввести слово из текущей главы → `WebView.findAllAsync()` подсвечивает; счётчик N.
4. `btnSearchNext` → `webView.findNext(true)`.
5. `btnCloseSearch` → снимает highlights `webView.clearMatches()`.
6. Текст не из текущей главы → `0/0`.

#### Regression: нормальный PlayerActivity

1. Открыть PDF/TXT/EPUB через обычный Browse.
2. Убедиться, что search panel работает как прежде.
3. Убедиться, что `setupEpubButtons()` изменение не затронуло нормальный Player (он использует собственный `SearchControlsManager` из `PlayerManagerInitializer`).

### 9.3 Maestro E2E

Добавить в `maestro/smoke/standalone_doc_search.yaml`:
```yaml
- launchApp
- openFile: test_media/sample.pdf
- rotateToLandscape
- tapOn:  { id: btnSearchPdfCmd }
- assertVisible: { id: searchPanel }
- inputText: "chapter"
- assertVisible: { id: tvSearchCounter }
- tapOn:  { id: btnCloseSearch }
- assertNotVisible: { id: searchPanel }
```

---

## 10. Accessibility

`searchPanel`, `etSearchQuery`, `btnSearchNext`, `btnSearchPrev`, `btnCloseSearch` уже имеют `contentDescription` в `player_search_panel_content.xml`. Кнопки поиска в command panel (`btnSearchPdfCmd`, `btnSearchTextCmd`, `btnSearchEpubCmd`) имеют `android:contentDescription="@string/search"` в `activity_player_unified.xml` (portrait). Landscape-версия имеет `contentDescription="@null"` — это **существующий дефицит** (не в scope данного spec, фиксируется отдельно в VIII.2). Новые click-listeners не меняют contentDescription. TalkBack достижимость не нарушается.

---

## 11. User-Facing Feature Update

Исправление сломанных кнопок в существующем интерфейсе. Пользователь получает ожидаемое поведение:

- `docs/FEATURES.md` (EN): под разделом **PDF / EPUB / Text Viewer** добавить:  
  `- Inline search panel (tap-to-search with Next/Prev navigation) works in both internal browser and standalone "Open with" mode for PDF, EPUB (current chapter) and TXT files.`
- `docs/FEATURES_RU.md` (RU):  
  `- Встроенная панель поиска (Next/Prev навигация по совпадениям) работает как во встроенном браузере файлов, так и в режиме «Открыть с помощью» для PDF, EPUB (текущая глава) и TXT.`
- `docs/FEATURES_UK.md` (UK):  
  `- Вбудована панель пошуку (навігація Next/Prev по збігах) працює як у вбудованому браузері, так і в режимі «Відкрити за допомогою» для PDF, EPUB (поточний розділ) і TXT.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Переиспользовать SearchControlsManager в standalone, а не дублировать логику**
- **Decision:** Инстанциировать существующий `SearchControlsManager` в `StandalonePlayerActivity` вместо создания `StandaloneSearchManager`.
- **Alternatives considered:** Создать `StandaloneSearchManager` как lean-копию; добавить 3 отдельных click-listener прямо в Activity без `SearchControlsManager`.
- **Reason:** `SearchControlsManager` уже инкапсулирует весь сложный search UX (keyboard management, counter, text watcher, navigation). Переиспользование исключает дублирование ~100 строк бизнес-логики. Coupling допустим — `SearchControlsManager` не зависит от PlayerActivity-специфики; зависимости передаются через провайдеры и callback.

**ADR-2: Заменить BottomSheet EPUB search (cross-chapter) на inline panel (current-chapter)**
- **Decision:** Удалить listener `btnSearchEpubCmd → showCrossChapterSearch()` из `setupEpubButtons()`. После `setupSearchControls()` кнопка будет открывать inline panel (в текущей главе).
- **Alternatives considered:** Оставить BottomSheet при нажатии и добавить отдельную кнопку для inline search; реализовать long-press → cross-chapter, short-press → inline.
- **Reason:** `showSearchPanel()` / inline search — канонический UX нормального Player. Паритет с нормальным режимом важнее, чем сохранение BoottomSheet-только-в-standalone. Cross-chapter BottomSheet может быть добавлен как отдельная кнопка (`btnSearchAllChaptersCmd`) в future spec.

**ADR-3: Провайдеры вместо прямого доступа к менеджерам**
- **Decision:** Добавить `epubViewerManagerProvider()` / `pdfViewerManagerProvider()` / `textViewerManagerProvider()` в `StandaloneViewManager` как публичные методы, а не раскрывать поля `_epubViewerManager` и т.д.
- **Reason:** Инкапсуляция. Паттерн provider lambda уже используется в `SearchControlsManager`'s конструкторе и в `StandaloneViewManager`'s внутреннем паттерне lazy init. Провайдерный подход откладывает создание менеджера до первого обращения — безопасно и last-moment.

**ADR-4: Координация с NEW.2 (translator)**
- **Decision:** `SearchControlsManager.setupSearchControls()` также регистрирует `btnTranslateEpubCmd` click/long-click listener (строки 73-89 в `SearchControlsManager`). Это **заменяет** явный `btnTranslateEpubCmd.setOnClickListener` из spec NEW.2.
- **Consequence:** Если NEW.3 реализуется ПОСЛЕ NEW.2, необходимо убрать прямое назначение listener из `setupEpubButtons()` (NewSpec NEW.2 шаг 3) — он будет перезаписан через `SearchControlsManager`. Оставить только метод `viewManager.toggleEpubTranslation()` в `StandaloneViewManager` (нужен для `SearchControlsCallback.onEpubTranslate()`).
- **Implementation order:** NEW.2 → NEW.3 (translator methods в StandaloneViewManager нужны для callback).

---

## 13. Implementation Steps

**Prereq:** NEW.2 (`toggleEpubTranslation()` в `StandaloneViewManager`) должен быть реализован перед шагом 4.

1. **[Backup]** Создать резервную копию перед правкой:
   ```powershell
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" `
             "temp/StandalonePlayerActivity_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.bak"
   ```

2. **[StandaloneViewManager.kt]** Добавить три публичных метода-провайдера — после строки `fun exitEpubFullscreen()` (~строка 298), рядом с блоком EPUB-делегаторов:
   ```kotlin
   // Viewer manager providers — for SearchControlsManager (and future StandaloneSearchManager)
   fun epubViewerManagerProvider(): EpubViewerManager = epubViewerManager
   fun pdfViewerManagerProvider(): PdfViewerManager   = pdfViewerManager
   fun textViewerManagerProvider(): TextViewerManager = textViewerManager
   ```
   Запустить dev-log:
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt" "StandaloneViewManager" "Add epubViewerManagerProvider/pdfViewerManagerProvider/textViewerManagerProvider for SearchControlsManager wiring"
   ```

3. **[StandalonePlayerActivity.kt — setupEpubButtons()]** Удалить конфликтующий listener:
   ```kotlin
   // УДАЛИТЬ эту строку — SearchControlsManager перезапишет её:
   // binding.btnSearchEpubCmd.setOnClickListener { viewManager.showEpubCrossSearch() }
   ```

4. **[StandalonePlayerActivity.kt]** Добавить поле и метод:
   ```kotlin
   private var searchControlsManager: SearchControlsManager? = null

   private fun setupSearchControls() {
       val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
       searchControlsManager = SearchControlsManager(
           binding                   = binding,
           textViewerManagerProvider = { viewManager.textViewerManagerProvider() },
           pdfViewerManagerProvider  = { viewManager.pdfViewerManagerProvider() },
           epubViewerManagerProvider = { viewManager.epubViewerManagerProvider() },
           lifecycleScope            = lifecycleScope,
           inputMethodManager        = imm,
           callback                  = object : SearchControlsManager.SearchControlsCallback {
               override fun getCurrentMediaFile() = viewModel.state.value.mediaFile
               override fun scheduleHideControls() { /* no auto-hide in standalone */ }
               override fun onEpubTranslate()      { viewManager.toggleEpubTranslation() }
               override fun showTranslationSettingsDialog() { /* out of scope */ }
           }
       )
       searchControlsManager?.setupSearchControls()
   }
   ```

5. **[StandalonePlayerActivity.kt — onCreate()]** Добавить вызов `setupSearchControls()` после `setupEpubButtons()` и `setupPdfButtons()`:
   ```kotlin
   setupPdfButtons()
   setupEpubButtons()
   setupSearchControls()  // ← добавить
   ```
   Запустить dev-log:
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "StandalonePlayerActivity" "Wire SearchControlsManager in standalone: PDF/TXT/EPUB inline search panel + remove BottomSheet EPUB search listener"
   ```

6. **[Build & Smoke]** Собрать `standardDebug`:
   ```powershell
   .\scripts\builders\build-debug.PS1 -SkipZip
   ```

7. **[Manual Test]** Выполнить все тест-кейсы из раздела 9.2 (PDF, TXT, EPUB search + regression нормального Player).

8. **[Maestro]** Создать `maestro/smoke/standalone_doc_search.yaml` согласно разделу 9.3.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "maestro/smoke/standalone_doc_search.yaml" "MaestroSmoke" "Add smoke test for standalone PDF/TXT/EPUB inline search panel"
   ```

9. **[Feature Docs]** Обновить все три FEATURES-файла согласно разделу 11.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Add inline search panel in standalone mode for PDF/EPUB/TXT"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "Add inline search panel in standalone mode for PDF/EPUB/TXT"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "Add inline search panel in standalone mode for PDF/EPUB/TXT"
   ```

### Mandatory step checklist

- [ ] String resources: без изменений (все строки уже в `values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` обновлены (шаг 9)
- [ ] Room DB: без изменений схемы
- [ ] `.\scripts\add_to_dev_log.ps1` выполнен после каждого изменённого файла (шаги 2, 5, 8, 9)
- [ ] **Prereq:** NEW.2 реализован перед шагом 4 (translator callback в SearchControlsCallback)

---

## 14. Out of Scope (future items)

- Cross-chapter (all-chapters) EPUB BottomSheet search: добавить отдельный `btnSearchAllEpubCmd` или long-press на `btnSearchEpubCmd` → `showCrossChapterSearch()`.
- TXT search Next/Prev: `TextViewerManager.highlightSearchMatch()` с индексом — requires custom navigation index tracking.
- `btnSearchEpubCmd` / `btnSearchPdfCmd` / `btnSearchTextCmd` в portrait: видимость кнопок в portrait mode не контролируется `CommandPanelController` в standalone → возможно излишнее отображение. Аудит видимости кнопок вынесен в будущий spec (единый `CommandPanel` для standalone).
- Contentdescription для кнопок поиска в landscape layout (фиксируется в рамках VIII.2 TalkBack audit).
