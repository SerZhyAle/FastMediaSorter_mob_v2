# Tactical Plan: Phase 1 — StandalonePlayerActivity Setup

This document breaks down **Phase 1** of the "Default Player" implementation into incremental, buildable micro-steps. Each step serves as a self-contained prompt for an AI agent or developer.

---

## ✅ Step 1.1: StandalonePlayerActivity & Manifest Registration — DONE
**Objective**: Create the activity class and declare it safely in `AndroidManifest.xml`.

**Status**: Already completed. `StandalonePlayerActivity.kt` exists at `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` and is declared in `AndroidManifest.xml` with `android:exported="true"`. No action needed.

---

## 🛠️ Step 1.2: StandalonePlayerViewModel & Hilt Injection
**Objective**: Create a simplified ViewModel that manages media state for a standalone (no-resource) playback session.

**Prompt for Developer**:
> 1. Create `StandalonePlayerViewModel.kt` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/`.
> 2. Annotate with `@HiltViewModel`. **Do NOT inject `SavedStateHandle`** — it is not used by `BaseViewModel` in this project.
> 3. Inherit from `BaseViewModel<StandalonePlayerState, StandalonePlayerEvent>()`.
> 4. Define:
>    ```kotlin
>    data class StandalonePlayerState(
>        val mediaFile: MediaFile? = null,
>        val mediaType: MediaType? = null,
>        val isLoading: Boolean = false,
>        val errorMessage: String? = null
>    )
>    sealed class StandalonePlayerEvent
>    ```
> 5. Expose a public method `fun loadFromUri(uri: Uri, mimeType: String?, displayName: String?)` that updates state. URI is passed **from Activity**, not read from SavedStateHandle.
> 6. Inject `StandalonePlayerViewModel` into `StandalonePlayerActivity` using `private val viewModel: StandalonePlayerViewModel by viewModels()`.
> 7. **Verification**: Run `.\scripts\builders\build-debug.PS1` verifying Hilt code-generation succeeded.
> 8. **Logging**: `.\.scripts\add_to_dev_log.ps1 "app_v2/.../StandalonePlayerViewModel.kt" "StandalonePlayerViewModel" "Phase 1 Step 1.2: created VM with StandalonePlayerState"`

---

## 🛠️ Step 1.3: Intent Data Parsing & Type Detection
**Objective**: Read the incoming URI from `Intent` in Activity, resolve display name via `ContentResolver`, detect media type, and pass data to ViewModel.

**Prompt for Developer**:
> 1. In `StandalonePlayerActivity.setupViews()`, read the incoming intent:
>    ```kotlin
>    val uri = intent.data ?: run { finish(); return }
>    val mimeType = intent.type
>    ```
> 2. Resolve the file's display name via `ContentResolver` (required for type detection from `content://` URIs — `uri.path` only contains the MediaStore ID, not the filename):
>    ```kotlin
>    val displayName = contentResolver.query(
>        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
>    )?.use { it.takeIf { it.moveToFirst() }?.getString(0) } ?: uri.lastPathSegment
>    ```
> 3. Call `viewModel.loadFromUri(uri, mimeType, displayName)` to pass data into the ViewModel.
> 4. Inside `StandalonePlayerViewModel.loadFromUri()`, use `MediaTypeUtils.getMediaTypeFromMimeOrExtension(mimeType, displayName ?: "")` to detect the type. Signature: `fun getMediaTypeFromMimeOrExtension(mimeType: String?, fileName: String): MediaType?`
> 5. If type is `null` — update state with `errorMessage` and emit an event to finish the Activity with a user-visible toast.
> 6. Update state with the resolved `MediaFile` wrapper (name, uri, type).
> 7. **Verification**: Run `.\scripts\builders\build-debug.PS1`.
> 8. **Logging**: `add_to_dev_log.ps1` for both VM and Activity changes.

---

## 🛠️ Step 1.4: Close Button (✕) & Back Navigation
**Objective**: Wire the close button and hardware Back key to `finish()` the Activity entirely.

**Prompt for Developer**:
> 1. The binding element for the back/close button is **`binding.btnBack`** (per `CommandPanelController.kt`). In `StandalonePlayerActivity.setupViews()`, change its icon to `✕` (e.g. `binding.btnBack.setImageResource(R.drawable.ic_close)` or equivalent drawable) and bind `finish()`:
>    ```kotlin
>    binding.btnBack.setImageResource(R.drawable.ic_close) // ✕ instead of ← arrow
>    binding.btnBack.setOnClickListener { finish() }
>    ```
> 2. Register hardware Back the same way via `OnBackPressedDispatcher`:
>    ```kotlin
>    onBackPressedDispatcher.addCallback(this) { finish() }
>    ```
> 3. Both actions must call `finish()` — no navigation to Browse or Main.
> 4. Buttons for Next/Previous file (`btnNextCmd`, `btnPreviousCmd`) must be **hidden** (`View.GONE`) — there is no playlist in standalone mode.
> 5. **Verification**: Run `.\scripts\builders\build-debug.PS1`. Manually test: open Activity → press ✕ → app closes.
> 6. **Logging**: `add_to_dev_log.ps1` for `StandalonePlayerActivity`.

---

## 🛠️ Step 1.5: Media Type Routing
**Objective**: Observe ViewModel state and dispatch to the correct viewer for each media type.

**Prompt for Developer**:
> 1. In `StandalonePlayerActivity.observeData()`, collect `viewModel.state` using `lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }`.
> 2. On state change, route by `state.mediaType`:
>    ```kotlin
>    when (state.mediaType) {
>        MediaType.IMAGE, MediaType.GIF -> { /* Use existing ImageLoadingManager, not raw Glide */ }
>        MediaType.VIDEO                -> { /* Initialize ExoPlayer surface — no playlist */ }
>        MediaType.AUDIO                -> { /* Start AudioPlaybackService — no playlist */ }
>        MediaType.PDF                  -> { /* PDF viewer manager */ }
>        MediaType.EPUB                 -> { /* EPUB viewer manager */ }
>        MediaType.TEXT                 -> { /* Text viewer */ }
>        null                           -> { /* show error toast, finish() */ }
>    }
>    ```
> 3. **Use `ImageLoadingManager`** (existing class) for IMAGE/GIF — do not call Glide directly from Activity.
> 4. All viewer initializations at this step are **stubs** (placeholder `TODO()`). Full viewer wiring is done in subsequent steps. The goal here is that the project compiles with all branches present.
> 5. Handle `state.errorMessage != null` → show `Toast` → `finish()`.
> 6. **Verification**: Run `.\scripts\builders\build-debug.PS1`. All `when` branches must compile without warnings.
> 7. **Logging**: `add_to_dev_log.ps1` for `StandalonePlayerActivity` — "Phase 1 complete: media type routing stubs".
