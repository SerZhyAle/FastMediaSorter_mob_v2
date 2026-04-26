# Phase 04 — BrowseActivity Wiring

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-04-25
**Completed:** 2026-04-25
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05, 06

---

## Objective

Instantiate `BrowseCameraCaptureManager` in `BrowseActivity`, implement the
`onCameraCaptureClicked()` callback, and wire the `onFileSaved` lambda to trigger a list
refresh and scroll-to-new-file.

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `ui/browse/BrowseActivity.kt` | Mod | ≤ 1000 |
| `ui/browse/managers/BrowseEventHandler.kt` | Mod | ≤ 400 |

---

## Steps

### Step 04.1 — Instantiate BrowseCameraCaptureManager in BrowseActivity

**Status:** `[x] done`
**File:** `ui/browse/BrowseActivity.kt`
**Depends on:** Phase 03

1. Add field:

   ```kotlin
   private lateinit var cameraCaptureManager: BrowseCameraCaptureManager
   ```

2. In `onCreate()`, before `super.onCreate()` / before `setContentView` (launcher must
   register before Activity starts — `registerForActivityResult` is called inside the
   `BrowseCameraCaptureManager` constructor):

   ```kotlin
   cameraCaptureManager = BrowseCameraCaptureManager(
       activity = this,
       settingsRepository = settingsRepository,
       coroutineScope = lifecycleScope,
       onFileSaved = { fileName -> onCapturedFileSaved(fileName) }
   )
   ```

**Verification:** `Grep "cameraCaptureManager" ui/browse/BrowseActivity.kt` → ≥ 2 hits.

---

### Step 04.2 — Implement onCameraCaptureClicked in BrowseActivity

**Status:** `[x] done`
**File:** `ui/browse/BrowseActivity.kt`
**Depends on:** Phase 02 (callback defined), Step 04.1

`BrowseActivity` implements `BrowseButtonSetupHelper.ButtonCallbacks` (directly or via a
delegate). Locate that implementation and add:

```kotlin
override fun onCameraCaptureClicked() {
    val resource = viewModel.state.value.resource ?: return
    cameraCaptureManager.launch(resource)
}
```

**Verification:** `Grep "onCameraCaptureClicked" ui/browse/BrowseActivity.kt` → ≥ 1 hit.

---

### Step 04.3 — Post-save: refresh list and scroll to new file

**Status:** `[x] done`
**File:** `ui/browse/BrowseActivity.kt`
**Depends on:** Step 04.2

Add private method:

```kotlin
private fun onCapturedFileSaved(fileName: String) {
    // Trigger same refresh as manual pull-to-refresh
    viewModel.refreshFileList()
    // After list reloads, scroll to the item whose name matches fileName
    viewModel.scrollToFileAfterRefresh(fileName)
}
```

If `BrowseViewModel` does not have a `scrollToFileAfterRefresh` method, add it:

- In `BrowseViewModel`, add `fun scrollToFileAfterRefresh(fileName: String)` that emits a
  one-shot `BrowseEvent.ScrollToFile(fileName)` event after the next list update completes.
- In `BrowseActivity` / `BrowseEventHandler`, handle `BrowseEvent.ScrollToFile` by calling
  `recyclerView.scrollToPosition(index)` where `index` is the adapter position of the file
  with the matching name.

If `BrowseEvent` already has a scroll event, reuse it. If not, add:

```kotlin
data class ScrollToFile(val fileName: String) : BrowseEvent()
```

**Verification:**

- `Grep "onCapturedFileSaved" ui/browse/BrowseActivity.kt` → ≥ 2 hits
- `Grep "ScrollToFile\|scrollToFileAfterRefresh" ui/browse/BrowseActivity.kt` → ≥ 1 hit

---

### Step 04.4 — Handle ScrollToFile in BrowseEventHandler

**Status:** `[x] done`
**File:** `ui/browse/managers/BrowseEventHandler.kt`
**Depends on:** Step 04.3

In the event handling `when` block, add:

```kotlin
is BrowseEvent.ScrollToFile -> {
    val index = adapter.currentList.indexOfFirst { it.name == event.fileName }
    if (index >= 0) recyclerView.scrollToPosition(index)
}
```

**Verification:** `Grep "ScrollToFile" ui/browse/managers/BrowseEventHandler.kt` → ≥ 1 hit.

---

## Phase Done Criteria

- [x] `Grep "cameraCaptureManager" ui/browse/BrowseActivity.kt` → ≥ 2 hits (3)
- [x] `Grep "onCameraCaptureClicked" ui/browse/BrowseActivity.kt` → ≥ 1 hit (1)
- [x] `Grep "onCapturedFileSaved" ui/browse/BrowseActivity.kt` → ≥ 2 hits (2)
- [x] `Grep "ScrollToFile" ui/browse/managers/BrowseEventHandler.kt` → ≥ 1 hit (3)
- [x] BUILD-REQUIRED: standard-debug must pass before Phase 05 begins — PASS 2026-04-25 (`assembleStandardDebug` BUILD SUCCESSFUL)

**Phase Step Log:**

- 2026-04-25 — Steps 04.1-04.4 all done. BrowseActivity gets cameraCaptureManager + onCameraCaptureClicked + onCapturedFileSaved; BrowseEvent.ScrollToFile added; BrowseViewModel.scrollToFileAfterRefresh waits for file in state then emits event; BrowseEventHandler handles ScrollToFile via onScrollToFile lambda wired in BrowseManagerInitializer.
