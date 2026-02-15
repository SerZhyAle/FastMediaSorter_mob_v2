# MASTER IMPROVEMENT PLAN: FastMediaSorter v2

**Created**: 2026-02-14  
**Updated**: 2026-02-15 (решения по опроснику применены)  
**Status**: ACTIVE  
**Source Specs**: 11 documents consolidated  
**Execution Model**: Sequential tracks in priority order, parallel where independent

---

## EXECUTION PROTOCOL

```
FOR EACH step:
  1. EXECUTE task
  2. BUILD:  .\build-debug.PS1
  3. VERIFY: zero new compile errors, zero new critical lint
  4. COMMIT: git add -A && git commit -m "<COMMIT_MSG>"
  5. MARK:   [x] in this document
  6. COPY:   PROMPT for next step → paste into Copilot Chat
```

**RULE**: One step = one atomic commit. No multi-step commits.  
**RULE**: If build fails → fix before proceeding.  
**RULE**: Backup files > 500 lines to `temp/` before modification.  
**RULE**: При неочевидном выборе реализации → СПРОСИТЬ пользователя ПЕРЕД написанием кода. Не гадать.  
**RULE**: Если функция недоступна на старом API → скрыть из UI (graceful degradation). Не делать сложные ветвления.

### BACKUP PROTOCOL

**Перед изменением больших файлов (> 500 строк) — ОБЯЗАТЕЛЬНО делать бэкап:**

```powershell
# Пример: перед изменением PlayerActivity.kt (1200 строк)
Copy-Item "app_v2\src\main\java\com\sza\fastmediasorter\ui\player\PlayerActivity.kt" `
          "temp\backup_PlayerActivity_2026-02-15_16-30.kt"
```

**Формат имени бэкапа**: `backup_<OriginalFileName>_<YYYY-MM-DD>_<HH-MM>.<ext>`

**Для чего нужен бэкап**:

1. Если билд сломался — сравнить текущую версию с бэкапом (`git diff` не всегда удобен для больших изменений).
2. Откатить изменения частично (взять рабочие куски из бэкапа).
3. Проверить, что именно сломалось — diff между бэкапом и текущей версией.

**Инструменты сравнения**:

- VS Code: `code --diff temp\backup_File.kt app_v2\...\File.kt`
- PowerShell: `Compare-Object (Get-Content temp\backup_File.kt) (Get-Content current_File.kt)`
- Git: `git diff temp\backup_File.kt current_File.kt`

**Когда НЕ нужен бэкап**:

- Файлы < 500 строк (малые изменения, легко откатить через git)
- Новые файлы (нечего бэкапить)
- Layout XML/ресурсы (обычно не ломают билд критично)

**Cleanup**: Бэкапы в `temp/` автоматически игнорируются git. Удалять вручную после успешного коммита шага.

---

## PRE-FLIGHT CHECKLIST

**⚠️ ОБЯЗАТЕЛЬНО выполнить ДО НАЧАЛА Track A, Step A.1:**

```powershell
# 1. Проверка текущего билда
.\build-debug.PS1
# Ожидаемый результат: BUILD SUCCESSFUL, 0 errors

# 2. Проверка git статуса
git status
# Ожидаемый результат: working tree clean (или только untracked файлы в temp/)

# 3. Проверка архивации спеков
Test-Path "temp\archived_specs\MAIN_WINDOW_OPTIMIZATION.md"
# Ожидаемый результат: True (все 13 спеков перенесены)

# 4. Проверка Java/Android SDK
java -version    # Java 17+
$env:ANDROID_HOME   # Должен быть установлен
```

**Если любая проверка провалилась** — исправить ДО начала разработки. Начинать Track A только при 100% зелёных проверках.

**Дополнительно (рекомендуется)**:

- Создать бранч: `git checkout -b feature/master-improvements`
- Запустить lint: `.\gradlew.bat lintStandardDebug` (проверить baseline ошибок)
- Проверить свободное место на диске: минимум 5GB для билдов и бэкапов

---

## DEFINITION OF DONE (для каждого шага)

**Шаг считается завершённым, когда ВСЕ критерии выполнены:**

### ✅ ОБЯЗАТЕЛЬНЫЕ критерии

1. **BUILD SUCCESS**: `.\build-debug.PS1` завершается без ошибок
2. **ZERO NEW ERRORS**: Количество compile errors = 0 (допустимы только pre-existing)
3. **ZERO NEW CRITICAL LINT**: Никаких новых Error-level lint проблем
4. **CODE COMMITTED**: `git commit -m "<COMMIT_MSG>"` выполнен с конкретным сообщением из плана
5. **TODO UPDATED**: Галочка `[x]` проставлена в MASTER TODO LIST + в детальной секции шага

### 🟡 РАБОТА С WARNING-АМИ

- **Допустимы**: Pre-existing warnings (которые были до шага)
- **Исправить если возможно**: Новые warnings связанные с изменениями
- **Игнорировать**: Warnings в сгенерированном коде, библиотеках, legacy коде вне scope шага

### 🧪 ТЕСТИРОВАНИЕ

- **После КАЖДОГО шага**: Quick smoke test — запустить приложение, открыть Browse, открыть 1 файл (видео/аудио/изображение)
- **Unit tests**: Писать ТОЛЬКО если шаг явно требует (обычно в конце трека, например C.8, D.7)
- **Регрессия**: Full regression test ТОЛЬКО в конце трека (шаги A.7, B.6, C.8, D.7, etc.)

### 📝 COMMIT MESSAGE FORMAT

```
<type>(<scope>): <short description>

<optional body explaining WHY and WHAT changed>
```

Используй type из плана: `feat`, `refactor`, `perf`, `fix`, `test`, `cleanup`, `polish`

---

## ROLLBACK PROCEDURE

**Если BUILD FAILED после изменений:**

### Сценарий 1: Код ещё НЕ закоммичен

```powershell
# 1. Сравнить с бэкапом
code --diff temp\backup_MyFile_2026-02-15_14-30.kt app_v2\...\MyFile.kt

# 2. Откатить весь файл из бэкапа
Copy-Item temp\backup_MyFile_*.kt app_v2\src\...\MyFile.kt -Force

# 3. Перезапустить шаг с корректировками
.\build-debug.PS1
```

### Сценарий 2: Код закоммичен, но НЕ запушен

```powershell
# 1. Откатить последний коммит (изменения вернутся в working tree)
git reset --soft HEAD~1

# 2. Восстановить из бэкапа нужные файлы
Copy-Item temp\backup_*.kt app_v2\src\...\

# 3. Исправить и перекоммитить
# ... fix code ...
.\build-debug.PS1
git add -A
git commit -m "fix(scope): corrected broken build from previous attempt"
```

### Сценарий 3: Код закоммичен И запушен

```powershell
# 1. Revert commit (создаёт новый коммит отменяющий изменения)
git revert HEAD

# 2. Push revert
git push

# 3. Начать шаг заново в новом коммите
```

**ПРАВИЛО**: Никогда не делать `git push --force` в `main` бранче. Используй `git revert`.

---

## QUICK REFERENCE CARD

### 📁 Структура проекта

```
app_v2/src/main/java/com/sza/fastmediasorter/
├── ui/              # Activities, Fragments, ViewModels
│   ├── browse/
│   ├── player/
│   │   └── helpers/  # PlayerActivity logic ВСЕГДА здесь
│   └── settings/
├── domain/          # UseCases, repository interfaces
│   ├── usecase/
│   └── repository/
└── data/            # Repository implementations, Room, network
    ├── local/
    ├── network/
    └── repository/
```

### 🔨 Build Commands

```powershell
.\build-debug.PS1                    # Быстрый debug (используй по умолчанию)
.\dev\build-with-version.ps1         # Debug с auto-version bump
.\gradlew.bat assembleStandardDebug  # Gradle напрямую
.\gradlew.bat lintStandardDebug      # Lint check
.\gradlew.bat testStandardDebugUnitTest  # Unit tests
```

### 📛 Naming Conventions

| Тип | Pattern | Пример |
|-----|---------|--------|
| UseCase | `VerbNounUseCase` | `GetMediaFilesUseCase` |
| Repository | `NounRepository` | `MediaRepository` |
| ViewModel | `NounViewModel` | `PlayerViewModel` |
| Manager (UI) | `NounVerbManager` | `VideoPlayerManager` |
| Strategy | `NounStrategy` | `SmbOperationStrategy` |

### 🐛 Типичные проблемы

**Hilt не inject-ит**:

- ✅ Проверь `@HiltViewModel` на ViewModel
- ✅ Проверь `@AndroidEntryPoint` на Activity/Fragment
- ✅ Проверь `@Inject constructor` на UseCase/Repository

**ExoPlayer не воспроизводит**:

- ✅ Проверь lifecycle: init in `onCreate`, release in `onDestroy`
- ✅ Проверь permissions (INTERNET, READ_EXTERNAL_STORAGE)
- ✅ Проверь URL/path encoding (пробелы → `%20`)

**RecyclerView мерцает**:

- ✅ Используй `DiffUtil` или `submitList()` на `ListAdapter`
- ✅ Проверь stable IDs: `setHasStableIds(true)` + override `getItemId()`

**Room migration failed**:

- ✅ Версия БД увеличена в `@Database(version = X)`?
- ✅ Migration добавлена в `addMigrations(MIGRATION_X_Y)`?

---

## TESTING STRATEGY

### 🧪 Уровни тестирования

**1. Quick Smoke Test (после КАЖДОГО шага)**:

```
1. Запустить app на эмуляторе/устройстве
2. Открыть Browse → выбрать ресурс (Local/SMB)
3. Открыть 1 файл каждого типа: видео, аудио, изображение
4. Проверка: открылось без краша, основные контролы работают
Время: ~2 минуты
```

**2. Track Regression Test (в конце трека, шаги X.7, X.8)**:

```
1. Smoke test
2. Проверить 3-5 основных сценариев трека (из Tasks шагов)
3. Проверить что старые фичи не сломались (open file, play, delete, etc.)
Время: ~10 минут
```

**3. Full Regression (только FINAL VALIDATION перед релизом)**:

```
1. Все flavors: standard, lite, photos, legacy
2. Все типы файлов: video, audio, image, GIF, PDF, EPUB, text
3. Все источники: Local, SMB, FTP, SFTP, Cloud (Drive/OneDrive/Dropbox)
4. Все основные операции: browse, play, sort, filter, move, copy, delete
Время: ~2 часа
```

### 📝 Unit Tests Strategy

**Когда писать**:

- UseCases: если логика нетривиальна (> 10 строк бизнес-логики)
- ViewModels: если сложные state transitions (D.1, E.1, F.1, G.1)
- Utilities: всегда (TextFilePager, CharsetDetector, PdfColorConversion)

**Когда НЕ писать**:

- Simple CRUD операции
- UI-only changes (layout, colors, strings)
- Glue code (Hilt modules, data classes)

**Coverage target**: 60% на `domain/` layer, 30% на `ui/` ViewModels. Не гнаться за 100%.

---

## DEVELOPMENT LOG

**Рекомендуется (опционально)**: создать файл `temp/DEVELOPMENT_LOG.md` для записи решений и проблем в процессе.

**Формат (пример)**:

```markdown
# Development Log: Master Improvement Plan

