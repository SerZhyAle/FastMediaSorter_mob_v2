# Phase 07 — Docs / Catalog / Release notes cleanup

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01, 02, 03, 04, 05, 06
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Finalize: verify no ad-hoc collapsible headers remain anywhere in `app_v2/src/main/`, regenerate the catalog after the bulk of migrations, add a release-note line documenting the visible UI changes, and confirm the FEATURES trilingual docs do not need an update (strategic §8 says "Без изменений").

---

## Prerequisites

- [ ] Phases 02–06 all `✅ Done`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | n/a (generated) |
| `dev/CATALOG/app_v2.md` | Modified | n/a (generated) |
| `WHATS_NEW.md` | Modified | ≤ 30 of delta |
| `dev/CHANGELOG.md` | Modified | n/a (appended) |

---

## Steps

### Step 07.1 — Verify all migration sites are clean

**Files:** project-wide grep, no edits

**Prompt for developer:**

> Run these greps to confirm the migration is complete:
>
> - `Grep -r "▼" app_v2/src/main/java/` — zero hits (no remaining symbol-prefix logic in Kotlin).
> - `Grep -r "▶" app_v2/src/main/java/` — zero hits.
> - `Grep -r "string_format_two_args" app_v2/src/main/java/` — zero hits **outside** existing unrelated usages (verify any remaining hit is for a different feature, not for a collapsible-header prefix).
> - `Grep -r "ic_expand_more" app_v2/src/main/res/layout/` and `layout-land/` — zero hits.
> - `Grep -r "CollapsibleSectionHeader" app_v2/src/main/res/layout/` — count ≥ 25 (all migrated sites).
> - `Grep -r "bindSectionToggle" app_v2/src/main/java/` — zero hits.
> - `Grep -r "updateHeader\(" app_v2/src/main/java/` — zero hits (or only inside `CollapsibleSectionHeader.kt` if that internal method was named the same).
>
> If any hit appears that does not correspond to a known later/skipped scope, list it in the Blockers Log of `INDEX.md` and re-open the relevant phase.

**Verification:**

- All seven greps above produce the expected outcome.
- Step log records the actual counts for each grep with `expected: X | actual: Y` pairs.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: no ad-hoc collapsible prefix logic remains outside the canonical component or unrelated player affordances | actual: `▼` hits = 1 (`app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt` only); `▶` hits = 5 (`CollapsibleSectionHeader.kt`, `PlayerActivity.kt`, `PlayerBigButtonsModeManager.kt` comment, `StandaloneVideoTouchDelegate.kt`, `VideoTouchDelegate.kt`) with no remaining ad-hoc collapsible headers outside the canonical component. expected: `string_format_two_args` only for non-ad-hoc usage | actual: 1 (`CollapsibleSectionHeader.kt`); expected: `ic_expand_more` in `layout/` = 0 | actual: 0; expected: `ic_expand_more` in `layout-land/` = 0 | actual: 0; expected: `CollapsibleSectionHeader` count in `layout/` >= 25 | actual: 36; expected: `bindSectionToggle` in `app_v2/src/main/java/` = 0 | actual: 0; expected: `updateHeader(` in `app_v2/src/main/java/` = 0 | actual: 0. A missed `WearSyncSettingsFragment` ad-hoc header was found during this audit and migrated before final counts.

---

### Step 07.2 — Drop unused string resource and drawable references

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, possibly `ic_expand_more.xml`

**Prompt for developer:**

> Verify that `activity_player_unified_moveToPanelIndicator_text` (= "▼") is unreferenced after Phase 05 — `Grep -r "activity_player_unified_moveToPanelIndicator_text"` returns zero hits in `app_v2/src/main/java/` and `res/`. If confirmed unreferenced, delete the key from all three `strings.xml` files (EN/RU/UK). Run `scripts/check_strings_localized.ps1 -KeyPrefix "activity_player_unified_moveToPanelIndicator"` — exit code 0 means the key is consistently removed.
>
> Verify `ic_expand_more.xml` is unreferenced anywhere in the codebase. If confirmed, delete the drawable. If still used by anything outside S0256 scope, leave it and note in the step log.
>
> Verify `string_format_two_args` is still used by other features — if zero hits remain in the entire project, delete; otherwise leave.

**Verification:**

