# Phase 01 - Format and export

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

**Step Log:**

- 2026-06-15 - Steps 01.1-01.3 done. New: ResourceShareFormat.kt, ResourceShareSerializer.kt, ExportResourcesToFileUseCase.kt. `a.ps1 fk` PASS. Neuroslop delta 0. Dev log recorded.

---

## Objective

Define the share-file format constants and produce a use case that serializes one or more resources (with decrypted credentials) into that format and writes it to an output stream. No UI yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceShareFormat.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/resourceshare/ResourceShareSerializer.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportResourcesToFileUseCase.kt` | New | ≤ 160 |

> Format reuses the existing `<media-resources>` XML element/attribute names so the bundled-config parser stays compatible (strategic ADR-1). Credentials are written in plaintext per owner decision (strategic ADR-3); warnings live in the UI phases.

---

## Steps

### Step 01.1 - Add format constants

**Files:** `domain/model/ResourceShareFormat.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an `object ResourceShareFormat` holding the share-file constants: `EXTENSION = "fmsr"`, `MIME_TYPE = "application/vnd.fms.resources+xml"`, `FORMAT_VERSION = 1`, `ROOT_TAG = "media-resources"`, `RESOURCE_TAG = "resource"`, `ATTR_VERSION = "version"`. These are the single source of truth for both serializer and manifest-facing code.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceShareFormat.kt` exists.
- `Grep` - `object ResourceShareFormat` matches once.
- `Grep` - `application/vnd.fms.resources+xml` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Add the serializer

**Files:** `data/resourceshare/ResourceShareSerializer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `class ResourceShareSerializer @Inject constructor()` exposing `fun serialize(resources: List<MediaResource>, credentialsById: Map<String, NetworkCredentialsEntity>): String`. Build the XML with `android.util.Xml.newSerializer()`: root `media-resources` with attribute `version=ResourceShareFormat.FORMAT_VERSION`; one `resource` element per item carrying the same attribute names the bundled-config parser reads (`name`, `path`, `type`, `supportedMediaTypes`, `sortMode`, `displayMode`, the boolean flags, `pin`). For network resources resolve the linked credential via `credentialsById` and emit `username` + decrypted `password` (use `CryptoHelper.decrypt`); emit `hostKeyFingerprint` when present. Do not emit `auth="key"` / `privateKeyAsset` - a bundled-asset key is not portable (handled as export-skip in 01.3). Never log credential values.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/resourceshare/ResourceShareSerializer.kt` exists.
- `Grep` - `class ResourceShareSerializer` matches once.
- `Grep` - `fun serialize(` present.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.3 - Add the export use case

**Files:** `domain/usecase/ExportResourcesToFileUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `class ExportResourcesToFileUseCase @Inject constructor(...)` injecting `ResourceRepository`, `NetworkCredentialsRepository`, `ResourceShareSerializer`, `@ApplicationContext Context`. Expose `suspend fun invoke(resourceIds: List<Long>, target: Uri): ExportResult` running on `Dispatchers.IO`. Load the requested resources, build the credential map from their `credentialsId`, call the serializer, and write the result to `contentResolver.openOutputStream(target)`. Skip key-auth SFTP resources (credential has an ssh key but no password) and count them as `skippedKeyAuth`. Return a sealed `ExportResult` with `Success(exported: Int, skippedKeyAuth: Int)` / `Failure(error: Throwable)`. Wrap I/O in a single try/catch that returns `Failure` (no empty catch).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportResourcesToFileUseCase.kt` exists.
- `Grep` - `class ExportResourcesToFileUseCase` matches once.
- `Grep` - `suspend fun invoke(` present.
- `Grep` - `ExportResult` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).

---

## Handoff Notes to Next Phase

Format constants (`ResourceShareFormat`) and `ExportResourcesToFileUseCase` are available for the UI phases. The serializer emits the same attribute names the importer reads, so a round-trip is lossless for password-auth resources.

---

## Rollback Plan

Revert phase commit(s) - three new files, no data migration or user-facing surface changed.
