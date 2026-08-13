# S1310 - Debug session-log mirror grows unbounded: 10s appender with no size cap, no rotation, full re-copy after source-log rotation

**Ticket:** S1310
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): critic-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Debug builds only - low user impact, easy fix.

## Finding 1: Debug session-log mirror file grows unbounded: 10 s appender with no size cap, no rotation, and full re-copy after every source-log rotation

- Severity: P3, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt:500`
- Symptom: In debug builds, once the user opens any local file folder (updateDebugMirrorTargetFromPath is called), a scheduled task on the 'fms-log-io' executor appends the session log delta to 'fastmediasorter_debug_live.log' in that user-visible folder every 10 seconds for the entire process lifetime. The mirror file is opened with FileOutputStream(targetFile, true) and is never truncated, rotated, or size-checked. The source session logs are rotated at 5 MB x 5 files (25 MB cap), but the mirror accumulates every byte of every session log forever: when the source log rotates, debugMirrorSourcePath changes and debugMirrorSourceOffsetBytes resets to 0, so the whole new session file gets appended on top of everything already mirrored. The mirror target also persists across process restarts only via re-set, but within one long-running debug session (background music playing for days, verbose minPriority=VERBOSE file logging) the file grows without bound in shared storage.
- Failure scenario: Debug build used for long-running remote diagnostics (the project's standard field-debugging flow). Tester opens a local media folder once, which sets the mirror target; the app then runs for days with verbose file logging (this app logs heavily - every Timber.d goes to the file tree at minPriority VERBOSE). The session log rotates repeatedly at 5 MB; each rotation resets the mirror offset to 0 and the entire new session file is appended to fastmediasorter_debug_live.log. After N rotations the mirror is ~N x 5 MB and keeps growing - hundreds of MB to GB of shared storage consumed in the user's media folder, competing with the media files the app manages, with no eviction path and no way to notice except a full disk.
- Fix sketch: In flushDebugMirrorDelta, cap the mirror: check targetFile.length() before appending and either truncate-and-restart (FileOutputStream(targetFile, false)) or stop mirroring past a fixed budget (e.g. 2x maxFileSize = 10 MB) with a final '=== mirror capped ===' line. Alternatively, on source-log rotation rewrite the mirror from scratch instead of appending (mirror = current session tail only). Also skip scheduling startDebugMirrorScheduler entirely until a mirror target is actually set to avoid the idle 10 s wakeup.
- Verifier rationale: critic direct evidence, unverified

Evidence excerpt:

```
private fun startDebugMirrorScheduler() { logIoExecutor.scheduleAtFixedRate({ ... flushDebugMirrorDelta() }, 10, 10, TimeUnit.SECONDS) } ... private fun flushDebugMirrorDelta() { ... if (debugMirrorSourcePath != sourceFile.absolutePath) { debugMirrorSourcePath = sourceFile.absolutePath; debugMirrorSourceOffsetBytes = 0L } ... RandomAccessFile(sourceFile, "r").use { input -> input.seek(debugMirrorSourceOffsetBytes); FileOutputStream(targetFile, true).use { output -> ... } }
```

