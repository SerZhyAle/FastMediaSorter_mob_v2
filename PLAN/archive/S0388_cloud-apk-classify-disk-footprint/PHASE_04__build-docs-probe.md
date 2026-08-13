# Phase 04 - Build, docs, catalog, debug probe

**Strategic spec:** [`../S0388_cloud-apk-classify-disk-footprint.md`](../S0388_cloud-apk-classify-disk-footprint.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🔄 In progress (device-test gate)
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps:** 3 / 4

---

## Objective

Validate the change on the only compiling variant, record the mandatory bookkeeping, and insert the single device-test debug probe so the ticket can enter `BlockNeedUserTest` with the IFF invariant intact.

---

## Steps

### Step 04.1 - Insert the `BlockNeedUserTest` debug probe

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** Phases 01-03 done

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", S0388 enters `BlockNeedUserTest`, so the changed flow needs exactly one entry-point probe. Add `Timber.d("S0388: transient cloud APK copy resolve reached")` once at the entry of `resolveCloudArchive` (the core of the disk-footprint change). This is the only place a ticket id is allowed in log text. Do not scatter `S0388:` tags elsewhere. `/spec-check` (or the transition out of `BlockNeedUserTest`) grep-deletes this line.

**Verification:**

- `Grep` - `Timber.d("S0388:` matches exactly once across all `.kt` files.
- `Grep` - that single match is inside `VrApkArchiveResolver.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-09 - `Timber.d("S0388: transient cloud APK copy resolve reached")` at entry of `resolveCloudArchive`. Verified count = 1 across app_v2+wear .kt, inside `VrApkArchiveResolver.kt`.

---

### Step 04.2 - Build `noLegalDebug`

**Files:** -
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the noLegal debug build (the only variant compiling the noLegal source set): `.\a.ps1 nd` (or `/build` → `noLegal debug`). Fix any compile error minimally (most likely an unresolved `VrArchiveResolution` case or a missing import). Re-run until it passes.

**Verification:**

- Build exits 0; record `expected: PASS | actual: <PASS/FAIL>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-09 - `a.ps1 nd` → `BUILD SUCCESSFUL in 1m 45s`. expected: PASS | actual: PASS. Also `:app_v2:testNoLegalDebugUnitTest --tests VrApkClassificationCacheTest` → 4 tests, 0 failures (incl. new OutOfSpace session-stop test).

---

### Step 04.3 - Bookkeeping: dev log, catalog sync, functionality log

**Files:** `dev/CHANGELOG.md` (via script), `dev/CATALOG/app_v2.jsonl`, `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> 1. Dev log for each touched code file via `scripts/add_to_dev_log.ps1` (or one `post-change.ps1` per file). 2. `scripts/catalog_sync.ps1 -Module app_v2` (new symbol `VrArchiveResolution`; set its `role`/`status` via `set.ps1`). 3. Functionality log: this is a behavior change to an existing capability (VR badge recognition), so a CHANGE/FIX entry via `scripts/add_to_functionality_log.ps1` describing reduced cache footprint + no error cascade. No `docs/FEATURES*.md` change (noLegal recognition is not in public feature docs; strategic §8).

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an S0388 line for each code file.
- `Grep` - `dev/FUNCTIONALITY.log` contains an S0388 entry.
- `dev/CATALOG/app_v2.jsonl` has a `VrArchiveResolution` entry.

**Status:** `[x] done`

**Step Log:**

- 2026-06-09 - Dev log: 4 files. Catalog sync app_v2 (1692 records); `VrArchiveResolution` catalogued, role/status set via `set.ps1`. Func log: S0388 FIX entry. Neuroslop gate PASS (empty-catch 75→74). No `docs/FEATURES*` change (noLegal not in public docs).

---

### Step 04.4 - Set `BlockNeedUserTest`, run device-test gate

**Files:** `PLAN/spec-catalog.jsonl` (via `update.ps1`)
**Depends on:** Step 04.3

**Prompt for developer:**

> `update.ps1 -Id S0388 -Status BlockNeedUserTest`. Then probe for a device with `scripts/devtest/device-ready.ps1`; if online, auto-run `/spec-test-device S0388` → `/spec-check S0388` (which removes the probe on the transition out of `BlockNeedUserTest`). If no device, leave the block and note "device-test deferred (no device)".

**Verification:**

- `select.ps1 -Id S0388` shows `BlockNeedUserTest` (or `Verified`/`Partial` if the device gate ran).
- IFF invariant: exactly one `Timber.d("S0388:` in `.kt` while `BlockNeedUserTest`; zero after the transition out.

**Status:** `[~] in progress`

**Step Log:**

- 2026-06-09 - `update.ps1 -Status BlockNeedUserTest` applied (Tactical → BlockNeedUserTest), header synced. IFF holds (1 probe). Device-test gate: probing for an attached device; auto-chain `/spec-test-device` + `/spec-check` if online, else park as `device-test deferred (no device)`.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `noLegalDebug` build PASS.
- [ ] IFF invariant holds for the S0388 probe.

---

## Device Test Script

On Quest (noLegalDebug):

1. Open a cloud folder containing several APK archives (some VR, some not).
2. Confirm VR badges appear on VR APKs.
3. Scroll the list back and forth - confirm no re-download storm in the log (re-binds served from memory).
4. Inspect the app cache volume - `vr_apk_classification/` does not accumulate full APK copies after browsing.
5. Artificially fill the cache volume, re-open the folder - confirm a single logged "cache volume full" line, no repeated-error cascade, app stays responsive.

---

## Rollback Plan

Revert Phases 01-03 commits. The probe line is removed automatically on the status transition out of `BlockNeedUserTest`.
