# Phase 04 - Delivery Channel Core (Source Failover + Integrity)

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-09
**Completed:** 2026-06-09

> Blockers B1 and B2 are resolved (2026-06-09, strategic §6.1) - this phase is execution-ready and depends only on Phase 02.

---

## Objective

Build the uniform self-download channel (strategic Pillar C, owner option C): per-set ordered source list (vendor → our mirror) with auto-failover, integrity/authenticity verification before attach, and secure persistence outside cache - reusing the proven `TesseractModelManager` / `PaddleOcrModelManager` download patterns.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [x] Blocker B1 (source mapping per set) resolved - strategic §6.1 B1.
- [x] Blocker B2 (authenticity & versioning format) resolved - strategic §6.1 B2.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSourceDescriptor.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSetDownloader.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/RealDeliverableSetDownloader.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveryManifestDataSource.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/PayloadIntegrityVerifier.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DeliveryModule.kt` | Modified | ≤ 120 |

---

## Steps

### Step 04.1 - Describe a set's ordered source list

**Files:** `domain/delivery/DeliverableSourceDescriptor.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `DeliverableSourceDescriptor` holding, per `DeliverableSet`: an ordered list of source URLs, expected payload `minSize`, and the app-pinned `sha256` (B2). Source order per B1: module sets (A/B/C/D) list our GitHub mirror as sole/primary source; language-data items list the vendor URL first (`tessdata_best/4.1.0`, `paddlelite-demo.bj.bcebos.com`) then our mirror as fallback. Pure data, no Android imports.

**Verification:**

- `Grep` - `class DeliverableSourceDescriptor` or `data class DeliverableSourceDescriptor` matches once.
- `Grep` - a `List<` of sources field present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. New: `domain/delivery/DeliverableSourceDescriptor.kt` (+44 LOC) holding set-level descriptor + per-file `sources: List<String>`/`sha256`/`minSize` (B1/B2). Dev log recorded.

---

### Step 04.2 - Remote manifest data source

**Files:** `data/delivery/DeliveryManifestDataSource.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement `DeliveryManifestDataSource` that fetches the remote manifest (hosted on our mirror, keyed by the running app `versionCode`) supplying **only the source URLs** per item, so mirror endpoints can change without an app release (B2). The app-pinned `sha256`/`minSize` come from a compiled bundled descriptor, never from the manifest, for native `.so` sets. Fall back entirely to the bundled descriptor if the manifest is unreachable. Use the existing OkHttp client.

**Verification:**

- `Grep` - `class DeliveryManifestDataSource` matches once.
- `Grep` - `DeliverableSourceDescriptor` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. New: `data/delivery/DeliveryManifestDataSource.kt` (+90 LOC). Overlays manifest URLs (keyed by `versionCode`) on the compiled bundled descriptor; integrity anchors never from manifest; unreachable/malformed → bundled verbatim. Bundled descriptors injected as `Map<DeliverableSet, DeliverableSourceDescriptor>` (provider added in step 04.5; Phase 05 merges contributors). Dev log recorded.

---

### Step 04.3 - Integrity/authenticity verifier

**Files:** `data/delivery/PayloadIntegrityVerifier.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement `PayloadIntegrityVerifier` applying B2's scheme: compare the downloaded payload's `MessageDigest` SHA-256 against the app-pinned `sha256` and confirm `length >= minSize` (mirror the existing check in `TesseractModelManager`). Verification failure returns a typed failure; never accept an unverified payload (ADR-3). This guards `System.load` of delivered native code in Phase 05/07.

**Verification:**

- `Grep` - `class PayloadIntegrityVerifier` matches once.
- `Grep` - `MessageDigest` and `"SHA-256"` both referenced.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 3/3 PASS. New: `data/delivery/PayloadIntegrityVerifier.kt` (+73 LOC). SHA-256 + min-size check, sealed `Result.Verified/Failed`; blank pinned hash = size-only (Set C resources); never accepts unverified payload (ADR-3). Dev log recorded.

---

### Step 04.4 - Downloader with ordered failover

**Files:** `domain/delivery/DeliverableSetDownloader.kt`, `data/delivery/RealDeliverableSetDownloader.kt`
**Depends on:** Step 04.2, Step 04.3

**Prompt for developer:**

> Define `interface DeliverableSetDownloader { fun download(set: DeliverableSet): Flow<DownloadProgress> }` where `DownloadProgress` covers queued/running(percent)/verifying/installed/failed. Implement `RealDeliverableSetDownloader`: resolve descriptor via manifest, try each source in order with a connect/read timeout (mirror the 15 s timeouts in `TesseractModelManager`), stage to `.tmp`, run `PayloadIntegrityVerifier`, atomically move into `filesDir/delivery/<set>/`, then call `DeliverableCapabilityRepository.markInstalled(set)`. On every source failing, emit `failed` without partial state. No broad `catch (e: Exception) {}` swallow - log at the correct level and surface failure (Rule 20).

**Verification:**

- `Grep` - `interface DeliverableSetDownloader` matches once.
- `Grep` - `class RealDeliverableSetDownloader` and `markInstalled` both present.
- `Grep` - `.tmp` staging reference present.
- `Grep -n "catch (e: Exception) {\s*}"` - zero empty-catch hits in `RealDeliverableSetDownloader.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 4/4 PASS. New: `domain/delivery/DeliverableSetDownloader.kt` (+34 LOC, `DownloadProgress` Queued/Running/Verifying/Installed/Failed) + `data/delivery/RealDeliverableSetDownloader.kt` (+170 LOC). Per-file ordered-source failover (15 s timeouts), `<set>.tmp` staging, `PayloadIntegrityVerifier` gate, atomic `renameTo` promote, `markInstalled`; all-or-nothing on failure (no partial payload). Typed `catch (IOException)` with warn log, no broad swallow. Dev log recorded.

---

### Step 04.5 - Bind the downloader

**Files:** `di/DeliveryModule.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Bind `RealDeliverableSetDownloader` → `DeliverableSetDownloader` in the existing `DeliveryModule`. Uniform across flavors (channel C) - no `BuildConfig` flavor guards (Rule 15).

**Verification:**

- `Grep` - `DeliverableSetDownloader` bound in `DeliveryModule.kt`.
- `Grep` - `BuildConfig.` returns zero hits in `DeliveryModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. Modified: `di/DeliveryModule.kt` (+`@Binds DeliverableSetDownloader`, companion `@Provides` empty bundled-descriptor map - Phase 05 replaces with contributor merge). No `BuildConfig` guard (Rule 15). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (v2.60.6091.023).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

> Note: the global `assert-no-ticket-logs` gate trips on a foreign, uncommitted stale `[S0388]`
> probe in `src/noLegal/.../VrApkArchiveResolver.kt` (concurrent workstream) - not touched per the
> no-clobber rule. All Phase 04 files are clean of ticket-id logs.

---

## Handoff Notes to Next Phase

A verified, failover-capable downloader populates `filesDir/delivery/<set>/` and flips the install marker. Phase 05 defines what each set's payload contains and strips those artifacts from the base build; Phase 06 drives this downloader from the UX.

---

## Rollback Plan

Revert phase commit(s). Downloader is additive and unreferenced by UI until Phase 06; no base-build artifact removed yet, so reverting cannot break playback/OCR. No schema migration introduced.