## 2026-02-15 | Track A, Step A.2

**Вопрос**: BrowseViewModel.reloadFiles() вызывается из 3 мест — из какого убрать `emptyList`?  
**Решение**: Убрал из стандартного refresh path (user pull-to-refresh). Оставил в: ресурс change, фильтр clear.  
**Commit**: a1b2c3d

**Проблема**: После A.2 тесты падают с NullPointerException в FavoritesDao.  
**Решение**: Batch query возвращал null для несуществующих путей. Добавил elvis operator `?: false`.  
**Commit**: d4e5f6g

---

## 2026-02-16 | Track A, Step A.4

**Отклонение от плана**: Вместо `bindingAdapterPosition` использовал `absoluteAdapterPosition` — deprecated, но работает на всех версиях RecyclerView.  
**Обоснование**: `bindingAdapterPosition` added in RecyclerView 1.2.0, у нас 1.1.0. Апгрейд вне scope шага.  
**TODO**: Апгрейдить RecyclerView к 1.3.0+ в Track K.5 (compatibility stabilization).
```

**Зачем нужен лог**:

- Память о решениях через месяц ("Почему мы сделали так?")
- Передача контекста новому разработчику
- Audit trail для code review

---

## BRANCH STRATEGY

**Рекомендуемый подход**:

```powershell
# Перед началом Track A
git checkout -b feature/master-improvements
git push -u origin feature/master-improvements

# Работа идёт в feature-бранче
# Каждый шаг = 1 коммит
# Push после каждого трека (A.7, K.5, B.6, ...) или ежедневно

# После завершения ВСЕХ 54 шагов + Final Validation
git checkout main
git merge feature/master-improvements --no-ff
git push origin main
```

**Альтернатива (если работаешь один)**:

```powershell
# Работать напрямую в main
# Push после каждого трека
# Если что-то совсем сломалось — revert последний коммит
```

**ПРАВИЛО**: Никогда не делать `git push --force` в main или feature-бранчах, которые используют другие разработчики.

---

## MASTER TODO LIST

**Порядок выполнения**: A → K → B → C → D → G → F → E → H → I → J  
**Прогресс**: 3 / 54 шагов

### TRACK A: Main Window (7 шагов)

- [x] 1. A.1 — Browse Anti-Flicker
- [x] 2. A.2 — Batch Favorites
- [x] 3. A.3 — Directory Hash Optimization
- [ ] 4. A.4 — Adapter Listener Optimization
- [ ] 5. A.5 — ViewStub Audit
- [ ] 6. A.6 — Player Warm-up (Feature-Flagged)
- [ ] 7. A.7 — StrictMode & Glide Profiling

### TRACK K: Device Compatibility (5 шагов)

- [ ] 8. K.1 — Memory-Aware Image Loading
- [ ] 9. K.2 — Storage Permission Unification
- [ ] 10. K.3 — Tablet, Laptop & Screen Adaptation
- [ ] 11. K.4 — Feature Degradation & Cloud Safety
- [ ] 12. K.5 — Compatibility Stabilization

### TRACK B: Settings (6 шагов)

- [ ] 13. B.1 — Base Settings Binding Layer
- [ ] 14. B.2 — Main Thread Safety
- [ ] 15. B.3 — Media Tab Simplification
- [ ] 16. B.4 — Global Settings Search
- [ ] 17. B.5 — Reset Section + Import/Export UX
- [ ] 18. B.6 — Visual Cleanup & Stabilization

### TRACK C: Resources (8 шагов)

- [ ] 19. C.1 — Domain Contracts & Strategy Interfaces
- [ ] 20. C.2 — Strategy Implementations
- [ ] 21. C.3 — Orchestration UseCase
- [ ] 22. C.4 — Unified ViewModel
- [ ] 23. C.5 — Unified Editor UI
- [ ] 24. C.6 — Edit Mode Features
- [ ] 25. C.7 — Copy/Duplicate Mode Features
- [ ] 26. C.8 — Cleanup & Regression

### TRACK D: Static Image (8 шагов)

- [ ] 27. D.1 — Renderer Contracts & State Machine
- [ ] 28. D.2 — Dual-Surface Layout
- [ ] 29. D.3 — Image Loading Integration (Instant Swap)
- [ ] 30. D.4 — Prefetch Priority & Lookahead
- [ ] 31. D.5 — Gesture Unification
- [ ] 32. D.6 — Slideshow Sync
- [ ] 33. D.7 — Stabilization & Legacy Cleanup
- [ ] 34. D.8 — Slideshow Keep-Awake

### TRACK G: Audio (4 шага)

- [ ] 35. G.1 — Audio Service Core (Audio-Only)
- [ ] 36. G.2 — Background Support & Notifications
- [ ] 37. G.3 — Audio UI Connection
- [ ] 38. G.4 — Playback Indicator & Sleep Timer

### TRACK F: Video (3 шага)

- [ ] 39. F.1 — Gesture Engine
- [ ] 40. F.2 — Custom Controls & Seeking
- [ ] 41. F.3 — Picture-in-Picture (Android 12+)

### TRACK E: Animated Image (4 шага)

- [ ] 42. E.1 — Controller Abstraction
- [ ] 43. E.2 — Play/Pause Implementation
- [ ] 44. E.3 — Frame Extraction (Раскадровка)
- [ ] 45. E.4 — Stabilization & Edge Cases

### TRACK H: Text (3 шага)

- [ ] 46. H.1 — Core IO: Pager + Encoding (up to 100MB)
- [ ] 47. H.2 — Rich Rendering & Reader UI
- [ ] 48. H.3 — Editor Enhancements

### TRACK I: PDF (3 шага)

- [ ] 49. I.1 — Vertical Scroll Engine
- [ ] 50. I.2 — Night Mode & Comfort
- [ ] 51. I.3 — Thumbnail Navigation

### TRACK J: EPUB (3 шага)

- [ ] 52. J.1 — Table of Contents Navigation
- [ ] 53. J.2 — Styling Engine
- [ ] 54. J.3 — Full-Text Search

**Как отмечать прогресс**:

1. После завершения шага: заменить `- [ ]` на `- [x]` в этом TODO-листе
2. Параллельно: отметить `- [x] **DONE**` в детальной секции шага
3. Обновить счётчик "Прогресс: X / 54 шагов" в начале этой секции

---

## DEPENDENCY GRAPH

```
LAYER 0: FOUNDATION (no player deps)
  ├── TRACK A: Main Window Optimization
  ├── TRACK B: Settings Improvement
  ├── TRACK C: Resource Management
  │     ├── C1: Resource Creation Unification
  │     └── C2: Resource Edit/Copy (depends on C1)
  └── TRACK K: Device & Screen Compatibility (parallel with A/B/C)

LAYER 1: IMAGE PIPELINE (depends on A.4 adapter optimization)
  ├── TRACK D: Static Image Playback
  └── TRACK E: Animated Image Playback (depends on D.1 renderer contracts)

LAYER 2: MEDIA PLAYBACK (depends on D.5 gesture unification)
  ├── TRACK F: Video Playback
  └── TRACK G: Audio Playback (depends on F decoupling pattern)

LAYER 3: DOCUMENT VIEWERS (independent, parallel)
  ├── TRACK H: Text Playback
  ├── TRACK I: PDF Playback
  └── TRACK J: EPUB Playback
```

**Parallelizable**: A ∥ K ∥ B ∥ C | D then E | G then F | H ∥ I ∥ J  
**Execution order**: A → K → B → C → D → G → F → E → H → I → J

---

## RISK MAP: РЕШЁННЫЕ ВОПРОСЫ

Все спорные решения приняты (2026-02-15). Ниже — итоговые решения и их влияние на план.

### ✅ РЕШЕНО: G.1–G.3 — Audio Service (поэтапно, audio-only)

- **Решение**: Audio-only Service рядом с текущим. Video playback остаётся в Activity без изменений.
- **Scope**: Только для музыки. Background playback — опциональная функция в настройках.
- **Влияние**: ExoPlayer для video НЕ трогаем. Риск серьёзно снижен — текущее видео не ломается.
- **Доп.задача**: Добавлен шаг D.8 — slideshow keep-awake (FLAG_KEEP_SCREEN_ON во время слайдшоу, независимо от глобальной настройки).

### ✅ РЕШЕНО: E.3 — GIF Speed УБРАН из плана

- **Решение**: Variable speed rendering НЕ ДЕЛАЕМ. Никаких Glide-хаков.
- **Замена**: E.3 = GIF Frame Extraction (раскадровка — создание серии PNG из GIF).
- **Принцип**: Если API недоступен на старом устройстве — функция скрывается из UI.
- **Влияние**: E.3 полностью переписан. E.4 заменён на стабилизацию раскадровки.

### ✅ РЕШЕНО: F.3 — PiP только Android 12+

- **Решение**: PiP реализуем только для Android 12+ (auto-enter API). Опция в настройках.
- **Старые устройства**: PiP-кнопка скрыта из UI. Функция gracefully absent.
- **Влияние**: Никакого lifecycle ветвления для Android 8-11. Чистая реализация.

### ✅ РЕШЕНО: D.3 — Instant swap, без cross-fade

- **Решение**: Только instant swap. Cross-fade НЕ ДЕЛАЕМ.
- **Влияние**: D.3 упрощён кардинально — нет dual-bitmap peak memory, нет синхронизации переходов. Один путь отображения. Dual-surface layout (D.2) используется только для prefetch.

### ✅ РЕШЕНО: G.4 — Вращающаяся пластинка вместо FFT

- **Решение**: Простая GIF-анимация вращающейся виниловой пластинки в углу экрана. Без FFT, без AudioProcessor, без RECORD_AUDIO.
- **Влияние**: G.4 превращается из сложной аудио-задачи в простую UI-задачу. Sleep Timer остаётся.

### ✅ РЕШЕНО: H.1 — Лимит 100MB

- **Решение**: Максимальный поддерживаемый размер текстового файла — 100MB.
- **Влияние**: Простая реализация чанками без сложной стыковки encoding на границах. Файлы > 100MB — предупреждение.

### ✅ РЕШЕНО: Приоритеты треков

**Порядок выполнения**: A → K → B → C → D → G → F → E → H → I → J

### ✅ РЕШЕНО: Совместимость устройств

- **Базовая версия**: Android 9+ (API 28) — все основные функции.
- **Legacy (API 23-27)**: Nice-to-have. Если функция недоступна — скрывается из UI. Сложные ветвления ради старых API НЕ ДЕЛАЕМ.
- **Большие экраны**: Планшеты, ноутбуки — ДА, важно. Tablet layout (sw600dp+) обязателен.
- **Маленькие экраны**: Цель — от 240×240. Если сложно — минимум 480×480.

### ПРОЕКТНЫЙ ПРОТОКОЛ: ВОПРОСЫ ВО ВРЕМЯ РАБОТЫ

**ПРАВИЛО**: При выполнении каждой задачи — если есть неочевидный выбор реализации, спрашивать пользователя ПЕРЕД написанием кода. Не гадать.  
**ПРАВИЛО**: Задавать вопросы типа "А или Б?" с описанием последствий каждого варианта.

---

# LAYER 0: FOUNDATION

---

## TRACK A: Main Window Optimization

**🔴 ПРИОРИТЕТ: 1 из 11** | **ШАГИ: 1-7 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: K (Device Compatibility)

**Source**: `MAIN_WINDOW_OPTIMIZATION.md`  
**Goal**: Responsive Browse, no flicker, fast player entry  
**Risk**: Hidden logic depends on intermediate empty-state  

### A.1 — Browse Anti-Flicker

- [x] **DONE**

**Tasks**:

1. In `BrowseViewModel.reloadFiles()`: remove `mediaFiles = emptyList()` from standard refresh path.
2. Update `StateFlow` only with complete data snapshot (post-load + post-sort).
3. Allow intermediate clear ONLY on: resource change, radical filter change, explicit user action.
4. Add explicit UI-state transitions for edge-cases.

**Files**: `BrowseViewModel.kt`, related StateFlow consumers  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(browse): anti-flicker list update — emit only complete snapshots`  
**PROMPT**:

