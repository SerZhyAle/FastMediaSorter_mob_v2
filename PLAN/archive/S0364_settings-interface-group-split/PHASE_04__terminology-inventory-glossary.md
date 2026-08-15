# Phase 04 - Terminology inventory and glossary

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - can run in parallel with Phase 01-03
**Blocks:** Phase 05, Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Establish "браузер файлов" as the canonical name of the Browse window in the communication-policy glossary, and produce a concrete inventory of every existing string/doc occurrence that names the Browse window inconsistently.

---

## Prerequisites

- [ ] Strategic §6 item 3 (terminology scope) is Resolved = full sweep.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/COMMUNICATION_POLICY.md` | Modified | n/a |
| `docs/COMMUNICATION_POLICY_RU.md` | Modified | n/a |
| `docs/COMMUNICATION_POLICY_UK.md` | Modified | n/a |
| `temp/S0364_terminology_inventory.md` | New | n/a |

> Inventory artifact lives in `temp/` (no project-root writes). It is a working list for Phases 05-06, not a tracked deliverable.

---

## Steps

### Step 04.1 - Add the canonical glossary entry

**Files:** `docs/COMMUNICATION_POLICY.md`, `docs/COMMUNICATION_POLICY_RU.md`, `docs/COMMUNICATION_POLICY_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a glossary/terminology rule to the communication policy in all three locales: the Browse window is called "браузер файлов" (RU) / "file browser" (EN) / "браузер файлів" (UK); qualified variants are allowed where the media type is relevant ("браузер видеофайлов", "браузер изображений", "браузер документов"); the word "проводник" / "провідник" / "explorer" is forbidden for this window. Note the exclusion: strings about a WEB browser (Chrome Custom Tabs, Google sign-in) keep "браузер" in the web sense and are out of scope.

**Verification:**

- `Grep` - `браузер файлов` present in `docs/COMMUNICATION_POLICY_RU.md`.
- `Grep` - `file browser` present in `docs/COMMUNICATION_POLICY.md`.
- `Grep` - `браузер файлів` present in `docs/COMMUNICATION_POLICY_UK.md`.
- `Grep` - the forbidden-term note (`проводник`) present in `docs/COMMUNICATION_POLICY_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Added `## 7. Glossary` to all three policy docs: EN `file browser`, RU `браузер файлов`, UK `браузер файлів`; forbidden-term note `проводник`/`провідник`/`explorer` + web-browser exclusion. Dev log recorded.

---

### Step 04.2 - Inventory inconsistent Browse-window mentions

**Files:** `temp/S0364_terminology_inventory.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Grep all user-facing strings (`app_v2/src/main/res/values*/strings*.xml`) and docs (`docs/**`) for current Browse-window terms and record each hit (file, key, locale, current wording, proposed wording) in `temp/S0364_terminology_inventory.md`. Seed terms to search: RU `обозреватель`, `просмотр папок`, `обзор файлов`, `окно просмотра`; EN `Browse view`, `file browser`, `browser` (filter out web-browser hits); UK `оглядач`, `перегляд файлів`. CRITICAL: exclude web-browser context - in `strings_google_account.xml` and any Chrome Custom Tabs / sign-in string, `браузер` means web browser; mark those `EXCLUDE`. Classify each hit `CHANGE` or `EXCLUDE`.

**Verification:**

- `Glob` - `temp/S0364_terminology_inventory.md` exists.
- `Grep` - `EXCLUDE` present in the inventory (web-browser hits explicitly classified).
- `Grep` - at least one `CHANGE` row present in the inventory.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. temp/S0364_terminology_inventory.md created. 8 string CHANGE rows (A-H), 9 doc CHANGE rows (FEATURES ×3 locales), plus EXCLUDE sets (web browser, verbs, metadata, machine artifacts, EN-only proper noun where RU/UK consistent, historical/developer docs, policy glossary forbidden-term mention). Classification rule documented: only window-as-noun renamed; verb "browse" and consistent EN proper noun left as-is.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the three policy docs via `.\scripts\add_to_dev_log.ps1` (inventory in `temp/` is not logged).

---

## Handoff Notes to Next Phase

`temp/S0364_terminology_inventory.md` lists every `CHANGE` string/doc occurrence with proposed wording and every `EXCLUDE` web-browser occurrence. Phases 05 (strings) and 06 (docs) apply only the `CHANGE` rows.

---

## Rollback Plan

Revert the three policy-doc edits; delete the `temp/` inventory. No user-facing surface changed.
