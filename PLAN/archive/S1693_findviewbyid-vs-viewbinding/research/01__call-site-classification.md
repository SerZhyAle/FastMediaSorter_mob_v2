# Research 01 - findViewById call-site classification (S1693)

Captured 2026-08-21, read-only sweep (android-solution-researcher). Answers strategic §6 items 1-2.

## Current numbers (drifted from §0's 2026-08-15 baseline)

- `findViewById`: **428 occurrences in 92 files** (was 421/89 on 2026-08-15 - +7 calls in 6 days,
  the pattern is still being added, not just residue).
- `viewBinding = true` project-wide (`app_v2/build.gradle.kts:1063`); `dataBinding` absent.
- Wear module: Compose end to end, out of scope.

## Category A - legitimate by construction: ~336 calls (~78%)

- A1 custom View/ViewGroup subclasses: 8 files / 45 calls (`ui/common/widget/Settings*Row.kt`,
  `CollapsibleSectionHeader`, `TranslationOverlayView`).
- A2 ViewHolder/adapters on itemView: 13 files / 55 calls (`MediaFileAdapter` 10, settings/player
  adapters).
- A3 runtime-resolved layouts ViewBinding cannot see: ~9 files / ~40 calls - PlayerView controls
  resolved via `?attr/customPlayerControlsLayout` (`ExoPlayerControlsManager` 18), dynamic
  id-as-parameter lookups (`MainCommandOverflowMenuManager`, `SettingsActivity:594`).
- A3b `<include>` without id / ViewStub post-inflate: `PhotoVideoStandaloneActivity:254` (S1115
  comment), `StreamsActivity:854` (stub inflate).
- A4 `android.R.id.content` decor lookups: 9 calls.
- A5 binding-free dialogs/helpers, several deliberately documented (S0380, S0410, S1549,
  "host-neutral" comments): ~30 files / ~180 calls.
- A6 fragment-container lookups into a child fragment's own layout: `DuplicatesActivity`,
  `AuthSessionsActivity` (5 calls).
- Test source sets: 14 calls / 3 files - exercising custom views directly; exclude from any gate.

## Category B - the mixed-style defect: ~18-20 calls in 7 files (~5%)

A typed accessor already exists (AGP include-binding: `<include>` WITH `android:id` generates a
nested binding field), but the code reaches through untyped `binding.root.findViewById`:

- `ui/player/standalone/DocumentStandaloneActivity.kt` :208/:209/:491 (copyToPanel, moveToPanel,
  bottomPanelsContainer)
- `ui/player/standalone/PhotoVideoStandaloneActivity.kt` :206/:207/:568/:176 (4 confirmed)
- `ui/player/StandalonePlayerActivity.kt` :492/:648
- `ui/player/standalone/AudioStandaloneActivity.kt` :341
- `ui/settings/fragments/GeneralSettingsFragment.kt` :282 (cardGoogleAccount)
- `ui/settings/helpers/GoogleAccountSettingsHelper.kt` - 7 child lookups under that card
- `ui/settings/SettingsActivity.kt` :472 (titleRow, less certain)

Dominant sub-pattern: the Copy/Move destination panel include (`bottomPanelsContainer`, same id in
4 standalone layouts; `player_bottom_panels_container_content.xml` carries `copyToPanel:10`,
`moveToPanel:41`). CAVEAT: generated-field hypothesis read from XML, not build-verified (no fresh
build output in checkout) - the converting phase's `fk` is the verification; a call whose typed
field does not materialize stays as is.

## Category C - plain legacy: ~35-40 files / ~110-120 calls

Raw-inflate Activities/Fragments/dialogs never touching their layout's binding
(`NowPlayingBottomSheetFragment` 15, `VrSettingsBlockFragment` 14, `DebugActivity` 11, ..).
Mechanically convertible, but the A5/C boundary ("documented architectural reason" vs "nobody got
around to it") is NOT lexically decidable - both shapes look identical and only some carry a
comment.

## Gate feasibility (research item 2)

- A per-file lexical rule ("no findViewById in a file importing *Binding") false-positives on
  `ExoPlayerControlsManager` (18 legitimate calls) and `PlayerBindingSafeViews` (mixes A and B in
  one class, S0217/S1549) - an allowlist would be hand-maintained and rot.
- The project's existing mechanism fits exactly: a count-ratchet baseline
  (`scripts/quality/*-baseline.txt`, `-UpdateBaseline` ratchets down only, FATAL per-file delta vs
  HEAD in `-ScopeToFile` mode, as `assert-listener-symmetry.ps1:66` does). Production sources only
  (exclude `src/test*`, `src/androidTest*`).

## Coverage note

None of the Category B files has any unit/androidTest coverage; conversion relies on compile proof
(`fk`) plus the existing screens' device pass at release.
