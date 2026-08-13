# Phase 04 - Cross-Cutting Research

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Produce artifacts for strategic §6.8 (re-entry/upgrade), §6.9 (flavor matrix), §6.10 (length/defaults), §6.11 (accessibility/input) - the constraints any recommended structure must satisfy.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (page order and download lifecycle fixed by artifacts 05 and 07).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0395_welcome-screens-redesign-research/research/08__reentry-upgrade.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/09__flavor-matrix.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/10__length-defaults.md` | New | ≤ 300 |
| `PLAN/S0395_welcome-screens-redesign-research/research/11__accessibility-input.md` | New | ≤ 300 |

---

## Steps

### Step 04.1 - Research re-entry and upgrade behavior

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/08__reentry-upgrade.md`
**Depends on:** - start of phase

**Prompt for developer:**

> From artifact 01's show-conditions plus the welcome-launch decision point (find where the app decides to show `WelcomeActivity` - first-run flag in prefs/DataStore) author the artifact answering: do existing users see the redesigned onboarding after an app update (current flag semantics); can onboarding be re-run from settings today (search settings for a welcome/onboarding entry point); how form pages must pre-populate from already-persisted settings (language, theme, profile, S0391 toggles, functionality toggles) so re-entry never resets user choices; that re-entry must not re-download installed deliverables (artifact 06's installed-state checks). Conclude with show/re-entry rules for the redesign.

**Verification:**

- `Glob` - `research/08__reentry-upgrade.md` exists under the ticket folder.
- `Grep` - `pre-populat` (case-insensitive) present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. welcome_completed untouched (migration coupling); upgraders get a one-shot pointer (recommended); pages render from real settings (no wizard state); re-entry preset/CLEAR_TASK defects fixed in skeleton ticket.

---

### Step 04.2 - Build the page × flavor matrix

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/09__flavor-matrix.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read the flavor matrix in `app_v2/build.gradle.kts` (standard, lite, photos, legacy + VR family incl. noLegal) and `dev/FLAVOR_DEVELOPMENT_RULES.md`. Using artifacts 02-07, author the matrix: for each target page (0,1,2,3,4,5) × each flavor - present, partially present (which toggles/buttons remain), or absent; the collapse rule for a page with zero available items; legacy API-23 specifics (from artifact 05); VR-family onboarding usability in-headset (controller/D-pad navigation - consult `temp/done/S0327_device-profile-onboarding.md` for prior VR onboarding decisions). State the source-set consequence: page visibility rules must flow through capability interfaces per `dev/FLAVOR_DEVELOPMENT_RULES.md`, never `BuildConfig` flavor guards in shared code. Conclude with the per-flavor page list.

**Verification:**

- `Glob` - `research/09__flavor-matrix.md` exists under the ticket folder.
- `Grep` - `lite` present, `photos` present, `legacy` present, `noLegal` present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Same six-page skeleton everywhere with per-flavor item subsets via availability interfaces; collapse rule "no actionable items → no page"; lite 4/5 pages, photos 4/5, others 5/6 (pre/post S0391); VR = panel+controller, only Quest sw-bucket needs a device check.

---

### Step 04.3 - Research onboarding length and defaults strategy

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/10__length-defaults.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Web-research mobile onboarding length/completion best practice (developer.android.com onboarding guidance, NN/g or comparable UX sources - cite URLs in `## Sources`). Author the artifact answering: is a 6-decision-page flow acceptable for this app's audience and what mitigations exist (progress indicator, sensible defaults, skip-all); the defaults strategy per page when the user skips - everything off, recommended-by-device-profile preset, or current-build behavior (compare options against strategic §3.2 "upgrade takes nothing away"); whether any owner-proposed page can default-and-hide instead of asking (candidates per artifacts 02-07). Conclude with a skip/defaults contract for every page.

**Verification:**

- `Glob` - `research/10__length-defaults.md` exists under the ticket folder.
- `Grep` - `## Sources` present with at least one `http` URL.
- `Grep` - `skip` (case-insensitive) present and `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS (4 cited URLs). Six pages defensible: count equals current flow, every page skippable with safe defaults, preference-question content type, five pages at launch pre-S0391. Zero-interaction acceptance criterion adopted.

---

### Step 04.4 - Research accessibility and input modes

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/11__accessibility-input.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Inspect the current welcome layouts (`app_v2/src/main/res/layout/page_welcome*.xml` + `layout-land` counterparts and `activity_welcome.xml`) for focus handling, `nextFocus*`, content descriptions. Author the artifact answering: what the pager framework needs for D-pad/TV traversal across interactive form pages (toggles, button grids); TalkBack semantics for toggle states and the recommended-profile badge (non-color state indication per strategic §3.2); landscape parity obligations for every new page (CLAUDE.md Rule 11) and systemBars/cutout safety (Rule 17); mouse support (Rule 16). Conclude with the accessibility acceptance checklist the dev tickets must inherit.

**Verification:**

- `Glob` - `research/11__accessibility-input.md` exists under the ticket folder.
- `Grep` - `TalkBack` present and `D-pad` present.
- `Grep` - `landscape` (case-insensitive) present and `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. S0289 slider covers D-pad/TV/mouse for free; work items = grid row-edge key consumption + nextFocus chains; TalkBack = native switch semantics + stateDescription + availability reasons; permissions-page conversion creates the missing landscape layout. Acceptance checklist defined for all page tickets.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] No source/config file modified - S0395 changes confined to the ticket folder + dev/CHANGELOG.
- [x] Dev log entry added for each artifact via post-change.ps1 (Doc) - 08, 09, 10, 11 recorded 2026-06-10.

---

## Handoff Notes to Next Phase

All 11 question artifacts now exist; Phase 05 synthesizes them into the recommendation and ticket split. Any contradiction between artifacts is resolved in synthesis, not patched here.

---

## Rollback Plan

Delete the artifact files - no code or data surface touched.
