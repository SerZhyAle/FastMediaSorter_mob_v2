# Phase 03 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0020_bugfix-vr-panel-swapchain-session-race.md`](../S0020_bugfix-vr-panel-swapchain-session-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Final phase. Refresh `dev/CATALOG/app_v2.{jsonl,md}` (no new public Kotlin API; native is out of scope for the catalog scanner); ensure CHANGELOG entries cover all modified files; trigger `/spec-check S0020` to flip strategic status to `Verified`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.md` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified (via add_to_dev_log.ps1) | n/a |

> `docs/FEATURES.md` and locale mirrors are NOT modified — strategic §8: no user-facing change.

---

## Steps

### Step 03.1 — Catalog refresh

**Files:** `dev/CATALOG/app_v2.{jsonl,md}`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. The two Kotlin files modified in Phase 02 (`OpenXrSessionManager.kt`, `VrInteractivePanelRenderer.kt`) live under `app_v2/src/vr/`, which the scanner intentionally skips (flavor source set is not catalogued). The native `OpenXrNative.cpp` is C++ and also outside the scanner's scope. Catalog will likely have no diff — that is acceptable.

**Verification:**

- `PowerShell` — scan command exits with status 0 and prints a `Scanned module 'app_v2': N files` summary.
- `PowerShell` — render command exits with status 0 and prints `Rendered N records`.

**Status:** `[x]` done

---

### Step 03.2 — Verify dev-log coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has 2026-04-28-or-later entries for: `OpenXrNative.cpp`, `OpenXrSessionManager.kt`, `VrInteractivePanelRenderer.kt`. Add missing entries via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `Grep` — `OpenXrNative\.cpp` matches at least 1 time on a 2026-04-28-or-later line in `dev/CHANGELOG.md`.
- `Grep` — `OpenXrSessionManager` matches at least 1 time on a 2026-04-28-or-later line.
- `Grep` — `VrInteractivePanelRenderer` matches at least 1 time on a 2026-04-28-or-later line.

**Status:** `[x]` done

---

### Step 03.3 — Run `/spec-check S0020`

**Files:** new file `PLAN/S0020_bugfix-vr-panel-swapchain-session-race__audit_<YYYY-MM-DD>.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Invoke `/spec-check S0020`. Expected outcome `Verified` — all §11 criteria are observable from the new log markers + on-device test (the strict §11.5 on-device-verification is `MANUAL` until the user runs the build).

**Verification:**

- `Glob` — `PLAN/S0020_bugfix-vr-panel-swapchain-session-race__audit_*.md` exists.
- `PowerShell` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0020 -Format json` returns status `Verified` or `Partial`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `/spec-check S0020` produced an audit report.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.

---

## Rollback Plan

Catalog refresh and dev log entries are append-only — rollback unnecessary.
