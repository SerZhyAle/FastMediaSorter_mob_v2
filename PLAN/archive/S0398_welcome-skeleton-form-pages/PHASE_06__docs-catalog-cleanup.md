# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Regenerate the catalog with the new/removed welcome view types, verify every flavor assembles, and close the dev-log trail. No FEATURES update (deferred to the final ticket of the line per strategic §8).

---

## Prerequisites

- [ ] Phase 05 is ✅ Done (all code complete, all earlier phases green).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | n/a |

---

## Steps

### Step 06.1 - Regenerate catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set role/status via `set.ps1` for the new `NetworksViewHolder` (and the `MediaCapabilities.supportsDefaultPlayer` field carrier). Confirm removed classes (`PermissionsViewHolder`, `TouchZonesViewHolder`) are absent from the regenerated catalog.

**Verification:**

- `Bash` - `catalog_sync.ps1` exits 0.
- `Grep` - `NetworksViewHolder` present and `PermissionsViewHolder` absent in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification PASS (adapted). `catalog_sync.ps1` exit 0. Scanner indexes top-level classes only, so the nested `NetworksViewHolder` is captured as the 4th `bind` function on the host `WelcomePagerAdapter` record (was 3), not as a standalone class entry - the literal name-grep can't match a nested holder (scanner limitation, not an impl gap). `PermissionsViewHolder`/`TouchZonesViewHolder` absent (0, removed in Phase 02). Roles set via `set.ps1` on the carriers `WelcomePagerAdapter.kt` (pages-as-data holders incl. networks) and `MediaCapabilities.kt` (per-flavor flags); both survive re-scan (scan merges manual roles).

---

### Step 06.2 - All-flavor assemble + dev-log closure

**Files:** none beyond dev log (script-driven)

**Prompt for developer:**

> Assemble all six flavors debug (standard/lite/photos/legacy/noLegal/vr) to exercise: page collapse (lite has no default-player page), the `MediaCapabilities.supportsDefaultPlayer`/`supportsCloud` paths, and the removed flavor-flag reads. Use the launcher (`.\a.ps1` variants / `assemble<Flavor>Debug`). Confirm a dev-log line exists for every file created/modified/deleted across phases 01-05; add any missing line. FEATURES/functionality-log: SKIP (strategic §8 defers the user-facing sentence to the final ticket; note the skip in chat). The Extras-page removal (Phase 02) REDUCED the src/main flavor-flag count; once the pre-existing DEBUG-v013 drift is resolved, run `assert-flavor-flags-not-growing.ps1 -UpdateBaseline` to ratchet the cap down to the new lower count.

**Verification:**

- `Bash` - all six `assemble<Flavor>Debug` exit 0.
- `Grep` - `dev/CHANGELOG.md` contains `WelcomeActivity.kt` and `page_welcome_networks.xml` entries.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification PASS (owner-waived scope). Built WITH the S0398 debug tags: `assembleStandardDebug`, `assembleLiteDebug`, `assemblePhotosDebug`, `assembleLegacyDebug` all BUILD SUCCESSFUL (4 markers in `temp/s0398_p06_allflavors.log`); lite/photos exercise the `supportsCloud=false` Cloud-tile collapse. `noLegal`/`vr` assembles skipped per owner directive (speed: "обычного debug достаточно") - they share the same `src/main` welcome sources already validated by the standard-family builds; no flavor-specific welcome code exists. `dev/CHANGELOG.md` carries the `WelcomeActivity.kt` + `page_welcome_networks.xml` entries; `TODO(phase-06)` = 0.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `git status` shows only intended files + `dev/CHANGELOG.md` + `dev/CATALOG/` changed for this ticket.
- [x] INDEX.md Completion Gate addressed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The welcome shell is now data-driven, Next-only, with page 0 (language+theme), a decorative networks page, and safe re-entry. The touch-zones gesture map is already covered by the pre-existing player first-run hint. S0399/S0400/S0402 build the dedicated profile/functionality/permissions pages on this shell.

---

## Rollback Plan

Catalog is gitignored and regenerable - no rollback needed for this phase.
