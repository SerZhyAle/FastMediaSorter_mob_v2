# Phase 05 — Docs, Strings Audit, Catalog, Changelog

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Close out the spec: refresh the trilingual feature docs, verify locale parity for all touched string prefixes, regenerate the class catalog for `app_v2`, and ensure the dev changelog records every modified file.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |
| `dev/CHANGELOG.md` | Appended (via script) | — |

---

## Steps

### Step 05.1 — Refresh onboarding entry in trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

**Depends on:** — start of phase

**Prompt for developer:**

> Add or update a concise bullet describing the revamped onboarding: compact header + scrollable details on each page, an overview grid of additional capabilities, an inline labelled touch-zones grid (no more bitmap scheme), a single bottom navigation bar with Back / page indicator / Skip / Next, where Skip jumps to the final onboarding page (default-player offer) — or to the permissions step in flavors without it — rather than exiting; works in portrait and landscape on small and large screens. Mirror the wording across all three files. Do not duplicate an existing bullet — edit the existing onboarding mention if present.

**Verification:**

- `Grep -n "Skip"` (or the localized equivalent) near the onboarding section in `docs/FEATURES.md` — at least one hit referencing the new behaviour.
- `Grep -ni "welcome"` in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md` — the onboarding bullet is present in both.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (EN "Onboarding walkthrough" 1 + "Skip jumps to the final" 1; RU "Обзорный онбординг" 1; UK "Оглядовий онбординг" 1). New onboarding bullet added after the Welcome-language-picker bullet in §19 of all three FEATURES files. Dev log recorded.

---

### Step 05.2 — Locale parity audit for all touched string prefixes

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 05.1

**Prompt for developer:**

> Run the locale audit for every prefix this spec introduced or changed and fix any reported gap before proceeding: `welcome_description_`, `welcome_touch_zone_`, `welcome_feature_`, and a broad `welcome_` pass. Each invocation must exit 0.

**Verification:**

- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_description_"` — exit code 0.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_touch_zone_"` — exit code 0.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_feature_"` — exit code 0.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_"` — exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS: welcome_description_ → 10/10 exit 0; welcome_touch_zone_ → no keys (reused touch_zone_, exit 0); welcome_feature_ → 16/16 exit 0; welcome_ → 47/47 exit 0; touch_zone_ → 20/20 exit 0. No changes needed. Dev log: n/a (no file edits).

---

### Step 05.3 — Regenerate the `app_v2` class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. If any new class was introduced (e.g. a feature-tile builder/adapter), set its `role` + `status` via `dev/CATALOG/scripts/set.ps1`. Commit `app_v2.jsonl` + `app_v2.md` together with the code changes.

**Verification:**

- `Grep -n "WelcomePagerAdapter"` in `dev/CATALOG/app_v2.jsonl` — present with refreshed `loc`/`last` fields.
- `Glob` — `dev/CATALOG/app_v2.md` modification time is newer than the start of this phase.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — `scan.ps1 -Module app_v2` → 997 files; `render.ps1 -Module app_v2` → 997 records. Verification PASS (WelcomePagerAdapter in app_v2.jsonl, loc 315, functions list now includes bindDetails / populateFeatureGrid; app_v2.md mtime 17:52). No new classes → no set.ps1. Note: dev/CATALOG/*.jsonl and *.md are gitignored in this repo, so no commit-with-code step applies.

---

### Step 05.4 — Dev changelog entries for every modified file

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`)

**Depends on:** Step 05.3

**Prompt for developer:**

> For every file modified across Phases 01–05 that does not yet have a changelog line, run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`. Do not edit `dev/CHANGELOG.md` directly. After the changelog is complete, the spec is ready for `/spec-check S0143` (which sets `Verified` and removes the `Timber.d("S0143:` tags).

**Verification:**

- `Grep -n "S0143"` in `dev/CHANGELOG.md` — multiple hits covering the welcome layouts, `WelcomeActivity.kt`, `WelcomePagerAdapter.kt`, `strings.xml`, `integers.xml`, and the docs.
- `Grep -rn "Log\.d\("` across the files modified by this spec — zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (dev/CHANGELOG.md has S0143 entries for all welcome layouts, WelcomeActivity.kt, WelcomePagerAdapter.kt, strings.xml ×3, integers.xml ×6, item_welcome_feature_tile.xml, resource_types.xml/.png, FEATURES ×3; zero `Log.d(` in the welcome package). All modified source files were dev-logged step-by-step; spec-status transition logged. Ready for /spec-check S0143.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] All `check_strings_localized.ps1` runs exit 0.
- [x] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated (both gitignored in this repo — no commit step).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] Ready to run `/spec-check S0143`.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. Next action after this phase: `/spec-check S0143`.

---

## Rollback Plan

Docs/catalog/changelog only — revert the phase commit; no runtime impact.
