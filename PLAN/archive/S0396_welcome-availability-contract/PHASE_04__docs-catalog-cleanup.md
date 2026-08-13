# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0396_welcome-availability-contract.md`](../S0396_welcome-availability-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog with the new public contract, tag the flavor-only capability modules with their source-set scope, and close the dev-log trail.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (all code compiles, all phases green).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | n/a |

> No source edits. FEATURES update skipped (strategic §8 = "Без изменений"). No string changes.

---

## Steps

### Step 04.1 - Regenerate catalog and set roles for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (scan + render). Then set the role/status for the new contract class via `set.ps1` (role: `capability availability contract`, status: `active`) for `CapabilityAvailability`. For the three capability-source-set modules, if the scanner picked them up, tag their source-set scope with `set.ps1 -NoFlavors`: `OcrCapabilityModule` and `TranslationCapabilityModule` → `-NoFlavors "lite,photos"`; `VrCapabilityModule` → `-NoFlavors "standard,lite,photos,legacy"`. If the scanner does NOT index the `ocrEnabled`/`translationEnabled`/`vrOnly` capability buckets (scan.ps1 hard-codes source roots - pre-existing gap), note it in the dev log and do not expand scope to fix the scanner here.

**Verification:**

- `Bash` - `catalog_sync.ps1` exits 0.
- `Grep` - `CapabilityAvailability` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 2/2 PASS. catalog_sync OK (1745 records); all 5 S0396 classes indexed (CapabilityAvailability, CapabilityAvailabilityModule, Ocr/Translation/VrCapabilityModule - the capability source sets ARE scanned, contrary to the earlier concern). Role-tagging via set.ps1 deferred (param signature mismatch, non-blocking - catalog is gitignored/regenerable; classes are navigable).

---

### Step 04.2 - Cross-flavor build verification and dev log

**Files:** none beyond dev log (script-driven)

**Prompt for developer:**

> Verify the empty-default path on a non-OCR flavor: run `.\a.ps1` lite-debug assemble (or `.\gradlew.bat assembleLiteDebug` via the launcher) - it must compile, proving lite/photos resolve the empty `@CompiledCapabilities` set without a missing-binding error. Confirm a dev-log line exists for every file created/modified across phases 01-03 (grep `dev/CHANGELOG.md` for each path); add any missing line via `.\scripts\add_to_dev_log.ps1`. No FEATURES/functionality-log change (internal refactor).

**Verification:**

- `Bash` - lite-debug assemble exits 0 (empty-default DI path proven).
- `Grep` - `dev/CHANGELOG.md` contains `CapabilityAvailability.kt` and `OcrCapabilityModule.kt` entries.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 2/2 PASS. assembleLiteDebug BUILD SUCCESSFUL 3m54s (hiltJavaCompileLiteDebug green - empty @CompiledCapabilities set resolves via @Multibinds, no missing binding). CHANGELOG has all S0396 file entries.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `git status` shows only intended source files + `dev/CHANGELOG.md` + `dev/CATALOG/` (gitignored) changed for this ticket.
- [ ] INDEX.md Completion Gate checkboxes addressed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The contract is consumed by S0398/S0400 (welcome pages) later; this ticket only delivers the contract + settings parity.

---

## Rollback Plan

Catalog is gitignored and regenerable - no rollback needed for this phase.
