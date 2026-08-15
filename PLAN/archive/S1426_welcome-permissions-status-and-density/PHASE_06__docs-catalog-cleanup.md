# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Re-render every generated inventory the ticket invalidated, journal the change, and close it mechanically.

---

## Prerequisites

- [ ] Phases 01 to 05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/icons/icon-inventory.json` | Regenerated | - |
| `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md` | Regenerated if the gate asks | - |
| `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended via script | - |

---

## Steps

### Step 06.1 - Re-render the icon inventory

**Files:** `docs/icons/icon-inventory.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the icon inventory so the three new state drawables are registered, then run its gate.

**Why:**

Phase 04 adds three drawables, and the icon-inventory gate fails a closure whose assets are not registered.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` exits 0.
- The three new drawable names are either present in `docs/icons/icon-inventory.json` or shown by the gate to be out of its scope.

**Status:** `[x]` done

> Planning correction, recorded during execution. The step's premise was wrong: the inventory tracks icons
> declared by the surface registries, not every file under `drawable/`. The three state icons are referenced
> straight from the row adapter and belong to no registry, so they neither appear in the inventory nor count
> as orphans. Gate result, run 2026-08-06: `PASS - 84 vector svg(s) present, no orphans, legend fresh,
> locales in parity`, exit 0, with zero hits for the three names in the inventory. Nothing was regenerated -
> and deliberately so, because the repository-wide freshness check is currently red on an unrelated
> player-command entry (`OFFICE_TEXT_SETTINGS` / `ic_book`) belonging to another ticket's in-flight work, and
> regenerating here would have pulled that change into this ticket's closure.

---

### Step 06.2 - Settle the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the settings doc-sync gate. If it reports drift, regenerate the manifest and reference and update the annotation file; if it passes untouched, record that the permissions rows are not settings entries and no regeneration was needed. Do not hand-edit either generated file.

**Why:**

CLAUDE.md Rule 22 binds any change to a setting's presence, behaviour, position or naming to a manifest regeneration, and this ticket changes a screen that lives inside the settings tree.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- The outcome - regenerated or confirmed unaffected - is recorded in this step body.

**Status:** `[x]` done

---

### Step 06.3 - Resync the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once, then set the role and status of the classes this ticket introduced with `dev/CATALOG/scripts/set.ps1`.

**Why:**

Phases 01 to 03 add three classes and delete two, and the catalog is the routing index every later research pass queries before grepping.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*PermissionRequestMarker*"` returns the new records.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ContextualRationale*"` returns nothing.

**Status:** `[x]` done

---

### Step 06.4 - Close the ticket mechanically

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and adding `-ScopeToFile`, with `-ChangeType Mixed`. Read the verdict: only the bare word PASS closes the phase. Do not write a FEATURES entry - strategic §8 states there is no user-facing capability change.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade, and `-ScopeToFile` is what keeps other tickets' in-flight work in this shared tree from failing a gate that belongs to this one.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `Grep` - `dev/CHANGELOG.md` carries exactly one new entry for this ticket.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit. Every file here is generated or appended, so a revert plus a re-run of the generators restores a consistent state.