```
Track A, Step A.2. Implement batch favorites API.
Add `getFavoritesForPaths(paths: List<String>): Map<String, Boolean>` to FavoritesRepository.
Replace N sequential DB queries with single batch query.
Integrate into BrowseViewModel file loading pipeline — single updateState with files+favorites.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.2
```

---

### A.2 — Batch Favorites

- [x] **DONE**

**Tasks**:

1. Add `getFavoritesForPaths(paths: List<String>): Map<String, Boolean>` to `FavoritesRepository` / DAO.
2. Replace N sequential `isFavorite()` calls with single batch query.
3. Integrate into `BrowseViewModel` loading pipeline — combine files + favorites into single `updateState`.
4. Fallback: two-phase render (files → favorites) only if batch latency exceeds first-frame target.

**Files**: `FavoritesDao.kt`, `FavoritesRepository.kt`, `BrowseViewModel.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `perf(browse): batch favorites loading — single DB query replaces N calls`  
**PROMPT**:

```
Track A, Step A.3. Optimize directory hash computation.
Replace string concatenation hash with rolling hash / XOR-64bit-mix by (name, size, mtime).
Move computation to Dispatchers.Default.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.3
```

---

### A.3 — Directory Hash Optimization

- [x] **DONE**

**Tasks**:

1. Find `computeDirectoryHash` implementation.
2. Replace full string concatenation with rolling hash / XOR-64bit-mix over `(name, size, mtime)`.
3. Move computation to `Dispatchers.Default`.

**Files**: `BrowseViewModel.kt` or utility class containing hash logic  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `perf(browse): lightweight directory hash — rolling XOR replaces string concat`  
**PROMPT**:

```
Track A, Step A.4. Optimize RecyclerView adapter listeners.
Move click listener setup from onBindViewHolder to ViewHolder.init.
Use bindingAdapterPosition + getItem(position) in handlers.
Eliminate new listener object creation in onBindViewHolder.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.4
```

---

### A.4 — Adapter Listener Optimization

- [ ] **DONE**

**Tasks**:

1. Locate media file `RecyclerView.Adapter`.
2. Move click listener setup to `ViewHolder.init {}` block.
3. In handlers, use `bindingAdapterPosition` + `getItem(position)` for data access.
4. Remove all listener object creation from `onBindViewHolder`.

**Files**: `MediaFileAdapter.kt` (or equivalent adapter)  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `perf(browse): adapter listeners moved to ViewHolder init — zero alloc in bind`  
**PROMPT**:

```
Track A, Step A.5. Audit layouts for ViewStub candidates.
Inspect item_media_file.xml and related layouts.
Move rare heavy elements to ViewStub.
Do NOT ViewStub frequently-visible elements.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.5
```

---

### A.5 — ViewStub Audit

- [ ] **DONE**

**Tasks**:

1. Audit `item_media_file.xml` and related list item layouts.
2. Identify rare, heavy UI elements (cloud badges, special indicators, etc.).
3. Wrap identified elements in `ViewStub`.
4. Do NOT wrap frequently-visible elements.

**Files**: `item_media_file.xml`, related layout files  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `perf(browse): ViewStub for rare heavy layout elements`  
**PROMPT**:

```
Track A, Step A.6. Add optional player warm-up behind feature flag.
Add warm-up flag to AppSettings. Trigger warm-up when >=80% video in current set.
Limit to infrastructure prep only — no content preload, no UI side effects.
Guarantee safe cancellation on screen exit.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.6
```

---

### A.6 — Player Warm-up (Feature-Flagged)

- [ ] **DONE**

**Tasks**:

1. Add `enablePlayerWarmup: Boolean` flag to `AppSettings` (default = `false`).
2. In `BrowseViewModel` or relevant scope: detect ≥80% video in current file set.
3. If flag ON + threshold met: launch warm-up coroutine (infrastructure only, no content preload).
4. Cancel warm-up on screen exit (`viewModelScope` lifecycle).

**Files**: `AppSettings.kt`, `BrowseViewModel.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(browse): optional player warm-up behind feature flag`  
**PROMPT**:

```
Track A, Step A.7. Enable StrictMode in debug for Main-thread I/O detection.
Add StrictMode setup in FastMediaSorterApp for debug builds.
Profile Glide decoding on large thumbnails.
Fix any Main-thread violations found.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.7
```

---

### A.7 — StrictMode & Glide Profiling

- [ ] **DONE**

**Tasks**:

1. In `FastMediaSorterApp.onCreate()`: enable `StrictMode.ThreadPolicy` for disk/network reads on Main thread (debug only).
2. Profile Glide thumbnail decoding — log timing for large images.
3. Fix any Main-thread I/O violations detected.

**Files**: `FastMediaSorterApp.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `quality(debug): StrictMode enabled — Main-thread I/O detection active`  
**PROMPT**:

```
Track A COMPLETE. Proceed to Track B, Step B.1.
Create BaseSettingsFragment or equivalent helper for unified settings binding.
Cover: Switch↔StateFlow, Spinner↔StateFlow, isUpdating cycle guard.
Remove binding duplication from concrete settings fragments.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.5
```

---

## TRACK B: Settings Improvement

**🔴 ПРИОРИТЕТ: 3 из 11** | **ШАГИ: 13-18 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: C (Resources)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track K

**Source**: `SETTINGS_IMPROVEMENT_SPEC.md`  
**Goal**: Fast setting discovery, no nested tabs, no Main-thread I/O  
**Risk**: Navigation regression after nested tabs removal  

### B.1 — Base Settings Binding Layer

- [ ] **DONE**

**Tasks**:

1. Create `BaseSettingsFragment` (or helper utility class).
2. Implement: `Switch` ↔ `StateFlow` binding with `isUpdating` guard.
3. Implement: `Spinner` ↔ `StateFlow` binding with `isUpdating` guard.
4. Implement: input field ↔ `StateFlow` binding with `isUpdating` guard.
5. Migrate one existing settings fragment to validate pattern.

**Files**: new `BaseSettingsFragment.kt`, one pilot fragment  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `refactor(settings): BaseSettingsFragment — unified control binding with cycle guard`  
**PROMPT**:

```
Track B, Step B.2. Move all I/O off Main Thread in settings fragments.
All file/log/disk reads → Dispatchers.IO.
Heavy computations → Dispatchers.Default.
No blocking loads in onCreateView — show loading indicator for long ops.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.4
```

---

### B.2 — Main Thread Safety

- [ ] **DONE**

**Tasks**:

1. Audit all settings fragments for Main-thread I/O (file reads, disk access, log loading).
2. Move all I/O to `Dispatchers.IO`.
3. Move heavy computations to `Dispatchers.Default`.
4. Replace blocking loads in `onCreateView` with async load + loading indicator.
5. Add error state handling for failed loads.

**Files**: All `*SettingsFragment.kt` files  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `perf(settings): all I/O moved off Main Thread`  
**PROMPT**:

```
Track B, Step B.3. Remove nested tabs from Media settings.
Delete nested ViewPager from MediaSettingsFragment.
Replace with single vertical ScrollView with expandable sections: Images, Video, Audio, Documents, Other.
No additional master-detail screens.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.2
```

---

### B.3 — Media Tab Simplification

- [ ] **DONE**

**Tasks**:

1. Remove nested `ViewPager` from `MediaSettingsFragment`.
2. Replace with single vertical layout.
3. Add expandable sections: `Images`, `Video`, `Audio`, `Documents`, `Other`.
4. Migrate all nested-fragment settings content into sections.
5. Test swipe conflict resolution.

**Files**: `MediaSettingsFragment.kt`, associated layout XML, child fragment cleanup  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `refactor(settings): Media tab — flat sections replace nested ViewPager`  
**PROMPT**:

```
Track B, Step B.4. Implement global settings search.
Create SettingsSearchIndex (key, title, keywords, sectionId, destination, viewId).
Add search action to SettingsActivity Toolbar.
On result tap: navigate to screen, scroll to control, highlight for 1-2 sec.
Filter: case-insensitive match on title+keywords.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.1
```

---

### B.4 — Global Settings Search

- [ ] **DONE**

**Tasks**:

1. Create `SettingsSearchIndex` data class: `key`, `title`, `keywords`, `sectionId`, `destination`, `viewId`.
2. Build centralized search index (no runtime reflection).
3. Add search action icon to `SettingsActivity` `Toolbar`.
4. Implement search results `RecyclerView` (flat list: title + section + description).
5. On tap: navigate to screen → scroll to target view → highlight 1-2 sec.
6. Filter: case-insensitive match on `title + keywords`, debounced input.

**Files**: new `SettingsSearchIndex.kt`, `SettingsActivity.kt`, new `SettingsSearchAdapter.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(settings): global search — index, toolbar action, result navigation`  
**PROMPT**:

```
Track B, Step B.5. Add section reset and improve import/export UX.
Add "Reset Section" button per logical settings group.
Reset affects only current section parameters.
Make import/export explicit in UI (buttons/menu with clear labels).
Add confirmation dialogs and result messages for reset/import/export.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.6
```

---

### B.5 — Reset Section + Import/Export UX

- [ ] **DONE**

**Tasks**:

1. Add `Reset Section` action per logical settings group.
2. Reset resets only current section parameters to defaults.
3. Move import/export to explicit UI elements (buttons/menu items with descriptive labels).
4. Add confirmation dialog before destructive actions (reset, import overwrite).
5. Show user-facing result message (success/failure) after operation.

**Files**: Settings fragments, `SettingsActivity.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(settings): section reset + explicit import/export UX`  
**PROMPT**:

```
Track B, Step B.6. Visual cleanup and stabilization.
Unify spacing, padding, header sizes across all settings screens.
Regression test: all settings accessible, all feature flags respected.
Profile SettingsActivity open time — no UI freeze.
Source: SETTINGS_IMPROVEMENT_SPEC.md §4.3
```

---

### B.6 — Visual Cleanup & Stabilization

- [ ] **DONE**

