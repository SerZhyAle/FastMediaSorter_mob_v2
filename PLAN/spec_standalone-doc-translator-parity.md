# Specification: NEW.2 — Standalone Player Document Translator Parity

**Status:** Draft  
**Date:** 2026-04-11  
**Tier:** 2 — Easy (2–4h, low risk)  
**Roadmap entry:** *(New item — not yet in IMPROVEMENT_ROADMAP.md. Proposed for TIER 2.)*  
В StandalonePlayerActivity кнопка перевода для EPUB и PDF видна (видимость выставляет
менеджер просмотра), но click-listener не подключён — нажатие ничего не делает.
Цель: единое поведение и внешний вид кнопки переводчика для всех типов документов
(EPUB, PDF, TXT) во всех режимах (нормальный Player и Standalone «Открыть с помощью»).

---

## 1. Problem Statement

`StandalonePlayerActivity` использует `StandaloneViewManager` как единственного координатора
всех просмотрщиков (PDF, EPUB, Text). При открытии EPUB `EpubViewerManager` выставляет
`binding.btnTranslateEpubCmd.isVisible = true` (строка 497), а при открытии PDF
`PdfViewerManager` выставляет `binding.btnTranslatePdfCmd.isVisible = isLandscape && settings.enableTranslation`
(строка 307). При этом `setupEpubButtons()` и `setupPdfButtons()` в
`StandalonePlayerActivity` не регистрируют `OnClickListener` для этих кнопок — нажатие
игнорируется. В нормальном `PlayerActivity` click-listener присваивает
`SearchControlsManager` (EPUB, строка 73) и `PlayerControlsSetupManager` (PDF, строка 251),
но эта инфраструктура недоступна в standalone. Также возможно лишнее отображение кнопки
EPUB в портретной ориентации в standalone: `EpubViewerManager` не проверяет ориентацию
сам по себе, тогда как в нормальном режиме видимость контролирует `CommandPanelController`
(строка 336, условие `isEpub && isLandscapeMode`).

---

## 2. Goals

1. `btnTranslateEpubCmd` в `StandalonePlayerActivity` вызывает `EpubViewerManager.toggleTranslation()` при нажатии.
2. `btnTranslatePdfCmd` в `StandalonePlayerActivity` вызывает `PdfViewerManager.toggleTranslation()` при нажатии.
3. Кнопка перевода EPUB в standalone скрыта в портретной ориентации (паритет с нормальным режимом).
4. Кнопка перевода TXT (`btnTranslateTextCmd`) проверена и подтверждена как рабочая (она самостоятельно регистрируется внутри `TextViewerManager`).
5. Иконка кнопки EPUB и PDF отражает активное/неактивное состояние перевода (тот же tint/drawable, что в нормальном режиме).
6. `BuildConfig.ENABLE_TRANSLATION`- и `AppSettings.enableTranslation`-условия соблюдаются в standalone (не показывать кнопки, когда перевод отключён по flavor или настройкам).

**Non-goals for this spec:**
- Long-press → диалог настроек перевода (язык источника/цели) в standalone (отложено в NEW.2.future).
- Перевод изображений/GIF через OCR в standalone.
- Изменение архитектуры `TranslationManager`; callback `/* Translation UI not exposed in standalone mode */` в `StandaloneViewManager` остаётся как есть.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | `ENABLE_TRANSLATION=true`, `ENABLE_EPUB=true` |
| `lite`     | ❌ | `ENABLE_TRANSLATION=false`, `ENABLE_EPUB=false` — кнопки hidden/gone |
| `photos`   | ❌ | `ENABLE_TRANSLATION=false`, `ENABLE_EPUB=false` |
| `legacy`   | ✅ | `ENABLE_TRANSLATION=true`, `ENABLE_EPUB=true` |

Флаги гейтинга: `BuildConfig.ENABLE_TRANSLATION` (строки 87/106/125/147 в `app_v2/build.gradle.kts`)
и `BuildConfig.ENABLE_EPUB` (строки 86/105/124/146). Оба уже используются в существующих
менеджерах; новый код в `StandaloneViewManager` ничего дополнительно проверять не должен —
видимость кнопок управляется внутри `PdfViewerManager`/`EpubViewerManager`.

### 3.2 Android API Level Forks

