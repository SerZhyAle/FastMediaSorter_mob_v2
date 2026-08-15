# Phase 03 - Search Index Sweep

**Strategic spec:** [`../S0259_settings-toggle-row-general-destinations.md`](../S0259_settings-toggle-row-general-destinations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the temporary `SettingsSearchIndex` placeholders with final row IDs from the completed general and destinations migrations.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Final `row*` IDs for general and destinations are present in both portrait and landscape layouts.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` | Modified | ≤ 780 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Replace `TODO(S0254)` placeholders with final IDs

**Files:** `SettingsSearchIndex.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Replace each `viewId = 0 // TODO(S0254)` placeholder with the final `R.id.row*` or surviving non-row control ID introduced by this spec. Remove only the placeholder comments that are fully resolved.

**Verification:**

- `Grep` - `viewId = 0` no longer appears in `SettingsSearchIndex.kt`.
- `Grep` - `TODO(S0254)` no longer appears in `SettingsSearchIndex.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Replaced all temporary `viewId = 0` / `TODO(S0254)` placeholders with real `row*` ids or removed orphaned entries with no surviving UI control.

---

### Step 03.2 - Confirm search targets point at real views

**Files:** `SettingsSearchIndex.kt`, touched general/destinations layouts
**Depends on:** Step 03.1

**Prompt for developer:**

> Cross-check each newly assigned `viewId` against the actual XML IDs so settings search cannot target a removed control.

**Verification:**

- `Grep` - every new `R.id.row*` introduced for this phase exists in at least one touched layout file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. Search targets now point only at existing settings controls in current layouts.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `SettingsSearchIndex.kt` has no temporary placeholder IDs.
- [x] Dev log entry added for every modified file.
