# Phase 04 — Docs + Catalog Cleanup

**Strategic spec:** [`../spec_vr-xr-cold-start.md`](../spec_vr-xr-cold-start.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03
**Blocks:** nothing — final phase
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Regenerate the VR module catalog, add dev-log entries for all modified files, and update feature docs if Phase 03 Branch A produced a user-visible performance improvement.

---

## Prerequisites

- [ ] All prior phases are ✅ Done.
- [ ] Working tree contains all phase commits (or working tree is clean on the feature branch).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (scan) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (render) | n/a |
| `docs/FEATURES.md` + `_RU.md` + `_UK.md` | Modified only if Branch A produced user-visible change | ≤ 3 bullets added |

---

## Steps

### Step 04.1 — Regenerate catalog for app_v2 module

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Confirm both scripts complete without error.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has a modification timestamp newer than Phase 01 completion.
- `Glob` — `dev/CATALOG/app_v2.md` exists.

**Status:** `[ ]` not done

---

### Step 04.2 — Add dev-log entries for all modified source files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once for every `.kt` file touched across Phase 01 and Phase 03.
> At minimum:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" "spec-dev" "Phase 01: add VR_PERF timing markers for cold-start stages"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "spec-dev" "Phase 01: add VR_PERF timing fields + first-frame marker"
> ```
>
> If Phase 03 Branch A modified additional files, add one `add_to_dev_log.ps1` call per file.

**Verification:**

- `Grep "VR_PERF\|vr-xr-cold-start"` in `dev/CHANGELOG.md` returns at least 2 matches.

**Status:** `[ ]` not done

---

### Step 04.3 — Update FEATURES docs (Branch A only)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> If Phase 03 Branch A produced a measurable reduction in cold-start latency that users can perceive:
> add a brief bullet to the VR section in all three files, e.g.:
> - EN: `- VR cold-start latency reduced: HUD and panel swapchain allocation deferred to after first frame.`
> - RU: `- Снижена задержка холодного старта VR: выделение swapchain HUD и панели перенесено после первого кадра.`
> - UK: `- Зменшено затримку холодного старту VR: виділення swapchain HUD і панелі перенесено після першого кадру.`
>
> If Phase 03 Branch B (Backlog / Won't-fix-now), skip this step.

**Verification:**

- If Branch A: `Grep "cold-start"` in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` returns 1 match in each file.
- If Branch B: step is `[⏭️ Skipped]` — no change needed.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done` or `[⏭️ Skipped]`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` are up to date.
- [ ] `dev/CHANGELOG.md` entries present for all modified source files.
- [ ] Strategic spec `Status:` is either `Verified` (after `/spec-check`) or `Backlog / Won't-fix-now`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert catalog regen commit if it introduces unexpected diff. Feature docs bullet is safe to revert without code impact.