**Tasks**:

1. Audit all settings screens: unify spacing, padding, header sizes, section separators.
2. Use project design system only (no new colors/shadows outside theme).
3. Regression test: all settings reachable, all `BuildConfig` feature flags respected.
4. Profile `SettingsActivity` open time — confirm no UI freeze.
5. Verify screen rotation safety.

**Files**: Settings layout XMLs, style resources  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `polish(settings): visual unification + stabilization pass`  
**PROMPT**:

```
Track B COMPLETE. Proceed to Track K, Step K.1 (parallel with Track C).
Add isLowRamDevice / memory tier detection to ImageLoadingManager.
If low RAM: Glide → RGB_565, dontAnimate, reduced thumbnail resolution.
Disable document previews (PDF/EPUB covers) on low-end devices.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §4.2
```

---

## TRACK K: Device & Screen Compatibility

**🔴 ПРИОРИТЕТ: 2 из 11** | **ШАГИ: 8-12 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: B (Settings)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track A

**Source**: `OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md`  
**Goal**: Graceful degradation on old devices, tablet/laptop layouts, small screens, permission safety across API 28-35  
**Risk**: Low — defensive checks, no architectural changes  
**Policy**: Android 9+ (API 28) = обязательная поддержка. Android 6-8 (API 23-27) = nice-to-have без сложных ветвлений.  
**Screens**: Tablet/laptop (sw600dp+) — обязательно. Малые экраны — цель 240×240, минимум 480×480 если сложно.  

### K.1 — Memory-Aware Image Loading

- [ ] **DONE**

**Tasks**:

1. In `ImageLoadingManager`: add `ActivityManager.isLowRamDevice()` + total RAM check.
2. If low-end (< 3GB RAM):
   - Force Glide `DecodeFormat.PREFER_RGB_565` (50% memory per pixel).
   - Add `.dontAnimate()` to Glide requests.
   - Reduce thumbnail resolution (override size down).
   - Disable PDF/EPUB cover previews → show generic file icon.
3. Add `MemoryTier` enum: `LOW`, `STANDARD`, `HIGH` — centralize detection.
4. Wire `MemoryTier` into `AppSettings` or singleton for reuse.

**Files**: `ImageLoadingManager.kt`, new `MemoryTier.kt` or extension in `AppSettings.kt`  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `perf(compat): memory-aware image loading — RGB_565, no-animate for low RAM`  
**PROMPT**:

```
Track K, Step K.2. Multi-API storage permission handling.
Unify permission logic: API 30+ → MANAGE_EXTERNAL_STORAGE, API 29 → legacy flag + SAF fallback,
API 23-28 → READ/WRITE_EXTERNAL_STORAGE.
Clean separation by Build.VERSION.SDK_INT. Add checkStoragePermissions() utility.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §4.1
```

---

### K.2 — Storage Permission Unification

- [ ] **DONE**

**Tasks**:

1. Create `StoragePermissionHelper` utility (or extend existing permission logic).
2. Implement `checkStoragePermissions(activity)` with clean API level branching:
   - API 30+ (Android 11+): `Environment.isExternalStorageManager()`.
   - API 29 (Android 10): `requestLegacyExternalStorage` + SAF fallback if denied.
   - API 28 (Android 9): `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`.
   - API 23-27 (Android 6-8): только если legacy flavor — простой `READ_EXTERNAL_STORAGE`. Без сложных ветвлений.
3. Route user to appropriate settings page based on OS version when permission missing.
4. Test: permission flow on API 28, 29, 30, 33, 35 emulators.

**Files**: new `StoragePermissionHelper.kt` or existing permission utility  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `fix(compat): unified storage permissions — API 28-35 clean branching`  
**PROMPT**:

```
Track K, Step K.3. Tablet, laptop and screen adaptation.
Create layout-sw600dp for activity_browse — RecyclerView GridLayoutManager 3 columns.
Ensure all input screens (AddResource, Rename) wrapped in ScrollView for keyboard.
Audit small screens: target 240×240, minimum 480×480 if complex.
Fix text truncation on small screens.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §2
```

---

### K.3 — Tablet, Laptop & Screen Adaptation

- [ ] **DONE**

**Tasks**:

1. Create `layout-sw600dp/activity_browse.xml`: `GridLayoutManager` with 3+ columns for tablet/laptop.
2. Create `layout-sw600dp` variants for other key screens if needed (player, settings).
3. Wrap all input screens (`AddResourceActivity`, `RenameDialog`, etc.) in `ScrollView` root — handle keyboard on short 16:9.
4. Audit small screens:
   - Цель: работоспособность на 240×240.
   - Минимум: 480×480 если 240×240 требует сложного кодинга.
5. Fix text truncation / overlapping buttons на маленьких экранах (в Toolbars, dialogs).
6. Convert `wrap_content` widths to `0dp` + constraint weights where truncation occurs.

**Files**: new `layout-sw600dp/` files, existing input screen layouts  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `feat(compat): tablet/laptop layout sw600dp + small screen fixes`  
**PROMPT**:

```
Track K, Step K.4. Feature degradation for old devices.
Hide OCR/Text Analysis settings on < 4GB RAM or < API 26.
Wrap Google Drive init in PlayServices availability check — hide if unavailable.
Disable Material3 DynamicColors on < API 31 — use fixed brand theme.
Replace heavy ripples with simple state drawables on low-RAM devices.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §4.3-4.5
```

---

### K.4 — Feature Degradation & Cloud Safety

- [ ] **DONE**

**Tasks**:

1. Hide "OCR / Text Analysis" settings on devices with < 4GB RAM or `Build.VERSION.SDK_INT < 26`.
2. Show grayed-out state with note "Requires newer device" instead of crash.
3. Allow manual single-file OCR with warning "This may be slow".
4. Wrap Google Drive initialization in `GoogleApiAvailability.isGooglePlayServicesAvailable()` check.
5. If Play Services unavailable: hide Google Drive option entirely (keep Dropbox/OneDrive/FTP/SMB).
6. Material3 DynamicColors: apply only on API 31+. Fixed brand theme on older devices.
7. On low-RAM devices: replace unbounded ripple effects with simple `StateListDrawable`.

**Files**: `SettingsActivity.kt`, `ImageLoadingManager.kt`, `FastMediaSorterApp.kt`, cloud init code  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `fix(compat): feature degradation — OCR/Cloud/Theme guards for old devices`  
**PROMPT**:

```
Track K, Step K.5. Compatibility stabilization pass.
Test on emulators: API 28 (standard), API 29, API 30, API 33, API 35.
Optional: API 23 (legacy flavor) if supported without complex branching.
Verify: permission flows, image loading on 2GB RAM, tablet layout, small screen, cloud options.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §1, §3
```

---

### K.5 — Compatibility Stabilization

- [ ] **DONE**

**Tasks**:

1. Test on emulators: API 28 (standard flavor), API 29, API 30, API 33, API 35.
2. Опционально: API 23 (legacy flavor build) — только если поддержка не потребовала сложных ветвлений.
3. Verify: permission flows work per API level.
4. Verify: image loading on 2GB RAM emulator — no OOM, reduced quality active.
5. Verify: tablet layout (sw600dp) — 3-column grid, no stretched elements.
6. Verify: small screen (240×240 or 480×480) — no truncation, usable UI.
7. Verify: cloud options visibility — Google Drive hidden without Play Services.
8. Fix any found issues.

**Files**: emulator testing, bug fixes as discovered  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `test(compat): stabilization pass — API 28-35, low-RAM, tablet, small screen verified`  
**PROMPT**:

```
Track K COMPLETE. Proceed to Track C, Step C.1.
Introduce domain contracts for unified resource editor:
ResourceEditorMode (CREATE|EDIT|COPY), ResourceFormData, ResourceValidationResult, ResourceConnectionTestResult.
Create ResourceStrategy interface with validate/testConnection/normalizeBeforeSave/fieldSchema.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §5.1-5.2
```

---

## TRACK C: Resource Management

**🔴 ПРИОРИТЕТ: 4 из 11** | **ШАГИ: 19-26 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: D (Static Image)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track B

**Source**: `RESOURCE_CREATION_IMPROVEMENT_SPEC.md` + `RESOURCE_EDITING_COPYING_SPEC.md`  
**Goal**: Single form engine for CREATE/EDIT/COPY, strategy-based validation  
**Risk**: Credential edge-cases, regression in auth flows  

### C.1 — Domain Contracts & Strategy Interfaces

- [ ] **DONE**

**Tasks**:

1. Create `ResourceEditorMode` enum: `CREATE`, `EDIT`, `COPY`.
2. Create `ResourceFormData` model: common fields + credentials + type-specific + metadata.
3. Create `ResourceValidationResult`: `isValid`, `fieldErrors: Map<FieldKey, ErrorCode>`, `globalErrors`.
4. Create `ResourceConnectionTestResult`: `status`, `latencyMs`, `errorCode`, `diagnosticMessage`.
5. Create `ResourceStrategy` interface: `validate()`, `testConnection()`, `normalizeBeforeSave()`, `fieldSchema()`.

**Files**: new files in `domain/model/` and `domain/strategy/`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): domain contracts — mode, form data, validation, strategy interface`  
**PROMPT**:

```
Track C, Step C.2. Implement strategy classes per resource type.
Create: LocalResourceStrategy, SmbResourceStrategy, SftpResourceStrategy, FtpResourceStrategy, CloudResourceStrategy.
Each implements validate(), testConnection(), normalizeBeforeSave(), fieldSchema().
Migrate existing validation rules from AddResourceViewModel/EditResourceViewModel.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §5.2
```

---

### C.2 — Strategy Implementations

- [ ] **DONE**

**Tasks**:

1. Implement `LocalResourceStrategy`.
2. Implement `SmbResourceStrategy`.
3. Implement `SftpResourceStrategy`.
4. Implement `FtpResourceStrategy`.
5. Implement `CloudResourceStrategy` (with provider sub-routing).
6. Migrate existing validation rules from `AddResourceViewModel` and `EditResourceViewModel`.

**Files**: new files in `domain/strategy/`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): strategy implementations — Local, SMB, SFTP, FTP, Cloud`  
**PROMPT**:

```
Track C, Step C.3. Create ResourceEditorUseCase orchestration.
Load initial state by mode (CREATE/EDIT/COPY).
Route validation and connection test to selected strategy.
Build persistence-ready model. Save + trigger async post-save verification.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §5.3
```

---

### C.3 — Orchestration UseCase

- [ ] **DONE**

**Tasks**:

1. Create `ResourceEditorUseCase`.
2. Implement mode-based state initialization (`CREATE` = empty, `EDIT` = load original, `COPY` = clone + reset id).
3. Route `validate()` and `testConnection()` to strategy selected by `resourceType`.
4. Build persistence-ready model via `normalizeBeforeSave()`.
5. Save resource. Return success immediately.
6. Launch async post-save verification/scan job.
7. Update resource status: `PendingVerification` → `Verified` / `NeedsAttention`.

