# Phase 01 — Schema Foundation

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Create the `dev/ACTIVITY_CATALOG/` directory with its `scripts/` subfolder and a `SCHEMA.md` that formally defines all JSONL record fields — the contract every subsequent script must honour.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/ACTIVITY_CATALOG/SCHEMA.md` | New | ≤ 80 |
| `dev/ACTIVITY_CATALOG/scripts/.gitkeep` | New | 1 |

---

## Steps

### Step 01.1 — Create directory structure

**Files:** `dev/ACTIVITY_CATALOG/`, `dev/ACTIVITY_CATALOG/scripts/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the directory `dev/ACTIVITY_CATALOG/scripts/` (and its parent). Place an empty `.gitkeep` file inside `dev/ACTIVITY_CATALOG/scripts/` so the empty folder is tracked by git.

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/scripts/.gitkeep` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. Files: dev/ACTIVITY_CATALOG/scripts/.gitkeep. Dev log recorded.

---

### Step 01.2 — Write SCHEMA.md

**Files:** `dev/ACTIVITY_CATALOG/SCHEMA.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write `dev/ACTIVITY_CATALOG/SCHEMA.md` with the field table below. This file is the authoritative contract for the JSONL format — all scripts must validate against it.

```markdown
# ACTIVITY_CATALOG — JSONL Schema

Merge key: `module + class` (unique combination per record).

## Auto-populated fields (set/overwritten by `scan.ps1`)

| Field | Type | Description |
|-------|------|-------------|
| `class` | string | Simple class name (e.g. `PlayerActivity`) |
| `package` | string | Fully-qualified class name |
| `module` | string | `app_v2` or `wear` |
| `path` | string | Relative path to `.kt` source under module source root; empty if not found |
| `sourceSet` | string | `main`, `vr`, or `""` |
| `exported` | bool | Value of `android:exported` attribute |
| `launcher` | bool | `true` if has `MAIN + LAUNCHER` intent-filter |
| `intentActions` | string[] | All `android:name` values from `<action>` tags |
| `intentCategories` | string[] | All `android:name` values from `<category>` tags |
| `noFlavors` | string[] | Flavors where the Activity is absent (detected from flavor manifests) |
| `loc` | int | Source file line count; 0 if source not found |
| `lastTouched` | string | `yyyy-MM-dd` from `git log`; empty if no source |

## Manual fields (preserved on rescan)

| Field | Type | Description |
|-------|------|-------------|
| `role` | string | English one-line description of the Activity's purpose |
| `roleRu` | string | Russian one-line description (used for RU-language search) |
| `tags` | string[] | Keyword tags for fast search (e.g. `["player","portrait","pip"]`) |
| `status` | string | One of: `new`, `tested`, `todo`, `unknown` |
| `notes` | string | Free-text notes |

## Allowed `status` values

`new` · `tested` · `todo` · `unknown`

## Flavors recognised by `noFlavors`

`standard` · `lite` · `photos` · `legacy` · `vr`
```

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/SCHEMA.md` exists.
- `Grep` — `merge key` present in `dev/ACTIVITY_CATALOG/SCHEMA.md` (case-insensitive).
- `Grep` — `roleRu` present in `dev/ACTIVITY_CATALOG/SCHEMA.md`.
- `Grep` — `tags` present in `dev/ACTIVITY_CATALOG/SCHEMA.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: dev/ACTIVITY_CATALOG/SCHEMA.md. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Glob` — `dev/ACTIVITY_CATALOG/scripts/.gitkeep` exists.
- [ ] `Glob` — `dev/ACTIVITY_CATALOG/SCHEMA.md` exists.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the contract. Phase 02 (scan-script) reads `SCHEMA.md` only for reference — it does not parse it programmatically.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
