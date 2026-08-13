# Phase 02 - Import from file

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

**Step Log:**

- 2026-06-15 - Steps 02.1-02.3 done. SzaResourcesImporter: importFromUri/importFromParser, preview + PreviewResult, flavor media-type filter. `a.ps1 fk` PASS. Neuroslop delta 0. Dev log recorded.

---

## Objective

Generalize the existing predefined-resource importer to read from an arbitrary user file (URI / stream) and add a non-destructive preview that reports what an import would create and overwrite, without writing anything.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SzaResourcesImporter.kt` | Modified | ≤ 360 |

> The importer already parses the `<media-resources>` format, encrypts credentials, handles SFTP key-auth + fingerprint, and matches existing resources by `path`. This phase only adds external-source entry points and a preview - the per-element apply logic is reused unchanged.

---

## Steps

### Step 02.1 - Accept an external source

**Files:** `ui/settings/helpers/SzaResourcesImporter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extract the parse loop into `private suspend fun importFromParser(parser: XmlPullParser): ImportResult` and have the existing no-arg `import()` build the parser from the bundled `R.xml.sza_resources` and delegate to it (behaviour unchanged). Add `suspend fun importFromUri(uri: Uri): ImportResult` that opens `contentResolver.openInputStream(uri)`, creates an `XmlPullParser` via `Xml.newPullParser()` on that stream, and delegates to `importFromParser`. Keep the existing `Failure` wrapping; a malformed file must return `Failure`, never apply a partial subset that corrupts existing data (each element is already applied independently, so reject before the loop if the root tag is not `ResourceShareFormat.ROOT_TAG`).

**Verification:**

- `Grep` - `suspend fun importFromUri(` matches once.
- `Grep` - `importFromParser(` present.
- `Grep` - `ResourceShareFormat.ROOT_TAG` present.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Add a non-destructive preview

**Files:** `ui/settings/helpers/SzaResourcesImporter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `suspend fun preview(uri: Uri): PreviewResult` that parses the same file but applies nothing: count how many `resource` elements would be created vs would overwrite an existing resource (match by `path`, the same key the apply path uses), whether any element carries a `password`/credential attribute (`containsCredentials: Boolean`), and the file's declared `version`. Add a sealed `PreviewResult` with `Valid(toCreate: Int, toUpdate: Int, containsCredentials: Boolean)` / `Invalid(reason: String)`. `Invalid` covers a wrong root tag or unparseable XML. No writes to repositories in this method.

**Verification:**

- `Grep` - `suspend fun preview(` matches once.
- `Grep` - `sealed interface PreviewResult` or `sealed class PreviewResult` present.
- `Grep` - `containsCredentials` present.

**Status:** `[ ]` not done

---

### Step 02.3 - Filter imported media types by flavor capability

**Files:** `ui/settings/helpers/SzaResourcesImporter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the per-element apply path (`importOne`), after parsing `supportedMediaTypes`, intersect the parsed set with the flavor's enabled media families using the existing `BuildConfig.SUPPORT_*` capability flags (the same flags `ProvisionDefaultResourcesUseCase` reads - `SUPPORT_AUDIO`, `SUPPORT_VIDEO`, `SUPPORT_IMAGES`, `SUPPORT_DOCUMENTS`). Drop unsupported families so a `photos` build importing an AUDIO resource creates a usable image resource (or skips it if nothing remains) rather than a dead one. This is a capability flag, not an `IS_<flavor>` guard, so it is allowed in `src/main`.

**Verification:**

- `Grep` - `BuildConfig.SUPPORT_` referenced in `SzaResourcesImporter.kt`.
- `Grep -n "BuildConfig.IS_"` in the file returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `SzaResourcesImporter.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).

---

## Handoff Notes to Next Phase

`SzaResourcesImporter.importFromUri(uri)` and `.preview(uri)` are the shared import entry points used by the Settings UI (Phase 03) and the file-association receiver (Phase 05). The bundled-config owner trigger keeps working through the unchanged `import()`.

---

## Rollback Plan

Revert phase commit - one modified file; the bundled `import()` path is untouched in behaviour.