**Files**: new `ResourceEditorUseCase.kt` in `domain/usecase/`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): ResourceEditorUseCase — unified orchestration for create/edit/copy`  
**PROMPT**:

```
Track C, Step C.4. Create unified ResourceFormViewModel.
Single ViewModel for CREATE/EDIT/COPY.
Hold StateFlow<ResourceEditorUiState>.
Expose: onFieldChanged, onTestConnection, onSave, onRetry.
Zero blocking I/O on Main dispatcher. One-off events via SharedFlow.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §6.2-6.3
```

---

### C.4 — Unified ViewModel

- [ ] **DONE**

**Tasks**:

1. Create `ResourceFormViewModel` (`@HiltViewModel`).
2. Define `ResourceEditorUiState` data class: `formData`, `fieldStates`, `isTestingConnection`, `isSaving`, `connectionResult`, `saveResult`, `isReadOnlyMode`.
3. Expose `StateFlow<ResourceEditorUiState>`.
4. Implement intent handlers: `onFieldChanged()`, `onTestConnection()`, `onSave()`, `onRetry()`.
5. All I/O on `Dispatchers.IO`. One-off events (navigation, toast) via `SharedFlow`.

**Files**: new `ResourceFormViewModel.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): ResourceFormViewModel — unified state management for all modes`  
**PROMPT**:

```
Track C, Step C.5. Build unified ResourceEditorFragment UI.
Single reusable fragment for CREATE/EDIT/COPY.
Inputs: mode, resourceId, preselected resourceType.
Dynamic fields from strategy fieldSchema(). Field-level validation rendering.
Explicit "Test Connection" for network/cloud types.
Mode-specific title and primary action label.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §6.1
```

---

### C.5 — Unified Editor UI

- [ ] **DONE**

**Tasks**:

1. Create `ResourceEditorFragment` (or refactor existing screen).
2. Accept inputs: `mode: ResourceEditorMode`, `resourceId: Long?`, `resourceType: ResourceType?`.
3. Render dynamic fields from strategy `fieldSchema()`.
4. Immediate field-level validation rendering (underline errors, error text).
5. `Test Connection` button visible for network/cloud resource types.
6. Mode-specific title (`Add Resource` / `Edit Resource` / `Copy Resource`) and primary action label.
7. Wire to `ResourceFormViewModel`.

**Files**: new or refactored `ResourceEditorFragment.kt` + layout XML  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): unified ResourceEditorFragment — single UI for create/edit/copy`  
**PROMPT**:

```
Track C, Step C.6. Add EDIT mode features.
Dirty-state tracking: hasChanges = currentFormState != originalSnapshot.
Save disabled until form valid + at least one field changed.
Add "Reset Changes" action to restore original values.
Context warnings: ReadOnly on destination, path changes requiring re-scan.
Add "Save as Copy" action in edit screen.
Source: RESOURCE_EDITING_COPYING_SPEC.md §5.1, §6.2
```

---

### C.6 — Edit Mode Features

- [ ] **DONE**

**Tasks**:

1. Implement dirty-state tracking: `hasChanges = currentFormState != originalSnapshot`.
2. Disable `Save` until form valid AND `hasChanges == true`.
3. Add `Reset Changes` action → restore to `originalSnapshot`.
4. Show warning dialog when enabling `ReadOnly` on resource used as destination.
5. Show warning dialog when path/endpoint changes require re-scan/re-verification.
6. Add `Save as Copy` action in edit screen → switch to COPY mode.

**Files**: `ResourceFormViewModel.kt`, `ResourceEditorFragment.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): EDIT mode — dirty tracking, reset, warnings, save-as-copy`  
**PROMPT**:

```
Track C, Step C.7. Add COPY/Duplicate mode features.
Add "Duplicate" to resource list context menu → opens editor in COPY mode.
COPY defaults: id=0, name="<source> (Copy)" with collision-safe suffix.
Credential behavior: explicit "Keep credentials" / "Use new credentials" choice.
Real-time name collision validation. Auto-suggest: Name, Name 1, Name 2...
Source: RESOURCE_EDITING_COPYING_SPEC.md §5.2, §8.1
```

---

### C.7 — Copy/Duplicate Mode Features

- [ ] **DONE**

**Tasks**:

1. Add `Duplicate` action to resource list context menu.
2. Open `ResourceEditorFragment` in `COPY` mode.
3. COPY defaults: `id = 0`, `name = "<sourceName> (Copy)"` with collision-safe auto-suffix.
4. Add credential dialog: `Keep credentials` / `Use new credentials`.
5. Real-time name collision validation.
6. Auto-suggest: `Name`, `Name 1`, `Name 2`, ...
7. Path/endpoint collision: allow duplicate path, show warning + suggest edit-existing.

**Files**: Resource list adapter/fragment, `ResourceFormViewModel.kt`, `ResourceEditorUseCase.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(resource): COPY mode — duplicate action, collision handling, credential safety`  
**PROMPT**:

```
Track C, Step C.8. Cleanup deprecated code and regression pass.
Remove old AddResourceViewModel, EditResourceViewModel duplication.
Ensure all resource types pass create/edit/copy flow.
Verify credential safety: no plaintext in logs.
Run regression checklist for all resource types and auth variants.
Source: RESOURCE_CREATION_IMPROVEMENT_SPEC.md §12.5, RESOURCE_EDITING_COPYING_SPEC.md §9
```

---

### C.8 — Cleanup & Regression

- [ ] **DONE**

**Tasks**:

1. Remove deprecated `AddResourceViewModel` / `EditResourceViewModel` duplication.
2. Remove old separate Add/Edit screen code if fully migrated.
3. Test: all resource types pass CREATE/EDIT/COPY flow.
4. Test: credential safety — no plaintext in logs/exceptions.
5. Test: connection test diagnostics redact secrets.
6. Full regression pass for all resource types and auth variants.

**Files**: Deprecated ViewModels, old fragment code  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `cleanup(resource): remove deprecated duplicate logic — unified path active`  
**PROMPT**:

```
Track C COMPLETE. LAYER 0 COMPLETE. Proceed to LAYER 1, Track D, Step D.1.
Create renderer contracts: StaticImageRenderer, RenderTarget, PrefetchQueue interface, TransitionPolicy.
Implement render state machine: Idle, Loading, Ready, Transitioning, Error.
Files: new package ui/player/render/
No behavior switch yet — contracts only.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §B
```

---

# LAYER 1: IMAGE PIPELINE

---

## TRACK D: Static Image Playback

**🟠 ПРИОРИТЕТ: 5 из 11** | **ШАГИ: 27-34 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: G (Audio)

**Source**: `STATIC_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md` + `STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md`  
**Goal**: Smooth transitions, fast navigation, unified gestures, no OOM  
**Risk**: Transition artifacts during migration, dual-surface memory  

### D.1 — Renderer Contracts & State Machine

- [ ] **DONE**

**Tasks**:

1. Create package `ui/player/render/`.
2. Create `RenderTarget.kt`: immutable render request (file/path/type/priority/mode hints).
3. Create `TransitionPolicy.kt`: centralized transition duration + fallback policy.
4. Create `PrefetchQueue.kt`: priority queue interface (Next > Prev > Lookahead), throttling, max depth.
5. Create `StaticImageRenderer.kt`: render state machine (`Idle`, `Loading`, `Ready`, `Transitioning`, `Error`). API: `render(target)`, `prefetch(list)`, `setMode(mode)`, `onPause/onResume/release`.
6. No behavior switch — contracts only. Existing code untouched.

**Files**: new files in `ui/player/render/`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): static image renderer contracts — state machine, prefetch queue, transition policy`  
**PROMPT**:

```
Track D, Step D.2. Add dual-layer image container to layout.
In activity_player_unified.xml: add dual-layer container for two PhotoView surfaces (A=current, B=next).
Keep existing IDs or provide compatibility mapping.
Container has deterministic z-order. No behavioral wiring yet.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §C
```

---

### D.2 — Dual-Surface Layout

- [ ] **DONE**

**Tasks**:

1. In `activity_player_unified.xml`: add dual-layer `FrameLayout` container.
2. Add two `PhotoView` surfaces: Surface A (visible/current), Surface B (prepared/next).
3. Keep existing view IDs or add compatibility mapping.
4. Deterministic z-order: A on top initially.
5. Verify `custom_player_controls.xml` compatibility.
6. No behavioral wiring — layout only.

**Files**: `activity_player_unified.xml`, `custom_player_controls.xml`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): dual-surface image container — layout wiring, no behavior change`  
**PROMPT**:

```
Track D, Step D.3. Bridge ImageLoadingManager to StaticImageRenderer.
Introduce adapter boundary in ImageLoadingManager → StaticImageRenderer facade.
Move transition orchestration out of direct ImageView/PhotoView toggling.
Replace direct preloading with PrefetchQueue API.
Keep compatibility shim with migration flag OFF by default.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §A (ImageLoadingManager)
```

---

### D.3 — Image Loading Integration (Instant Swap)

- [ ] **DONE**

**Tasks**:

1. Add renderer migration flag to `AppSettings` (default = `false`).
2. In `ImageLoadingManager`: introduce adapter boundary to `StaticImageRenderer`.
3. Move transition orchestration from direct view toggling to renderer API.
4. Replace direct preloading calls with `PrefetchQueue` API.
5. Instant swap only — загрузить следующее изображение в Surface B, затем мгновенная замена (visibility swap). Cross-fade НЕ ДЕЛАЕМ.
6. Post-swap: переключить роли surface (B→A), переработать освобождённые ресурсы.
7. Keep legacy path behind migration flag as compatibility shim.

**Files**: `ImageLoadingManager.kt`, `StaticImageRenderer.kt`, `AppSettings.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): ImageLoadingManager → renderer integration — instant swap transitions`  
**PROMPT**:

```
Track D, Step D.4. Implement prefetch priority and ViewModel lookahead.
In PlayerViewModel: add renderer-facing lookahead model (next/prev/+2).
Prevent redundant emits triggering duplicate loads.
PrefetchQueue: priority Next > Prev > Lookahead. Congestion-aware degradation.
Slideshow bias: increase forward prefetch, decrease backward pressure.
Source: STATIC_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §6, CHECKLIST §A (PlayerViewModel)
```

---

### D.4 — Prefetch Priority & Lookahead

- [ ] **DONE**

**Tasks**:

1. In `PlayerViewModel`: add explicit lookahead model (next, prev, optional +2/+3).
2. Keep slideshow state transitions deterministic and idempotent.
3. Prevent redundant emits that trigger duplicate image loads.
4. `PrefetchQueue` implementation: priority rules (Next > Prev > Lookahead).
5. Slideshow bias: increase forward prefetch priority, decrease backward cache pressure.
6. Expose `ConnectionThrottleManager` congested-state signal for degradation.
7. Reduce lookahead depth under congestion.

**Files**: `PlayerViewModel.kt`, `PrefetchQueue.kt`, `ConnectionThrottleManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): prefetch priority queue — lookahead model, congestion-aware degradation`  
**PROMPT**:

