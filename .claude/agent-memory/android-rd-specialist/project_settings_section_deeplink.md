---
name: settings-section-deeplink
description: How to deep-link into a specific Settings group/section (open tab + expand collapsible section)
metadata:
  type: project
---

Deep-linking Settings to a specific collapsible group is done via Intent extras, not a router.

**Why:** SettingsActivity is a ViewPager2 with per-tab fragments and `CollapsibleSectionsManager` groups. There is no central section navigator; each tab fragment owns its sections.

**How to apply:**
- Build the intent with a `SettingsActivity` companion factory (mirror `GameLaunchIntents`): set `EXTRA_INITIAL_TAB` (TAB_GENERAL=0, TAB_MEDIA=1, TAB_PLAYBACK=2, TAB_OPERATIONS=3) + `EXTRA_EXPAND_SECTION` (String section id). Ready-made: `openStreamsSectionIntent`, `openProgramsSectionIntent` (S0780).
- The target tab fragment self-expands in `onViewCreated` via a `checkAndExpandSectionFromIntent()` that reads `EXTRA_EXPAND_SECTION`, matches its own section id, calls `ensureSectionExpanded(id)`, then `intent.removeExtra(..)` (consume so it doesn't re-fire on rotation). Mirror of `OperationsScheduledManager.checkAndExpandFromIntent` / `EXTRA_OPEN_SCHEDULED` (S0353).
- `MediaSettingsFragment.ensureSectionExpanded("streams")` attaches the lazy child + expands. `OperationsSettingsFragment.ensureSectionExpanded("additional_programs")` expands header + scrolls (S0780 added it; section ids: `SECTION_STREAMS`, `SECTION_ADDITIONAL_PROGRAMS`).
- To add a new target: register the section in that fragment's `ensureSectionExpanded` + `checkAndExpandSectionFromIntent`, add a section-id const + factory on SettingsActivity.
- Gotcha: `EXTRA_HIGHLIGHT_SETTING` exists but is NOT consumed anywhere (dead deep-link, parked S0829). Do not rely on it to scroll to a specific row.
