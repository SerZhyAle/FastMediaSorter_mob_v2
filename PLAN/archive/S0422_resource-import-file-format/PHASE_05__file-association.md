# Phase 05 - File association

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-15
**Completed:** 2026-06-15

**Step Log:**

- 2026-06-15 - Steps 05.1-05.2 done. ResourceImportActivity (transparent, VIEW+SEND, preview/confirm/import via SzaResourcesImporter, IntentCompat). Manifest: 3 intent-filters (vendor MIME content/file VIEW, file pathPattern .fmsr, SEND vendor MIME); octet-stream not registered. `a.ps1 fc` PASS. Neuroslop delta 0.

---

## Objective

Let a share file open the app directly: a dedicated receiver activity handles `ACTION_VIEW` (file `.fmsr`) and `ACTION_SEND` (vendor MIME) intents, runs the same preview/confirm/import flow, then finishes.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceimport/ResourceImportActivity.kt` | New | ≤ 180 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |

> Mirrors the existing `ReceiveShareActivity` pattern: transparent theme, no own layout, dialog-only UI. Intent-filter design follows `research/01__file-association.md` (vendor MIME for SEND + content VIEW, file `pathPattern` for VIEW; `application/octet-stream` intentionally not registered).

---

## Steps

### Step 05.1 - Add the receiver activity

**Files:** `ui/resourceimport/ResourceImportActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@AndroidEntryPoint class ResourceImportActivity : AppCompatActivity()`. On create, resolve the incoming `Uri` from either `intent.data` (VIEW) or `intent.getParcelableExtra(Intent.EXTRA_STREAM)` (SEND). Inject `SzaResourcesImporter`. In a lifecycle-scoped coroutine call `preview(uri)`; on `Valid` show a `MaterialAlertDialogBuilder` with the create/overwrite counts and the credential note (`resource_share_credentials_warning`), confirm -> `importFromUri(uri)` -> show the result, then `finish()`; on `Invalid` show `resource_share_invalid_file` and `finish()`. Use the transparent theme. Take a persistable read permission only if needed; otherwise read the stream immediately. No business logic in the activity beyond orchestration.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceimport/ResourceImportActivity.kt` exists.
- `Grep` - `class ResourceImportActivity` matches once.
- `Grep` - `EXTRA_STREAM` and `intent.data` both handled.
- `Grep` - `preview(` and `importFromUri(` called.

**Status:** `[ ]` not done

---

### Step 05.2 - Register intent filters

**Files:** `AndroidManifest.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Register `<activity android:name=".ui.resourceimport.ResourceImportActivity" android:exported="true" android:theme="@style/Theme.FastMediaSorter.Transparent" android:excludeFromRecents="true">` with three filters: (1) VIEW + DEFAULT + BROWSABLE, `scheme="content"`/`scheme="file"`, `mimeType="application/vnd.fms.resources+xml"`; (2) VIEW + DEFAULT + BROWSABLE, `scheme="file"`, `host="*"`, `pathPattern=".*\\.fmsr"`, `mimeType="*/*"`; (3) SEND + DEFAULT, `mimeType="application/vnd.fms.resources+xml"`. Do not register `application/octet-stream` (research 01). Keep the MIME string identical to `ResourceShareFormat.MIME_TYPE`.

**Verification:**

- `Grep` - `ResourceImportActivity` present in `AndroidManifest.xml`.
- `Grep` - `application/vnd.fms.resources+xml` present in the manifest.
- `Grep` - `.*\\.fmsr` pathPattern present.
- `Grep` - `application/octet-stream` NOT added for this activity.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new activity).

---

## Handoff Notes to Next Phase

All three entry points (Settings, per-resource share, file-open) now route into the shared importer. Final phase documents the feature and refreshes the catalog.

---

## Rollback Plan

Revert phase commit(s) - one new activity + manifest filters; disabling is a manifest revert, no migration.