```
Track D, Step D.5. Unify gesture path for static images.
Route gesture listeners in PlayerGestureSetupManager to renderer-owned active surface.
Remove mode-specific duplication. Ensure touch zones and PhotoView gestures don't conflict.
Validate action mapping for unified PhotoView strategy in TouchZoneConfig.
Slideshow navigation gestures responsive under transition state.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §D
```

---

### D.5 — Gesture Unification

- [ ] **DONE**

**Tasks**:

1. `PlayerGestureSetupManager`: route gesture listeners to renderer-owned active `PhotoView`.
2. Remove mode-specific gesture duplication where possible.
3. `TouchZoneGestureManager`: ensure touch zones and `PhotoView` gestures don't conflict.
4. Keep slideshow navigation gestures responsive during transition state.
5. `TouchZoneConfig`: validate action mapping for unified `PhotoView` strategy, update constants.
6. Test: pinch/pan/zoom consistent across fit/crop/zoom modes.

**Files**: `PlayerGestureSetupManager.kt`, `TouchZoneGestureManager.kt`, `TouchZoneConfig.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `refactor(player): gesture unification — renderer-owned surface, no mode duplication`  
**PROMPT**:

```
Track D, Step D.6. Slideshow engine sync + renderer readiness.
In SlideshowController: add hooks to query renderer readiness before commit.
Reconcile SlideshowController vs SlideshowManager — keep one scheduler.
Keep countdown in sync with actual transition completion.
No slide advance on stale/canceled state.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §A (Slideshow)
```

---

### D.6 — Slideshow Sync

- [ ] **DONE**

**Tasks**:

1. `SlideshowController`: add renderer readiness query before committing next frame.
2. Ensure tick scheduling is cancellation-safe.
3. Keep countdown in sync with actual transition completion.
4. `SlideshowManager`: reconcile with `SlideshowController` — remove overlap, keep one scheduler.
5. No slide advance on stale or canceled state.

**Files**: `SlideshowController.kt`, `SlideshowManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `fix(player): slideshow sync — renderer readiness check, single scheduler`  
**PROMPT**:

```
Track D, Step D.7. Stabilization pass — remove legacy path, profiling, regression.
Enable renderer as default for static images.
Remove legacy single-surface branch (or fully disable behind flag).
Profile: transition smoothness, prefetch under constrained network.
Test: rapid nav bursts, prolonged slideshow (OOM check), local + network images.
Add renderer diagnostics tags to LoggingHelper.
Source: STATIC_IMAGE_PLAYBACK_IMPLEMENTATION_CHECKLIST.md §4, §5
```

---

### D.7 — Stabilization & Legacy Cleanup

- [ ] **DONE**

**Tasks**:

1. Enable renderer path as default for static images (flip migration flag).
2. Remove or disable legacy single-surface transition branch.
3. Profile: transition visual smoothness, prefetch under constrained network.
4. Test: rapid next/prev bursts (no crash), prolonged slideshow (no OOM), local + network images.
5. Add renderer diagnostics tags to `LoggingHelper` (state transitions, prefetch drops, fallback reasons).
6. Verify `StrictMode` — no new Main-thread violations from renderer path.
7. Wire `PlayerActivity` renderer lifecycle: `init`, `pause`, `resume`, `release`.

**Files**: `AppSettings.kt`, `ImageLoadingManager.kt`, `PlayerActivity.kt`, `LoggingHelper.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `refactor(player): static image renderer — default path, legacy removed, stabilized`  
**PROMPT**:

```
Track D, Step D.8. Slideshow keep-awake behavior.
During active slideshow: set FLAG_KEEP_SCREEN_ON regardless of global "don't sleep" setting.
Clear flag when slideshow stops or user exits player.
Show user a note that screen stays on during slideshow.
Source: User decision 2026-02-15
```

---

### D.8 — Slideshow Keep-Awake

- [ ] **DONE**

**Tasks**:

1. In `SlideshowController` (or `SlideshowManager`): при старте слайдшоу — установить `FLAG_KEEP_SCREEN_ON` на Activity window.
2. При остановке слайдшоу — снять `FLAG_KEEP_SCREEN_ON` (если глобальная настройка "не засыпать" выключена).
3. Если глобальная настройка "не засыпать" включена — не снимать флаг при остановке.
4. Показать пользователю информацию, что экран не будет гаснуть во время слайдшоу.
5. Убедиться, что flag корректно снимается при выходе из player.

**Files**: `SlideshowController.kt` или `SlideshowManager.kt`, `PlayerActivity.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): slideshow keep-awake — FLAG_KEEP_SCREEN_ON during slideshow`  
**PROMPT**:

```
Track D COMPLETE. Proceed to Track E, Step E.1.
Create AnimatedImageController class.
Abstract Start/Stop logic away from ImageLoadingManager.
Manage GifDrawable/AnimatedImageDrawable lifecycle.
Ensure PhotoView used for all animated content.
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 1
```

---

## TRACK E: Animated Image Playback

**🟡 ПРИОРИТЕТ: 8 из 11** | **ШАГИ: 42-45 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: H (Text)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track F

**Source**: `ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Play/Pause, GIF frame extraction (raскадровка), unified zoom for GIF/WEBP/APNG  
**Risk**: Low — no Glide internals hacking, standard Android APIs  

### E.1 — Controller Abstraction

- [ ] **DONE**

**Tasks**:

1. Create `AnimatedImageController` class.
2. Abstract "Start/Stop" lifecycle from `ImageLoadingManager`.
3. Manage `GifDrawable` / `AnimatedImageDrawable` lifecycle.
4. Ensure `ImageLoadingManager` always routes animated content to `PhotoView` regardless of settings.
5. Verify `PhotoView` supports `Animatable` drawables.

**Files**: new `AnimatedImageController.kt`, `ImageLoadingManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): AnimatedImageController — lifecycle abstraction for animated drawables`  
**PROMPT**:

```
Track E, Step E.2. Implement Play/Pause toggle for animated images.
togglePlayback() in AnimatedImageController.
Pause = freeze on current frame (not reset). Play = resume from frozen frame.
Clear UI overlay on Pause to allow frame inspection + zoom.
Show subtle "GIF" badge when animated image detected.
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 2
```

---

### E.2 — Play/Pause Implementation

- [ ] **DONE**

**Tasks**:

1. Implement `togglePlayback()` in `AnimatedImageController`.
2. Pause = freeze on current frame (not reset to start).
3. If standard Glide `GifDrawable.stop()` resets — implement custom wrapper or use `AnimatedImageDrawable`.
4. Clear UI overlay on Pause so user can inspect frozen frame + zoom.
5. Show subtle "GIF" badge overlay when animated image is detected.
6. Add play/pause icon to animation control bar.

**Files**: `AnimatedImageController.kt`, overlay layout XML  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): animated image play/pause — freeze frame, GIF badge overlay`  
**PROMPT**:

```
Track E, Step E.3. Implement GIF frame extraction (raскадровка).
Extract all frames from GIF/WEBP/APNG as individual PNG files.
Use ImageDecoder (API 28+) or GifDecoder. Save to user-chosen directory.
If API unavailable on device — hide button from UI.
Source: User decision 2026-02-15, replaces original speed control
```

---

### E.3 — Frame Extraction (Раскадровка)

- [ ] **DONE**

**Tasks**:

1. Create `ExtractGifFramesUseCase` в `domain/usecase/`.
2. Извлечь все кадры из GIF/WEBP/APNG как отдельные PNG-файлы.
3. Метод: `ImageDecoder` (API 28+) или `GifDecoder` / `Movie` API.
4. Сохранять в выбранную пользователем папку (target resource directory).
5. Именование: `<original_name>_frame_001.png`, `<original_name>_frame_002.png`, ...
6. Progress dialog с отменой (проверка `Job.isActive`).
7. Если API недоступен на устройстве — скрыть кнопку из UI.
8. Добавить кнопку "Раскадровка" в панель управления анимацией.

**Files**: new `ExtractGifFramesUseCase.kt`, `AnimatedImageController.kt`, control bar layout  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): GIF frame extraction — export animated frames as PNG series`  
**PROMPT**:

```
Track E, Step E.4. Stabilization and edge cases for animated image playback.
Test: play/pause + zoom on all animated formats (GIF, WEBP, APNG).
Test: frame extraction on large GIFs (100+ frames). Memory stability.
Verify graceful degradation on devices where API is unavailable.
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md wrap-up
```

---

### E.4 — Stabilization & Edge Cases

- [ ] **DONE**

**Tasks**:

1. Тест: play/pause + zoom на всех анимированных форматах (GIF, WEBP, APNG).
2. Тест: frame extraction на больших GIF (100+ кадров). Проверка памяти.
3. Верифя: graceful degradation на устройствах без нужного API — кнопка скрыта, нет крэшей.
4. Проверка корректной отмены экстракции (не оставлять недописанные файлы).
5. Профилирование памяти на крупных анимациях.

**Files**: `AnimatedImageController.kt`, `ExtractGifFramesUseCase.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `test(player): animated image stabilization — all formats, extraction, degradation`  
**PROMPT**:

```
Track E COMPLETE. LAYER 1 COMPLETE. Proceed to LAYER 2, Track F, Step F.1.
Create VideoGestureController / VideoTouchDelegate.
Vertical Left drag → brightness control. Vertical Right drag → volume control.
Horizontal drag → precise seeking/scrubbing.
Double tap edges → ±10s seek. Center → play/pause.
Add visual overlay indicators for brightness/volume/seek.
Source: VIDEO_PLAYBACK_IMPROVEMENT_SPEC.md §5.1, §6 Phase 1
```

---

# LAYER 2: MEDIA PLAYBACK

---

## TRACK F: Video Playback

**🟡 ПРИОРИТЕТ: 7 из 11** | **ШАГИ: 39-41 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: E (Animated Image)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track G

**Source**: `VIDEO_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: MX Player-class gestures, visual consistency, PiP  
**Risk**: Gesture conflict with PlayerView internals, PiP lifecycle  

### F.1 — Gesture Engine

- [ ] **DONE**

**Tasks**:

1. Create `VideoTouchDelegate` (or `VideoGestureController`).
2. Intercept touch events before `PlayerView` internal handling.
3. `onScroll` vertical left: `WindowManager.LayoutParams.screenBrightness` control.
4. `onScroll` vertical right: `AudioManager.STREAM_MUSIC` volume control.
5. `onScroll` horizontal: `ExoPlayer.seekTo(current + delta)` fine scrubbing.
6. `onDoubleTap`: X < 35% → rewind 10s, X > 65% → forward 10s, center → play/pause.
7. Add visual overlay indicators: "☀ 50%", "🔊 80%", "⏪ 10s", "⏩ 10s".
8. Disable default `PlayerView` click handling, implement custom toggle.