| API level | Поведение |
|-----------|-----------------------|
| 26+ (standard minSdk) | Основной путь — без ветвлений |
| 23+ (legacy minSdk) | Запасной путь через legacy flavor — то же поведение |

Специфической API-логики нет. Изменения касаются только wiring click-listeners и видимости View.

### 3.3 Wear OS Impact

No Wear OS changes required. Standalone player — исключительно телефонный компонент.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `StandalonePlayerActivity` | `ui/player/StandalonePlayerActivity.kt` (~767 LOC) | "Open With" Activity; делегирует всю логику в `StandaloneViewManager`; `setupEpubButtons()` (строка 494) и `setupPdfButtons()` (строка 486) — точки регистрации click-listeners |
| `StandaloneViewManager` | `ui/player/helpers/StandaloneViewManager.kt` (~411 LOC) | Единственный фасад для всех просмотрщиков в standalone; имеет приватные `_pdfViewerManager` и `_epubViewerManager`; паблик-методы типа `showEpubPreviousChapter()` делегируют к ним |
| `EpubViewerManager` | `ui/player/helpers/EpubViewerManager.kt` (~2000+ LOC) | Управляет WebView EPUB; `toggleTranslation()` — публичный (строка 1767); устанавливает `btnTranslateEpubCmd.isVisible = true` при загрузке (строка 497) **без** проверки ориентации |
| `PdfViewerManager` | `ui/player/helpers/PdfViewerManager.kt` | PDF-рендерер; `toggleTranslation()` — публичный (строка 677); показывает `btnTranslatePdfCmd` с проверкой `isLandscape && settings.enableTranslation` (строка 307) |
| `TextViewerManager` | `ui/player/helpers/TextViewerManager.kt` | TXT-просмотрщик; `btnTranslateTextCmd` регистрируется **внутри** менеджера (строка 217); `toggleTranslation()` — приватный (вызывается из внутреннего listener) |
| `SearchControlsManager` | `ui/player/helpers/SearchControlsManager.kt` (~700 LOC) | В нормальном PlayerActivity регистрирует click на `btnTranslateEpubCmd` (строка 73) — **недоступен** в standalone |
| `CommandPanelController` | `ui/player/CommandPanelController.kt` (~920 LOC) | В нормальном режиме управляет видимостью `btnTranslateEpubCmd` по ориентации (строка 336) — **недоступен** в standalone |

**Ключевой пробел**: `StandaloneViewManager` не предоставляет публичных методов для делегирования вызовов `toggleTranslation()`. `StandalonePlayerActivity.setupEpubButtons()` / `setupPdfButtons()` не подключают click-listeners. `EpubViewerManager` делает кнопку видимой, но без listener нажатие — это silent no-op.

---

## 5. Proposed Architecture

### 5.1 Добавить методы-делегаторы в StandaloneViewManager

Добавить два публичных метода-однострочника, снисходя к приватным менеджерам (паттерн, уже используемый для `showEpubPreviousChapter()` и `showPdfFirstPage()`):

```kotlin
// Добавить рядом с showEpubCrossSearch() (строка ~297)
fun toggleEpubTranslation() { _epubViewerManager?.toggleTranslation() }

// Добавить рядом с showPdfFirstPage() (строка ~282)
fun togglePdfTranslation()  { _pdfViewerManager?.toggleTranslation() }
```

### 5.2 Подключить click-listeners в StandalonePlayerActivity

В `setupEpubButtons()` добавить перед закрывающей скобкой метода:

```kotlin
binding.btnTranslateEpubCmd.setOnClickListener { viewManager.toggleEpubTranslation() }
```

С guard по ориентации для правильной видимости (паритет с `CommandPanelController`):

```kotlin
// В onConfigurationChanged / при старте — скрывать в portrait
fun updateTranslatorVisibilityForOrientation() {
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val settings = /* получить из StandalonePlayerSettingsManager */
    binding.btnTranslateEpubCmd.isVisible =
        isLandscape && BuildConfig.ENABLE_TRANSLATION && (settings?.enableTranslation ?: false)
}
```

В `setupPdfButtons()` добавить:

```kotlin
binding.btnTranslatePdfCmd.setOnClickListener { viewManager.togglePdfTranslation() }
```

> PDF уже сам управляет видимостью через `isLandscape && settings.enableTranslation`.

### 5.3 Новые классы / файлы

