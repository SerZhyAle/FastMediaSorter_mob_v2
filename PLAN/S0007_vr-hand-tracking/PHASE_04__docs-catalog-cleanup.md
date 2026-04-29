# Phase 04 · docs-catalog-cleanup

**Spec:** S0007 · vr-hand-tracking  
**Phase:** 04 / 04  
**Status:** ⬜ Not started  
**Depends on:** Phase 03 ✅

---

## Objective

Complete all mandatory post-implementation documentation and housekeeping so S0007 can be
closed as `Implemented`:

1. Update feature inventory (`docs/FEATURES.md`, `_RU.md`, `_UK.md`).
2. Regenerate the dev/CATALOG for the VR module.
3. Log all remaining changelog entries.
4. Flip spec catalog status to `Implemented`.
5. Mark INDEX.md phases 01–04 as Done.

---

## Execution Steps

### A. FEATURES.md update

Add a bullet under the **VR Player** section in all three docs.

#### `docs/FEATURES.md` (EN) — add after existing VR bullet list

```
- Hand tracking: aim ray cursor, pinch-to-click, thumb microgestures (seek/volume), double-pinch play/pause; cursor highlights interactive elements with colour + audio feedback
```

#### `docs/FEATURES_RU.md` (RU) — добавить

```
- Управление жестами рук: луч прицеливания, щипок для нажатия, жесты большим пальцем (перемотка/громкость), двойной щипок — пауза/воспроизведение; курсор подсвечивает интерактивные элементы цветом и звуком
```

#### `docs/FEATURES_UK.md` (UK) — додати

```
- Керування жестами рук: промінь прицілювання, щипок для натискання, жести великим пальцем (перемотування/гучність), подвійний щипок — пауза/відтворення; курсор підсвічує інтерактивні елементи кольором і звуком
```

---

### B. dev/CATALOG regeneration

Run after every `.kt` file change from Phases 02–03:

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

Commit the updated `dev/CATALOG/app_v2.jsonl` + `app_v2.md` together with code.

---

### C. Dev changelog entries for phase files

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/S0007_vr-hand-tracking/INDEX.md" "S0007 tactical plan" "spec-tech: created 4-phase tactical plan for VR Hand Tracking"
.\scripts\add_to_dev_log.ps1 "PLAN/S0007_vr-hand-tracking/PHASE_01__verify-layer-e.md" "S0007 Phase 01" "spec-tech: verification checklist for Layer E"
.\scripts\add_to_dev_log.ps1 "PLAN/S0007_vr-hand-tracking/PHASE_02__hover-visual-feedback.md" "S0007 Phase 02" "spec-tech: hover cursor highlight design"
.\scripts\add_to_dev_log.ps1 "PLAN/S0007_vr-hand-tracking/PHASE_03__hover-click-audio.md" "S0007 Phase 03" "spec-tech: hover + pinch-complete audio SFX design"
.\scripts\add_to_dev_log.ps1 "PLAN/S0007_vr-hand-tracking/PHASE_04__docs-catalog-cleanup.md" "S0007 Phase 04" "spec-tech: docs/catalog cleanup phase"
```

---

### D. Spec catalog → Implemented

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0007 -Status Implemented
```

---

### E. Update strategic spec status

In `PLAN/S0007_vr-hand-tracking.md`:

- Change `**Status:** Tactical` → `**Status:** Implemented`

---

### F. Update INDEX.md

Mark all phases ✅ Done in `PLAN/S0007_vr-hand-tracking/INDEX.md` Phase Graph table.

---

## Execution Checklist

```
[ ] A. docs/FEATURES.md — add hand tracking bullet (EN)
[ ] A. docs/FEATURES_RU.md — add hand tracking bullet (RU)
[ ] A. docs/FEATURES_UK.md — add hand tracking bullet (UK)
[ ] B. pwsh dev/CATALOG/scripts/scan.ps1 -Module app_v2
[ ] B. pwsh dev/CATALOG/scripts/render.ps1 -Module app_v2
[ ] C. Run dev log commands for phase files (see above)
[ ] C. Run dev log for FEATURES docs:
       .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "VR Player section" "S0007: hand tracking feature bullet (EN)"
       .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "VR Player section" "S0007: hand tracking feature bullet (RU)"
       .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "VR Player section" "S0007: hand tracking feature bullet (UK)"
[ ] D. pwsh scripts/spec_catalog/update.ps1 -Id S0007 -Status Implemented
[ ] E. Update PLAN/S0007_vr-hand-tracking.md Status → Implemented
[ ] F. Update INDEX.md Phase Graph all ✅ Done
[ ] G. Mark this phase Done.
```

---

## Verification Predicates

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0007 -Format json` returns `"status":"Implemented"`.
- FEATURES.md × 3 contain the hand tracking bullet.
- `dev/CATALOG/app_v2.md` timestamp is ≥ the last `.kt` file modification.

---

## Status

**Done:** ⬜
