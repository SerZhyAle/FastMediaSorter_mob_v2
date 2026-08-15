# S1352 - BrowseViewModel has zero automated test coverage

**Ticket:** S1352
**Status:** Archived
**Priority:** 30
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - discovered during S1350 research, parked 2026-08-02

<!-- auto-approved by /spec-all - 2026-08-02 -->

---

## Goal

`BrowseViewModel` (926 LOC, `@HiltViewModel`, центр экрана Browse) не имеет ни одного unit- или
instrumented-теста - подтверждено grep по `app_v2/src/test/` и `app_v2/src/androidTest/` на
упоминание имени класса. S1350 (Verified) сузил конструктор до 9 параметров, 6 из которых -
dependency-holder классы (`BrowseRemoteAccessDependencies` и т.д.), что делает тестовую обвязку
посильной - relaxed MockK на 6 холдеров + `Context` вместо ~40 сырых моков. Бо́льшая часть публичной
поверхности класса - тонкие однострочные делегаты к отдельным `Browse*Manager` классам, каждый уже
покрыт своим тестом (`BrowseFileListManagerTest`, `BrowseNavigationManagerNavigateToDepthTest`,
`ResourceOpsMenuManagerTest` и др.) - переисследовать их не цель. Цель - закрыть тестами логику,
которая реально живёт в самом `BrowseViewModel`: инициализацию и экспозицию состояния (`state`,
`loading`, `error`, `settings`), отображение исключений в понятные сообщения
(`resolveFriendlyBrowseErrorRes`), координацию выделения файлов (`selectFile`/`selectFileRange`/
`selectAll`/`clearSelection`, читающие `state.value.mediaFiles`) и прямые мутации списка
(`replaceMediaFiles`, `markListAsSubmitted`).

**Non-goals:**

- Повторное тестирование логики, уже покрытой тестами отдельных `Browse*Manager` классов
  (навигация, файловые операции, архивация, удаление, автообнаружение и т.д.) - каждый тестируется
  в своём файле, не здесь.
- Instrumented (`androidTest`) покрытие - вне объёма, только `app_v2/src/test/` unit-тесты.
- Тестирование приватных методов через reflection - только через публичный контракт класса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1350 (Verified - предпосылка, сузила конструктор `BrowseViewModel` до 6
  dependency-holder классов, что и делает эту обвязку посильной).
