# Phase 02 — IA Model

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Produce the canonical information architecture model for the settings surface: surface hierarchy, entity-type classification, placement checklist for new features, flavor gating contract, multi-input and responsive contracts.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `PLAN/S0119_settings-information-architecture-revision/docs/settings-inventory.md` exists with all required sections.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md` | New | ≤ 500 |

> No Kotlin files touched in this phase. Output is a design document only.

---

## Steps

### Step 2.1 — Define canonical surface hierarchy

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`

**Depends on:** — start of phase (Phase 01 completed)

**Prompt for developer:**

> Create `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`. Add a `## Surface Hierarchy` section that defines all allowed placement levels for settings elements. Levels to define: (1) top-level tab, (2) collapsible section within a tab, (3) dedicated management screen (separate Fragment/Activity), (4) contextual control on a feature screen (outside Settings), (5) onboarding / permission-request flow, (6) system redirect (OS settings). For each level, specify: the entity types that belong there, the navigation depth from the Settings entry point, the input activation requirement (touch / keyboard / D-pad must all work), and a one-sentence decision rule for choosing this level.

**Verification:**

- `Glob` — `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md` exists.
- `Grep` — `## Surface Hierarchy` matches in that file.
- `Grep` — `management screen` mentioned in that section.
- `Grep` — `system redirect` mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. docs/ia-model.md exists; Surface Hierarchy, management screen, system redirect all present. Dev log recorded.

---

### Step 2.2 — Define entity type classification and placement checklist

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`

**Depends on:** Step 2.1

**Prompt for developer:**

> Add `## Entity Type Classification` and `## Placement Checklist` sections. Classification must enumerate: preference (persistent toggle / value), capability-enable (turns on a feature capability), service-action (one-shot destructive or maintenance action), management-surface (opens a list/editor for a complex resource), permission-redirect (routes to OS permission dialog), debug/expert control (hidden by default or behind a flag). Checklist must be a sequential question set that a developer answers before adding any new settings entry. Questions must cover: scope (global vs contextual), persistence (permanent preference vs one-time action), flavor dependency, reversibility of the action, discoverability requirement (must be in primary Settings vs contextual is sufficient), and the resulting recommended placement level.

**Verification:**

- `Grep` — `## Entity Type Classification` matches in `docs/ia-model.md`.
- `Grep` — `## Placement Checklist` matches in `docs/ia-model.md`.
- `Grep` — `service-action` mentioned in the entity type section.
- `Grep` — `flavor dependency` mentioned in the checklist section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Entity Type Classification, Placement Checklist, service-action, flavor dependency all present in ia-model.md. Dev log recorded.

---

### Step 2.3 — Define flavor gating contract

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`

**Depends on:** Step 2.2

**Prompt for developer:**

> Add `## Flavor Gating Contract`. Using the Phase 01 inventory and the flavor matrix (`standard`, `lite`, `photos`, `legacy`), define: which top-level tabs are present in each flavor, which collapsible sections within tabs are hidden per flavor, and the rule for when a flavor-specific divergence remains a leaf-level gate vs. when it breaks the shared mental model. State explicitly that the top-level tab structure must remain consistent across flavors (tabs can be empty or hidden, but their conceptual slots do not move). Reference `BuildConfig.SUPPORT_IMAGES`, `SUPPORT_VIDEO`, `SUPPORT_AUDIO`, `SUPPORT_DOCUMENTS` as the existing gate mechanism.

**Verification:**

- `Grep` — `## Flavor Gating Contract` matches in `docs/ia-model.md`.
- `Grep` — `BuildConfig.SUPPORT_` mentioned in that section.
- `Grep` — `standard` and `lite` mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Flavor Gating Contract section present; BuildConfig.SUPPORT_, standard, lite greps pass. Dev log recorded.

---

### Step 2.4 — Define multi-input, responsive, and theme-parity contracts

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`

**Depends on:** Step 2.3

**Prompt for developer:**

> Add three sections: `## Multi-Input Surface Contract`, `## Responsive Contract`, `## Theme Parity Contract`. Multi-input: list required activation methods for each element type (toggle, spinner, button, management-surface), state that `SettingsKeyboardNavigationManager` is the existing implementation anchor, and define minimum focus-order requirements. Responsive: define at minimum three breakpoint modes — narrow portrait (< 420dp wide), standard (420–840dp), wide / tablet-like (> 840dp) — and state the layout rule for each (max content width, single-column vs multi-column within a section, how far related controls may drift from each other). Theme parity: state that every group header, collapsed/expanded state, helper button, and search result must pass WCAG AA contrast in both light and dark theme, and reference the existing day/night theme resource system.

**Verification:**

- `Grep` — `## Multi-Input Surface Contract` matches in `docs/ia-model.md`.
- `Grep` — `## Responsive Contract` matches in `docs/ia-model.md`.
- `Grep` — `## Theme Parity Contract` matches in `docs/ia-model.md`.
- `Grep` — `SettingsKeyboardNavigationManager` mentioned in the multi-input section.
- `Grep` — `WCAG` mentioned in the theme parity section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Multi-Input Surface Contract, Responsive Contract, Theme Parity Contract all present; SettingsKeyboardNavigationManager and WCAG greps pass. Dev log recorded.

---

### Step 2.5 — Define multilingual search contract

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md`

**Depends on:** Step 2.4

**Prompt for developer:**

> Add `## Multilingual Search Contract`. Define: the search corpus structure (canonical key + localized alias lists for EN / RU / UK), partial-word matching requirement, the rule that search results display in the active UI locale while matching against all locale alias sets, and the relationship between a search result and its canonical placement (result must navigate to the canonical element location, not a detached copy). State that `SettingsSearchRegistry` is the implementation anchor. Note that Phase 04 is the code phase that implements this contract; this section captures the design intent only.

**Verification:**

- `Grep` — `## Multilingual Search Contract` matches in `docs/ia-model.md`.
- `Grep` — `EN / RU / UK` or `EN/RU/UK` mentioned in that section.
- `Grep` — `SettingsSearchRegistry` mentioned in that section.
- `Grep` — `partial-word` mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Multilingual Search Contract section present; EN/RU/UK, SettingsSearchRegistry, partial-word greps pass. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] `PLAN/S0119_settings-information-architecture-revision/docs/ia-model.md` exists with all six required sections.
- [x] §6 blockers §6.1, §6.2, §6.3, §6.5, §6.7, §6.10, §6.11 marked `[x]` in INDEX.md.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in all files touched.
- [x] Dev log entry added for `docs/ia-model.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `docs/ia-model.md` is the authoritative placement model that Phase 03 uses to assign canonical locations.
- The Placement Checklist from Step 2.2 becomes the standard developer reference for future feature work.
- The Multilingual Search Contract in Step 2.5 is the design specification that Phase 04 implements in code.

---

## Rollback Plan

Revert phase commit(s) — no code changes, no data migration. Only `docs/ia-model.md` is produced.
