# Phase 06 - Docs, catalog and rule cleanup

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Record the widened scope where the next agent will look for it: Rule 22's wording, the document registry, the class catalog and the dev log.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 5 |
| `AGENTS.md` | Modified | ≤ 5 |
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 3 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 06.1 - Widen Rule 22's wording to match the new scope

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rule 22 currently reads as if settings live only on settings screens, which is the assumption this ticket disproved. Reword it so the trigger is "any change to a setting - including one hosted in a dialog, bottom sheet, or wizard page" and name `SettingsDocScopeCatalog` as the place a non-screen surface is registered. Keep the sentence short and keep the existing gate reference. `AGENTS.md` carries the parallel rule set for non-Claude agents - apply the same edit there so the two do not drift.

**Verification:**

- `Grep` - `SettingsDocScopeCatalog` matches in `CLAUDE.md`.
- `Grep` - `SettingsDocScopeCatalog` matches in `AGENTS.md`.
- `Grep` - `assert-settings-doc-sync.ps1` still matches in both files.
- Value equality - Rule 22 text is identical in `CLAUDE.md` and `AGENTS.md`.
  **Deviation:** false premise - `AGENTS.md` is a condensed digest (71 lines, §3 "Core Rules" bullet
  list), not a numbered 1:1 mirror of `CLAUDE.md`'s rules; it never carried a "Rule 22" line to reword
  (several `CLAUDE.md` rules are deliberately absent from it already, e.g. Rule 21). Added a new,
  differently-worded bullet to AGENTS.md §3 conveying the same fact (settings-doc-sync requirement +
  `SettingsDocScopeCatalog` for non-screen surfaces) instead of forcing byte-identical text into a
  structurally different file - achieves the stated intent (drift prevention) without a format mismatch.

**Status:** `[x] done`

---

### Step 06.2 - Register the new documentation artifacts

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> The `settings-reference` record is the only registry entry covering this area, and its trigger list does not mention dialog-hosted settings. Extend that record's triggers so a future dialog-setting change routes to it, and register `docs/settings/settings-scope-exclusions.json` as a maintained document with product area `settings`. Do not hand-edit `docs/DOCS_MAP.md` or `sitemap.xml` - they are generated.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0. Confirmed: "PASS: 25 record(s)".
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0. Confirmed.
- `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "settings"` lists the exclusions record. Confirmed: `settings-scope-exclusions | Settings Scope Exclusions | areas=settings | triggers=setting,dialog-setting`.

**Status:** `[x] done`

---

### Step 06.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Phase 01 added a public object, so the class catalog is stale. Regenerate it and set the role and status for the new class. The catalog files are gitignored local indexes - regenerate, never commit.

```powershell
pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt" -Role "settings documentation scope registry" -Status new
```

> **Deviation:** `set.ps1`'s actual parameter is `-Path` (file path), not `-Class`; `-Status` accepts
> `{new|tested|legacy|todo|unknown}`, not `active` as the plan's example showed. Used `new` - freshest,
> valid value; no dedicated unit test exists for this class specifically (exercised indirectly via
> `SettingsManifestExportTest`).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "SettingsDocScopeCatalog"` returns exactly one record. Confirmed.
- Value equality - that record's `role` is non-empty. Confirmed: "settings documentation scope registry".

**Status:** `[x] done`

---

### Step 06.4 - Record the capability and journal the change

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.3

**Prompt for developer:**

> Record the shippable capability - the published settings reference now documents settings hosted in dialogs, not only settings screens - as one EN-only record via `scripts/all_features/add.ps1`. Then run the post-change facade once for the ticket with `-ChangeType Mixed`, batching the dev-log entries rather than one call per file. Never edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0. Confirmed: "PASS: 629 record(s)".
- `Grep` - the new capability record matches once in `docs/ALL_FEATURES.jsonl`. Confirmed:
  `settings.dialog-hosted-settings-documented`.
- `Grep` - `dev/CHANGELOG.md` contains an entry mentioning `SettingsDocScopeCatalog`. Confirmed (one
  consolidated ticket-level entry per CLAUDE.md journaling granularity, not a per-file entry - its
  description names the class).
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0. Confirmed (full run,
  manifest gradle test included: "catalog complete, manifest fresh, annotations covered, reference up to
  date, HOW_TO recipes in sync").
- **Note on `post-change.ps1` itself:** the facade's own `settings-doc-sync-gate` step raced against its
  backgrounded `detekt-gate` `Start-ThreadJob` on `temp/BUILD.LOCK` and failed on lock contention (not a
  real finding) on 2 of 3 attempts this session - a pre-existing, deterministic ordering bug in
  `post-change.ps1` unrelated to this ticket's content, reproduced and diagnosed, parked as **S1349**
  (dedup-checked, no duplicate found). One attempt landed both gates in the right order and returned
  `PASS WITH ADVISORIES (1)` (the advisory was the document-registry sibling-file reminder, resolved by
  also updating `.github/copilot-instructions.md` and re-running with `-RegistryAck`). Every gate the
  facade runs was independently confirmed green by direct invocation regardless.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exits 0.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] `docs/FEATURES*.md` untouched - confirmed, only `docs/ALL_FEATURES.jsonl` (the separate developer inventory) was touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edits and re-run `scripts/catalog_sync.ps1 -Module app_v2`. No data migration or user-facing app surface changed.
