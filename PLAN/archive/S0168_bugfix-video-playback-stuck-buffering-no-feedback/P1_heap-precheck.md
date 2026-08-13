# S0168 Phase 1 — Native heap pre-check before ExoPlayer creation

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Spec ref:** §5.3

## Steps

### Step 1.1 — Add constant `NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES`

In companion object, after `NATIVE_HEAP_RECREATE_THRESHOLD_BYTES`:

```kotlin
// S0168: threshold for Glide eviction + GC before ExoPlayer creation.
// VP9 decoder allocates 20-30 MB native at init; 30 MB free is the minimum safe margin.
private const val NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES = 30L * 1024 * 1024
```

Verification: `grep "NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES"` returns 2 hits (declaration + usage).

### Step 1.2 — Add Glide import

Add to import block:
```kotlin
import com.bumptech.glide.Glide
```

Verification: file compiles without unresolved symbol.

### Step 1.3 — Add pre-check block in `playVideo()`

After the ExoPlayer recreate block (after `trackChangesSinceRecreate = 0`), before `managerScope.launch`:

```kotlin
// WHY S0168 §5.3: VP9/other codec decoders allocate 20–30 MB native at init.
// When native heap is critically low, buffer allocation stalls immediately → errorCode=1004.
// Run Glide eviction + GC before ExoPlayer starts to maximise available native memory.
val nativeFreePrePlay = Debug.getNativeHeapFreeSize()
if (nativeFreePrePlay < NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES) {
    Timber.w(
        "VideoPlayerManager: native heap low before playback — free=%dMB, running Glide eviction + GC (S0168 §5.3)",
        nativeFreePrePlay / 1024 / 1024
    )
    Glide.get(context).clearMemory()
    Runtime.getRuntime().gc()
    val nativeFreeAfterGc = Debug.getNativeHeapFreeSize()
    if (nativeFreeAfterGc < NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES) {
        Timber.w(
            "VideoPlayerManager: native heap still low after GC — free=%dMB — proceeding with caution",
            nativeFreeAfterGc / 1024 / 1024
        )
        Toast.makeText(context, context.getString(R.string.warning_low_memory_playback), Toast.LENGTH_SHORT).show()
    } else {
        Timber.i(
            "VideoPlayerManager: native heap recovered after GC — free=%dMB",
            nativeFreeAfterGc / 1024 / 1024
        )
    }
}
```

Verification: block inserted after recreate block, before `managerScope.launch {`.

### Step 1.4 — Add string `warning_low_memory_playback` trilingual

EN `values/strings.xml`:
```xml
<string name="warning_low_memory_playback">Low memory — video playback may fail.</string>
```

RU `values-ru/strings.xml`:
```xml
<string name="warning_low_memory_playback">Мало памяти — воспроизведение может не запуститься.</string>
```

UK `values-uk/strings.xml`:
```xml
<string name="warning_low_memory_playback">Мало пам'яті — відтворення може не запуститись.</string>
```

Verification: `grep "warning_low_memory_playback"` returns 3 hits (one per locale).
