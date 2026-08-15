# Phase 01 - portrait-leading-anchor

**Goal:** Make the three top panels start their first working button at one shared leading X in portrait.

## Steps

- [ ] **1.1** Analyze the current leading geometry of each panel in `res/layout/`:
  - `view_main_programs_panel.xml` (programs panel + its start menu),
  - `view_main_streams_panel.xml` (streams panel + wide entry button),
  - the resource-tabs strip in `activity_main.xml`.
  For each, identify the first WORKING/content button and any leading service/entry control (start menu button, entry button) that precedes it, plus each panel's current start padding / marginStart / min-width.
  - Verify: written note of each panel's current leading offset + what the first working button is.
- [ ] **1.2** Introduce ONE shared leading anchor - a common `@dimen/main_top_panel_leading_anchor` (new dimen) applied as the effective start inset so the first working button of every panel aligns to the same X, regardless of a leading service control (the service control sits within/before that anchor without pushing it). Keep each panel's existing overflow/collapse/scroll mechanism intact - only normalize the leading start, not inter-element spacing.
  - Verify: all three first-working-buttons share the anchor; no hardcoded per-panel start px remains diverging.
- [ ] **1.3** No hardcoded hex; touch targets, focus order, D-pad/TalkBack, overflow discoverability unchanged.
  - Verify: `a.ps1 fr` (resources) PASS.

## Done criteria
- Portrait: the three panels visually share one leading anchor for the first working button.
