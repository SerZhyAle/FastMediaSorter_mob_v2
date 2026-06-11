---
name: s0398-welcome-skeleton-inprogress
description: S0398 welcome-skeleton In Progress - Phases 01-02 done/build-green, resume at Phase 03 via /spec-dev S0398
type: project
---

S0398 (welcome-skeleton-form-pages, keystone of the welcome redesign) is In Progress as of 2026-06-11. Phases 01 (data-driven shell + Next-only, Skip button removed) and 02 (decorative-page + dead-path + 19-orphan-string removal) are DONE and build-green (standard + lite). Resume with `/spec-dev S0398` - it continues at Phase 03 (page-0 theme row). Remaining: 03 theme row, 04 networks decorative page, 05 re-entry fixes, 06 catalog cleanup.

**Why:** long multi-phase ticket split across sessions; PLAN/S0398_.../INDEX.md + phase Step Logs are the authoritative resume state, not chat memory.

**How to apply (gotchas captured live, not yet in code):**
- DEBUG-v013 has an AUTOMATED commit process (timestamp-named commits like "2606102356") that periodically commits the working tree - my S0396 + S0398 work got committed under those messages. This is NOT lost work; do not panic if `git log` shows your edits under a timestamp commit. A static stash `temp_stash_s0397` (another agent's player-refactor WIP) sits untouched - do not pop/drop it (no file overlap with welcome work).
- `ColorThemePrefs` (core/theme) has NO public getter for the current value - Phase 03.3 must add a small `getMode(context): String` to pre-check the theme toggle. Values: "AUTO"/"LIGHT"/"DARK"; dual-write = `setMode(context,value)` (SP mirror) + DataStore via settings repo; NO restart/recreate mid-welcome (force-light, deferred apply).
- `welcome_description_3` string is KEPT (reused by `player_first_run_hint_overlay_content.xml` - the pre-existing player touch-zones hint that already satisfies strategic §2.5). Do not remove it.
- Residual orphans (build-safe, Rule-21 follow-up): ~12 `welcome_feature_*` Extras-only strings remain unused after the Extras page removal; the 6 page-0-shared ones (photos/local_folders/network/cloud/sorting/slideshow) must survive.
- S0398 does NOT depend on S0396; visibility uses `MediaCapabilities` (added `supportsDefaultPlayer` field + 5 flavor modules; noLegal via vr mount).