Новых файлов не создаётся. Все изменения — добавление строк в существующие файлы.

| Класс | Локация | Строк после |
|-------|---------|------------|
| `StandaloneViewManager` | `ui/player/helpers/StandaloneViewManager.kt` | ~413 |
| `StandalonePlayerActivity` | `ui/player/StandalonePlayerActivity.kt` | ~773 |

### 5.4 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Вся логика остаётся в `EpubViewerManager`/`PdfViewerManager` |
| Naming convention | ✅ | `toggleEpubTranslation()`, `togglePdfTranslation()` — глагол + существительное |
| Data flow `UI → ViewModel → UseCase → ...` | ✅ | Click → `StandaloneViewManager` → `*ViewerManager.toggleTranslation()` |
| No `Log.d()` — Timber only | ✅ | |
| Room schema не изменяется | N/A | |
| Hilt DI: без новых bindings | N/A | |

---

## 6. Data Flow

```
[User taps btnTranslateEpubCmd]
        │
        ▼
StandalonePlayerActivity.setupEpubButtons()
  └── binding.btnTranslateEpubCmd.setOnClickListener
        │
        ▼
StandaloneViewManager.toggleEpubTranslation()
        │
        ▼
_epubViewerManager?.toggleTranslation()    ← EpubViewerManager.kt:1767
        │
        ├── translationEnabled = !translationEnabled
        ├── (show/hide WebView translation overlay)
        └── updateTranslateButtonIcon()    ← обновляет tint/drawable btnTranslateEpubCmd

--- PDF ---

[User taps btnTranslatePdfCmd]
        │
        ▼
StandalonePlayerActivity.setupPdfButtons()
  └── binding.btnTranslatePdfCmd.setOnClickListener
        │
        ▼
StandaloneViewManager.togglePdfTranslation()
        │
        ▼
_pdfViewerManager?.toggleTranslation()    ← PdfViewerManager.kt:677
        │
        ├── translationEnabled = !translationEnabled
        ├── (show/hide translation overlay)
        └── updateTranslateButtonIcon()    ← обновляет tint/drawable btnTranslatePdfCmd
```

---

## 7. Files to Modify

| Файл | Изменение | Ожид. размер |
|------|-----------|-------------|
| `ui/player/helpers/StandaloneViewManager.kt` | Добавить 2 публичных метода `toggleEpubTranslation()` и `togglePdfTranslation()` рядом с аналогичными делегаторами | ~413 строк |
| `ui/player/StandalonePlayerActivity.kt` | В `setupEpubButtons()`: добавить `btnTranslateEpubCmd.setOnClickListener`. В `setupPdfButtons()`: добавить `btnTranslatePdfCmd.setOnClickListener`. Добавить `updateEpubTranslatorVisibility()` и вызвать в `onConfigurationChanged` и при загрузке EPUB | ~773 строки |

> `StandalonePlayerActivity.kt` > 500 строк → **создать backup** перед правкой:  
> `Copy-Item "app_v2/.../StandalonePlayerActivity.kt" "temp/StandalonePlayerActivity_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.bak"`

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| `_epubViewerManager` равен null при вызове (EPUB не загружен) | Low | Safe-call `?.` — уже используется повсюду в `StandaloneViewManager` |
| `btnTranslateEpubCmd` видна в portrait после фикса видимости | Low | Явная проверка ориентации + `isVisible = false` в portrait в `updateEpubTranslatorVisibility()` |
| `AppSettings` недоступны в `StandalonePlayerActivity` при вызове `updateEpubTranslatorVisibility` | Low | `StandalonePlayerSettingsManager` уже хранит ссылку и доступен через `playerSettingsManager` |
| Иконка кнопки не обновляется (нет language badge) в standalone | Med | `EpubViewerManager`/`PdfViewerManager` сами обновляют `imageTintList` и `setImageDrawable` внутри `updateTranslateButtonIcon()` — не требует действий от активности |
| Регрессия нормального PlayerActivity | Low | Изменяются только standalone-файлы; `SearchControlsManager` и `CommandPanelController` не трогаются |

---

## 9. Testing Plan

### 9.1 Unit Tests

Логика сводится к делегированию; unit-тесты нецелесообразны (мокировать ViewBinding не выгодно).
Покрытие — ручное тестирование + интеграционный тест внутри Maestro.

### 9.2 Manual Test Cases