**Files**: new `VideoTouchDelegate.kt`, overlay layout, `PlayerActivity.kt` wiring  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): video gesture engine — brightness/volume/seek gestures + overlays`  
**PROMPT**:

```
Track F, Step F.2. Custom video transport controls UI.
Replace/customize controller_layout_id for PlayerView.
Match app design language (consistent with audio/animation controls).
Speed control prominent in UI. Audio track / subtitle quick switcher.
Verify applySubtitleStyle respects user font preferences.
Source: VIDEO_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 2
```

---

### F.2 — Custom Controls & Seeking

- [ ] **DONE**

**Tasks**:

1. Create custom `controller_layout_id` for `PlayerView` matching app design language.
2. Make playback speed control prominent in custom controls.
3. Add audio track quick-switcher button.
4. Add subtitle track quick-switcher button.
5. Verify `VideoPlayerManager.applySubtitleStyle` respects user font preferences.
6. Ensure visual consistency with audio player and animation player controls.

**Files**: new `custom_video_controls.xml`, `VideoPlayerManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): custom video controls — unified design, speed/track/subtitle switcher`  
**PROMPT**:

```
Track F, Step F.3. Implement Picture-in-Picture (Android 12+ only).
Add android:supportsPictureInPicture="true" to PlayerActivity manifest.
Use setPictureInPictureParams with setAutoEnterEnabled(true) — Android 12+ auto-enter.
Add PiP toggle in Settings (default OFF). Hide PiP button on API < 31.
Add PiP button to custom controls. Test on Android 12, 14.
Source: VIDEO_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 3
```

---

### F.3 — Picture-in-Picture (Android 12+ only)

- [ ] **DONE**

**Tasks**:

1. Update `AndroidManifest.xml`: `android:supportsPictureInPicture="true"` on `PlayerActivity`.
2. Add PiP toggle in Settings (по умолчанию выключен).
3. Проверка `Build.VERSION.SDK_INT >= 31` — если ниже, PiP-кнопка скрыта из UI.
4. Использовать `setPictureInPictureParams` с `setAutoEnterEnabled(true)` (auto-enter на Android 12+).
5. Handle `onPictureInPictureModeChanged` — обновить UI controls.
6. Add PiP button to custom video controls (видимость по API level + настройка).
7. Handle PiP actions (play/pause via `RemoteAction`).
8. Тест: Android 12, 14 — lifecycle поведение.

**Files**: `AndroidManifest.xml`, `PlayerActivity.kt`, `VideoPlayerManager.kt`, custom controls layout, `AppSettings.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): PiP mode — Android 12+ auto-enter, optional in settings`  
**PROMPT**:

```
Track F COMPLETE. Proceed to Track G, Step G.1.
Create AudioPlaybackService (MediaSessionService from Media3).
Scope: AUDIO-ONLY. Video ExoPlayer stays in Activity.
Create second ExoPlayer instance in Service for audio.
Add "Background playback" toggle in Settings (default OFF).
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §5.1, §6 Phase 1
```

---

## TRACK G: Audio Playback

**🟠 ПРИОРИТЕТ: 6 из 11** | **ШАГИ: 35-38 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: F (Video)  
**⚠️ ВЫПОЛНЯТЬ ПОСЛЕ**: Track D

**Source**: `AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Background audio playback (optional), system media controls, playback indicator  
**Risk**: Medium — audio-only Service doesn't touch video pipeline. Scoped to music only.  

### G.1 — Audio Service Core (Audio-Only)

- [ ] **DONE**

**Tasks**:

1. Create `AudioPlaybackService` extending `MediaSessionService` (Media3).
2. Scope: ТОЛЬКО для аудио-файлов. Video ExoPlayer в Activity НЕ ТРОГАЕМ.
3. Создать второй ExoPlayer instance в Service для аудио.
4. Implement `MediaSession` callback handling (play, pause, skip, seek).
5. Service lifecycle: starts on audio play, survives Activity destruction.
6. Добавить настройку "Фоновое воспроизведение" в Settings (по умолчанию OFF).
7. Если настройка OFF — аудио работает как сейчас (в Activity, без Service).

**Files**: new `AudioPlaybackService.kt`, `AppSettings.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): AudioPlaybackService — audio-only Media3 session service, optional`  
**PROMPT**:

```
Track G, Step G.2. Implement background audio support.
Create MediaNotificationManager for system media controls.
Handle startForeground requirements. Update AndroidManifest with FOREGROUND_SERVICE permission.
Notification: play/pause/skip/seek. Lock screen controls.
Audio focus handling for interruptions (calls, other apps).
Scope: audio-only, video NOT affected.
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 2
```

---

### G.2 — Background Support & Notifications

- [ ] **DONE**

**Tasks**:

1. Create `MediaNotificationManager` for system media notification.
2. Implement `startForeground` with media notification.
3. Update `AndroidManifest.xml`: add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
4. Notification: play/pause, skip next/prev, seekbar.
5. Lock screen controls via `MediaSession`.
6. Audio focus: handle interruptions (phone calls, other apps) — duck/pause behavior.
7. Bluetooth/headset button integration via `MediaSession`.

**Files**: new `MediaNotificationManager.kt`, `AndroidManifest.xml`, `FastMediaPlaybackService.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): background support — foreground service, notification, audio focus`  
**PROMPT**:

```
Track G, Step G.3. Connect audio UI to Service via MediaController.
When "Background playback" ON: PlayerViewModel observes state via MediaController.
When OFF: standard ExoPlayer path in Activity (as is now).
Video playback: DO NOT TOUCH. Video always goes through Activity ExoPlayer.
Cover art extraction using MediaMetadataRetriever.
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 3
```

---

### G.3 — Audio UI Connection

- [ ] **DONE**

**Tasks**:

1. Когда настройка "Фоновое воспроизведение" ON: `PlayerViewModel` наблюдает playback state через `MediaController`.
2. Когда OFF: стандартный путь ExoPlayer в Activity (как сейчас).
3. Video playback: НЕ ТРОГАЕМ. Видео всегда идёт через Activity ExoPlayer.
4. Cover art: extract via `MediaMetadataRetriever`, display properly.
5. Gapless audio playback support via ExoPlayer playlist API in Service.

**Files**: `PlayerActivity.kt`, `PlayerViewModel.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): UI connection — MediaController for background, direct for foreground`  
**PROMPT**:

```
Track G, Step G.4. Add playback indicator and sleep timer.
Add small rotating vinyl record animation in corner during music playback.
Implementation: ObjectAnimator rotation on ImageView with PNG vinyl asset.
Show only when music actively playing. Stop on pause.
No FFT, no AudioProcessor, no RECORD_AUDIO permission.
Add Sleep Timer logic (countdown → pause + fade out).
Source: User decision 2026-02-15, replaces original FFT visualizer
```

---

### G.4 — Playback Indicator & Sleep Timer

- [ ] **DONE**

**Tasks**:

1. Добавить анимированный индикатор воспроизведения музыки — вращающаяся виниловая пластинка в углу экрана.
2. Реализация: простая GIF/анимация через `ObjectAnimator.ofFloat(rotation)` на ImageView с PNG пластинки.
3. Показывать только когда музыка активно играет (не на паузе, не остановлена). При паузе — анимация стоп.
4. Минимальная нагрузка: никакого FFT, AudioProcessor, RECORD_AUDIO. Просто вращение картинки.
5. Implement Sleep Timer: countdown → pause playback + optional fade out.
6. Add Sleep Timer UI in player menu/controls.
7. Test: индикатор не мешает просмотру фото на фоне.

**Files**: player layout XML, new drawable asset `ic_vinyl_record.png`, `PlayerActivity.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): vinyl record indicator + sleep timer — lightweight playback animation`  
**PROMPT**:

```
Track G COMPLETE. LAYER 2 COMPLETE. Proceed to LAYER 3, Track H, Step H.1.
Implement TextFilePager for large file handling (RandomAccessFile, 50KB chunks).
Max supported size: 100MB. Files > 100MB → warning and refuse.
Implement CharsetDetector (BOM check + heuristic probe first 4KB).
Add "Encoding" menu option in Player.
Replace readText() with paged reader in TextViewerManager.
Source: TEXT_PLAYBACK_IMPROVEMENT_SPEC.md §5.1-5.2, §6 Phase 1
```

---

# LAYER 3: DOCUMENT VIEWERS

**Note**: Tracks H, I, J are independent. Can be parallelized across developers.

---

## TRACK H: Text Playback

**🟢 ПРИОРИТЕТ: 9 из 11** | **ШАГИ: 46-48 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: I (PDF)

**Source**: `TEXT_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Open files up to 100MB, encoding detection, Markdown/code rendering  
**Risk**: Low — 100MB limit avoids complex encoding stitch edge cases  

### H.1 — Core IO: Pager + Encoding (up to 100MB)

- [ ] **DONE**

**Tasks**:

1. Create `TextFilePager` class: `RandomAccessFile`-based, 50KB chunks, page navigation.
2. Create `CharsetDetector` utility: BOM check → heuristic probe (first 4KB) → fallback charsets (Windows-1251, ISO-8859-1).
3. Add "Encoding" menu option in Player: `Re-open with Encoding...` → charset picker.
4. Replace `file.readText()` in `TextViewerManager` with `TextFilePager`.
5. UI: page indicator ("Page X / Total") or `RecyclerView` infinite scroll.
6. Remove hardcoded `textSizeMax` limit. New limit: 100MB.
7. Files > 100MB — show warning dialog "Файл слишком большой" + отказ открывать.
8. Encoding boundary: простая коррекция на границе chunk (backup несколько байт назад до UTF-8 boundary).

**Files**: new `TextFilePager.kt`, new `CharsetDetector.kt`, `TextViewerManager.kt`, player menu  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(text): paged reader + charset detection — no more OOM on large files`  
**PROMPT**:

```
Track H, Step H.2. Add rich rendering: Markdown and code highlighting.
Integrate Markwon for .md files. Toggle "Raw Text" / "Rendered Markdown".
Add syntax highlighting for .kt, .json, .xml, .py (CodeView or native spannable highlighter).
Add "Text Settings" dialog: font, size, theme (Sepia/Dark/Light).
Add TTS "Read Aloud" overlay with ExoPlayer or system TTS.
Source: TEXT_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 2
```

---

### H.2 — Rich Rendering & Reader UI

- [ ] **DONE**

**Tasks**:

1. Integrate `io.noties.markwon:core` for Markdown rendering.
2. Add toggle: "Raw Text" / "Rendered Markdown" for `.md` files.
3. Add syntax highlighting for `.kt`, `.json`, `.xml`, `.py` (CodeView or native spannable).
4. Disable highlighting for large code files (threshold) — highlight visible range only.
5. Create "Text Settings" dialog: Font, Size, Theme (Sepia / Dark / Light independent of system).
6. Implement TTS "Read Aloud" feature using system `TextToSpeech` API.

**Files**: `TextViewerManager.kt`, new `TextSettingsDialog.kt`, new Markwon integration  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(text): Markdown rendering + syntax highlighting + reader themes + TTS`  
**PROMPT**:

```
Track H, Step H.3. Editor enhancements.
Add Undo/Redo stack. Implement "Find and Replace" with full-file disk search.
Add line number gutter (RecyclerView decoration or custom view).
Implement auto-save (drafts saved to temp/). Search scans file on disk, jumps to page.
Source: TEXT_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 3
```

---

### H.3 — Editor Enhancements

- [ ] **DONE**

**Tasks**:

1. Implement Undo/Redo stack (command pattern or `UndoManager`).
2. Implement "Find and Replace": scan entire file on disk (background thread) → jump to page/chunk.
3. Add line number gutter (custom `RecyclerView` decoration or dedicated view).
4. Implement auto-save: save drafts to `temp/` periodically and on background.
5. Refine search: `SearchControlsManager` integration for large-file paged search.

**Files**: `TextViewerManager.kt`, `SearchControlsManager.kt`, auto-save logic  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(text): editor — undo/redo, find/replace, line numbers, auto-save`  
**PROMPT**:

```
Track H COMPLETE. Proceed to Track I, Step I.1 (or parallel with J).
Refactor PdfViewerManager to support multiple view strategies.
Implement VerticalPdfStrategy using RecyclerView + PdfPageAdapter.
Single-threaded PdfRenderer access (SerialExecutor or Mutex).
LruCache for rendered bitmaps (limit 3-4 screens). Keep legacy horizontal mode.
Source: PDF_PLAYBACK_IMPROVEMENT_SPEC.md §5.1, §6 Phase 1
```

---

## TRACK I: PDF Playback

**🟢 ПРИОРИТЕТ: 10 из 11** | **ШАГИ: 49-51 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: J (EPUB)

**Source**: `PDF_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Continuous scroll, night mode, thumbnail navigation  
**Risk**: PdfRenderer thread safety, memory for large pages  

### I.1 — Vertical Scroll Engine

- [ ] **DONE**

**Tasks**:

1. Refactor `PdfViewerManager` to support view strategy pattern.
2. Create `VerticalPdfStrategy` using `RecyclerView`.
3. Create `PdfPageAdapter` with `SubsamplingScaleImageView` or `ImageView` per page.
4. `PdfRenderer` access: single background thread (use `SerialExecutor` or `Mutex`).
5. `LruCache` for rendered bitmaps (limit: 3-4 screens, ~4 bitmaps max in memory).
6. Add toggle UI: "Page Mode" (horizontal swipe) vs "Scroll Mode" (vertical).
7. Keep existing horizontal mode as legacy option.

**Files**: `PdfViewerManager.kt`, new `PdfPageAdapter.kt`, new `VerticalPdfStrategy.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(pdf): vertical scroll engine — RecyclerView, cached renderer, dual mode toggle`  
**PROMPT**:

```
Track I, Step I.2. Add night mode and reading comfort features.
Implement ColorMatrixColorFilter for night mode (invert) and sepia filter.
Apply to ImageView in adapter. Add toggle in overlay/settings.
Double-tap zoom to column width (or standard zoom).
Source: PDF_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 2
```

---

### I.2 — Night Mode & Comfort

- [ ] **DONE**

**Tasks**:

1. Create `PdfColorConversion` utility: invert (`NEGATIVE_MATRIX`), sepia filters.
2. Apply `ColorMatrixColorFilter` to page `ImageView` in adapter.
3. Add Night Mode / Sepia toggle in player overlay or menu.
4. Implement double-tap zoom (smart zoom to column width if detectable, standard zoom fallback).
5. Persist night mode preference per session or globally.

**Files**: new `PdfColorConversion.kt`, `PdfPageAdapter.kt`, overlay/menu  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(pdf): night mode + sepia filter — ColorMatrix applied to page views`  
**PROMPT**:

```
Track I, Step I.3. Add thumbnail grid and scroll handle navigation.
Render low-res thumbnails (100x150) via PdfRenderer on background coroutine.
UI: BottomSheet or SideDrawer with RecyclerView grid.
Fast scrub handle linked to page number indicator.
Clicking thumbnail jumps to page in main view.
Source: PDF_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 3
```

---

### I.3 — Thumbnail Navigation

- [ ] **DONE**

**Tasks**:

1. Render low-res thumbnails (100x150px) via `PdfRenderer` on background coroutine.
2. Share `PdfRenderer` instance with main view via `SerialExecutor`/`Mutex` (thread safety).
3. Create `ThumbnailSidebarFragment` (or BottomSheet): `RecyclerView` grid of thumbnails.
4. On thumbnail click: scroll main view to target page.
5. Add fast scroll handle linked to page number indicator.
6. Test: 100-page PDF — thumbnails render quickly, main view jumps smoothly.

**Files**: new `ThumbnailSidebarFragment.kt`, thumbnail adapter  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(pdf): thumbnail navigation — grid view + fast scroll handle`  
**PROMPT**:

```
Track I COMPLETE. Proceed to Track J, Step J.1 (if not already in parallel).
Extract TOC from epub book.tableOfContents.
Flatten nested tree into linear list with indentation levels.
Create TocBottomSheetFragment with RecyclerView.
On tap: navigate to chapter in WebView.
Source: EPUB_PLAYBACK_IMPROVEMENT_SPEC.md §5.1, §6 Phase 1
```

---

## TRACK J: EPUB Playback

**🟢 ПРИОРИТЕТ: 11 из 11** | **ШАГИ: 52-54 из 54** | **СЛЕДУЮЩИЙ ТРЕК**: Final Validation

**Source**: `EPUB_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: TOC navigation, full search, reader comfort  
**Risk**: epub4j perf on large books, WebView paging quirks  

### J.1 — Table of Contents Navigation

- [ ] **DONE**

**Tasks**:

1. Extract TOC from `book.tableOfContents` (epub4j).
2. Flatten nested TOC tree into linear list with indentation levels.
3. Create `TocAdapter` for `RecyclerView`.
4. Create `TocBottomSheetFragment` (or Navigation Drawer).
5. On tap: navigate to chapter → load chapter resource in `WebView`.
6. Show "Chapter X of Y" progress in player overlay.

**Files**: new `TocBottomSheetFragment.kt`, new `TocAdapter.kt`, `EpubViewerManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(epub): TOC navigation — bottom sheet, chapter structure, one-tap jump`  
**PROMPT**:

```
Track J, Step J.2. Enhance EPUB styling engine.
Create EpubStyleManager for CSS generation.
Add customizable CSS variables: --line-height, --margin-x, --bg-color, --text-color.
Themes: Sepia, OLED Black, Blue Light Filter + existing.
Add margins & line height settings dialog.
Optional: horizontal paging via CSS column-width trick.
Source: EPUB_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 2
```

---

### J.2 — Styling Engine

- [ ] **DONE**

**Tasks**:

1. Create `EpubStyleManager` class for CSS generation.
2. Replace hardcoded CSS in `preprocessHtml` with dynamic style from `EpubStyleManager`.
3. Add CSS variables: `--line-height`, `--margin-x`, `--bg-color`, `--text-color`.
4. Implement themes: Sepia, OLED Black, Blue Light Filter (plus existing).
5. Create "Reader Settings" dialog: margins, line height, theme selection.
6. Optional: horizontal paging via `html { height: 100vh; column-width: 100vw; }` + CSS `img { max-width: 100%; height: auto; }`.
7. Persist reader preferences.

**Files**: new `EpubStyleManager.kt`, `EpubViewerManager.kt`, new settings dialog  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(epub): styling engine — dynamic CSS, themes, margin/line-height controls`  
**PROMPT**:

```
Track J, Step J.3. Implement full-text EPUB search.
Create EpubSearchUseCase: scan all chapter resources text via epub4j.
Background thread search — user-triggered action.
Results: list showing "Chapter X: ...context around match...".
In-page search: JavaScript findText highlighting in WebView.
Source: EPUB_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 3
```

---

### J.3 — Full-Text Search

- [ ] **DONE**

**Tasks**:

1. Create `EpubSearchUseCase`: scan all chapter resources text (epub4j content).
2. Search runs on background coroutine — explicit user trigger ("Search Book").
3. Results UI: list showing "Chapter X: ...found text context..." with highlights.
4. On result tap: navigate to chapter → scroll to match → highlight.
5. In-page search: JavaScript `window.find()` or equivalent for current WebView page.
6. Add search UI overlay in player.
7. Test: search across 50+ chapters, results load without freeze.

**Files**: new `EpubSearchUseCase.kt`, search overlay UI, `EpubViewerManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(epub): full-text search — cross-chapter scan, in-page highlight`  
**PROMPT**:

```
Track J COMPLETE. LAYER 3 COMPLETE. ALL TRACKS COMPLETE.
Run full regression: build all flavors, run unit tests, lint pass.
.\gradlew.bat assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat lintStandardDebug
Verify no new critical issues across all modified modules.
```

---

# FINAL VALIDATION

## Full Project Regression

- [ ] `.\gradlew.bat assembleStandardDebug` — SUCCESS
- [ ] `.\gradlew.bat assembleLiteDebug` — SUCCESS
- [ ] `.\gradlew.bat assemblePhotosDebug` — SUCCESS
- [ ] `.\gradlew.bat assembleLegacyDebug` — SUCCESS
- [ ] `.\gradlew.bat testStandardDebugUnitTest` — SUCCESS
- [ ] `.\gradlew.bat lintStandardDebug` — zero new critical
- [ ] Manual smoke test: browse → player → all media types
- [ ] StrictMode clean (debug) — no Main-thread I/O violations

---

# SUMMARY TABLE

| Порядок | Layer | Track | Steps | Source Spec | Priority |
|--------|-------|-------|-------|-------------|----------|
| 1 | 0 | A: Main Window | 7 | MAIN_WINDOW_OPTIMIZATION | 🔴 CRITICAL |
| 2 | 0 | K: Compatibility | 5 | OLD_DEVICE_AND_SCREEN_COMPAT | 🔴 CRITICAL |
| 3 | 0 | B: Settings | 6 | SETTINGS_IMPROVEMENT_SPEC | 🔴 CRITICAL |
| 4 | 0 | C: Resources | 8 | RESOURCE_CREATION + EDITING_COPYING | 🔴 CRITICAL |
| 5 | 1 | D: Static Image | 8 | STATIC_IMAGE_PLAYBACK + CHECKLIST | 🟠 HIGH |
| 6 | 2 | G: Audio | 4 | AUDIO_PLAYBACK | 🟠 HIGH |
| 7 | 2 | F: Video | 3 | VIDEO_PLAYBACK | 🟡 MEDIUM |
| 8 | 1 | E: Animated Image | 4 | ANIMATED_IMAGE_PLAYBACK | 🟡 MEDIUM |
| 9 | 3 | H: Text | 3 | TEXT_PLAYBACK | 🟢 LOW |
| 10 | 3 | I: PDF | 3 | PDF_PLAYBACK | 🟢 LOW |
| 11 | 3 | J: EPUB | 3 | EPUB_PLAYBACK | 🟢 LOW |
| — | — | **TOTAL** | **54** | — | — |

**Порядок выполнения**: A → K → B → C → D → G → F → E → H → I → J  
**Ограничения**: D зависит от A.4 | E зависит от D.1 | F/G зависят от D.5 | H/I/J независимы

---

# EXECUTION START PROMPT

```
Track A, Step A.1. Implement Browse anti-flicker.
In BrowseViewModel.reloadFiles(): remove `mediaFiles = emptyList()` from standard refresh path.
Update StateFlow only with complete data snapshot (post-load + post-sort).
Allow intermediate clear ONLY on: resource change, radical filter change, explicit user action.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.1
```
