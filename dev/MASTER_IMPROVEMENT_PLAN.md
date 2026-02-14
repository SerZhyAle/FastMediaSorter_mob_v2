# MASTER IMPROVEMENT PLAN: FastMediaSorter v2

**Created**: 2026-02-14  
**Status**: ACTIVE  
**Source Specs**: 11 documents consolidated  
**Execution Model**: Sequential layers, parallel tracks within layer where independent

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

**Parallelizable**: A ∥ B ∥ C ∥ K | D then E | F then G | H ∥ I ∥ J

---

## RISK MAP: СПОРНЫЕ / СЛОЖНЫЕ ЗАДАЧИ

Ниже — задачи с высоким техническим риском. Для каждой — причина сложности, реалистичная оценка и альтернатива.

### 🔴 КРИТИЧНО СЛОЖНЫЕ (могут потребовать пересмотра подхода)

#### G.1–G.3: Audio — MediaSessionService миграция

- **Что**: перенос ExoPlayer из Activity в Service, MediaSession, Notification, Audio Focus, Bluetooth
- **Почему сложно**: это полная перестройка playback-архитектуры. PlayerActivity, VideoPlayerManager, PlayerViewModel — всё затронуто. Media3 Session API имеет нетривиальный lifecycle. Нужна синхронизация Service ↔ UI через MediaController, а не прямые вызовы. Foreground Service требования различаются Android 12 vs 14+.
- **Объём**: ~2-3 недели чистой работы, без учёта стабилизации
- **Альтернатива**: поэтапно — сначала только audio background (без полного decoupling video), потом полная миграция
- **Решение**: ❓ обсудить — делаем полный decoupling сразу или только audio-service отдельно?

#### E.3: Animated Image — Variable Speed Rendering

- **Что**: перехват frame delay в Glide GifDrawable для real-time скорости 0.25x–4x
- **Почему сложно**: `GifFrameLoader` и `GifDecoder` — internal/private классы Glide. Нет публичного API для модификации frame delay. Два пути: reflection (хрупко, ломается при обновлении Glide) или полная обёртка drawable с перехватом `scheduleSelf` (сложная, нужна копия frame-управления).
- **Объём**: ~3-5 дней исследование + прототип
- **Альтернатива**: использовать `ImageDecoder` + `AnimatedImageDrawable` (Android 9+, имеет реальный speed API) — но теряем поддержку Android 7-8.
- **Решение**: ❓ минимальный API level проекта? Если ≥ 28 — `AnimatedImageDrawable` закрывает вопрос без хаков.

### 🟡 ВЫСОКАЯ СЛОЖНОСТЬ (реализуемо, но трудоёмко)

#### F.3: Video — Picture-in-Picture

- **Что**: PiP mode с RemoteActions
- **Почему сложно**: PiP lifecycle радикально отличается между Android 8 и 12+. `onPause` вызывается при входе в PiP (до Android 12), что конфликтует с текущей логикой release ExoPlayer. Нужен отдельный lifecycle-aware guard. Auto-PiP (Android 12+) и manual PiP (Android 8-11) — два разных пути.
- **Объём**: ~3-4 дня + тестирование на 3+ версиях Android
- **Митигация**: ограничить PiP до Android 12+ и использовать `setPictureInPictureParams` с auto-enter. Покрытие 8-11 — второй итерацией.

#### D.3: Static Image — Dual-Surface Transition Integration

- **Что**: мост ImageLoadingManager → StaticImageRenderer с двумя PhotoView + cross-fade
- **Почему сложно**: Glide + PhotoView + dual surface = комбинаторная сложность. PhotoView хранит матрицу zoom/pan, при swap surface нужно переносить состояние. Cross-fade между двумя большими bitmap может вызвать peak memory × 2. Нужна точная синхронизация Glide callback → surface ready → transition start.
- **Объём**: ~4-5 дней
- **Митигация**: instant swap без cross-fade как fallback при low memory.

#### G.4: Audio Visualizer (FFT через AudioProcessor)

- **Что**: real-time FFT извлечение через ExoPlayer AudioProcessor
- **Почему сложно**: ExoPlayer `AudioProcessor` API задокументирован слабо для FFT use-case. Нужен custom AudioProcessor, который копирует PCM data без нарушения playback pipeline. Отрисовка FFT на каждом кадре — CPU/GPU нагрузка. Альтернатива `android.media.audiofx.Visualizer` требует RECORD_AUDIO permission.
- **Объём**: ~3-4 дня
- **Митигация**: начать с простой waveform (amplitude only, без FFT) — значительно проще. FFT-спектр — вторая итерация.

