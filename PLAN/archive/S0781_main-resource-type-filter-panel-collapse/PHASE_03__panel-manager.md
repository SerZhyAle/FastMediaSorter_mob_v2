# Phase 03 - Panel Manager

**Strategic spec:** [`../S0781_main-resource-type-filter-panel-collapse.md`](../S0781_main-resource-type-filter-panel-collapse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (persisted flag), Phase 02 (strip view + color + label)
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

Introduce `MainResourceTabsCollapseManager` owning the expanded↔collapsed toggle (long-press tabs → collapse, tap strip → expand), coordinated with the existing vanish rule and persisted via the Phase 01 flag. Wire it through the existing `MainResourceTabsManager` to keep `MainActivity` growth near-zero (S0777: fold main-window wiring into a Main*Manager, no new MainActivity field).

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `AppSettings.resourceTypeTabCollapsed` persists.
- [ ] Phase 02 ✅ Done - `@id/resourceTabsCollapsedStrip` + label/color exist in all three layouts.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsCollapseManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1530 (minimal delta - constructor args only; do NOT add a new field/wiring block) |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsCollapseManagerTest.kt` | New | ≤ 220 |

> No layout edits here (Phase 02 owns them). No flavor implications - the panel is flavor-agnostic.

---

## Steps

### Step 03.1 - Create MainResourceTabsCollapseManager

**Files:** `ui/main/helpers/MainResourceTabsCollapseManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a UI-state helper owning the two visual states of the resource-type filter row. Mirror the cache-before-write pattern of `DestinationButtonsManager` (read it first) so the toggle does not depend on an async settings re-emission.
> Constructor (all collaborators injected for testability):
> - `tabLayout: TabLayout`, `collapsedStrip: View`,
> - `isPanelAvailable: () -> Boolean` (wraps the vanish rule, e.g. `gate.anyRemoteEnabled()`),
> - `initialCollapsed: Boolean`, `onCollapsedChanged: (Boolean) -> Unit` (persist callback).
> Behavior:
> - private `var collapsed = initialCollapsed` (authoritative in-memory cache).
> - `applyVisibility()`: `val available = isPanelAvailable(); tabLayout.isVisible = available && !collapsed; collapsedStrip.isVisible = available && collapsed`. Never show both; when unavailable, hide both (defer to the vanish rule).
> - `install()`: `tabLayout.setOnLongClickListener { collapse(); true }` and `collapsedStrip.setOnClickListener { expand() }`.
> - `collapse()` / `expand()`: guard on current state; flip `collapsed`; call `applyVisibility()`; move focus to the now-visible view if the now-hidden one held focus (D-pad); invoke `onCollapsedChanged(collapsed)`.
> - `refresh()`: re-apply `applyVisibility()` without persisting (called after tab rebuilds / config changes).
> Use Timber only if logging; no `Log.d`. No trivial comments - one short KDoc on the class explaining the two states and the vanish-rule deferral.

**Verification:**

- `Glob` - `MainResourceTabsCollapseManager.kt` exists.
- `Grep` - `class MainResourceTabsCollapseManager` matches once.
- `Grep` - `setOnLongClickListener` and `setOnClickListener` each match once in the file.
- `Grep` - `fun applyVisibility` and `fun refresh` present.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

---

### Step 03.2 - Compose the collapse manager inside MainResourceTabsManager

**Files:** `ui/main/helpers/MainResourceTabsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend `MainResourceTabsManager` to own a `MainResourceTabsCollapseManager` so MainActivity stays thin (S0777):
> - Add constructor params: `collapsedStrip: View`, `getCollapsed: () -> Boolean`, `setCollapsed: (Boolean) -> Unit`.
> - In `init` (or first use) build `collapseManager = MainResourceTabsCollapseManager(tabLayout, collapsedStrip, isPanelAvailable = { gate.anyRemoteEnabled() }, initialCollapsed = getCollapsed(), onCollapsedChanged = setCollapsed)` and call `collapseManager.install()`.
> - At the END of `createTabs()`, AFTER the existing `tabLayout.isVisible = gate.anyRemoteEnabled()` line, call `collapseManager.refresh()` so the persisted collapse state re-applies on every rebuild and coexists with the vanish rule (do not remove the existing vanish-rule line; the collapse manager refines it).
> Keep the class single-purpose-readable; the collapse logic stays in the new manager, this class only owns/forwards.

**Verification:**

- `Grep` - `MainResourceTabsCollapseManager` matches in `MainResourceTabsManager.kt`.
- `Grep` - `collapseManager.refresh()` appears inside `createTabs()` (after the `tabLayout.isVisible` assignment).
- `Grep` - `collapseManager.install()` matches once.

**Status:** `[x]` done

---

### Step 03.3 - Wire MainActivity construction (minimal delta)

**Files:** `ui/main/MainActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Locate the existing `MainResourceTabsManager(..)` construction in `MainActivity` and pass the three new arguments only - do NOT introduce a new field or a separate wiring block (S0777):
> - `collapsedStrip = binding.resourceTabsCollapsedStrip`,
> - `getCollapsed = { <current AppSettings>.resourceTypeTabCollapsed }` - read from the AppSettings the activity already observes (mirror how MainActivity reads other persisted main-window flags),
> - `setCollapsed = { collapsed -> <persist> }` - persist via the same mechanism MainActivity/MainViewModel already use to write `AppSettings` (e.g. an injected `SettingsRepository.updateSettings { it.copy(resourceTypeTabCollapsed = collapsed) }` on a lifecycle/viewModel scope, mirroring `DestinationButtonsManager`).
> If MainActivity does not currently hold a live `AppSettings` reference at the tabs-manager construction site, capture the latest observed settings value (the activity already collects settings for other UI) rather than adding a new collection.

**Verification:**

- `Grep` - `resourceTabsCollapsedStrip` matches in `MainActivity.kt`.
- `Grep` - `resourceTypeTabCollapsed` matches in `MainActivity.kt`.
- `/build` → `standard debug` compiles (`.\a.ps1 dq`).

**Status:** `[x]` done

---

### Step 03.4 - Unit-test the collapse manager

**Files:** `src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsCollapseManagerTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Mirror the harness of `DestinationButtonsManagerTest` (read it first - same mockk/Robolectric style). Cover:
> - initial `collapsed=false`, available=true → `tabLayout` visible, strip gone.
> - `collapse()` → tabLayout gone, strip visible, `onCollapsedChanged(true)` invoked once.
> - `expand()` → tabLayout visible, strip gone, `onCollapsedChanged(false)` invoked once.
> - `isPanelAvailable()` returns false → both hidden regardless of collapsed; `applyVisibility()` honors it.
> Verify the persist callback fires exactly on user toggles, not on `refresh()`.

**Verification:**

- `Glob` - `MainResourceTabsCollapseManagerTest.kt` exists.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*MainResourceTabsCollapseManagerTest"` → the class passes (check the per-class XML; ignore the ~26 pre-existing unrelated failures).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` → `standard debug`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `MainResourceTabsCollapseManagerTest` passes (targeted run).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager) - done in Phase 04.

---

## Handoff Notes to Next Phase

The feature is functionally complete after this phase. The changed-flow entries for the `BlockNeedUserTest` debug tags (CLAUDE.md "Debug Verification Tags") are `collapse()` and `expand()` in `MainResourceTabsCollapseManager` - `/spec-dev` inserts `Timber.d("S0781: ..")` there as the final code edits before the last build. Phase 04 is docs/catalog only.

---

## Rollback Plan

Revert phase commit(s) - the strip view stays `GONE` without the manager, so the UI returns to the always-expanded TabLayout; no data migration.
