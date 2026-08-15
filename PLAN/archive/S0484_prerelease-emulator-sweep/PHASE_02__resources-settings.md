# Phase 02 - Resources & Settings

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-17

> **Blocked:** requires research §6.2 (import path + endpoint reachability) and §6.3 (settings list + apply/verify method) Resolved before start.

---

## Objective

Add a helper that owns the **adb-scriptable** preparation: per-endpoint reachability pre-check, intent-push import trigger (push resources XML + `am start ResourceImportActivity`), and the adb-applicable setting (language via `cmd locale`). The single import confirm-dialog tap, the theme + DataStore-backed setting toggles, and listing verification are UI-driven and belong to the skill scenario (Phase 05) - per research §6.2 / §6.3.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Research §6.2 Resolved - `research/02__resource-import-reachability.md` exists.
- [ ] Research §6.3 Resolved - `research/03__settings-apply.md` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/prerelease.config.psd1` | Modified | ≤ 80 |
| `scripts/devtest/prerelease-configure.ps1` | New | ≤ 240 |

---

## Steps

### Step 02.1 - Populate run config (resource selection + settings)

**Files:** `scripts/devtest/prerelease.config.psd1`
**Depends on:** - start of phase (file skeleton created in Phase 01.5)

**Prompt for developer:**

> Populate the `Resources` and `Settings` blocks of the existing run-config file. `Resources`: the named predefined resources per class (LOCAL `Downloads`, SFTP `SFTP`, one SMB), each tagged with a reachability class (`probe-and-list` for reachable public endpoints, `register-only` for LAN-unreachable). `Settings`: the significant settings with target values (research §6.3), each tagged with an apply channel (`adb` for theme/language, `ui` for DataStore-backed toggles). Keep endpoints and credentials sourced by resource name only; do not duplicate credential literals here.

**Verification:**

- `Grep` - `Resources` block lists a local, an SMB, and an SFTP resource name.
- `Grep` - `Settings` block is non-empty.
- `Script` - `pwsh -NoProfile -Command "Import-PowerShellDataFile scripts/devtest/prerelease.config.psd1"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (Resources lists Downloads/LOCAL, test_media/SMB register-only, SFTP/SFTP probe-and-list; Settings=6 with adb|ui channels; import exit 0). Files: scripts/devtest/prerelease.config.psd1 (+24 LOC). Dev log recorded.

---

### Step 02.2 - Endpoint reachability pre-check

**Files:** `scripts/devtest/prerelease-configure.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `prerelease-configure.ps1` (`-DeviceId`, `-Json`). For each configured resource, probe reachability of its endpoint from the emulator context (per research §6.2 - distinguish reachable public endpoints from possibly-unreachable LAN ones). Mark an unreachable resource `SKIP` with reason rather than failing the whole run.

**Verification:**

- `Glob` - `scripts/devtest/prerelease-configure.ps1` exists.
- `Grep` - `Import-PowerShellDataFile` referenced (loads the config).
- `Grep` - `SKIP` reachability path present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (file exists, Import-PowerShellDataFile ×1, SKIP path; parse OK). Smoke on emulator-5554: Downloads reachable, SFTP 193.178.50.43:22 reachable (TCP), test_media SKIP (register-only); valid JSON, exit 0. Files: scripts/devtest/prerelease-configure.ps1 (New, ~115 LOC). Dev log recorded.

---

### Step 02.3 - Trigger resource import via intent-push

**Files:** `scripts/devtest/prerelease-configure.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add the import-trigger stage (research §6.2 intent-push path): `adb push` a resources XML (root `<media-resources>`, the configured picks - LOCAL `Downloads`, SFTP `SFTP`, one SMB register-only) to `/sdcard`, then `am start -a android.intent.action.VIEW -d file://<path> -t application/vnd.fms.resources+xml -n com.sza.fastmediasorter.debug/.ui.resourceimport.ResourceImportActivity`. Confirm the import activity launched via logcat. The confirm-dialog tap and per-resource listing verification are UI-driven and delegated to the skill scenario (Phase 05) - do not attempt the tap here. Record stage status (launched / failed) in the result object.

**Verification:**

- `Grep` - `ResourceImportActivity` referenced.
- `Grep` - `application/vnd.fms.resources+xml` referenced.
- `Grep` - `am start` and a `push` of the resources XML present (resource classes themselves verified in the config at 02.1).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (ResourceImportActivity, mime application/vnd.fms.resources+xml, am start, push; parse OK). Intent-push: adb push sza_resources.xml -> ACTION_VIEW at exported ResourceImportActivity. RISK noted in code: file:// read on scoped-storage devices must be validated in Phase 05 (fallback OWNER_TRIGGER UI). Confirm tap delegated to skill. Files: scripts/devtest/prerelease-configure.ps1 (+30 LOC). Dev log recorded.

---

### Step 02.4 - Apply adb-scriptable settings

**Files:** `scripts/devtest/prerelease-configure.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add the settings stage for the adb-applicable subset only. After research, the robust adb-scriptable setting is **language** via `adb shell cmd locale set-app-locales <pkg> --locales <loc>` (the system handles the restart); verify with `cmd locale get-app-locales`. All other settings - including theme - carry `Channel='ui'` and are delegated to the skill UI scenario (Phase 05); record them as `delegated-ui`, do not apply here. Run this stage before the import trigger so the locale restart does not interrupt the import dialog. Exit non-zero only when a required adb setting failed.

**Verification:**

- `Grep` - `cmd locale` referenced.
- `Grep` - settings stage iterates the config `Settings` block (Channel switch, `delegated-ui` for ui-channel entries).
- `Script` - `pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 -Json` parses against a prepared emulator.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (cmd locale ×4, config.Settings iterate + delegated-ui, parse OK). Live run on emulator-5554: ok=true, exit 0, language=ru applied, all ui-channel settings delegated, import-launch OK. Live run also surfaced and fixed a real bug in the 02.3 import stage: `am start -n <pkg>/.ui...` expands the leading dot against applicationId (`...debug`) → nonexistent class; corrected to FQCN `$CodePackage.ui.resourceimport.ResourceImportActivity` (code package has no .debug suffix). file:// import read still to be confirmed in Phase 05. Files: scripts/devtest/prerelease-configure.ps1 (+24 LOC, +FQCN fix). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 -Json` runs and emits valid JSON (live run on emulator-5554, ok=true, exit 0).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for both new files.

---

## Handoff Notes to Next Phase

Provides imported resources (with skip markers for unreachable endpoints) and applied settings, plus the run config consumed by Phase 05.

---

## Rollback Plan

Delete the two new files - no data migration or user-facing surface changed.
