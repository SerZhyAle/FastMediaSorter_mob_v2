# Phase 04 - Docs, catalog, cleanup

**Strategic spec:** [`../S1161_landscape-settings-collapsed-groups-columns.md`](../S1161_landscape-settings-collapsed-groups-columns.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Close the ticket mechanically: catalog, capability inventory, gates, device-test probes.

---

## Prerequisites

- [x] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SettingsGroupColumnsManager.kt` | Modified | ≤ 120 |

---

## Steps

### Step 04.1 - Insert the device-test probe

**Files:** `BaseSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> This ticket ends in `BlockNeedUserTest`, so it must carry exactly one `Timber.d("S1161: ...")` probe
> at the entry point of the changed flow. One tag for the whole ticket, not one per phase: the gate
> counts a spec's probes across all `.kt`, and per-phase tags make the removal step ambiguous.
>
> Put it in `BaseSettingsFragment.onViewCreated` next to the `install` call rather than inside the
> manager: the question a device log has to answer is *which tab did or did not get a grid*, and the
> fragment is the only place that knows the tab's identity - the manager sees a `NestedScrollView`.
>
> Keep the line at or below 120 characters (CLAUDE.md Rule 19, detekt-clean-first).

**Verification:**

- `Grep` - `Timber.d("S1161:` matches exactly once across `app_v2/src/**/*.kt`.
- `Grep` - that line is at most 120 characters.

**Status:** `[x]` done

---

### Step 04.2 - Catalog + capability inventory

**Files:** `dev/CATALOG/app_v2.jsonl`, `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the class catalog (`scripts/catalog_sync.ps1 -Module app_v2`) - two new classes were
> added - and set `role`/`status` for `SettingsGroupsGridLayout` and `SettingsGroupColumnsManager` via
> `dev/CATALOG/scripts/set.ps1`.
>
> Record the capability in `docs/ALL_FEATURES.jsonl` through `scripts/all_features/add.ps1`. Flavors:
> read them off the actual gate - this change sits in `src/main` with no `BuildConfig` guard, so it
> ships in every flavor. Do not copy a sibling record's flavor list.

**Verification:**

- `Grep` - `SettingsGroupsGridLayout` and `SettingsGroupColumnsManager` both appear in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - a record mentioning `S1161` exists in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit code 0.

**Status:** `[x]` done

---

### Step 04.3 - Gates and closure

**Files:** - (validation only)
**Depends on:** Step 04.2

**Prompt for developer:**

> Run mechanical closure through the facade with `-ScopeToFile` (the tree carries other tickets' WIP):
> `scripts/post-change.ps1 -ChangeType Mixed -Module app_v2`.
>
> Two gates deserve attention rather than a glance at the exit code. **Rule 22 (settings docs sync):**
> no setting changed its presence, behaviour, or naming here - only the visual arrangement of group
> cards - so the manifest should come back unchanged; if the gate reports drift, the drift belongs to
> another ticket's in-flight edit, not to this one, and must not be silently regenerated into this
> change. **Ticket-log audit:** it fails while a `S1161:` probe exists and the status is not yet
> `BlockNeedUserTest`, so flip the status first, then run the gates.
>
> Close via `close-and-log.ps1 -Status BlockNeedUserTest -StatusNote` describing what to check on
> device: landscape two-column collapsed groups on every settings tab, expanded group full width,
> rotation with settings open, collapse state surviving restart, D-pad traversal down the left column.

**Verification:**

- `scripts/post-change.ps1` - exit code 0.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1161 -Format json` - status is `BlockNeedUserTest` and `statusNote` is non-empty.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry for every file this ticket touched.
- [x] `docs/ALL_FEATURES.jsonl` carries the capability record.
- [x] Ticket status is `BlockNeedUserTest` with a status note.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Device verification runs via `/spec-test-device S1161`
or the batch `/spec-sweep`; `/spec-check` then removes the probe on the transition out of
`BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commits - no data migration or user-facing surface changed beyond layout arrangement.