- **Scope:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelTest.kt` (новый
  файл, единственный тронутый файл).
- **Flavors:** все - тестовый код в `src/test`, без `BuildConfig`-гейта.

---

## Phase 01 - Test fixture, construction, and state-exposure coverage

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelTest.kt` | New | ≤ 220 (this phase's slice - grows further in Phase 02) |

### Step 01.1 - Create the test file with a reusable fixture and baseline construction tests

**Files:** `ui/browse/BrowseViewModelTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BrowseViewModelTest.kt` in `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/`.
> Follow the `MainDispatcherRule` + relaxed-MockK pattern from `GameViewModelTest.kt` and
> `StandalonePlayerViewModelTest.kt` - no Robolectric needed. `context` stays a relaxed MockK mock;
> string-resource lookups are verified by resource id in Phase 02, not by real string content.
>
> Add `@get:Rule val dispatcherRule = MainDispatcherRule()`.
>
> Private fixture function `createViewModel(context: Context = mockk(relaxed = true)):
> BrowseViewModel` that:
> - Builds relaxed MockK mocks for each of the 6 dependency holders (`BrowseRemoteAccessDependencies`,
>   `BrowseCleanupUseCases`, `BrowseContentDiscoveryDependencies`, `BrowsePersistedStateDependencies`,
>   `BrowseContentAuthoringUseCases`, `BrowseFileMutationDependencies`, all in
>   `ui/browse/BrowseViewModelDependencies.kt`) via `mockk<T>(relaxed = true)`.
> - Stubs every suspend/Flow-returning use case actually reached from `init {}`'s
>   `lifecycleSetupManager.initialize()` and `loadResource()` calls before construction - a relaxed
>   mock returns `null`/default for a non-nullable `Flow`/`List`/data-class return type, which throws
>   inside the collecting coroutine rather than degrading gracefully. Read `BrowseLifecycleSetupManager`
>   and `BrowseResourceLoadManager` (`ui/browse/managers/`) to find the exact call chain before writing
>   stubs. One confirmed landmine: `fileMutation.settingsRepository.getSettings(): Flow<AppSettings>`
>   is collected from *two* places at construction time - the eager `val settings: StateFlow<AppSettings>`
>   field (`BrowseViewModel.kt:79-81`) and `lifecycleSetupManager`'s `loadSettings()` - an unstubbed
>   relaxed mock's Flow-returning function is not guaranteed to behave like a real one-shot-emitting
>   Flow, and `.first()` on an empty/never-emitting Flow throws. Stub it explicitly:
>   `every { fileMutation.settingsRepository.getSettings() } returns flowOf(AppSettings())`. Same
>   landmine for `persistedState.browseStateDataStore.filter: Flow<FileFilter?>`, collected via
>   `.first()` in `lifecycleSetupManager`'s `restoreFilterState()` with no surrounding try/catch and no
>   `exceptionHandler` on that `scope.launch` - stub `every { persistedState.browseStateDataStore.filter }
>   returns flowOf(null)`.
>   `getResourcesUseCase.getById(resourceId)` can stay unstubbed - a relaxed mock returns `null` for
>   its nullable return type, which `BrowseResourceLoadManager.loadResource()` already handles as a
>   clean "resource not found" branch (`sendEvent` + `setLoading(false)`, no throw, `state.value`
>   stays at its empty `getInitialState()` default) - this is what makes the "settles to Empty state"
>   assertion below correct without needing to fabricate a `MediaResource`.
> - `savedStateHandle = SavedStateHandle(mapOf("resourceId" to 1L))` - the pure-JVM
>   `androidx.lifecycle.SavedStateHandle(Map)` constructor, no Robolectric required. If it throws in
>   the local unit-test sandbox, fall back to `mockk<SavedStateHandle>(relaxed = true)` with
>   `every { get<Long>("resourceId") } returns 1L` and matching stubs for the other keys the
>   constructor reads (`extra_window_id`, `skipAvailabilityCheck`, `initialFolderPath`,
>   `initialFilePath`, `resumeIsPlaying`).
> - `ioDispatcher = dispatcherRule.testDispatcher`.
> - Constructs and returns `BrowseViewModel(context, remoteAccess, cleanupUseCases, contentDiscovery,
>   persistedState, contentAuthoringUseCases, fileMutation, ioDispatcher, savedStateHandle)`.
>
> Tests (`runTest(dispatcherRule.testDispatcher)`, `advanceUntilIdle()` after construction):
> - `construction does not throw and settles to Empty state` - assert no exception, then
>   `viewModel.loading.value == false` and `viewModel.state.value.mediaFiles.isEmpty()` (with the
>   stubbed use cases returning nothing, `BaseViewModel.createUiState`'s `resolveUiState` resolves to
>   `UiState.Empty` - `state`/`loading`/`error` are `BaseViewModel`'s public plain
>   `MutableStateFlow.asStateFlow()` exposures, always current; prefer them over `fileListUiState`,
>   which is built via `stateIn(.., SharingStarted.WhileSubscribed(..), ..)` and only updates past its
>   `initialValue` once something actively collects it).
> - `settings StateFlow starts with default AppSettings` - assert
>   `viewModel.settings.value == AppSettings()`.
> - `markListAsSubmitted stores the list on lastEmittedMediaFiles` - call with a 2-element fake
>   `MediaFile` list (reuse the `MediaFile(name=.., path=.., type=MediaType.IMAGE, size=.., createdDate=..)`
>   fixture-helper style from `BrowseFileListManagerTest.kt`), assert
>   `viewModel.lastEmittedMediaFiles` equals it.

**Verification:**

- `Glob` - `BrowseViewModelTest.kt` exists.
- `Grep` - `class BrowseViewModelTest` matches exactly once.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests
  "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"` passes, 3/3 tests green.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. A third landmine surfaced beyond the two anticipated in this
  step's prompt: `BrowseInlineAudioManager` constructs an `AudioFocusManager`, whose init block does
  `context.getSystemService(Context.AUDIO_SERVICE) as AudioManager` (hard cast, no `as?`) - an
  unstubbed relaxed `Context` mock returns a generic relaxed `Object` for that call, throwing
  `ClassCastException` at `BrowseViewModel` construction (not inside a coroutine, so it fails every
  test synchronously regardless of dispatcher stubbing). Fixed with one more explicit stub:
  `every { context.getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>(relaxed = true)`.
  The two anticipated stubs (`settingsRepository.getSettings()`, `browseStateDataStore.filter`) were
  both necessary as predicted - removing either reproduces a `.first()` failure.
  `check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"`
  BUILD SUCCESSFUL, 3/3 green.

---

## Phase 02 - Error mapping, selection coordination, and list-mutation coverage

**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelTest.kt` | Modified (append tests) | ≤ 420 total |

### Step 02.1 - Error-mapping tests (handleError -> resolveFriendlyBrowseErrorRes)

**Files:** `ui/browse/BrowseViewModelTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the same test class, add tests for the public `override fun handleError(throwable: Throwable)`,
> which resolves a `Throwable` to a friendly string via the private `resolveFriendlyBrowseErrorRes`
> (`BrowseViewModel.kt:543-595`). Test it through the public seam by verifying
> `context.getString(<expected R.string id>)` was invoked - a relaxed `context.getString(any())`
> returns `""` regardless of id, so assert on the *id passed to the mock*, not on `viewModel.error.value`.
>
> Use `createViewModel(context = ..)` from Phase 01 with an explicit `mockk<Context>(relaxed = true)`
> captured in a local `val` so it can be passed to `verify`.
>
> Cover at least these branches from `resolveFriendlyBrowseErrorRes`:
> - `WifiRequiredException(..)` -> `verify { context.getString(R.string.error_wifi_required_smb) }`.
> - `java.io.IOException("Authentication failed")` ->
>   `verify { context.getString(R.string.friendly_copy_error_auth_failed) }`.
> - `java.io.IOException("Connection timed out")` ->
>   `verify { context.getString(R.string.error_network_timeout) }`.
> - `java.io.IOException("some unmapped detail")` (fallback branch) ->
>   `verify { context.getString(R.string.friendly_copy_error_generic) }`.
>
> Check `WifiRequiredException`'s actual constructor signature
> (`data/network/exceptions/WifiRequiredException.kt`) before instantiating it - do not guess.

**Verification:**

- `Grep` - all 4 new `@Test fun` names present.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests
  "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"` passes, all tests green.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. Correction to this step's own prompt: `handleError` is
  `protected` (inherited visibility from `BaseViewModel`, `BrowseViewModel`'s `override fun
  handleError` does not widen it), not callable from the test class directly. Switched to the
  natural production path instead: `createViewModel` gained a `contentDiscovery` parameter (mirroring
  `context`, not built internally) so each test pre-stubs `coEvery {
  contentDiscovery.getResourcesUseCase.getById(any()) } throws <exception>` before construction -
  `loadResource()` runs from `init {}` inside `scope.launch(ioDispatcher + exceptionHandler)` with no
  surrounding try/catch, so the thrown exception reaches `handleError` exactly the way a real
  "resource fetch failed" would in production. All 4 branches (`WifiRequiredException`, auth-failure
  message, timeout message, unmapped fallback) verified via `context.getString(<expected resId>)`.
  Two test names needed shortening post-hoc for detekt's 120-char `MaxLineLength` (see Phase Done
  Criteria note). `check-standard-fast.ps1` BUILD SUCCESSFUL, 4/4 green (7/7 cumulative).

---

### Step 02.2 - Selection coordination tests

**Files:** `ui/browse/BrowseViewModelTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add tests seeding `state.value.mediaFiles` via
> `viewModel.replaceMediaFiles(listOf(mediaFile("a.jpg", 1L), mediaFile("b.jpg", 2L), mediaFile("c.jpg", 3L)))`
> (reuse the `mediaFile(name, createdDate)` fixture-helper style from `BrowseFileListManagerTest.kt` -
> `MediaFile(name=.., path="/$name", type=MediaType.IMAGE, size=.., createdDate=..)`), then:
> - `selectFile toggles a path into currentSelectedPaths` - call once, assert path present; call again
>   with the same path, assert it is removed (toggle semantics).
> - `selectAll selects every seeded path` - assert `currentSelectedPaths()` equals the full set of 3
>   paths.
> - `clearSelection after selectAll empties currentSelectedPaths` - assert empty set.
> - `selectFileRange selects the contiguous range` - read `ui/browse/selection/
>   BrowseSelectionManager.kt`'s `selectRange` KDoc/body first to confirm the actual anchor/range
>   semantics (does it need a prior `selectFile` anchor call, or does it range from list start) before
>   writing the assertion - do not guess the contract.

**Verification:**

- `Grep` - all 4 new `@Test fun` names present.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests
  "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"` passes, all tests green.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. `BrowseSelectionManager.selectRange` confirmed to need a prior
  anchor (`lastSelectedPath` from an earlier `toggleSelection` call) - without one it just selects the
  given path alone. The range test calls `selectFile("/a.jpg")` first to establish the anchor, then
  `selectFileRange("/c.jpg")`, asserting the inclusive `[a,b,c]` range. `check-standard-fast.ps1` BUILD
  SUCCESSFUL, 4/4 green (11/11 cumulative).

---

### Step 02.3 - replaceMediaFiles state-mutation test

**Files:** `ui/browse/BrowseViewModelTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> `replaceMediaFiles updates mediaFiles and totalFileCount together` - call with a 3-element fake
> list, assert `viewModel.state.value.mediaFiles == list` and
> `viewModel.state.value.totalFileCount == 3`.

**Verification:**

- `Grep` - new `@Test fun` name present.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests
  "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"` passes, all tests green.
- `.\a.ps1 fk` succeeds (compile-only, standard flavor).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. `check-standard-fast.ps1` BUILD SUCCESSFUL, 12/12 green
  (cumulative total - one more than the 11 originally estimated, since Step 02.1's error-mapping
  redesign kept 4 separate branch tests as planned). `.\a.ps1 fk` (standard, compile-only) BUILD
  SUCCESSFUL. `post-change.ps1 -ScopeToFile` PASS after two formatting fixes: `java.io.IOException`
  import belongs at the end of the import block (ktlint's `java`/`javax`/`kotlin`/aliases-last layout,
  not flat alphabetical - the opposite of where it was first placed), and two test names plus one
  4-element `listOf(..)` call needed shortening/wrapping for the 120-char `MaxLineLength` /
  `ArgumentListWrapping` rules. Dev log entry written, `dev/CATALOG/app_v2.jsonl` regenerated as a
  `catalog-sync` side effect of `post-change.ps1`.

---

## Phase Done Criteria (both phases)

- [x] Every step above `[x] done`.
- [x] `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests
      "com.sza.fastmediasorter.ui.browse.BrowseViewModelTest"` green - 12/12 test methods.
- [x] `.\a.ps1 fk` succeeds.
- [x] Dev log entry added for the new test file.
- [x] `dev/CATALOG/app_v2.jsonl` regen attempted via `post-change.ps1`'s `catalog-sync` step - PASS,
      but the file itself does not appear in the catalog: scan roots exclude `src/test/` by design
      (confirmed against `GameViewModelTest`/`StandalonePlayerViewModelTest`, both absent too) - this
      checkbox's original wording was a wrong assumption, not a gap. See Last Audit.
- [x] `/spec-check S1352` returns `Verified`.

No device-test gate: pure unit-test addition, zero production-code behavior change, no new
user-facing flow - same precedent as S1351 (Verified without a device pass).

---

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (Simple path - no tactical folder, phases inline)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 3

### Manual / on-device

None - pure unit-test addition, zero production-code behavior change, no new user-facing flow (same
precedent as S1351). `dev/CATALOG/app_v2.jsonl` EXEMPT: catalog scan roots exclude `src/test/` by
design (confirmed - `GameViewModelTest`/`StandalonePlayerViewModelTest`, both long-standing real
files, are likewise absent from the catalog), so the original Phase Done Criteria checkbox expecting
this file to appear there was a wrong assumption in the spec itself, not a code gap. FEATURES
trilingual and flavor-gating EXEMPT per Non-goals (internal-only, no `BuildConfig` gate).