#### EPUB в StandalonePlayerActivity

1. Открыть `.epub`-файл через «Открыть с помощью» → `StandalonePlayerActivity`.
2. **Portrait**: убедиться, что `btnTranslateEpubCmd` **скрыта**.
3. Повернуть в **landscape**: `btnTranslateEpubCmd` должна появиться (если `enableTranslation=true`).
4. Нажать кнопку → ожидается активация режима перевода (появляется overlay / меняется иконка).
5. Нажать ещё раз → перевод деактивируется.
6. Передача устройства обратно в **portrait** → кнопка снова скрыта; состояние перевода сохраняется.
7. Включить `enableTranslation=false` в настройках → открыть EPUB заново → кнопка **не появляется** в landscape.

#### PDF в StandalonePlayerActivity

1. Открыть `.pdf`-файл через «Открыть с помощью».
2. **Portrait**: `btnTranslatePdfCmd` скрыта (управляется `PdfViewerManager`).
3. **Landscape**: кнопка появляется.
4. Нажать → перевод включается.
5. Нажать повторно → перевод выключается.

#### TXT в StandalonePlayerActivity (regression)

1. Открыть `.txt`-файл → убедиться, что `btnTranslateTextCmd` работает как прежде.

#### Flavor/Settings gates

1. Сборка `lite` или `photos` → кнопки перевода отсутствуют.
2. `standard`/`legacy`, `enableTranslation=false` → кнопки отсутствуют в landscape.

### 9.3 Maestro E2E

Добавить в `maestro/smoke/standalone_doc_translator.yaml`:
```yaml
- launchApp
- openFile: test_media/sample.epub
- assertNotVisible: { id: btnTranslateEpubCmd }  # portrait
- rotateToLandscape
- assertVisible:   { id: btnTranslateEpubCmd }
- tapOn:           { id: btnTranslateEpubCmd }
- assertVisible:   { id: translationOverlay }     # или проверить tint кнопки
```

---

## 10. Accessibility

Кнопки `btnTranslateEpubCmd` и `btnTranslatePdfCmd` уже имеют `android:contentDescription="@string/translate"` в XML-макете `activity_player_unified.xml`. Новые click-listeners не меняют contentDescription. Минимальный размер 48dp — определён в `@dimen/player_cmd_button_size`. TalkBack-поведение корректно без дополнительных изменений.

---

## 11. User-Facing Feature Update

Исправление существующей сломанной функции, новой возможности не добавляется. Тем не менее изменение видимо пользователю:

- `docs/FEATURES.md` (EN): под разделом **PDF / EPUB / Text Viewer** добавить:  
  `- Translation toggle (portrait/landscape-aware) works in both internal browser and standalone "Open with" mode.`
- `docs/FEATURES_RU.md` (RU):  
  `- Кнопка переводчика работает как во встроенном браузере файлов, так и в режиме «Открыть с помощью» (портрет скрывает кнопку, пейзаж — показывает).`
- `docs/FEATURES_UK.md` (UK):  
  `- Кнопка перекладача працює як у вбудованому браузері, так і в режимі «Відкрити за допомогою» (у портреті — прихована, у ландшафті — видима).`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Метод-делегатор в StandaloneViewManager, а не прямой доступ к менеджеру**
- **Decision:** Добавить `toggleEpubTranslation()` / `togglePdfTranslation()` в `StandaloneViewManager`, а не раскрывать `_epubViewerManager` / `_pdfViewerManager` как публичные свойства.
- **Alternatives considered:** Сделать `_pdfViewerManager` `internal` или добавить `val epubViewerManager get() = _epubViewerManager` в `StandaloneViewManager`.
- **Reason:** Инкапсуляция. Паттерн делегатора уже используется повсюду (`showEpubPreviousChapter()`, `showPdfFirstPage()` и т. д.). Прямое раскрытие менеджеров создаёт лишние точки связи.

**ADR-2: Ориентационная видимость кнопки EPUB управляется Activity, а не EpubViewerManager**
- **Decision:** Добавить `updateEpubTranslatorVisibility()` в `StandalonePlayerActivity` и вызывать при `onConfigurationChanged` и после загрузки EPUB, а не изменять логику видимости внутри `EpubViewerManager`.
- **Alternatives considered:** Патчить `EpubViewerManager.kt`, чтобы он проверял ориентацию и возможность standalone-режима.
- **Reason:** `EpubViewerManager` уже используется в нормальном Player, где видимостью управляет `CommandPanelController`. Изменять общий менеджер рискованно — можно сломать нормальный режим. Проще добавить guard в standalone-Activity.