- `Grep -r "activity_player_unified_moveToPanelIndicator_text"` returns zero hits across the codebase.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "activity_player_unified_moveToPanelIndicator"` exits 0.
- `Glob` — `ic_expand_more.xml` either does not exist or its surviving references are listed in the step log.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `activity_player_unified_moveToPanelIndicator_text` refs = 0 across codebase | actual: 0; expected: deleted player indicator keys remain locale-consistent | actual: `check_strings_localized.ps1` reported no matching keys for `activity_player_unified_copyToPanelIndicator`, `activity_player_unified_moveToPanelIndicator`, or `panel_indicator_collapsed`; expected: `ic_expand_more.xml` absent or fully unreferenced | actual: file deleted and refs = 0. `string_format_two_args` kept because it is still referenced by `CollapsibleSectionHeader.kt`.

---

### Step 07.3 — Add release-note line

**Files:** `WHATS_NEW.md`

**Prompt for developer:**

> Append a single concise English line describing the visible change: "Unified the look of all expandable group headers across Settings, AddResource, ResourceEditor, ScheduledOperation, the Player Copy/Move panels, Duplicates, and Keybinding screens. All headers now use the same triangle indicator (▼/▶) and the same row layout. The 'About' section in General Settings becomes a virtual divider (no expand affordance)."
>
> Mirror this into `_RU` / `_UK` only if `WHATS_NEW.md` has those mirrors (verify with Glob). If only EN exists, append once.
>
> Tone: pass through `docs/COMMUNICATION_POLICY.md` §6 checklist before committing the line.
>
> No update to `docs/FEATURES.md` / `_RU` / `_UK` — strategic §8 explicitly says "Без изменений". This is a unification of existing UI elements, not a new user-facing feature.

**Verification:**

- `Grep -n "expandable group headers"` — present in `WHATS_NEW.md`.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual gate before commit).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: release-note line about unified expandable headers present in `WHATS_NEW.md` | actual: present once; mirrors added to `docs/WHATS_NEW_RU.md` and `docs/WHATS_NEW_UK.md`. Tone checked against `docs/COMMUNICATION_POLICY.md` §6: concise, user-facing, no bureaucratic phrasing, no FEATURES update required.

---

### Step 07.4 — Final catalog sync + spec status update

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `PLAN/S0256_collapsible-section-header.md`, `INDEX.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Final pass:
>
> 1. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` — regenerate.
> 2. Verify every `Sxxxx Phase NN` count in `dev/CHANGELOG.md` matches the expected file-touched count per phase (Phase 01: ≥4, Phase 02: ≥11, Phase 03: ≥2, Phase 04: ≥2, Phase 05: ≥7, Phase 06: ≥3).
> 3. In `INDEX.md`: bump every phase row to `✅ Done`, set `Phases: 7 / 7 done`, flip `Status:` to `Done`. Append a `Change Log` line.
> 4. Add `dev/CHANGELOG.md` entry: `Phase 07: docs/catalog cleanup; release notes added`.
> 5. Run `/spec-check S0256` — this transitions the strategic-spec status to `Verified` (or `Partial` / `Broken` if any criterion failed).

**Verification:**

- `Grep -n "Phases: 7 / 7 done"` present in `INDEX.md`.
- `select.ps1 -Id S0256` returns `Status: Verified` (or `Partial` if `/spec-check` found a partial).
- `Grep -n "S0256 Phase 07"` count ≥ 2 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: final catalog sync completed | actual: `scripts/catalog_sync.ps1 -Module app_v2` OK; expected per-phase dev-log minima | actual: Phase 01 = 5, Phase 02 = 14, Phase 03 = 5, Phase 04 = 4, Phase 05 = 14, Phase 06 = 6, Phase 07 = 9; expected: `Phases: 7 / 7 done` in `INDEX.md` | actual: present; expected: standard debug app compiles | actual: `build-debug.PS1 -SkipZip` passed (`assembleStandardDebug` inside wrapper). Strategic audit block updated and spec-catalog closure queued via `close.ps1`.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] `INDEX.md` shows `Phases: 7 / 7 done` and `Status: Done`.
- [ ] `/spec-check S0256` returns `Verified`.
- [ ] No stale `▼` / `▶` literals, no stale `bindSectionToggle` / `updateHeader` references in `app_v2/src/main/`.
- [ ] `WHATS_NEW.md` carries the release-note line; FEATURES docs untouched.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. No follow-up phases inside S0256.

Two natural follow-up tickets (out of scope for S0256, to be filed separately if desired):

1. **Persistence unification** — fold the various SharedPreferences files (`media_sections_state`, `playback_sections_state`, `settings_section_states`, `general_sections_state`, `add_resource_ui_state`, `resource_editor_ui_state`) into a single typed store. Strategic spec explicitly leaves this out (Non-goals).
2. **Help-text content pass** — fill in `csh_helpTitle` / `csh_helpMessage` for groups where the component is now ready to show a tooltip but no copy has been written. ADR-2 makes this a content-only change with no Kotlin work.

---

## Rollback Plan

Revert the final catalog regeneration + WHATS_NEW edit. The migrated screens (Phases 02–06) can be rolled back independently — each phase's Rollback Plan is self-contained.
