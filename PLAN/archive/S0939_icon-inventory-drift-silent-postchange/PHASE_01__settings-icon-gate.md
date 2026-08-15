# Phase 01 - Settings icon gate

**Strategic spec:** [`../S0939_icon-inventory-drift-silent-postchange.md`](../S0939_icon-inventory-drift-silent-postchange.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** -
**Blocks:** -
**Steps done:** 3 / 3

---

## Objective

Close the local gap that lets settings-driven icon inventory drift stay silent until CI or a later manual re-render. The fix stays lightweight: pure PowerShell source scan for settings icon/title pairs plus wider post-change routing to actually invoke the gate on relevant app-resource edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-icon-inventory-sync.ps1` | Modified | ≤ 320 |
| `scripts/post-change.ps1` | Modified | ≤ 520 |
| `docs/icons/README.md` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Add a cheap settings source scan to the icon-inventory gate

**Files:** `scripts/quality/assert-icon-inventory-sync.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a pure-PowerShell settings-source freshness stage that scans `app_v2/src/main/res/layout/fragment_settings_*.xml`, extracts the same public settings icon/title pairs that `IconInventoryExportTest` scans (`csh_icon/csh_title`, `str_icon/str_title`, `ssr_icon/ssr_title`), normalizes them into the committed inventory shape, and compares that source-derived subset against the committed `settings-header` + `settings-row` subset in `docs/icons/icon-inventory.json`. Keep the full Robolectric export test opt-in (`-IncludeExportTest`) and move it to the next stage number in the gate narrative.

**Verification:**

- `Grep` - helper logic for scanning `fragment_settings_*.xml` exists in `assert-icon-inventory-sync.ps1`.
- `Grep` - the gate now mentions a settings-source freshness stage in its synopsis/description.
- `Grep -n "testStandardDebugUnitTest"` - still present only behind `-IncludeExportTest`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. Added `fragment_settings_*.xml` source scan + settings subset compare in `assert-icon-inventory-sync.ps1`; heavy `testStandardDebugUnitTest` path remains opt-in behind `-IncludeExportTest`.

---

### Step 01.2 - Trigger the gate on relevant settings resource edits

**Files:** `scripts/post-change.ps1`, `docs/icons/README.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Expand the `runsIconInventoryGate` routing so the icon-inventory gate also runs for settings source edits that can stale the legend/inventory: `app_v2/src/main/res/layout/fragment_settings_*.xml` and merged string-table XML under `app_v2/src/main/res/values*/strings*.xml`. Update `docs/icons/README.md` so the automatic trigger documentation matches the new routing and explicitly explains that settings icon/title changes now get a cheap local freshness check.

**Verification:**

- `Grep` - `fragment_settings_.*\.xml` is part of the icon-inventory trigger in `scripts/post-change.ps1`.
- `Grep` - `values*/strings*.xml` (or equivalent regex) is part of the icon-inventory trigger in `scripts/post-change.ps1`.
- `Grep` - `docs/icons/README.md` mentions settings layout/string changes as an automatic trigger.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. `scripts/post-change.ps1` now routes icon-inventory gate for settings layouts + `values*/strings*.xml`; `docs/icons/README.md` documents the new automatic trigger scope.

---

### Step 01.3 - Validate the lightweight gate end-to-end

**Files:** `scripts/quality/assert-icon-inventory-sync.ps1`, `scripts/post-change.ps1`, `docs/icons/README.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run the cheapest proof that the new routing and cheap gate parse cleanly and succeed on the current tree. Because this ticket is script/doc only, prefer direct script execution over a Gradle compile. Record the exact command(s) and outcome. If the scripts fail, fix them before closing the phase.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -Gate` exits 0.
- `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/icons/README.md" -Target "docs/icons/README.md" -Description "document settings-source icon inventory gate triggers" -ChangeType Doc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 2/2 PASS. `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -Gate` -> PASS. `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/icons/README.md" -Target "docs/icons/README.md" -Description "document settings-source icon inventory gate triggers" -ChangeType Doc -ScopeToFile` -> PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] The gate can detect settings-source drift without `-IncludeExportTest`.
- [x] `post-change.ps1` no longer silently skips icon-inventory checks for relevant settings source edits.
- [x] Validation commands in Step 01.3 PASS.