---

## 13. Implementation Steps

1. **[Backup]** Создать резервную копию `StandalonePlayerActivity.kt` перед правкой:
   ```powershell
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" `
             "temp/StandalonePlayerActivity_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.bak"
   ```

2. **[StandaloneViewManager.kt]** Добавить два метода-делегатора рядом со строкой ~297 (после `showEpubCrossSearch()`):
   ```kotlin
   fun toggleEpubTranslation() { _epubViewerManager?.toggleTranslation() }
   fun togglePdfTranslation()  { _pdfViewerManager?.toggleTranslation() }
   ```
   Запустить dev-log:
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt" "StandaloneViewManager" "Add toggleEpubTranslation() and togglePdfTranslation() delegate methods"
   ```

3. **[StandalonePlayerActivity.kt — setupEpubButtons()]** Добавить click-listener для `btnTranslateEpubCmd` в конец метода `setupEpubButtons()`:
   ```kotlin
   binding.btnTranslateEpubCmd.setOnClickListener { viewManager.toggleEpubTranslation() }
   ```

4. **[StandalonePlayerActivity.kt — setupPdfButtons()]** Добавить click-listener для `btnTranslatePdfCmd` в конец метода `setupPdfButtons()`:
   ```kotlin
   binding.btnTranslatePdfCmd.setOnClickListener { viewManager.togglePdfTranslation() }
   ```

5. **[StandalonePlayerActivity.kt — Видимость EPUB в portrait]** Добавить приватный метод и вызвать его:
   ```kotlin
   private fun updateEpubTranslatorVisibility() {
       val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
       val translationEnabled = playerSettingsManager?.currentSettings?.enableTranslation ?: false
       binding.btnTranslateEpubCmd.isVisible =
           BuildConfig.ENABLE_TRANSLATION && translationEnabled && isLandscape
   }
   ```
   Вызвать `updateEpubTranslatorVisibility()`:
   - В `onConfigurationChanged()`
   - Сразу после `setupEpubButtons()` (строка 228), когда тип файла = EPUB
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "StandalonePlayerActivity" "Wire btnTranslateEpubCmd and btnTranslatePdfCmd; add orientation-aware EPUB translator visibility"
   ```

6. **[Verify TXT]** Открыть TXT в standalone и убедиться, что перевод работает (TextViewerManager self-wires; должно было работать и до фикса). Если нет — зафиксировать в issue.

7. **[Build & Smoke]** Собрать `standardDebug`, запустить ручные тест-кейсы из раздела 9.2.
   ```powershell
   .\scripts\builders\build-debug.PS1 -SkipZip
   ```

8. **[Maestro]** Создать `maestro/smoke/standalone_doc_translator.yaml` с базовым flows согласно разделу 9.3.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "maestro/smoke/standalone_doc_translator.yaml" "MaestroSmoke" "Add smoke test for standalone EPUB/PDF translator button"
   ```

9. **[Feature Docs]** Обновить все три FEATURES-файла согласно разделу 11.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Add translator-parity note for standalone mode"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "Add translator-parity note for standalone mode"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "Add translator-parity note for standalone mode"
   ```

### Mandatory step checklist

- [ ] String resources: изменений нет (кнопки уже имеют `@string/translate`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` обновлены (шаг 9)
- [ ] Room DB: нет изменений схемы
- [ ] `.\scripts\add_to_dev_log.ps1` выполнен после каждого изменённого файла (шаги 2, 5, 8, 9)

---

## 14. Out of Scope (future items)

- Long-press на кнопку перевода → диалог выбора языков в standalone (требует портирования `showTranslationSettingsDialog()` из `PlayerCommandPanelCallbackImpl`).
- Перевод изображений (OCR) в standalone: callback `displayOcrText` сейчас — no-op (строка 411 `StandaloneViewManager.kt`).
- Collapse/overflow-menu для кнопок команд в standalone (сейчас в нормальном режиме это делает `CommandPanelController`).
- Унификация layout для standalone vs нормального Player (сейчас оба используют `activity_player_unified.xml`, но кнопки управляются по-разному).
