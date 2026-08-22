# S1967 - Settings search jump lands in a collapsed section

**Status:** Archived

## 0. Symptom and evidence

Searching a setting and tapping its result navigates to the correct tab but the setting itself never
becomes visible, because the collapsible section holding it stays collapsed.

Reproduced on the pre-release sweep of 2026-08-22 (emulator-5554, Pixel 9, API 35, standard-debug,
app locale `ru`). Maestro flow `maestro/features/settings/settings_search_navigates.yaml` failed:

- `Tap on "Language/Язык/Мова"` COMPLETED
- `Assert that id: .../rowLanguage is visible` FAILED after the 10 s `extendedWaitUntil`

Log: `temp/settings_search_navigates_maestro_20260822_151229.log`.
Suite: 20 of 21 flows passed; this was the only failure.

## 1. Where it comes from

`SettingsActivity.onSearchResultSelected` expands the destination section for exactly one tab:

```kotlin
if (item.destination == SettingsSearchDestination.MEDIA) {
    (getSettingsFragment(item.destination.tabIndex) as? MediaSettingsFragment)
        ?.ensureSectionExpanded(item.sectionId)
}
navigateToTarget(item.viewId, retryCount = 0)
```

Two consequences.

- `SettingsSearchDestination` has four members - GENERAL, MEDIA, PLAYBACK, OPERATIONS. Three of them
  get no expansion call.
- `GeneralSettingsFragment` installs `CollapsibleSectionsManager` and its sections default to
  collapsed (S0535). `rowLanguage` lives inside one, so `navigateToTarget` resolves the view, calls
  `requestRectangleOnScreen` on a collapsed container, and nothing reaches the screen.

`OperationsSettingsFragment.ensureSectionExpanded` already exists and is never called from anywhere -
dead wiring pointing at the same gap.

## 1a. Correction: adding the three missing calls would not have fixed it (2026-08-22)

Section 1 reads as if the fix is to call `ensureSectionExpanded` for the other three destinations.
It is not, and the reason is one level down.

`SettingsSearchTabMapping` assigns a section **per layout file**, not per row:

```kotlin
R.layout.fragment_settings_general to TabAssignment(GENERAL, "general")
```

and `LocalizedKeywordCollector` copies that single value onto every entry it finds in the file
(`sectionId = assignment.sectionId`). For MEDIA that happens to work, because each media sub-screen is
its own layout - `images`, `video`, `audio`, `documents` - so the layout and the section coincide.

For GENERAL they do not. `fragment_settings_general.xml` is one layout carrying **eight** collapsible
sections (`general__interface`, `general__main_window_interface`, `general__file_browser`,
`general__remote_sources`, `general__authorization`, `general__app_data`, `general__system`, and a
debug one). Every row in it is indexed as `sectionId = "general"`. So there is no value to hand an
expansion call: `"general"` does not name any of the eight. The same holds for PLAYBACK.

That is why the Maestro flow failed on **Language**, a GENERAL row, and why the ticket looked like a
missing call: the call is missing, but so is the argument it would need.

## 1b. The fix already has its mechanism - `ancestorIds`

`SettingsSearchIndex` already carries `ancestorIds` for every entry, populated by the same collector.
A collapsible section's container is an ancestor of the rows inside it, so the section holding a row
is identifiable without touching the layout-level mapping at all.

That gives a fix that is uniform across all four destinations:

1. `CollapsibleSectionsManager.register()` retains `key -> container id` (today it retains nothing) and
   gains an `expand(...)` entry point.
2. The expansion moves onto `BaseSettingsFragment`, keyed by the row's ancestors rather than by a
   section name - so a section is expanded when its container is among the row's ancestors.
3. `SettingsActivity.onSearchResultSelected` calls it for **every** destination and passes
   `item.ancestorIds`, instead of special-casing MEDIA.

This answers open question 1 by construction: the behaviour lives on the base, so a new tab cannot be
added without it - which is exactly the failure this ticket is.

