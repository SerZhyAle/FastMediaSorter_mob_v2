# Phase 06 — Docs / catalog cleanup + S0024 unblock

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01–05
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Final cleanup: regenerate the class catalog with the new `OpenXr*` headers/cpp split (cpp files are not catalogued — only the Kotlin Manager additions matter) and the three new Kotlin Managers, ensure dev log lines are present for every modified file, confirm both build flavors are green, and emit the explicit unblock command for S0024. No user-facing strings ship — strategic §8 says feature docs are unaffected.

---

## Prerequisites

- [ ] Phase 05 ✅ Done; smoke test on Quest 3 either passed or formally deferred via `BlockNeedUserTest`.
- [ ] Working tree clean except for catalog/dev-log changes about to be made.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |
| `dev/CHANGELOG.md` | Appended (via script) | — |
| `PLAN/spec-catalog.jsonl` | Modified via `update.ps1 -Status Verified` for S0033, then `-Status "In Progress"` for S0024 | — |

---

## Steps

### Step 06.1 — Regenerate Kotlin catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Confirm new entries appear for `VrRenderPipelineManager`, `VrSessionLifecycleManager`, `VrPlayerCommandRouter`. Use `set.ps1` (see `dev/CATALOG/README.md`) to fill `role` + `status` for the three new classes — role examples: `VrRenderPipelineManager` → `Manages VR render pipeline init/release and per-frame dispatch`; `VrSessionLifecycleManager` → `Owns immersive session route decisions and fallbacks`; `VrPlayerCommandRouter` → `Routes controller / hand-tracking commands to the player`.

**Verification:**

- `Grep` — `VrRenderPipelineManager` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VrSessionLifecycleManager` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VrPlayerCommandRouter` matches in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 06.2 — Dev log audit

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> For every file in this spec's Phase 01..05 "Files Touched" tables, confirm there is at least one `dev/CHANGELOG.md` entry recorded via `.\scripts\add_to_dev_log.ps1`. Add missing entries — never edit `dev/CHANGELOG.md` directly. Typical missing entries are catalog regen (`dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`) and the catalog updates from this very phase.

**Verification:**

- `Grep` — every `OpenXr*.cpp` and `OpenXr*.h` filename appears at least once in `dev/CHANGELOG.md`.
- `Grep` — every `Vr*Manager.kt` and `VrPlayerCommandRouter.kt` filename appears at least once.

**Status:** `[ ]` not done

---

### Step 06.3 — Final build gate (both flavors)

**Files:** —
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `/build vr debug` and `/build standard debug` one last time on a clean working tree. Resolve any lint regression in files this spec touched (CLAUDE.md rule 7).

**Verification:**

- `assembleVrDebug` PASS.
- `assembleStandardDebug` PASS.
- `Grep` — `TODO(phase-0[1-6])` returns zero hits across the entire repo.

**Status:** `[ ]` not done

---

### Step 06.4 — Mark S0033 Verified and unblock S0024

**Files:** `PLAN/spec-catalog.jsonl` (via CLI only)
**Depends on:** Step 06.3

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0033 -Status Verified
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0024 -Status "In Progress"
> ```
>
> Append a dev-log entry for the unblock:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "PLAN/S0024_vr-hud-ray-input.md" "spec-tech" "S0024 unblocked by S0033 landing; ready for /spec-dev S0024 Phase 02"
> ```
>
> Notify the user: S0024 Phase 02 is now ready to resume — next command is `/spec-dev S0024`.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0033 -Format json` returns `"status":"Verified"`.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0024 -Format json` returns `"status":"In Progress"`.
- `Grep` — the unblock dev-log line appears in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-0[1-6])` returns zero hits.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with three new Manager entries.
- [ ] S0033 status `Verified`; S0024 status `In Progress`.
- [ ] User notified about resume path.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog/dev-log changes are append-only; rollback is not meaningful for this phase. If S0033 needs to be reopened (e.g. after on-device smoke regression discovered), use `update.ps1 -Status Broken` and follow up with `/spec-fix S0033`.