#### H.1: Text — Paged Reader для файлов 500MB+

- **Что**: RandomAccessFile + 50KB chunks + CharsetDetector
- **Почему сложно**: multi-byte encoding (UTF-8) может разрываться на границе chunk. Нужна корректная обработка partial-character на стыке. Поиск по пагинированному файлу — нетривиально: нужен background scan всего файла с mappingом позиций. Line numbering через chunks — отдельная проблема.
- **Объём**: ~3-4 дня core + 2 дня edge cases
- **Митигация**: лимит поддерживаемого размера до 100MB вместо 500MB. Файлы > 100MB — предупреждение + принудительная пагинация без line numbers.

### 🟢 РЕАЛИЗУЕМО БЕЗ ВЫСОКОГО РИСКА

- **A.1–A.7**: Main Window — стандартная оптимизация, чёткие задачи
- **B.1–B.6**: Settings — рефакторинг UI, предсказуемо
- **C.1–C.8**: Resources — масштабный, но архитектурно понятный рефакторинг
- **K.1–K.5**: Device Compatibility — defensive coding, API level checks, layout qualifiers
- **D.1–D.2, D.4–D.7**: Static Image — кроме D.3, всё линейно
- **E.1–E.2, E.4**: Animated Image — кроме E.3, стандартные задачи
- **F.1–F.2**: Video gestures — хорошо документированный паттерн
- **I.1–I.3**: PDF — RecyclerView + PdfRenderer, стандартный паттерн
- **J.1–J.3**: EPUB — epub4j API простой, WebView CSS injection — стандартно

### SUMMARY: ТОЧКИ ПРИНЯТИЯ РЕШЕНИЙ

| # | Задача | Вопрос | Влияние |
|---|--------|--------|---------|
| 1 | G.1–G.3 | Полный decoupling ExoPlayer в Service сразу или поэтапно? | Определяет объём Track G: 1 неделя vs 3 недели |
| 2 | E.3 | Min API level ≥ 28? Тогда AnimatedImageDrawable, иначе Glide хаки | Определяет подход Track E.3 |
| 3 | F.3 | PiP: Android 8+ или только 12+? | Определяет сложность: 2 дня vs 5 дней |
| 4 | G.4 | Visualizer: простая waveform сначала или сразу FFT? | Определяет scope Phase 4 |
| 5 | H.1 | Max supported file size: 100MB или 500MB? | Определяет сложность edge cases |

---

# LAYER 0: FOUNDATION

---

## TRACK A: Main Window Optimization

**Source**: `MAIN_WINDOW_OPTIMIZATION.md`  
**Goal**: Responsive Browse, no flicker, fast player entry  
**Risk**: Hidden logic depends on intermediate empty-state  

### A.1 — Browse Anti-Flicker

- [ ] **DONE**

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

- [ ] **DONE**

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

- [ ] **DONE**

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

**Source**: `OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md`  
**Goal**: Graceful degradation on old devices, tablet layouts, permission safety across API 23-35  
**Risk**: Low — defensive checks, no architectural changes  

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
   - API 23-28 (Android 6-9): `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`.
3. Route user to appropriate settings page based on OS version when permission missing.
4. Test: permission flow on API 23, 29, 30, 33, 35 emulators.

**Files**: new `StoragePermissionHelper.kt` or existing permission utility  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `fix(compat): unified storage permissions — API 23-35 clean branching`  
**PROMPT**:
```
Track K, Step K.3. Tablet layout adaptations.
Create layout-sw600dp for activity_browse — RecyclerView GridLayoutManager 3 columns.
Ensure all input screens (AddResource, Rename) wrapped in ScrollView for small screens + keyboard.
Verify no text truncation on sw320dp (4-inch screens).
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §2
```

---

### K.3 — Tablet & Screen Adaptation

- [ ] **DONE**

**Tasks**:
1. Create `layout-sw600dp/activity_browse.xml`: `GridLayoutManager` with 3+ columns for tablet.
2. Create `layout-sw600dp` variants for other key screens if needed (player, settings).
3. Wrap all input screens (`AddResourceActivity`, `RenameDialog`, etc.) in `ScrollView` root — handle keyboard on short 16:9.
4. Audit `sw320dp` (4-inch): fix text truncation in Toolbars, overlapping buttons.
5. Convert `wrap_content` widths to `0dp` + constraint weights where truncation occurs.

