# S0585 - ALL_FEATURES records use spec-id as area prefix

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-21
**Origin:** Auto-captured (CLAUDE.md §3.1) during S0580 Phase 05 - `scripts/all_features/validate.ps1` fails.

<!-- auto-approved by /spec-all - 2026-06-21 -->

## Goal (RU)

`scripts/all_features/validate.ps1` падает: активные записи в `docs/ALL_FEATURES.jsonl` используют spec-id (`s####.`) как area-префикс вместо area-слага (правило S0543). Из-за этого весь инвентарь не проходит валидацию и маскирует будущие реальные регрессии, блокируя любой шаг с gate на чистый `validate.ps1`. Цель - привести все нарушающие **активные** записи к форме `<area-slug>.<feature>` и довести `validate.ps1` до exit 0.

## 0. Raw capture

`pwsh -NoProfile -File scripts/all_features/validate.ps1` fails with 2 errors (pre-existing, unrelated to S0580):

```
ALL_FEATURES validation FAILED (2 error(s)) in docs/ALL_FEATURES.jsonl
  L367: id 's0575.streams-feature-master-toggle-with-per-device-profile' uses a spec id as area prefix; use the area slug
  L368: id 's0559.take-a-screenshot-from-the-app-operations-settings' uses a spec id as area prefix; use the area slug
```

## 1. Problem

`validate.ps1` enforces the S0543 rule: an **active** record's id must be `<area-slug>.<feature>` kebab, and the area prefix must not be a spec id (`s\d{4}`). `removed` tombstones are exempt - they keep their frozen historical id. A spec-id prefix on an active record fails the whole inventory, masking future real regressions and blocking any step gating on a clean `validate.ps1`.

Scope drifted since capture (2026-06-21):

- The two originally captured records (`s0575.*`, `s0559.*`) are already fixed to `streams.*` / `screen-capture.*` - no longer violating.
- One **active** violator remains: `s0583.stream-catalog-import-timeout` (area `Streams`, spec S0583).
- `s0490.after-an-app-crash-the-next-launch-offers` keeps its `s0490.*` id but is `status: removed` - correctly exempt, do not touch.

## 2. Phases

### Phase 01 - Rename violating active records to area-slug ids

1. Enumerate active violators: `pwsh -NoProfile -File scripts/all_features/validate.ps1` - note every `uses a spec id as area prefix` line (currently L371 `s0583.stream-catalog-import-timeout`, area `Streams`).
   - Verification: each reported id has `status:"active"` and an `s\d{4}.` prefix; `removed` tombstones (e.g. `s0490.*`) are not listed.
2. For each violator, rename via `patch.ps1 -NewId` to `<area-slug>.<feature>`, reusing the record's existing area slug. For S0583: `pwsh -NoProfile -File scripts/all_features/patch.ps1 -Id s0583.stream-catalog-import-timeout -NewId streams.stream-catalog-import-timeout`.
   - Verification: `patch.ps1` exits 0; the new id is unique against existing `streams.*` ids (`streams-feature-master-toggle-with-per-device-profile`, `background-playback-and-exit`, `category-language-filter`).
3. Confirm no other artefact references the old id (release showcase / FEATURES tooling): grep the old id across the repo.
   - Verification: `s0583.stream-catalog-import-timeout` appears in no file after the rename (was referenced only inside `docs/ALL_FEATURES.jsonl`).
4. Re-validate the inventory: `pwsh -NoProfile -File scripts/all_features/validate.ps1`.
   - Verification: exit 0, `ALL_FEATURES validation PASS`.

## 3. Notes

- Data-only fix in `docs/ALL_FEATURES.jsonl`; no Kotlin/resource impact, no build needed (docs-only diff).
- Area slug derives from the record's `area` field lower-kebab (`Streams` -> `streams`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0543 (added the area-prefix validation rule), S0583 (current violator source), S0575, S0559, S0580

## Last Audit

**Date:** 2026-06-21 | **Verdict:** Verified

- `scripts/all_features/validate.ps1` -> exit 0, `ALL_FEATURES validation PASS: 371 record(s)`.
- Renamed `s0583.stream-catalog-import-timeout` -> `streams.stream-catalog-import-timeout` via `patch.ps1 -NewId` (area `Streams`, spec `S0583`, flavors `[standard]`, status `active`); unique against existing `streams.*` ids.
- No file references the old id after the rename (was confined to `docs/ALL_FEATURES.jsonl`).
- `s0490.after-an-app-crash-the-next-launch-offers` left untouched - `status: removed` tombstone, exempt by the S0543 rule.
- Build: skipped - docs-only diff (`docs/ALL_FEATURES.jsonl`). No Kotlin/resource impact.
- Originally captured violators (`s0575.*`, `s0559.*`) were already fixed before this run; scope reconciled in §1.
