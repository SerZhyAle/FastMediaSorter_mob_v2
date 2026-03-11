# Implementation Plan: Audio Metadata Enrichment (Phase 3)

This document provides a step-by-step prompt-based execution plan for a developer AI (or human) to implement Phase 3 of `SPEC_AUDIO_METADATA_ENRICHMENT.md`.

**Execution Rule**: Copy each prompt exactly, execute it, then perform the **Build & Commit** block before moving to the next step. Following standard project rules, all log files/temporary edits must remain in `/temp`, and no logic should be placed in Activities directly.

---

## Step 1: Add `readPartial` to FileTransferProvider Interface

**Developer Prompt:**
```text
Implement Phase 3, Step 3.1 (Part 1) from SPEC_AUDIO_METADATA_ENRICHMENT.md.
Modify `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileTransferProvider.kt`.
Add a new suspend function to the interface:
`suspend fun readPartial(path: String, maxBytes: Int): ByteArray`
Also, provide stub implementations (e.g., throwing NotImplementedError or returning empty arrays) in all implementing classes (`SmbTransferProvider`, `SftpTransferProvider`, `FtpTransferProvider`, `LocalTransferProvider`, `CloudTransferProvider`, etc.) so that the project compiles successfully. Ensure no lint warnings are introduced.
```

**Post-Step Actions:**
1. **Build**: 
   ```powershell
   .\build-debug.PS1
   ```
2. **Dev Log**: 
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileTransferProvider.kt" "FileTransferProvider" "Added readPartial to interface with stubs"
   ```
3. **Commit**: 
   ```powershell
   git add .
   git commit -m "feat: add readPartial interface to FileTransferProvider with stubs"
   ```

---

## Step 2: Implement `readPartial` in Network Providers

**Developer Prompt:**
```text
Implement Phase 3, Step 3.1 (Part 2) from SPEC_AUDIO_METADATA_ENRICHMENT.md.
Replace the stub implementations of `readPartial(path, maxBytes)` in the network providers:
1. In `SmbTransferProvider.kt`, use `smbj` `file.read()` to read up to `maxBytes`.
2. In `SftpTransferProvider.kt`, use `jsch` `channel.get()` to read up to `maxBytes` (close stream early after reading the requested block).
3. In `FtpTransferProvider.kt`, use `commons-net` `InputStream` to read up to `maxBytes` and close early.
4. For `LocalTransferProvider`, you can implement a standard `RandomAccessFile` or `FileInputStream` reader up to `maxBytes`.
Important: Ensure all streams, files, and channels are safely closed using Kotlin's `use` block or a `finally` block to prevent resource leaks.
```

**Post-Step Actions:**
1. **Build**: 
   ```powershell
   .\build-debug.PS1
   ```
2. **Dev Log**: 
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/" "NetworkProviders" "Implemented actual readPartial for SMB, SFTP, FTP, Local"
   ```
3. **Commit**: 
   ```powershell
   git add .
   git commit -m "feat: implement actual readPartial logic in network transfer providers"
   ```

---

## Step 3: Create `AudioMetadataLoader`

**Developer Prompt:**
```text
Implement Phase 3, Step 3.2 from SPEC_AUDIO_METADATA_ENRICHMENT.md.
1. Create a new class `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`.
2. Provide a function: `fun loadIfNeeded(file: MediaFile, position: Int, onLoaded: (MediaFile) -> Unit)`
3. Flow logic:
   - Check DB cache (`FileMetadataCacheDao`). If cache hit, format fields, invoke `onLoaded` and exit.
   - On miss, check a `failedCache` (LruCache or FIFO LinkedHashMap, max 5000 items). Skip if path failed before.
   - Enqueue a `Dispatchers.IO` block limited by a concurrency semaphore: `Semaphore(3)`.
   - Call `FileTransferProvider.readPartial(file.path, 65536)` (i.e. 64KB).
   - Pass the ByteArray to Media3's `MetadataRetriever` using a `ByteArrayDataSource` to avoid writing temp files.
   - Extract artist, album, title, and duration (`durationMs`).
   - Save via `FileMetadataCacheDao.upsertAll()`.
   - Call `onLoaded` with the enriched payload.
4. On network or parsing failure: add to `failedCache`.
5. Register this class via DI (Hilt) in the appropriate module.
Resolve any lint warnings introduced. Keep Timber logs detailed but write them to Logcat/temp files.
```

**Post-Step Actions:**
1. **Lint & Build**: 
   ```powershell
   .\gradlew.bat lintStandardDebug
   .\build-debug.PS1
   ```
2. **Dev Log**: 
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt" "AudioMetadataLoader" "Created AudioMetadataLoader with Media3 parsing"
   ```
3. **Commit**: 
   ```powershell
   git add .
   git commit -m "feat: create viewport-based AudioMetadataLoader using Media3 retriever"
   ```

---

## Step 4: Integrate Adapter and Scroll Listener

**Developer Prompt:**
```text
Implement Phase 3, Steps 3.3 and 3.4 from SPEC_AUDIO_METADATA_ENRICHMENT.md.
1. In `MediaFileAdapter.kt`:
   - Add `const val PAYLOAD_AUDIO_METADATA = "LOAD_AUDIO_METADATA"`.
   - Add function `loadVisibleAudioMetadata(firstVisible: Int, lastVisible: Int)` triggering `notifyItemRangeChanged` with this payload.
   - Add payload handling inside `onBindViewHolder(holder, position, payloads)`: if the payload matches and it's a network AUDIO file (`!isLocalPath`) missing an artist, invoke `audioMetadataLoader.loadIfNeeded()`. In the callback, conditionally update the `MediaFile` list and run `notifyItemChanged(position, ...)` or standard bind.
2. In `BrowseActivity.kt` (or wherever thumbnail scrolling logic is handled):
   - Locate `onScrollStateChanged` -> `SCROLL_STATE_IDLE`.
   - Add a call to `adapter.loadVisibleAudioMetadata(first, last)` right alongside the existing thumbnail load trigger.
Remove unused code, fix any UI glitches during bindings, and run lint.
```

**Post-Step Actions:**
1. **Release Build**: 
   ```powershell
   .\dev\build-with-version.ps1
   ```
2. **Dev Log**: 
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/" "BrowseUI" "Integrated metadata loader into BrowseActivity and MediaFileAdapter"
   ```
3. **Commit**: 
   ```powershell
   git add .
   git commit -m "feat: integrate AudioMetadataLoader into Browse UI scroll listener"
   ```

---

## Step 5: Final Review & Quality Assurance

**Developer Prompt:**
```text
Finalize Phase 3 from SPEC_AUDIO_METADATA_ENRICHMENT.md.
1. Perform a thorough review of the modified code. Ensure no hardcoded strings remain, all `Timber` logs match standards, and Coroutines are properly scoped (e.g., cancellable upon layout detachment if needed).
2. Check `dev/TECH_REQUIREMENTS.md` and `docs/ARCHITECTURE.md` to ensure nothing contradicts the core design.
3. Clean up any loose ends. If any formatting issues or minor unused imports exist across `AudioMetadataLoader`, `MediaFileAdapter`, and the transfer providers, resolve them now.
Ensure successful compilation of all flavors.
```

**Post-Step Actions:**
1. **Full Tests & Lint Check**: 
   ```powershell
   .\gradlew.bat testStandardDebugUnitTest
   .\gradlew.bat lintStandardDebug
   ```
2. **Commit (If Any Updates)**: 
   ```powershell
   git add .
   git commit -m "chore: final cleanup and linting for audio metadata implementation"
   ```