Note that Media still needs its lazy child attachment (`ensureChildAttached`): its sections build their
child fragment on first expand, so expanding the container is necessary but not sufficient there.

## 1c. Open question 2 is answered by precedent

"Should a jump into a section the user collapsed leave it expanded afterwards?" - Media already leaves
it expanded and has since it was written. Restoring the collapsed state on leaving would make the two
tabs behave differently for the same gesture, so consistency settles it: leave it expanded. No owner
decision needed.

---

## 2. Open questions

Both are answered above and need no owner:

- **Shared base rather than per fragment** - yes, and section 1b shows the shape. Keying on
  `ancestorIds` is what makes a shared implementation possible at all; a per-name API cannot work for
  GENERAL, which has one name and eight sections.
- **Leave expanded or restore** - leave expanded, by precedent (section 1c).

What remains is not a question but work: the three-step change in section 1b, plus a re-run of
`maestro/features/settings/settings_search_navigates.yaml` on an emulator, since that flow is the
evidence this ticket exists on and the only thing that can show the jump now lands visibly.

---

### 3.3 Owner inputs (Approval gate)

No owner decision is outstanding: both open questions of §2 are answered by the code itself and by
precedent, and §1a-1c record how. Nothing here changes what a setting does, only whether the user can
see the one they searched for.

- **Related tickets:** S1612 (wrote `settings_search_navigates.yaml`, the flow that caught this),
  S0535 (made the General sections default to collapsed - the condition the bug needs), S0780
  (Operations deep-link, the `ensureSectionExpanded` this ticket removes as dead)
- **Device:** emulator-5554, Pixel 9, API 35, standard-debug, app locale `ru` - the configuration the
  failure was reproduced on, and where the fix must be shown
- **Localization:** no user-visible string is added or changed

---

## 2a. Goals

1. A search result tapped in **any** of the four settings tabs leaves the target row visible, not
   hidden inside a section that stayed collapsed.
2. The behaviour lives on the shared base, so a fifth tab cannot be added without it - which is the
   shape of this very failure.

**Non-goals:**

- What the sections are, what they hold, or their default collapsed state (S0535 decided that).
- The search index itself: `ancestorIds` already exists and is already populated.
- Restoring the collapsed state after the user navigates away - section 1c settles this by precedent.

## 2b. Phases

### Phase 01 - The manager can expand a section by the row inside it

**Files:** `CollapsibleSectionsManager.kt`

**Prompt for developer:**

> `register()` already receives the container view but keeps nothing. Retain `container.id -> header`
> and add one entry point that takes a row's ancestor view ids and expands whichever registered
> section is among them. Return whether a section was found, so a caller can tell "expanded it" from
> "this tab has no collapsible section around that row" instead of guessing.

**Why:**

Section 1a: `sectionId` cannot name a GENERAL section, because one layout carries eight of them under
one name. The container's view id can, and `ancestorIds` already carries it.

**Verification:**

- `Grep` - `register()` stores the container id; the new entry point is the only reader of that map.
- `..ps1 fk` returns 0.

### Phase 02 - Every settings tab answers the same call

**Files:** `BaseSettingsFragment.kt`, the four settings fragments, `SettingsActivity.kt`

**Prompt for developer:**

> Put the expansion on `BaseSettingsFragment`, taking the row's `ancestorIds`, and let each fragment
> hand over the manager it already owns. Then make `onSearchResultSelected` call it for **every**
> destination instead of only MEDIA. Leave MEDIA's existing `ensureSectionExpanded` call in place: its
> sections attach their child fragment on first expand, so expanding the container is necessary there
> but not sufficient, and that path is already proven. Remove
> `OperationsSettingsFragment.ensureSectionExpanded`, which nothing calls and which the general path
> replaces.

**Why:**

Section 1b: keying on ancestors is what makes one implementation serve all four tabs, and putting it on
the base is what stops the next tab from repeating this bug.