**Files**: new `layout-sw600dp/` files, existing input screen layouts  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `feat(compat): tablet layout sw600dp + small screen fixes`  
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
Test on emulators: API 23 (legacy flavor), API 29, API 30, API 33, API 35.
Verify: permission flows, image loading on 2GB RAM, tablet layout, cloud options visibility.
Fix any TLS/SSL issues on Android 6-7 (ProviderInstaller check).
Verify vector drawable rendering on API 23.
Source: OLD_DEVICE_AND_SCREEN_COMPATIBILITY_SPEC.md §1, §3
```

---

### K.5 — Compatibility Stabilization

- [ ] **DONE**

**Tasks**:
1. Test on emulators: API 23 (legacy flavor build), API 29, API 30, API 33, API 35.
2. Verify: permission flows work per API level.
3. Verify: image loading on 2GB RAM emulator — no OOM, reduced quality active.
4. Verify: tablet layout (sw600dp) — 3-column grid, no stretched elements.
5. Verify: cloud options visibility — Google Drive hidden without Play Services.
6. Verify TLS/SSL on Android 6-7: `ProviderInstaller` or equivalent active.
7. Verify vector drawable rendering on API 23 emulator.
8. Fix any found issues.

**Files**: emulator testing, bug fixes as discovered  
**BUILD**: `.\.build-debug.PS1`  
**COMMIT**: `test(compat): stabilization pass — API 23-35, low-RAM, tablet verified`  
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

### D.3 — Image Loading Integration

- [ ] **DONE**

**Tasks**:

1. Add renderer migration flag to `AppSettings` (default = `false`).
2. In `ImageLoadingManager`: introduce adapter boundary to `StaticImageRenderer`.
3. Move transition orchestration from direct view toggling to renderer API.
4. Replace direct preloading calls with `PrefetchQueue` API.
5. Implement dual-surface cross-fade transition: alpha with bounded duration.
6. Post-transition: swap surface roles, recycle released resources.
7. Keep legacy path behind migration flag as compatibility shim.

**Files**: `ImageLoadingManager.kt`, `StaticImageRenderer.kt`, `AppSettings.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): ImageLoadingManager → renderer integration — dual surface transitions`  
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
Track D COMPLETE. Proceed to Track E, Step E.1.
Create AnimatedImageController class.
Abstract Start/Stop logic away from ImageLoadingManager.
Manage GifDrawable/AnimatedImageDrawable lifecycle.
Ensure PhotoView used for all animated content.
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 1
```

---

## TRACK E: Animated Image Playback

**Source**: `ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Play/Pause, non-destructive speed, unified zoom for GIF/WEBP/APNG  
**Risk**: Glide internals are private, performance on old devices  

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
Track E, Step E.3. Implement non-destructive real-time speed control.
Delay Interceptor: nextFrameDelay = originalFrameDelay / speedMultiplier.
Speed range: 0.25x - 4.0x. UI: speed selector buttons (0.5x, 1x, 2x, etc.).
Method: wrap GifDrawable to intercept scheduleSelf or hook GifFrameLoader.
No file I/O — rendering-only change.
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 3
```

---

### E.3 — Variable Speed Rendering

- [ ] **DONE**

**Tasks**:

1. Implement "Delay Interceptor" in `AnimatedImageController`.
2. Algorithm: `nextFrameDelay = originalFrameDelay / speedMultiplier`.
3. Speed range: 0.25x — 4.0x.
4. Method A: hook Glide's `GifFrameLoader` delay. Method B: wrap drawable, intercept `scheduleSelf`.
5. Add UI: speed selector (0.5x, 1x, 2x, etc.) in animation control bar.
6. Wire UI buttons to `controller.setPlaybackSpeed(float)`.
7. No file I/O — rendering-only change.

