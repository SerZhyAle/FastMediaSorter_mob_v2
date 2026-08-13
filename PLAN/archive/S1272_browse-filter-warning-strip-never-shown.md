# S1272 - The Browse filter-warning strip exists in every layout but can never appear

**Status:** Archived
**Priority:** 40

## 0. Raw capture

Found while implementing S1227 (background-transfer indicator), 2026-07-29.

Evidence, live tree:

- `ui/browse/managers/BrowseStateUiUpdater.kt` - `updateFilterBadge()` runs `binding.tvFilterWarning.isVisible = false` unconditionally, then only ever updates the *badge* on `btnFilter`.
- No other write to `tvFilterWarning.isVisible` exists anywhere under `app_v2/src` for the Browse binding - the only other writer, `ui/main/helpers/MainLayoutChromeManager.kt`, drives the same-named view in `activity_main.xml`, a different screen.
- The view is nonetheless declared in all three Browse layouts (`layout/`, `layout-land/`, `layout-w600dp/activity_browse.xml`) with a `tools:text` filter description, and until S1227 it also consumed the navigation-bar inset in `BrowseEdgeToEdgeHelper`.

## 1. Why it needs its own ticket

Two readings, and they lead to opposite fixes:

- **Regression.** Browse once showed "Filter active: .." like Main still does, and a refactor to the filter *badge* left the strip switched off rather than deleted. Fix: restore the text, mirroring `MainLayoutChromeManager`.
- **Dead weight.** The badge deliberately replaced the strip, and the view plus its three layout declarations are leftovers. Fix: delete the view from all three layouts and drop the branch, per Rule 20.

Deciding needs the product call on whether Browse should restate an active filter in prose when the badge already carries the count. That is not derivable from the code.

**Decided by the owner 2026-07-31: REVIVE.** Browse restates the active filter in prose, the way Main
already does. The dead-weight reading is rejected - the strip is not leftover, it is a missing half.

### Quiz decisions (2026-07-31)

- Browse shows an active filter only as a count badge while Main also spells it out - revive, delete from Browse, or delete from both? → **Revive in Browse** (the two screens should read the same, and this is also the cheapest of the three - see §1.2).

## 1.1 Evidence added 2026-07-31 (/spec-next round 3)

Checked before parking, because the two fixes are not equally cheap and the draft priced them as if they were.

The strip is dead twice over, not once: every one of the three Browse layouts declares it
`android:visibility="gone"`, and `BrowseStateUiUpdater.updateFilterBadge()` then sets
`isVisible = false` unconditionally. The hard-off line sits exactly where a show/hide branch would
be, immediately before the badge logic - that reads as a deliberate switch-off during the badge
refactor, not as a branch someone forgot to write.

What the draft misses: `tvFilterWarning` is a **constraint anchor**. In all three layouts
`tvTransferIndicator` (S1227) anchors to it. Deleting the view is therefore not a three-line
deletion - it means re-anchoring the transfer indicator in `layout/`, `layout-land/` and
`layout-w600dp/activity_browse.xml`, and re-checking the inset-owner rule S1227 established.

The strip also still owns dedicated colours `@color/filter_warning_bg` and
`@color/filter_warning_text`, declared in both `values/colors.xml` and `values-night/colors.xml`.
Under Rule 20 the delete option owns those too - and `activity_main.xml` uses the same two colours
for the strip Main still shows, so they cannot simply be dropped.

`tools:text` preserves the intended wording: `⚠ Filter active: name contains 'photo', created after
01.01.2024`. So the revive option does not need new copy invented.

Neither `docs/ALL_FEATURES.jsonl` nor `dev/CHANGELOG.md` mentions a Browse filter-warning strip, so
the inventory records no promise to users either way. That is why the question stayed open: the code
proves the current state is dead, and cannot prove which state was wanted.

## 1.2 Correction 2026-07-31, before the quiz: reviving is the CHEAP option

§1.1 priced revive as "wire the sentence and translate it". Checked before asking the owner, and
that was wrong in the direction that matters - it made the cheaper option look dearer.

`MainLayoutChromeManager` does not merely have a strip, it actively drives one:
`binding.tvFilterWarning.text = activity.getString(R.string.filters_active, parts.joinToString(" | "))`
followed by `isVisible = true`. So the prose form is live on Main today, and the two screens really do
disagree in front of the user.

`filters_active` already exists in all three locales - EN `Filters: %s`, RU `Фильтры: %s`,
UK `Фільтри: %s`. Reviving therefore needs **no new string and no translation**, and no layout edit
either, since the view and its two colours are already declared in all three Browse layouts. It is
roughly fifteen lines in `BrowseStateUiUpdater.updateFilterBadge()`, mirroring the Main manager.

Deleting is the expensive one: three layout edits plus re-anchoring the S1227 transfer indicator in
each, and the two colours have to stay because Main still uses them. The costs are the reverse of
what §1.1 implied.

## 2. Scope note

S1227 does not depend on this either way. Its indicator stacks above `tvFilterWarning` exactly as the owner specified, and the inset owner is now chosen from the lowest *visible* strip, so the stack is correct whether the filter strip is revived or removed.

## 3. Fix

Mirror `MainLayoutChromeManager` inside `BrowseStateUiUpdater.updateFilterBadge()`: build the same
`filters_active` sentence from the active filter parts, set it on `tvFilterWarning` and show the view;
hide it when no user filter is set. The badge on `btnFilter` stays as it is - the two are
complementary, a count at a glance and the detail underneath, which is exactly the Main pattern.

The `tools:text` in the layouts spells the intent with a warning glyph and a longer phrasing than
`filters_active` produces. Use the shared string, not the mock-up text: one wording for both screens
is the point of the decision, and `docs/COMMUNICATION_POLICY.md` applies to any wording change.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1227 (owns the strip stacking and the inset-owner rule; its indicator anchors to this view)
- **UI / behaviour:** no new strings - reuses `filters_active`, already present in EN/RU/UK; no layout edit, the view exists in all three Browse layouts

---

## 4. Verification

- On-device: Browse, apply a filter (type / name / date) → the strip appears under the toolbar with the same sentence Main shows. Clear the filter → the strip disappears.
- Portrait, landscape and the `w600dp` tablet layout - the view is declared in all three.
- Regression: with a filter active and a background transfer running, the S1227 indicator still stacks above the strip and the navigation-bar inset is still taken by the lowest visible strip.

---

## 5. Related

- **S1227** - discovered here; owns the strip stacking and the inset ownership rule.
