# Phase 07 — Docs / catalog / build / device-gate cleanup

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Final hygiene pass: refresh the class catalog with VR-flavor metadata, run the strings localisation audit, confirm both target-flavor builds, and transition the spec to `BlockNeedUserTest` after the developer inserts the `Timber.d("S0245: …")` verification tag at the master-toggle flow entry. No `docs/FEATURES.md` change on Stage 0 (strategic §7 mandates).

---

## Prerequisites

- [ ] Phases 01..06 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto via `scan.ps1`) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto via `render.ps1`) | — |
| `dev/CHANGELOG.md` | Modified (auto via `add_to_dev_log.ps1`) | — |
| `dev/FUNCTIONALITY.log` | Modified (auto via `add_to_functionality_log.ps1` if Stage 0 ships a perceivable behaviour) | — |
| `PLAN/spec-catalog.jsonl` | Modified (auto via `update.ps1 -Status BlockNeedUserTest`) | — |

---

## Steps

### Step 07.1 — Catalog scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Refresh the auto-fields after all the new Kotlin classes landed:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Verify the new catalog rows for all new classes. For every flavor-only file, run `set.ps1` to record the source-set hint so future grep does not mistake them for `src/main/` code:
>
> ```powershell
> # vrStub (no-op) classes — invisible to vr / noLegal
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 `
>     -Path "src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEnvironmentDetector.kt" `
>     -Role "xr-stub" -Status "active" -NoFlavors "vr,noLegal"
> # Repeat for NoOpXrDetectionFacade, NoOpXrEntryGateway, NoOpXrModule
>
> # vr (real) classes — invisible to standard / lite / photos / legacy
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 `
>     -Path "src/vr/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetectorImpl.kt" `
>     -Role "xr-impl" -Status "active" -NoFlavors "standard,lite,photos,legacy"
> # Repeat for MasterTogglePreferences, XrDetectionFacadeImpl, XrEntryGatewayImpl, XrModule,
> # VrSettingsFragment, VrSettingsTabExtension, VrSettingsExtensionModule
> ```
>
> Re-render after `set` calls so the human-readable `.md` reflects updated metadata.

**Verification:**

- `Grep` — `XrEnvironmentDetectorImpl` appears in `dev/CATALOG/app_v2.jsonl` with a `noFlavors` field containing `standard,lite,photos,legacy`.
- `Grep` — `NoOpXrEnvironmentDetector` appears with `noFlavors` containing `vr,noLegal`.
- `dev/CATALOG/app_v2.md` lists every new class.

**Status:** `[ ]` not done

---

### Step 07.2 — Strings localisation audit

**Files:** —
**Depends on:** Phase 06 strings.

**Prompt for developer:**

> Run the locale parity check for the keys introduced in Phase 06:
>
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_tab_vr"
> ```
>
> Both must exit 0. If any locale is missing a key, fix in Phase 06 strings files (Steps 06.1 / 06.2 / 06.3) and re-run.

**Verification:**

- Both script invocations exit code 0.

**Status:** `[ ]` not done

---

### Step 07.3 — Build gate (target flavors)

**Files:** —
**Depends on:** Steps 07.1 / 07.2

**Prompt for developer:**

> Final build gate: `standard debug`, `vr debug`, `noLegal debug` all must compile and assemble. Use `/build` (not direct gradle). On any failure, return to the offending phase, fix, re-run from this step.
>
> ```text
> ./a.ps1 dq            # standard debug (quiet)
> /build vr debug
> /build noLegal debug
> ```
>
> APK content audit (sanity check):
>
> ```powershell
> Compress-Archive -ListAvailable  # ensure unzip-equivalent available
> # On any APK, inspect via `unzip -l` and grep for VrSettingsFragment.
> unzip -l app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_*.apk | findstr VrSettings
> # ↑ must be EMPTY (no match) — standard APK must not contain VR classes.
> unzip -l app_v2/build/outputs/apk/vr/debug/FastMediaSorter_vr_debug_*.apk | findstr VrSettings
> # ↑ must list VrSettingsFragment.class.
> ```

**Verification:**

- All three builds exit 0.
- `standard` APK: `unzip -l … | grep -i VrSettings` returns zero lines.
- `vr` APK: `unzip -l … | grep -i VrSettings` returns ≥ 1 line.
- `noLegal` APK: same as `vr` — `unzip -l … | grep -i VrSettings` returns ≥ 1 line.

**Status:** `[ ]` not done

---

### Step 07.4 — Spec transition to `BlockNeedUserTest`

**Files:** `app_v2/src/vr/java/.../XrDetectionFacadeImpl.kt` (add log tag), `PLAN/spec-catalog.jsonl`

**Depends on:** Step 07.3

**Prompt for developer:**

> Insert one `Timber.d("S0245: …")` debug verification tag at the entry of the changed flow per CLAUDE.md "Debug Verification Tags" rule. The chosen flow is `XrDetectionFacadeImpl.state()` because every consumer of the new contract hits it. Insert at the top of the function body — once per Stage 0:
>
> ```kotlin
> override fun state(): Flow<XrDetectionState> {
>     val env = detector.detect()
>     Timber.d("S0245: XrDetectionFacadeImpl.state() env=$env")
>     return preferences.enabled
>         .map { enabled -> fold(env, enabled) }
>         .distinctUntilChanged()
> }
> ```
>
> Then flip status:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0245 -Status BlockNeedUserTest
> pwsh -File scripts/add_to_dev_log.ps1 "PLAN/S0245_vr-settings-scaffold-stage0.md" "spec-tech" "Status -> BlockNeedUserTest; await Quest 3 device gate."
> ```
>
> Functionality log: Stage 0 surfaces no user-visible new capability per strategic §7 — **skip** `add_to_functionality_log.ps1`. (If `/spec-check` later disagrees, it will surface a `[FUNC_LOG MISSED]` line and the operator can fill manually.)

**Verification:**

- `Grep` — `Timber.d\(\"S0245:` matches exactly once across all `.kt` files.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0245 -Format json` → `"status":"BlockNeedUserTest"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` updated and committed alongside code.
- [ ] Both string audits exit 0.
- [ ] All three target-flavor builds pass.
- [ ] APK content audit confirms `vrStub` source-set isolation.
- [ ] One `Timber.d("S0245: …")` tag present; status = `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Hand off to operator for Quest 3 / Android XR emulator device test. After the operator confirms the `S0245:` tag sighting in logcat and tab visibility on the device, `/spec-check S0245` removes the tag and transitions to `Verified`.

---

## Rollback Plan

Roll back per-phase from 06 → 01 in reverse order. The status flip in 07.4 is reversed via `update.ps1 -Id S0245 -Status Tactical`.
