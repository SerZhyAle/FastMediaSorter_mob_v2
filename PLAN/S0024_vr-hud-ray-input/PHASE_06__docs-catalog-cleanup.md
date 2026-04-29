# Phase 06 — Docs / catalog / changelog cleanup

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Trilingual user-facing documentation, full catalogue regeneration, dev changelog finalisation, and journal status flip via `update.ps1`.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.
- [ ] All on-device smoke-tests recorded.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 06.1 — Update `docs/FEATURES*` (EN / RU / UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In each of the three feature docs, in the VR section, add one bullet (strategic §8). Texts:
> - EN: "In the immersive HUD, controller aim-ray and hand-tracking pinch click HUD elements; subtle hover highlight under the ray, audio cue on click."
> - RU: "В иммерсивном HUD луч контроллера и pinch hand-tracking кликают по элементам HUD: лёгкая подсветка под лучом и звуковой отклик при клике."
> - UK: "В імерсивному HUD промінь контролера та pinch hand-tracking клікають по елементах HUD: легка підсвітка під променем та звуковий відгук при кліку."
> Use `..` (two dots) instead of `...` and use `ё`/`Ё` in the Russian text where grammatically correct (the bullet above is already compliant — copy verbatim).

**Verification:**

- `Grep` — each language-specific phrase ("controller aim-ray and hand-tracking pinch", "луч контроллера и pinch", "промінь контролера та pinch") matches once in its respective file.
- `Grep` — `\.\.\.` (three-dot ellipsis) does not match within the new bullets in any of the three files.

**Status:** `[ ]` not done

---

### Step 06.2 — Regenerate the catalogue

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` to refresh the auto-fields, then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` to regenerate the human-readable markdown. For each new public class introduced by S0024 (`VrHudElement`, `VrHudElementRegistry`, `VrHudHitTester`, `VrHudHoverState`, `VrHudInputDispatcher`, `VrHudInteractionCallback`), confirm an entry exists in the `.jsonl`; fill `role` + `status` via `pwsh -File dev/CATALOG/scripts/set.ps1 ..` for any class missing manual fields.

**Verification:**

- `Grep` — each new class name (`VrHudElementRegistry`, `VrHudHitTester`, `VrHudHoverState`, `VrHudInputDispatcher`, `VrHudInteractionCallback`) matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — same names match in `dev/CATALOG/app_v2.md`.
- `git status` — both catalogue files appear modified.

**Status:** `[ ]` not done

---

### Step 06.3 — Append final dev-log entries

**Files:** `dev/CHANGELOG.md` (via script — never edited directly)
**Depends on:** Step 06.2

**Prompt for developer:**

> For every file modified across Phases 01-05 (and Step 06.1 / 06.2 above), run `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "<description>"`. The earlier phases already logged their own entries; this step exists to catch anything missed and to log the catalogue + docs writes. Final entry should reference the strategic spec id `S0024`.

**Verification:**

- `Grep` — `S0024` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — every file from Phases 01-05 "Files Touched" tables appears in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 06.4 — Flip journal status to Implemented

**Files:** `PLAN/spec-catalog.jsonl` (via CLI — never edited directly)
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0024 -Status Implemented`. After this, hand off to `/spec-check S0024`, which will flip the status to `Verified` (or `Partial` / `Broken` depending on audit findings).

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0024 -Format json` — output contains `"status":"Implemented"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU` + `_UK` contain the trilingual HUD ray-input bullet.
- [ ] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated and committed.
- [ ] `dev/CHANGELOG.md` references `S0024` and every touched file.
- [ ] Journal `S0024` status reads `Implemented`; `/spec-check S0024` ready to run.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and catalogue changes are reversible by `git revert`. Journal status can be moved back to `In Progress` via `update.ps1 -Status "In Progress"` if `/spec-check` finds blocking issues.
