# Phase 01 — Settings Inventory

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Produce a complete written inventory of every user-facing interactive element in the settings surface (`app_v2`), covering current placement, entity type, flavor scope, and behavior contract, with confirmed placement anomalies flagged.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. (All §6 items resolved by executing this phase — no external dependency.)
- [ ] Working tree is clean or on a feature branch.
- [ ] Source files for all four settings tabs are readable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md` | New | ≤ 600 |

> No Kotlin files touched in this phase. Output is a design document only.

---

## Steps

### Step 1.1 — Catalog all interactive elements in General tab

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` and all its helper files (`helpers/GeneralSettings*.kt`)

**Depends on:** — start of phase

**Prompt for developer:**

> Read `GeneralSettingsFragment.kt` and all `helpers/GeneralSettings*.kt` files. For each user-facing interactive element (spinners, switches, buttons, edit texts, click targets) record: element id, display label (from string resource), entity type (preference / action / service-action / permission-redirect / debug), flavor scope (all / standard-only / etc.), and behavior contract (toggle / spinner-select / button-click / management-surface / dialog-launch / system-redirect). Write the result as a structured markdown table under a `## General Tab` heading in `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md`.

**Verification:**

- `Glob` — `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md` exists.
- `Grep` — `## General Tab` matches in that file.
- `Grep` — `switchAllFiles` mentioned in that file (spot-check for one known element id).
- `Grep` — `spinnerLanguage` mentioned in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: docs/settings-inventory.md (General Tab section written). Dev log recorded.

---

### Step 1.2 — Catalog all interactive elements in Media, Playback, and Operations tabs

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/ImagesSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`

**Depends on:** Step 1.1

**Prompt for developer:**

> For each remaining tab (Media with all 5 sub-sections, Playback, Operations), read the fragment sources. For each interactive element record the same columns as Step 1.1. Append `## Media Tab`, `## Playback Tab`, `## Operations Tab` sections to `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md`. Also catalog the `BackupRestoreFragment`, `PermissionsManagementFragment`, `WearSyncSettingsFragment`, and `AuthSessionsListFragment` as sub-surfaces inside their parent tabs.

**Verification:**

- `Grep` — `## Media Tab` matches in `docs/settings-inventory.md`.
- `Grep` — `## Playback Tab` matches in `docs/settings-inventory.md`.
- `Grep` — `## Operations Tab` matches in `docs/settings-inventory.md`.
- `Grep` — `switchSupportImages` mentioned in `docs/settings-inventory.md`.
- `Grep` — `switchEnableSafeMode` mentioned in `docs/settings-inventory.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Media/Playback/Operations tabs + sub-surfaces added. Dev log recorded.

---

### Step 1.3 — Cross-check inventory against SettingsSearchRegistry and flag anomalies

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`
- `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md`

**Depends on:** Step 1.2

**Prompt for developer:**

> Read `SettingsSearchIndex.kt` (the full `SettingsSearchRegistry` entries list). For each registry entry, verify its `destination` tab matches the element's actual tab in the inventory. Flag entries where: (a) the registry tab doesn't match the current fragment tab, (b) entity type is service-action but lives next to preferences, (c) an element is indexed for search but missing from the inventory. Add an `## Anomalies and Placement Issues` section to `docs/settings-inventory.md` listing each flagged case with: element key, current placement, expected placement, anomaly type.

**Verification:**

- `Grep` — `## Anomalies and Placement Issues` matches in `docs/settings-inventory.md`.
- `Grep` — at least one entry under that section (the `switchAllowDelete` placement issue — currently in Playback > File ops section while semantically belonging to Operations — is known; it must appear).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Anomalies section written (7 items: A1–A7). Dev log recorded.

---

### Step 1.4 — Add behavior contract summary for management surfaces and action elements

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md`

**Depends on:** Step 1.3

**Prompt for developer:**

> For every element in the inventory with entity type `management-surface`, `service-action`, or `dialog-launch`, add a `Behavior notes` column entry describing the preserved behavior contract: what the element opens / triggers / navigates to, whether it uses a back-stack, whether it is a deep-link target from search, and whether it supports non-touch activation (keyboard / D-pad). Add an `## Behavior Contracts` summary section listing all elements with non-trivial behavior that must be preserved under any future reorganization. Elements to cover at minimum: BackupRestoreFragment launch, PermissionsManagementFragment launch, AuthSessionsActivity launch, ScheduledOperationDialog launch, ScheduledLogDialog launch, btnAddDestination, ColorPickerDialog launch.

**Verification:**

- `Grep` — `## Behavior Contracts` matches in `docs/settings-inventory.md`.
- `Grep` — `BackupRestoreFragment` mentioned in that section.
- `Grep` — `AuthSessionsActivity` mentioned in that section.
- `Grep` — `ScheduledOperationDialog` mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Behavior Contracts section written (9 contracts: BC1–BC9). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md` exists with sections: General Tab, Media Tab, Playback Tab, Operations Tab, Anomalies and Placement Issues, Behavior Contracts.
- [x] All 12 §6 research items from INDEX blockers section items §6.1, §6.4, §6.8, §6.9, §6.12 are covered by inventory content — mark those five checklist items in INDEX.md as `[x]`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in all files touched.
- [x] Dev log entry added for `docs/settings-inventory.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `docs/settings-inventory.md` is the authoritative current-state catalogue for Phase 02 and Phase 03.
- The Anomalies section provides the confirmed misplacement list that Phase 03 migration-map must resolve.
- The Behavior Contracts section provides non-regression requirements for any future implementation phase.

---

## Rollback Plan

Revert phase commit(s) — no code changes, no data migration. Only `docs/settings-inventory.md` is produced.
