# S0464 — Mic recording save fails with EACCES on car OEM (SPRD ums512)

**Status:** Archived
**Priority:** 50

## §0 — Raw evidence (auto-captured, 2026-06-16)

Device: SPRD `ums512_1h10_Natv`, Android 14 firmware / API 29, release build 2.60.6150.338 standard.

Log lines (fastmediasorter_20260616_181347.log, 18:29:08):

```
E/UserAction: save FAILED name=REC_20260616_182903.m4a
java.io.FileNotFoundException: /storage/emulated/0/Download/REC_20260616_182903.m4a: open failed: EACCES (Permission denied)
    at libcore.io.IoBridge.open
    at java.io.FileOutputStream.<init>
    ...
Caused by: android.system.ErrnoException: open failed: EACCES (Permission denied)
```

Same error repeated for `REC_20260616_182912.m4a` at 18:29:15.

The app writes mic recordings via direct `FileOutputStream` to `/storage/emulated/0/Download/`. On this car OEM, WRITE_EXTERNAL_STORAGE is not granted (or restricted by OEM policy for API 29). Two consecutive recordings failed to save.

## §1 — Problem

Mic recording save to Download/ fails on SPRD car head units running API 29 with restrictive OEM storage policy. Direct `FileOutputStream` path requires `WRITE_EXTERNAL_STORAGE` which is blocked. On API 29, saving to shared Download/ should go through `MediaStore.Downloads` or SAF (Storage Access Framework).

## §2 — Цель и объём

Сохранение mic-записи на устройство (публичный fallback Downloads и сконфигурированный локальный публичный таргет) не падает с EACCES на API 29+ - публичные коллекции пишутся через MediaStore.

**В объёме:**

- `BrowseMicRecordingManager.save()` - единственный путь с прямым `File.copyTo` в публичную коллекцию (подтверждено логом: copy в `/storage/emulated/0/Download/REC_*.m4a`).

**Вне объёма:**

- `QuickAudioRecorderService` - сохраняет в app-private `getExternalFilesDir(MUSIC)`, EACCES не подвержен.
- Camera / video capture flows - имеют идентичный латентный баг через ту же `CaptureDestinationPolicy`; вынесены в отдельный тикет (follow-up).
- Сетевые / облачные загрузки (`onUploadFile`) - не затронуты.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0367 (mic destination resolution), S0231/S0280 (MediaStore local writer)

## §3 — Реализация (фазы)

### PHASE_01 — route on-device mic save through the MediaStore-aware writer

- [ ] Inject `LocalDestinationClassifier` and `LocalDestinationWriter` into `BrowseMicRecordingManager` (constructor params), supplied from its construction site (`BrowseActivity`/`BrowseManagerInitializer`, both DI-reachable - `@Inject` on both types).
- [ ] In `save()`, replace the on-device writes (the `ResourceType.LOCAL` `tempFile.copyTo(File(targetResource.path, name))` branch and the `CaptureDestinationPolicy.resolveMicDestination(null)` Downloads-fallback `copyTo`) with: build the absolute destination path (`dir`/`name`) → `classifier.classify(path)` → `writer.open(category, overwrite = true)` → stream `tempFile` bytes into `sink.outputStream` (IO dispatcher) → `sink.commit()`; on any failure `sink.abort()` and return `false`.
- [ ] Keep network/cloud (`onUploadFile`) and destination-resolution (`resolveMicSaveResource`) logic unchanged.
- [ ] Delete the now-unused temp file as today (`clearPendingSession(deleteTempFile = true)`).

**Verification:**

- [ ] `.\a.ps1 fc` (standard debug compile + resources) - PASS.
- [ ] No remaining `tempFile.copyTo(File(` to a public collection in `BrowseMicRecordingManager`.
- [ ] On-device (BlockNeedUserTest): a recording saved with the Downloads fallback succeeds on an API 29+ device with restrictive storage (no EACCES); appears in Downloads via MediaStore.

### PHASE_02 — build + docs

- [ ] Standard debug build PASS (`.\a.ps1 d`).
- [ ] Dev log + functionality log (FIX, user-visible).
