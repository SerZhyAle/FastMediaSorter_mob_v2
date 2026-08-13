# Phase 05 - Surface sweep, docs & catalog cleanup

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Prove no system-Share show-point was missed (the dominant risk), then record the user-visible feature and refresh the catalog. No new code beyond what the sweep might surface.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Appended | - |
| `docs/FEATURES_RU.md` | Appended | - |
| `docs/FEATURES_UK.md` | Appended | - |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Regenerated | - |
| `dev/CHANGELOG.md` (via script) | Appended | - |

---

## Steps

### Step 05.1 - Surface-completeness sweep

**Files:** - (audit only)
**Depends on:** - start of phase

**Prompt for developer:**

> Run a repo-wide grep over `app_v2/src/main` for every system-Share show-point pattern and confirm each is behind the `system_share` gate (or is an out-of-scope receiver / distinct target). Patterns to sweep:
>
> - `btnShareCmd.isVisible` - must never be assigned literal `true`; every assignment reads the flag.
> - `btnShare.isVisible` (browse toolbar) - flag ANDed in.
> - `add(PlayerCommand.SHARE)` - must be inside a flag guard in both the player planner and browse extended-command builder.
> - `R.id.menu_share` / `menu_share` - dispatch only; visibility is driven by the gated planner, no separate always-show. Confirm there is no ungated direct `menu_share` visibility set.
>
> For each hit, classify: gated (OK), or out-of-scope (incoming `ACTION_SEND` receiver, Office fallback-dialog share, drawing/print export, Google Lens / Telegram / Keep). Record the classification table in the Step Log. Any in-scope hit that is not gated is a missed surface - gate it before proceeding (extend the relevant Phase 02-04 file).

**Verification:**

- `Grep` - zero `btnShareCmd.isVisible = true` (literal) across `app_v2/src/main`.
- `Grep` - zero unconditional `add(PlayerCommand.SHARE)` across `app_v2/src/main`.
- Step Log contains the classification of every remaining `ACTION_SEND` / Share hit as gated or out-of-scope.

**Status:** `[ ]` not done

---

### Step 05.2 - FEATURES (EN/RU/UK) + dev-log

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the user-visible feature to `docs/FEATURES.md` + `_RU.md` + `_UK.md` in lockstep: a "Allow Share" toggle in the player settings "Send file to.." group, default ON, hides/shows the system Share command everywhere. Keep wording factual (COMMUNICATION_POLICY). Then run `.\scripts\add_to_dev_log.ps1` for every file touched in Phases 01-04 (do not hand-edit `dev/CHANGELOG.md`).

**Verification:**

- `Grep` - the feature line is present in all three FEATURES files (EN/RU/UK parity).
- `Grep` - each modified Phase 01-04 file name appears in a `dev/CHANGELOG.md` line.

**Status:** `[ ]` not done

---

### Step 05.3 - Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. No new public classes are introduced by this ticket (only a multibinding provider + gates in existing classes), so no `set.ps1` role assignment is expected - confirm the regenerate is clean.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Surface-completeness sweep recorded zero ungated in-scope show-points.
- [ ] FEATURES EN/RU/UK updated in lockstep.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0445`, then a device test (toggle OFF/ON, verify Share visibility across player, browse, standalone hosts) recorded against the BlockNeedUserTest transition.

---

## Rollback Plan

Docs are append-only; catalog is regenerable. No rollback needed for this phase.
