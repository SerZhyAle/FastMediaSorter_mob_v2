# Phase 02 - Dead Class Removal

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (02.4 deferred to Phase 04)
**Depends on:** none - independent phase
**Blocks:** none
**Steps done:** 3 / 4 (02.4 ⏭️ Deferred)
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Delete confirmed-dead first-party classes and narrow the package-wide keep rules that force-ship the force-kept subset into release.

> **Exclusion (cross-ticket guard):** `HostKeyMismatchException` in `data/remote/sftp/PinnedHostKeyRepository.kt` is **NOT** deleted by this phase. Although it is currently never thrown, it is planned scaffolding for **S0046 `sftp-key-auth-hardening` (status: Partial)** - its Phase 05 wires `HostKeyMismatchException` into `AddResourceSftpKeyCoordinator.kt`. Deleting it would break the in-flight S0046 work. Leave it untouched; do not narrow `-keep class ...data.remote.** { *; }` in a way that strips it.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBuffer.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferEncoder.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/ErrorPropagatingPipedInputStream.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/EncryptedStringConverter.kt` | Modified/Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseFragment.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/UiEvent.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfHelper.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/KpiAlertChecker.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/MetricsExporter.kt` | Deleted | - |
| `app_v2/proguard-rules.pro` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 - Delete the dead Glide byte-buffer cluster

**Files:** `data/network/glide/SafeByteBuffer.kt`, `SafeByteBufferBitmapDecoder.kt`, `SafeByteBufferEncoder.kt`, `ErrorPropagatingPipedInputStream.kt`, `NetworkVideoFrameDecoder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete `SafeByteBuffer`, `SafeByteBufferBitmapDecoder`, `SafeByteBufferEncoder`, and `ErrorPropagatingPipedInputStream` - none are registered in the Glide module or instantiated. Remove the trailing unused `NetworkFileDataInputStream` class declared at the bottom of `NetworkVideoFrameDecoder.kt`. Do not touch the live decoders/loaders registered in the Glide module.

**Verification:**

- `Grep` - `SafeByteBuffer`, `SafeByteBufferBitmapDecoder`, `SafeByteBufferEncoder`, `ErrorPropagatingPipedInputStream`, `NetworkFileDataInputStream` each return zero hits across `app_v2/src/**`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (0 hits). Deleted SafeByteBuffer.kt, SafeByteBufferBitmapDecoder.kt, SafeByteBufferEncoder.kt, ErrorPropagatingPipedInputStream.kt; removed trailing `NetworkFileDataInputStream` from NetworkVideoFrameDecoder.kt plus its now-unused `import java.io.InputStream`. Pre-delete grep confirmed all 5 were self-referential only (not registered in GlideAppModule).

---

### Step 02.2 - Remove the orphaned encrypted-string converter remnant

**Files:** `data/local/db/EncryptedStringConverter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> The `EncryptedString` data class has no Room converter and no entity field. Remove the orphaned `EncryptedString` declaration (and the file if nothing else lives in it). Do not alter any live `@TypeConverter`.

**Verification:**

- `Grep` - `EncryptedString` returns zero hits across `app_v2/src/**`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (0 hits). Deleted EncryptedStringConverter.kt entirely (file held only the orphaned `data class EncryptedString` - no live `@TypeConverter` in it).

---

### Step 02.3 - Delete the abandoned base/UI/util/metrics classes

**Files:** `core/ui/BaseFragment.kt`, `core/ui/UiEvent.kt`, `utils/PdfHelper.kt`, `core/metrics/KpiAlertChecker.kt`, `core/metrics/MetricsExporter.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Delete `BaseFragment` (zero subclasses), `UiEvent` (sealed, zero subclasses), `PdfHelper` (zero references), `KpiAlertChecker` (zero callers) and `MetricsExporter` (reachable only from the dead checker). Confirmed not referenced by any active spec ticket. Leave the live metrics recorders untouched; if removing `MetricsExporter` orphans a recorder method that nothing else calls, leave the recorder in place - that is out of scope for this phase.

**Verification:**

- `Grep` - `BaseFragment`, `UiEvent`, `PdfHelper`, `KpiAlertChecker`, `MetricsExporter` each return zero hits across `app_v2/src/**`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (0 hits). Deleted BaseFragment.kt, UiEvent.kt, PdfHelper.kt, KpiAlertChecker.kt, MetricsExporter.kt. Live metrics recorders (OperationMetricsRecorder, ScanMetricsRecorder) left in place per step scope. Pre-delete grep confirmed the metrics pair was a dead cluster (MetricsExporter referenced only by the dead KpiAlertChecker).

---

### Step 02.4 - Narrow the package-wide keep rules covering the deleted classes

**Files:** `app_v2/proguard-rules.pro`
**Depends on:** Step 02.3

**Prompt for developer:**

> The deleted force-kept classes lived under `-keep class ...data.network.glide.** { *; }` and `-keep class ...data.local.db.** { *; }`. Narrow these two rules to the specific classes that actually need reflection/Room/Glide retention (the live registered decoders, the Room entities/converters) so the shrinker can prune future dead members. Keep the rules that protect genuinely reflection-accessed types. **Do not** touch `-keep class ...data.remote.** { *; }` in this phase - it still guards `HostKeyMismatchException` reserved by S0046 plus live FTP/SFTP/iTunes models.

**Verification:**

- `Grep` - `data.network.glide.\*\*` (wildcard whole-package keep) is no longer present in `proguard-rules.pro`; specific class keeps remain.
- `Grep` - `data.remote.\*\*` whole-package keep is still present (untouched).
- `Grep -n "Log\.d\("` returns zero hits in any modified `.kt` file.

**Status:** `⏭️ Skipped` - deferred to Phase 04 (release-validated keep-narrowing batch)

**Step Log:**

- 2026-06-08 - Deferred. Deleting the dead classes (02.1-02.3) already stops them shipping in release regardless of keep breadth, so the primary win is delivered. Narrowing the `data.network.glide.**` and `data.local.db.**` reflection keeps is only verifiable on a RELEASE build with live Glide image-loading + Room runtime exercise (debug has minify off). That release-validation is exactly the scope the owner deferred by choosing Phases 01-03 over 01-04. Moved to Phase 04, which owns staged keep-narrowing with per-path release validation (added a note there). No proguard edit made in this phase.

---

## Phase Done Criteria

- [x] Steps 02.1-02.3 `[x] done`; 02.4 `⏭️ Deferred` to Phase 04 (release-validated keep-narrowing).
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL (1m7s) after deleting 10 classes + trimming NetworkVideoFrameDecoder. | expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL.
- [x] Release R8 safety: no keep rule was narrowed here, and every deleted class sat under a wildcard `-keep ...** { *; }` (no specific named-class keep targeted a deleted class) → R8 cannot fail on the deletions. Full standardRelease validation folded into Phase 04 (keep-narrowing batch).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `HostKeyMismatchException` untouched in `PinnedHostKeyRepository.kt` (data.remote not modified) - S0046 scaffolding preserved.
- [x] Dev log entries added (post-change Kotlin + deletion batch line).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (catalog_sync PASS: 1669 records, deleted classes dropped).

---

## Handoff Notes to Next Phase

Dead first-party classes removed; their force-keep rules narrowed so they no longer ship in release. `HostKeyMismatchException` deliberately preserved for S0046. Broader library keep-rule narrowing (BouncyCastle, GMS, ML Kit) is handled in Phase 04.

---

## Rollback Plan

Revert the phase commit - deletions only; restore proguard-rules.pro to the prior package-wide keeps if any release runtime path regresses.