**Verification:**

- `Grep` - `onSearchResultSelected` has no `if (destination == ..)` around the expansion.
- `Grep` - `OperationsSettingsFragment.ensureSectionExpanded` is gone and unreferenced.
- `..ps1 fk` returns 0; scoped detekt over the changed files returns 0.

### Phase 03 - The flow that caught it passes

**Files:** none - evidence only

**Prompt for developer:**

> Re-run `maestro/features/settings/settings_search_navigates.yaml` against an emulator. That flow is
> the reason this ticket exists and the only thing that shows the jump now lands visibly.

**Why:**

Section 3: the other search flow proves only that a no-match query shows the empty state. This one is
the positive half.

**Verification:**

- The flow completes, including the `rowLanguage` assertion that failed on 2026-08-22.

## 2d. What implementation changed about the plan (2026-08-22)

Two things worth the next reader's time.

**The expansion moved inside `navigateToTarget`'s retry loop, and the reason it moved was wrong.**
Phase 02 first called it once, right after selecting the tab. The Playback check then failed, and the
failure was read as a race - the destination fragment does not exist yet when a tab has only just been
selected, so an expansion aimed at it does nothing. The retry-loop placement was adopted on that
reading. The real cause of that particular failure turned out to be the test, not the product: the
throwaway flow typed the row's full title and then tapped by that same text, which matched the search
input it had just typed into. With a prefix query the same build passed.

The placement was kept anyway, and honestly labelled: `ViewPager2` does build a newly selected page's
fragment asynchronously, so the single-shot call really can miss on any tab but the one the screen
opens on - but this ticket never demonstrated it. It is defence against a mechanism that exists, not a
fix for an observed failure. Cost is a map lookup per retry, and retries happen only while the target
view is absent.

**A second flow was added rather than the failing one extended.** `settings_search_navigates.yaml`
covers General, which is the tab Settings opens on. The bug was that three destinations of four had no
expansion at all, so a suite that only ever exercised the opening tab could not have caught it and
would not catch the next one. `settings_search_navigates_other_tab.yaml` lands on a Playback row inside
a collapsed section.

---

## 2c. Acceptance criteria

1. Searching **Language** and tapping the result leaves `rowLanguage` visible - the exact case that failed.
2. The same holds for a row in a collapsed section of each of the other three tabs.
3. A row that is not inside any collapsible section still navigates as before.
4. `settings_search_navigates.yaml` passes.

---

## 3. Notes

Not caught before because `settings_search.yaml` proves only the negative half - a no-match query
shows the empty state. `settings_search_navigates.yaml` (S1612) is the positive half and is what
caught this.

---

## Last Audit

**Date:** 2026-08-22
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

The flow this ticket exists on passes: `settings_search_navigates.yaml`, whose
`Assert that rowLanguage is visible` FAILED on 2026-08-22 after its 10 s wait, now reports COMPLETED
on emulator-5554 with the same query.

Three of the four destinations were exercised on the device rather than argued from the code:
General through the original flow, Playback through the new
`settings_search_navigates_other_tab.yaml`, and Operations through a throwaway flow on `rowUseTrash`
that also passed and was then deleted, being a one-off observation rather than a suite member. Media
is EXEMPT from a new observation: its call site is the one path this ticket did not change, and it
worked before.

The mechanism is the row's `ancestorIds`, which the index already collected - the parser pushes every
enclosing element's id and a row's ancestors are what is on that stack. That is what makes one
implementation serve all four tabs where a section name cannot, because one General layout carries
eight sections under the single name `general`.

`OperationsSettingsFragment.ensureSectionExpanded` was removed. It was called by nothing and pointed
at the same gap this ticket closes.

Section 2d records the one place implementation departed from the plan, including a diagnosis that
turned out to be wrong - the retry-loop placement was kept for a reason it never actually
demonstrated, and says so.
