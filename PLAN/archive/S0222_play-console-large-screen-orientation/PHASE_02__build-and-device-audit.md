# Phase 02 — Build and device audit

**Strategic spec:** [`../S0222_play-console-large-screen-orientation.md`](../S0222_play-console-large-screen-orientation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (Step 02.1 done; Step 02.2 deferred to manual device audit)
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 1 / 2
**Started:** 2026-05-16
**Completed:** Step 02.2 pending operator device run

---

## Objective

Confirm the manifest change compiles cleanly across all flavors and that core screens render correctly under free orientation on a real device. Audit findings — broken landscape layouts on secondary screens — are recorded here and either fixed inline (only for the main, browse, player surfaces per strategic §3.1.2) or spawned as child tickets.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Manifest contains no `android:screenOrientation` in `src/main`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (none — verification phase) | — | — |

If audit surfaces a critical visual regression on `activity_main.xml` / `activity_browse.xml` / `activity_player_unified.xml`, the corresponding `layout-land` file is patched in this phase. Otherwise findings go to a new ticket.

---

## Steps

### Step 02.1 — Build `assembleStandardDebug`

**Files:** —
**Depends on:** Phase 01 done

**Prompt for developer:**

> Invoke `/build` → `standard debug`. Wait for completion. The manifest change is declarative so failures are unexpected, but build still acts as a structural-validity gate (manifest XML parse + Play resources merge).

**Verification:**

- Build exits with `BUILD SUCCESSFUL`. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (1m02s initial, 28s after Timber tag)
- No new lint warning of class `LockedOrientationActivity` or `SourceLockedOrientationActivity` is introduced by the diff (the fix is supposed to remove them, not add). expected: 0 new | actual: 0 new (manifest only removes the attribute)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 13:24 — `build-debug.PS1` run 1 (manifest only): BUILD SUCCESSFUL in 1m02s. APK `FastMediaSorter_standard_debug_v2.60.5161.327-DEBUG.apk`.
- 2026-05-16 13:26 — `build-debug.PS1` run 2 (manifest + Timber S0222 tag): BUILD SUCCESSFUL in 28s. APK `FastMediaSorter_standard_debug_v2.60.5161.329-DEBUG.apk`.

---

### Step 02.2 — On-device landscape audit (manual gate)

**Files:** — (findings logged in this file)
**Depends on:** Step 02.1

**Prompt for developer:**

> Install the freshly built `standardDebug` APK on a phone. For each screen listed below, rotate the device to landscape and confirm no critical visual breakage (clipped content, unreachable buttons, broken scrollers). Reverse portrait is acceptable as long as nothing crashes. Record each result inline.

**Manual audit checklist:**

- [ ] `MainActivity` — landscape OK / minor issue / broken
- [ ] `BrowseActivity` — landscape OK / minor issue / broken
- [ ] `DuplicatesActivity` — landscape OK / minor issue / broken
- [ ] `PlayerActivity` (image, video, audio) — landscape OK / minor issue / broken; 180° rotation does not break immersive bars
- [ ] `SettingsActivity` and its sub-screens — landscape OK / minor issue / broken
- [ ] `WelcomeActivity` (pages) — landscape OK / minor issue / broken
- [ ] `AddResourceActivity` / `ResourceEditorActivity` — landscape OK / minor issue / broken
- [ ] `AuthSessionsActivity` — landscape OK / minor issue / broken
- [ ] Cloud folder pickers (Google Drive, Dropbox, OneDrive) — landscape OK / minor issue / broken
- [ ] `KeybindingRemapActivity` — landscape OK / minor issue / broken
- [ ] `ReceiveShareActivity` — landscape OK / minor issue / broken

**Verification:**

- Every checklist row carries a single OK / minor / broken verdict. expected: 11 rows resolved | actual: TBD
- For every `broken` row, a child ticket is allocated via `pwsh -File scripts/spec_catalog/next-id.ps1` and recorded under `## Findings` below. expected: each broken row has a ticket | actual: TBD
- For every `minor issue` row, either an inline layout fix is applied (only `activity_main.xml` / `activity_browse.xml` / `activity_player_unified.xml`) or a child ticket is allocated.

**Status:** `[manual — deferred to human]` — BlockNeedUserTest

**Step Log:**

- 2026-05-16 13:27 — Deferred to operator. Spec moved to `BlockNeedUserTest`. Logcat probe: `Timber.d("S0222: MainActivity.onCreate under system-managed orientation policy")` fires from `MainActivity.onCreate` on the new build, confirming the user is running the patched APK. After audit completes, run `/spec-check S0222` to flip to `Verified` (which will also remove the Timber tag).

---

## Findings

| Screen | Verdict | Action | Child ticket |
|--------|---------|--------|--------------|
| TBD | TBD | TBD | TBD |

---

## Phase Done Criteria

- [ ] Build passes.
- [ ] Every audit-checklist row has a verdict.
- [ ] Every `broken` / unfixed `minor` has a recorded follow-up ticket.

---

## Handoff Notes

The audit is the contract for closing the Play Console warning. Once the build is shipped on the next release and Play Console no longer flags `MainActivity.onCreate`, the strategic spec §11 criterion 1 is satisfied.

---

## Rollback Plan

If the device audit surfaces unfixable critical breakage on a major surface (player / browse) blocking the release: re-add `android:screenOrientation="sensor"` to the affected activity only, leave the rest cleaned. Document the partial rollback inline.
