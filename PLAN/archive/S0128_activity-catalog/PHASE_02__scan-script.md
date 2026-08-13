# Phase 02 — Scan Script

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (schema-foundation)
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Write `dev/ACTIVITY_CATALOG/scripts/scan.ps1` — reads all `AndroidManifest.xml` source sets for the given module, extracts own-package `<activity>` entries, derives auto-fields, and writes/merges the JSONL output preserving manual fields.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/ACTIVITY_CATALOG/scripts/scan.ps1` | New | ≤ 350 |

---

## Background: manifest sources to scan

For `app_v2`:

| Source set | Path | Notes |
|------------|------|-------|
| main | `app_v2/src/main/AndroidManifest.xml` | Primary; all owned Activities |
| vr | `app_v2/src/vr/AndroidManifest.xml` | `VrPlayerActivity`, `VrPhoneFallbackActivity`; adds `noFlavors` = everything except vr |
| lite | `app_v2/src/lite/AndroidManifest.xml` | Overlay only; check for new Activity declarations |
| photos | `app_v2/src/photos/AndroidManifest.xml` | Overlay only |
| legacy | `app_v2/src/legacy/AndroidManifest.xml` | Overlay only |

For `wear`: single manifest at `wear/src/main/AndroidManifest.xml`.

Own-package prefix to distinguish from third-party: `com.sza.fastmediasorter` (or relative `.ClassName` form).

---

## Steps

### Step 02.1 — Write scan.ps1 skeleton and manifest parser

**Files:** `dev/ACTIVITY_CATALOG/scripts/scan.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `dev/ACTIVITY_CATALOG/scripts/scan.ps1` with the following signature and logic.
>
> **Parameters:**
> - `-Module` (mandatory) — `app_v2` or `wear`
> - `-Root` (optional) — project root; defaults to `$PSScriptRoot/../../../`
> - `-OutFile` (optional) — defaults to `dev/ACTIVITY_CATALOG/$Module.jsonl`
>
> **Manifest discovery logic:**
> 1. For `app_v2`: collect manifest paths for source sets `main`, `vr`, `lite`, `photos`, `legacy`. Skip if file does not exist.
> 2. For `wear`: collect `wear/src/main/AndroidManifest.xml` only.
> 3. Parse each manifest with `[xml]`. For each `<activity>` element whose `android:name` starts with `com.sza.fastmediasorter` or with `.` (relative, package-local), extract:
>    - `class` — last segment after the last `.`
>    - `package` — full name (expand relative `.Foo` → `com.sza.fastmediasorter.Foo`)
>    - `sourceSet` — which source set the manifest came from
>    - `exported` — `android:exported` attribute (`$true` if attribute == `"true"`)
>    - `launcher` — `$true` if any `<intent-filter>` child contains both `android.intent.action.MAIN` action and any `android.intent.category.LAUNCHER` or `android.intent.category.LEANBACK_LAUNCHER` category
>    - `intentActions` — deduplicated array of all `action android:name` values across all intent-filters
>    - `intentCategories` — deduplicated array of all `category android:name` values
>
> Collect per-class which source sets declare the activity. Activities declared only in a flavor sourceSet (`vr`, `lite`, `photos`, `legacy`) populate `noFlavors` with the flavors where they are absent: an Activity in `vr` only → `noFlavors = ["standard","lite","photos","legacy"]`. Activities in `main` → `noFlavors = []`.

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/scripts/scan.ps1` exists.
- `Grep` — `param(` present in `scan.ps1`.
- `Grep` — `\[xml\]` present in `scan.ps1` (XML parser usage).
- `Grep` — `noFlavors` present in `scan.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: dev/ACTIVITY_CATALOG/scripts/scan.ps1 (manifest parser skeleton + noFlavors logic). Dev log recorded.

---

### Step 02.2 — Add source-file correlation and git lastTouched

**Files:** `dev/ACTIVITY_CATALOG/scripts/scan.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend `scan.ps1` to resolve each Activity's source `.kt` file and populate `path`, `sourceSet` (override to actual source root), `loc`, and `lastTouched`.
>
> **Source file search:**
> 1. Convert `package` to a relative path: replace `.` with `/`, append `.kt`.
> 2. Search in `$Module/src/main/java/` and `$Module/src/vr/java/`. Take the first match.
> 3. If found: set `path` to the relative path within the source root, `sourceSet` to `main` or `vr`, `loc` to `(Get-Content $fullPath).Count`, `lastTouched` to output of `git log -1 --format=%as -- <fullPath>` (trim whitespace; empty string if no output).
> 4. If not found: `path = ""`, `loc = 0`, `lastTouched = ""`.

**Verification:**

- `Grep` — `git log` present in `scan.ps1`.
- `Grep` — `\.Count` present in `scan.ps1` (LOC counting).
- `Grep` — `lastTouched` present in `scan.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS (git log, .Count, lastTouched present). Files: dev/ACTIVITY_CATALOG/scripts/scan.ps1. Dev log recorded.

---

### Step 02.3 — Add JSONL read/merge/write with manual field preservation

**Files:** `dev/ACTIVITY_CATALOG/scripts/scan.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend `scan.ps1` to read any existing `$OutFile` JSONL, extract manual fields (`role`, `roleRu`, `tags`, `status`, `notes`), merge them into the newly scanned records by `module + class` key, and write the result as one JSON object per line (UTF-8, no BOM).
>
> **Merge algorithm:**
> 1. Load existing records from `$OutFile` (if exists) into a hashtable keyed on `"$module|$class"`.
> 2. For each newly scanned record, check if the key exists in the hashtable.
> 3. If yes: copy `role`, `roleRu`, `tags`, `status`, `notes` from the existing record (do not overwrite with empty strings — keep existing values even if the new scan yields empty strings for manual fields).
> 4. If no: set `role = ""`, `roleRu = ""`, `tags = @()`, `status = "new"`, `notes = ""`.
> 5. Write all records to `$OutFile`, one `ConvertTo-Json -Compress` line each.
> 6. Print summary: `"Scanned $total Activities → $OutFile"`.

**Verification:**

- `Grep` — `ConvertTo-Json` present in `scan.ps1`.
- `Grep` — `ConvertFrom-Json` present in `scan.ps1`.
- `Grep` — `"$($r.module)|$($r.class)"` or equivalent merge-key pattern present in `scan.ps1`.
- Running `& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module app_v2` exits with code 0 and prints `"Scanned"` in stdout.
- `Glob` — `dev/ACTIVITY_CATALOG/app_v2.jsonl` exists after the run.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS (ConvertTo-Json, ConvertFrom-Json, merge-key pattern present; scan app_v2 exit 0, app_v2.jsonl exists). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `scan.ps1 -Module app_v2` runs without error and produces `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- [ ] `scan.ps1 -Module wear` runs without error and produces `dev/ACTIVITY_CATALOG/wear.jsonl`.
- [ ] Re-running `scan.ps1` a second time preserves any manually edited `role` / `tags` values in the JSONL.
- [ ] `Grep` for `Log\.d\(` in `scan.ps1` returns zero hits (no Android logging in PS scripts — trivially true, but confirm no stray print statements violate conventions).
- [ ] Dev log entry added for `dev/ACTIVITY_CATALOG/scripts/scan.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 produces two JSONL files. Phase 03 reads them without modification — it only adds query/render/set tooling on top.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. Delete `dev/ACTIVITY_CATALOG/*.jsonl` if generated files should not persist.