**Files**: `AnimatedImageController.kt`, control bar layout  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): animated speed control — non-destructive delay interceptor 0.25x-4x`  
**PROMPT**:

```
Track E, Step E.4. Wire export to existing ChangeGifSpeedUseCase.
Add "Export Speed" button in animation control bar.
On tap: open existing speed dialog pre-filled with current playback speed.
Reuse ChangeGifSpeedUseCase for permanent file save.
Test: speed roundtrip (set playback → export → verify file).
Source: ANIMATED_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md §6 Phase 4
```

---

### E.4 — Export Integration & Stabilization

- [ ] **DONE**

**Tasks**:

1. Add "Export Speed" button to animation control bar.
2. On tap: open existing speed change dialog, pre-fill with current `playbackSpeed`.
3. Reuse `ChangeGifSpeedUseCase` for permanent file save.
4. Test: speed roundtrip (set playback speed → export → reopen → verify).
5. Test: memory stability for large GIFs (no spike vs current baseline).
6. Disable variable speed on low-end devices if frame drops detected (optional).

**Files**: `AnimatedImageController.kt`, UI wiring  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): animated export integration — pre-filled speed, legacy reuse`  
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
Track F, Step F.3. Implement Picture-in-Picture (PiP) support.
Add android:supportsPictureInPicture="true" to PlayerActivity manifest.
Handle onUserLeaveHint → trigger PiP.
Ensure VideoPlayerManager does NOT release ExoPlayer in onPause if isInPictureInPictureMode.
Add PiP button to custom controls. Test on Android 8-14.
Source: VIDEO_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 3
```

---

### F.3 — Picture-in-Picture

- [ ] **DONE**

**Tasks**:

1. Update `AndroidManifest.xml`: `android:supportsPictureInPicture="true"` on `PlayerActivity`.
2. Handle `onUserLeaveHint` to trigger PiP entry.
3. In `PlayerActivity`: do NOT release `ExoPlayer` in `onPause` if `isInPictureInPictureMode == true`.
4. Add PiP button to custom video controls.
5. Handle PiP actions (play/pause via `RemoteAction`).
6. Test: Android 8, 12, 14 lifecycle behavior.

**Files**: `AndroidManifest.xml`, `PlayerActivity.kt`, `VideoPlayerManager.kt`, custom controls layout  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(player): PiP mode — manifest, lifecycle, remote actions`  
**PROMPT**:

```
Track F COMPLETE. Proceed to Track G, Step G.1.
Create FastMediaPlaybackService (MediaSessionService from Media3).
Move ExoPlayer initialization to Service.
Implement MediaSession callback handling.
Service survives Activity destruction. Binds to UI for updates.
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §5.1, §6 Phase 1
```

---

## TRACK G: Audio Playback

**Source**: `AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Background playback, system media controls, visualizer  
**Risk**: Media3 service architecture complexity, audio focus  

### G.1 — Service Core

- [ ] **DONE**

**Tasks**:

1. Create `FastMediaPlaybackService` extending `MediaSessionService` (Media3).
2. Move `ExoPlayer` initialization from `VideoPlayerManager` to Service.
3. Create `MediaControllerWrapper` to abstract `ExoPlayer` management.
4. Implement `MediaSession` callback handling (play, pause, skip, seek).
5. Service lifecycle: starts on first play, binds to Activity, survives destruction.

**Files**: new `FastMediaPlaybackService.kt`, new `MediaControllerWrapper.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): FastMediaPlaybackService — Media3 session service core`  
**PROMPT**:

```
Track G, Step G.2. Implement background support.
Create MediaNotificationManager for system media controls.
Handle startForeground requirements. Update AndroidManifest with FOREGROUND_SERVICE permission.
Notification: play/pause/skip/seek. Lock screen controls.
Audio focus handling for interruptions (calls, other apps).
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
Track G, Step G.3. Refactor PlayerActivity UI to connect via MediaSession.
PlayerViewModel observes playback state via MediaController (not direct method calls).
Refactor VideoPlayerManager to delegate to MediaControllerWrapper.
UI communicates with Service via MediaController API.
Cover art extraction using MediaMetadataRetriever.
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §5.2, §6 Phase 3
```

---

### G.3 — UI Connection Refactor

- [ ] **DONE**

**Tasks**:

1. Refactor `PlayerActivity` to connect to `MediaSession` for playback control.
2. `PlayerViewModel`: observe playback state via `MediaController`, not direct `ExoPlayer` calls.
3. Refactor `VideoPlayerManager` to delegate to `MediaControllerWrapper`.
4. Communication: UI → `MediaController` API → Service.
5. Cover art: extract via `MediaMetadataRetriever`, display properly.
6. Gapless playback support via `ExoPlayer` playlist API in Service.

**Files**: `PlayerActivity.kt`, `PlayerViewModel.kt`, `VideoPlayerManager.kt`  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `refactor(audio): UI connects via MediaSession — decoupled from ExoPlayer lifecycle`  
**PROMPT**:

```
Track G, Step G.4. Add audio visualizer and sleep timer.
Integrate FFT extraction via ExoPlayer AudioProcessor (no RECORD_AUDIO permission).
Create AudioVisualizerView (waveform/spectrum).
Add Sleep Timer logic (countdown → pause + fade out).
Source: AUDIO_PLAYBACK_IMPROVEMENT_SPEC.md §5.3, §6 Phase 4
```

---

### G.4 — Visualizer & Sleep Timer

- [ ] **DONE**

**Tasks**:

1. Integrate FFT data extraction via ExoPlayer `AudioProcessor` (no `RECORD_AUDIO` permission needed).
2. Create `AudioVisualizerView` custom view: waveform/spectrum visualization.
3. Show visualizer when no cover art, or as overlay on cover art.
4. Implement Sleep Timer: countdown → pause playback + optional fade out.
5. Add Sleep Timer UI in player menu/controls.
6. Test: audio continues playing on screen off, app minimized, headset controls.

**Files**: new `AudioVisualizerView.kt`, `FastMediaPlaybackService.kt`, player menu  
**BUILD**: `.\build-debug.PS1`  
**COMMIT**: `feat(audio): visualizer + sleep timer — FFT-based, no microphone permission`  
**PROMPT**:

```
Track G COMPLETE. LAYER 2 COMPLETE. Proceed to LAYER 3, Track H, Step H.1.
Implement TextFilePager for large file handling (RandomAccessFile, 50KB chunks).
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

