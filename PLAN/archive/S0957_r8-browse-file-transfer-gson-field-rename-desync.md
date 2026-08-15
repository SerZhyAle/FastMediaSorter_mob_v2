# S0957 - R8 renames BrowseFileTransfer Gson fields; cross-update JSON desync of persisted transfer state

**Status:** Archived
**Priority:** 35
**Date:** 2026-07-05
**Tier:** 3 - Moderate (ad-hoc)

<!-- parked by S0905 audit sweep (Layer 7, R8 build proof) - 2026-07-05 -->
<!-- auto-approved by /spec-all - 2026-07-06 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 7 R8 build proof (standardBenchmark mapping.txt).

Symptom: `BrowseFileTransferRequest`/`BrowseFileTransferTerminalPayload` are Gson field-reflection round-tripped to disk (`active_request.json`, `terminal_event.json`) and consumed by a `@HiltWorker` that survives process death. No keep rule covers `com.sza.fastmediasorter.ui.browse.transfer`, so R8 renames the fields. Round-trip is safe within one build, but a Play-Store auto-update mid-transfer (new APK, new R8 mapping) between write and read desyncs field names and drops/corrupts the resumed request or terminal notification.

Evidence (confirmed on minified build):
- mapping.txt `standardBenchmark`: `BrowseFileTransferRequest -> ct0`; fields `operationType -> a`, `sourceResourceId -> b`, `sourceResourceName -> c`, `sourceCredentialsId -> d`, `currentBrowsePath -> e` (renamed).
- Contrast: keep-ruled `domain.model.FavoritesExportFile` maps to identity (fields not renamed).
- Source: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModels.kt:7,84`; store `BrowseFileTransferRequestStore.kt:27-74` (self-recovering `runCatching{}.getOrNull()` at 70-73).

Severity: Low-Med (narrow update-race window, self-recovering fallback).

Scope note: add `-keep`/`@SerializedName` (or `@Keep`) for the transfer models so field names survive R8, matching the `domain.model.**` pattern.

## 1. Fix decision (2026-07-06)

- Root cause: the two disk-persisted Gson models in `ui.browse.transfer` (`BrowseFileTransferRequest` with its nested `BrowseFileTransferSource`, and `BrowseFileTransferTerminalPayload`) are field-reflection round-tripped by `BrowseFileTransferRequestStore`. No keep rule covers that package, so R8 renames the fields; a Play auto-update mid-transfer (new mapping) between write and read desyncs the JSON key names.
- Chosen fix: pin the wire format with `@SerializedName` on every field of the three persisted types, backed by the existing `-keepclassmembers class * { @SerializedName <fields>; }` rule (proguard line ~240). Preferred over a bare `-keep class .. { *; }` because it is R8-independent (survives every future mapping, not just this build), keeps obfuscation, and is provable by a fast JVM Gson round-trip test rather than a minified build + mapping.txt diff.
- Out of scope: `BrowseFileTransferProgressSnapshot` is not Gson-persisted (WorkManager `Data` via `BrowseFileTransferProgressCodec`, string-literal keys); `FileOperationType`/`UndoOperation` live in the keep-ruled `domain.model.**`, so their names are already stable.
- Migration: a transfer persisted by a pre-fix release (R8-renamed keys) is read once by the fixed build as a key-mismatched object and dropped by the store's self-recovering `runCatching{}.getOrNull()` path - the same narrow, self-recovering update-race the bug already tolerates; no new failure mode, and every fixed-to-fixed update afterwards is stable.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0905 (audit source), S0737 and S0719 (prior Gson-persistence R8 keep-rule precedent, proguard lines 23-27), CLAUDE.md Rule 20 / CODE_AUDIT_PROTOCOL Layer 7.
- **Data format:** persisted JSON keys in `active_request.json` and `terminal_event.json` are pinned to the current field names; going forward the format is stable across app updates. One-time only, an in-flight transfer persisted by a pre-fix release is dropped on first read by the fixed build (self-recovering, no crash), losing at most that single resume.

## Implementation State (2026-07-06)

- **Done.** `@SerializedName` added to every persisted field of `BrowseFileTransferRequest` (9), nested `BrowseFileTransferSource` (4) and `BrowseFileTransferTerminalPayload` (11) in `ui/browse/transfer/BrowseFileTransferModels.kt`, with a WHY comment. Backed by the pre-existing `-keepclassmembers class * { @SerializedName <fields>; }` rule (proguard line ~240).
- **Guard test.** `app_v2/src/test/.../BrowseFileTransferModelsSerializationTest.kt` reflects every persisted instance field and asserts it carries `@SerializedName` matching its name (skips the Compose `$stable` static field), plus Gson round-trips. A plain key-presence assertion would pass without the annotation on a non-obfuscated JVM, so the reflection guard is what actually catches a future regression.
- Out of scope confirmed: `BrowseFileTransferProgressSnapshot` is WorkManager `Data` (string-literal keys via `BrowseFileTransferProgressCodec`), not Gson; `FileOperationType`/`UndoOperation` are in the keep-ruled `domain.model.**`.

## Last Audit

**Date:** 2026-07-06
**Verdict:** Verified (JVM wire-format guard + minified R8 proof; Low-Med severity)

- **Correctness (wire format).** JVM test PASS: all 24 persisted fields carry `@SerializedName` == field name; Gson serialize/deserialize round-trips. The wire keys are pinned by the annotation, so they are stable regardless of any R8 field rename - the exact cross-version desync is closed permanently, not just for this build.
- **Minified R8 proof (Layer 7).** `assembleStandardBenchmark` BUILD SUCCESSFUL. In `standardBenchmark/mapping.txt` the three model classes retain full constructors + getters, and the pre-fix field renames the audit found (`BrowseFileTransferRequest.operationType -> a`, etc.) are gone - the fields are kept at identity by the line-240 rule. Symmetric to the audit's own evidence method.
- **Migration.** A transfer persisted by a pre-fix release (R8-renamed keys) is read once by the fixed build as a key-mismatched object and dropped by the store's `runCatching{}.getOrNull()` path - the same narrow, self-recovering update-race the bug already tolerated; no new failure mode.
- **Residual (not fixed, pre-existing, out of scope).** On a key-mismatched read Gson yields an object with null non-null fields rather than null; the store returns it and downstream tolerates/drops it. Hardening `readActiveRequest` to null-validate is a separate, optional robustness follow-up, unchanged by this ticket.

## Related

- S0905 (audit-tail sweep, source); docs/CODE_AUDIT_PROTOCOL.md Layer 7; CLAUDE.md Rule 20.
