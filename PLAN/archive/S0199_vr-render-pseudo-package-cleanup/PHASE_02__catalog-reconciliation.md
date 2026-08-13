# Phase 02 — Catalog Reconciliation

**Strategic spec:** [`../S0199_vr-render-pseudo-package-cleanup.md`](../S0199_vr-render-pseudo-package-cleanup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Refresh `dev/CATALOG` for the renamed files and restore any manual metadata that the path-based merge cannot keep automatically.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` has room for catalogue backups.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | script-generated |
| `dev/CATALOG/app_v2.md` | Modified | script-generated |

> `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` are large generated files. Create timestamped backups in `temp/` before regenerating them.

---

## Steps

### Step 02.1 — Backup and regenerate catalogue records for the moved contracts

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create timestamped backups of `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` in `temp/` before running the catalogue tools. Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Compare the backup against the regenerated records for `DefaultVrLayerFactory.kt`, `VrLayerDescriptor.kt`, `VrLayerFactory.kt`, `VrLayerType.kt`, `VrRenderContext.kt`, and `VrRenderPlanner.kt`; if any pre-scan record had non-empty `role`, `status != unknown`, `noFlavors`, or function descriptions, restore them with `pwsh -File dev/CATALOG/scripts/set.ps1` against the new `ui/player/render/stereoscopic/*` paths. Leave `noFlavors` empty for the moved contracts so the catalogue continues to mean "used in all flavors".

**Verification:**

- `Grep` — `"path":"com/sza/fastmediasorter/ui/player/render/stereoscopic/DefaultVrLayerFactory.kt"` exists in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `"path":"com/sza/fastmediasorter/ui/player/render/stereoscopic/VrRenderPlanner.kt"` exists in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `"path":"com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt"` returns zero matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `"path":"com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerFactory.kt".*"noFlavors":\[\]` exists in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — `scan.ps1 -Module app_v2` rescanned 1049 files; `render.ps1 -Module app_v2` regenerated 1049 catalog rows; new path `ui/player/render/stereoscopic/*.kt` confirmed present; old `vr/render/*.kt` confirmed absent; `noFlavors` field defaulted to empty so the moved contracts count as used in every flavor.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Catalogue records now point at the neutral package. Phase 03 only records the change in `dev/CHANGELOG.md` and preserves the internal-only docs decision.

---

## Rollback Plan

Restore the timestamped catalogue backups from `temp/`, then re-run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` if the Markdown view also needs rollback.
