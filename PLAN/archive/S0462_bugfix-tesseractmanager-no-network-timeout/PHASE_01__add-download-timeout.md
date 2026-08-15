# Phase 01 - Add Download Timeout

**Strategic spec:** [`../S0462_bugfix-tesseractmanager-no-network-timeout.md`](../S0462_bugfix-tesseractmanager-no-network-timeout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - only phase
**Blocks:** -
**Steps done:** 1 / 1
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Replace bare `URL.openStream()` in `TesseractManager.checkAndDownloadData()` with `HttpURLConnection` carrying 15 s connect/read timeouts and explicit `disconnect()` in `finally`, mirroring the pattern already used in `TesseractModelManager.downloadModel()`.

---

## Prerequisites

- [x] `TesseractManager.kt` read - `checkAndDownloadData()` confirmed to use `URL.openStream()` at line 125 with no timeout.
- [x] `TesseractModelManager.kt` read - `HttpURLConnection` pattern with `connectTimeout = 15000` / `readTimeout = 15000` confirmed at lines 135–136.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 - Replace URL.openStream() with HttpURLConnection + 15 s timeouts

**Files:** `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

In `TesseractManager.kt`:

1. Add import at the top of the import block: `import java.net.HttpURLConnection`

2. In `companion object`, add constant below `TESS_DATA_URL_BASE`:
   ```
   private const val CONNECT_READ_TIMEOUT_MS = 15_000
   ```

3. Replace the entire `checkAndDownloadData()` method body (the `return try { ... }` block, lines ~124–138) with:

   ```kotlin
   var connection: HttpURLConnection? = null
   return try {
       connection = URL("$TESS_DATA_URL_BASE$lang.traineddata").openConnection() as HttpURLConnection
       connection.connectTimeout = CONNECT_READ_TIMEOUT_MS
       connection.readTimeout = CONNECT_READ_TIMEOUT_MS
       connection.instanceFollowRedirects = true
       connection.inputStream.use { input ->
           FileOutputStream(file).use { output ->
               input.copyTo(output)
           }
       }
       Timber.d("Downloaded $lang.traineddata")
       true
   } catch (e: Exception) {
       Timber.e(e, "Failed to download $lang.traineddata")
       if (file.exists()) file.delete()
       false
   } finally {
       connection?.disconnect()
   }
   ```

   Do not change any other code in the file.

**Verification:**

- `Grep` - `import java.net.HttpURLConnection` appears in `TesseractManager.kt`.
- `Grep` - `CONNECT_READ_TIMEOUT_MS` defined in companion object.
- `Grep` - `connection.connectTimeout = CONNECT_READ_TIMEOUT_MS` present.
- `Grep` - `connection.readTimeout = CONNECT_READ_TIMEOUT_MS` present.
- `Grep` - `connection?.disconnect()` in finally block.
- `Grep` - `URL(` + `.openStream()` does NOT appear in `TesseractManager.kt` (bare openStream eliminated).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Step 01.1 `[x]` done.
- [x] Project compiles: `build-debug.PS1` exits 0 (BUILD SUCCESSFUL in 21s).

---

## Step Log

- 2026-06-16 - Step 01.1 verification 6/6 PASS. Files: app_v2/src/ocrEnabled/.../TesseractManager.kt (+ import, + constant, replaced checkAndDownloadData try-block). Build SUCCESSFUL in 21 s.
