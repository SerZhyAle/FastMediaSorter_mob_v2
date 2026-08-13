# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 02 and 04
**Blocks:** none
**Steps done:** 3 / 3

## Objective

Close code metadata, documentation, and device-test handoff after every feature path is integrated.

## Steps

### Step 05.1 - Record the launcher capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phases 02 and 04

**Prompt for developer:**

> Add the shipped launcher maps capability through the feature-inventory script.

**Why:** The project inventory is the source of truth for shipped capability descriptions.

**Verification:**

- `scripts/all_features/validate.ps1` passes.
- Done: `launcher.place-shortcut` and `launcher.map-gadget` added for `standard,noLegal`; validation PASS over 691 records.

**Status:** `[x]` done

### Step 05.2 - Synchronize catalog and project records

**Files:** generated catalog, dev log
**Depends on:** Step 05.1

**Prompt for developer:**

> Run catalog synchronization and closure gates for the whole S1175 change set.

**Why:** Public Kotlin symbols and the human-readable change log must match the delivered source.

**Verification:**

- `catalog_sync.ps1 -Module app_v2` passes.
- Done: catalog synchronized to 2806 records with a role and status on every new class, and both change sets closed through `post-change.ps1 -ScopeToFile` (phase 02 PASS; phases 03-04 PASS with one advisory - detekt debt in two files this ticket never touched).

**Status:** `[x]` done

### Step 05.3 - Produce device-test handoff

**Files:** tactical records and ticket status
**Depends on:** Step 05.2

**Prompt for developer:**

> Build the standard APK and hand off the two device-only checks: capture an actual Maps share payload and pin a Maps shared-location shortcut.

**Why:** Research established that Google Maps and the real share payload cannot be verified on the emulator.

**Verification:**

- Standard debug APK builds and ticket advances to `BlockNeedUserTest`.
- Done: `a.ps1 d` exit 0, `FastMediaSorter_standard_debug_v2.60.8111.809-DEBUG.apk`, and the ticket carries the device-test list in its status note. `Timber.d("S1175: ..")` probes sit at the three flow entries a device test cannot see from outside - the share receiver, the geographic cell tap, and the map gadget's state each poll.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All steps are `[x] done`.
- [x] Device-only tests are explicitly handed off.
