# Phase 03 — Docs Catalog Cleanup

**Strategic spec:** [../S0188_slideshow-stop-on-resource-unavailable.md](../S0188_slideshow-stop-on-resource-unavailable.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Finalize S0188 copy, synchronize the strategic/tactical artefacts with the resolved research, and run mandatory repo hygiene checks.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 20 |
| `PLAN/S0188_slideshow-stop-on-resource-unavailable.md` | Modified | ≤ 80 |
| `PLAN/S0188_slideshow-stop-on-resource-unavailable/INDEX.md` | Modified | ≤ 120 |
| `PLAN/S0188_slideshow-stop-on-resource-unavailable/PHASE_03__docs-catalog-cleanup.md` | Modified | ≤ 80 |

---

## Steps

### Step 03.1 — Add the new slideshow stop messages in all locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `slideshow_stopped_connection_lost` and `slideshow_stopped_resource_unavailable` to EN/RU/UK. Keep them as short toast-style notifications, verify them against `docs/COMMUNICATION_POLICY.md` §2.1 and §6, and do not include any CTA in the string itself.

**Verification:**

- `Grep` — `name="slideshow_stopped_connection_lost"` present in all three `values*/strings.xml` files.
- `Grep` — `name="slideshow_stopped_resource_unavailable"` present in all three `values*/strings.xml` files.
- `Grep` — `Slideshow stopped.` present in `app_v2/src/main/res/values/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 6/6 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 03.2 — Close the strategic research section and attach the tactical plan

**Files:** `PLAN/S0188_slideshow-stop-on-resource-unavailable.md`, `PLAN/S0188_slideshow-stop-on-resource-unavailable/INDEX.md`, `PLAN/S0188_slideshow-stop-on-resource-unavailable/PHASE_03__docs-catalog-cleanup.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update the strategic spec so §6 reflects resolved research answers from the codebase and the tactical folder path is concrete rather than future work. Keep the tactical files aligned with the final implementation scope.

**Verification:**

- `Grep` — `## 6. Research results` present in `PLAN/S0188_slideshow-stop-on-resource-unavailable.md`.
- `Grep` — `**Tactical plan:**` present in `PLAN/S0188_slideshow-stop-on-resource-unavailable.md`.
- `Grep` — `docs-catalog-cleanup` present in `PLAN/S0188_slideshow-stop-on-resource-unavailable/INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Strategic spec and tactical index updated. Dev log recorded.

---

### Step 03.3 — Run catalog, localization, and build hygiene

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> After code and string edits are complete, run `scripts/check_strings_localized.ps1` for the new prefix, regenerate the app_v2 catalog with `scan.ps1` + `render.ps1`, append a dev log entry for every touched file via `scripts/add_to_dev_log.ps1`, and run the standard debug build script.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists.
- `Glob` — `dev/CATALOG/app_v2.md` exists.
- `Grep` — `S0188` present in `dev/CHANGELOG.md` after logging.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Catalog regenerated, string audit passed, build verified. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.