**Source**: `TEXT_PLAYBACK_IMPROVEMENT_SPEC.md`  
**Goal**: Open 500MB files, encoding detection, Markdown/code rendering  
**Risk**: Pagination limits search, Markdown parsing slowness  

### H.1 — Core IO: Pager + Encoding

- [ ] **DONE**

**Tasks**:

1. Create `TextFilePager` class: `RandomAccessFile`-based, 50KB chunks, page navigation.
2. Create `CharsetDetector` utility: BOM check → heuristic probe (first 4KB) → fallback charsets (Windows-1251, ISO-8859-1).
3. Add "Encoding" menu option in Player: `Re-open with Encoding...` → charset picker.
4. Replace `file.readText()` in `TextViewerManager` with `TextFilePager`.
5. UI: page indicator ("Page X / Total") or `RecyclerView` infinite scroll.
6. Remove hardcoded `textSizeMax` limit (replaced by paging).

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

| Layer | Track | Steps | Source Spec | Priority |
|-------|-------|-------|-------------|----------|
| 0 | A: Main Window | 7 | MAIN_WINDOW_OPTIMIZATION | HIGH |
| 0 | B: Settings | 6 | SETTINGS_IMPROVEMENT_SPEC | HIGH |
| 0 | C: Resources | 8 | RESOURCE_CREATION + EDITING_COPYING | HIGH |
| 0 | K: Compatibility | 5 | OLD_DEVICE_AND_SCREEN_COMPAT | HIGH |
| 1 | D: Static Image | 7 | STATIC_IMAGE_PLAYBACK + CHECKLIST | HIGH |
| 1 | E: Animated Image | 4 | ANIMATED_IMAGE_PLAYBACK | MEDIUM |
| 2 | F: Video | 3 | VIDEO_PLAYBACK | MEDIUM |
| 2 | G: Audio | 4 | AUDIO_PLAYBACK | MEDIUM |
| 3 | H: Text | 3 | TEXT_PLAYBACK | LOW |
| 3 | I: PDF | 3 | PDF_PLAYBACK | LOW |
| 3 | J: EPUB | 3 | EPUB_PLAYBACK | LOW |
| — | **TOTAL** | **53** | — | — |

---

# EXECUTION START PROMPT

```
Track A, Step A.1. Implement Browse anti-flicker.
In BrowseViewModel.reloadFiles(): remove `mediaFiles = emptyList()` from standard refresh path.
Update StateFlow only with complete data snapshot (post-load + post-sort).
Allow intermediate clear ONLY on: resource change, radical filter change, explicit user action.
Source: MAIN_WINDOW_OPTIMIZATION.md §4.1
```
