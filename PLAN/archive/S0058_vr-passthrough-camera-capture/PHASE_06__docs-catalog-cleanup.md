# Phase 06 — Docs + Catalog Cleanup

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-05

---

## Objective

Update `docs/FEATURES.md` + trilingual mirrors with the new passthrough-capture bullet; regenerate the app_v2 CATALOG; add trailing dev-log entries. After this phase `/spec-check S0058` should return `Verified`.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] All phases 01–05 show ✅ Done in INDEX.md.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +3 lines |
| `docs/FEATURES_RU.md` | Modified | +3 lines |
| `docs/FEATURES_UK.md` | Modified | +3 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 06.1 — Add passthrough-capture bullet to `FEATURES.md` + mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In each features file, locate the **VR Features** section. Add a new bullet exactly as specified below.
>
> **`docs/FEATURES.md`** (English):
> ```markdown
> - **Passthrough Snapshot (Quest 3, Horizon OS v74+).** The camera button in Browse captures a single JPEG from the front passthrough cameras and saves it to the current local resource. Requires `horizonos.permission.HEADSET_CAMERA`; silently hidden on devices without Passthrough Camera API support. Captured frames are processed locally only — no automatic upload or telemetry.
> ```
>
> **`docs/FEATURES_RU.md`** (Russian):
> ```markdown
> - **Захват passthrough-кадра (Quest 3, Horizon OS v74+).** Кнопка камеры в Browse делает одиночный JPEG с фронтальных passthrough-камер и сохраняет его в текущий локальный ресурс. Требует разрешения `horizonos.permission.HEADSET_CAMERA`; автоматически скрывается на устройствах без поддержки Passthrough Camera API. Захваченные кадры обрабатываются только локально — никакой автоматической загрузки или телеметрии.
> ```
>
> **`docs/FEATURES_UK.md`** (Ukrainian):
> ```markdown
> - **Захоплення passthrough-кадру (Quest 3, Horizon OS v74+).** Кнопка камери в Browse робить одиночний JPEG із фронтальних passthrough-камер і зберігає його в поточний локальний ресурс. Вимагає дозволу `horizonos.permission.HEADSET_CAMERA`; автоматично прихована на пристроях без підтримки Passthrough Camera API. Захоплені кадри обробляються лише локально — без автоматичного завантаження або телеметрії.
> ```

**Verification:**

- `Grep` — `Passthrough Snapshot` present in `docs/FEATURES.md`.
- `Grep` — `Захват passthrough` present in `docs/FEATURES_RU.md`.
- `Grep` — `Захоплення passthrough` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md (modified). Dev log recorded.

---

### Step 06.2 — Regenerate app_v2 CATALOG

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run catalog scan and render:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File "C:/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For any newly created `.kt` files (`BrowsePassthroughCaptureProvider.kt`, `BrowsePassthroughOptionalModule.kt`, `VrBrowsePassthroughCaptureManager.kt`), set `role` and `status` via `set.ps1` if not auto-detected:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "app_v2/src/main/.../BrowsePassthroughCaptureProvider.kt" -Role "Interface for VR passthrough camera capture provider" -Status "active"
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "app_v2/src/main/.../BrowsePassthroughOptionalModule.kt" -Role "Hilt @BindsOptionalOf module for passthrough provider" -Status "active"
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "app_v2/src/vr/.../VrBrowsePassthroughCaptureManager.kt" -Role "Camera2 passthrough capture provider for Quest 3 VR builds" -Status "active"
> ```

**Verification:**

- `Grep` — `VrBrowsePassthroughCaptureManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `BrowsePassthroughCaptureProvider` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Catalog scan 924 files; roles set for 3 new classes. Dev log recorded.

---

### Step 06.3 — Final dev log + spec status → Implemented

**Files:** `dev/CHANGELOG.md` (via script), `PLAN/S0058_vr-passthrough-camera-capture.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add dev log entries for any files not yet logged (docs and catalog):
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0058" "Add passthrough snapshot bullet to VR Features"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0058" "Add passthrough snapshot bullet to VR Features (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0058" "Add passthrough snapshot bullet to VR Features (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0058" "Catalog regen after passthrough capture classes added"
> ```
>
> Then advance spec status to `Implemented`:
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0058 -Status Implemented
> ```
>
> Update `INDEX.md`: flip `Status: Not started` to `Status: Done`, set `Phases: 6 / 6 done`.
>
> Run `/spec-check S0058` to verify implementation.

**Verification:**

- `Grep` — `S0058` present in `dev/CHANGELOG.md` (at least one entry).
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0058 -Format json` shows `"status":"Implemented"` or `"Verified"`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. S0058 status → Implemented. INDEX flipped Done, 6/6 phases. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` contain passthrough snapshot bullet.
- [ ] `dev/CATALOG/app_v2.jsonl` contains all three new/modified classes.
- [ ] `dev/CHANGELOG.md` has entries for all changed files.
- [ ] `/spec-check S0058` run; result recorded in strategic spec `## Last Audit` block.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert docs + catalog changes only — no code impact. Re-run scan/render to restore previous catalog state.
