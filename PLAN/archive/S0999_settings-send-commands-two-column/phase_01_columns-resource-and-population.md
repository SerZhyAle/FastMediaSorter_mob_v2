# Phase 01 - Columns resource + horizontal container + column-major population

**Status:** Pending

Single-phase change: make column count a config-resource and rebuild the group into weighted columns from Kotlin. `columns=1` must reproduce today's exact single-column look.

## Files touched

- `res/values/integers.xml` (or the existing integers resource file) - add `settings_send_commands_columns` = 1.
- `res/values-land/integers.xml` (NEW bucket if absent) - `settings_send_commands_columns` = 2.
- `res/values-sw600dp/integers.xml` (NEW bucket) - `settings_send_commands_columns` = 2.
- `res/layout/fragment_settings_playback.xml` - `containerSendCommands` becomes a horizontal `LinearLayout` (`orientation="horizontal"`) that will hold N weighted column children.
- `res/layout-land/fragment_settings_playback.xml` - SAME edit to the land twin (Rule 11 parity).
- `ui/settings/.../PlaybackSettingsFragment.kt` - `setupSendCommandsGroup()` column-building logic.

## Steps

1. Add the integer resource in three buckets (`values/`=1, `values-land/`=2, `values-sw600dp/`=2). Confirm no existing key collision.
   - Verify: `Grep settings_send_commands_columns res/values*` -> 3 hits; `values/`=1.
2. Change `containerSendCommands` to `orientation="horizontal"` in BOTH `layout/` and `layout-land/` `fragment_settings_playback.xml`. Keep the same id and card wrapper.
   - Verify: both files horizontal; ids unchanged; `res/layout-land` counterpart edited (Rule 11).
3. Rework `setupSendCommandsGroup()`:
   - Read `resources.getInteger(R.integer.settings_send_commands_columns)`.
   - If targets `<= 1` OR `columns <= 1` -> build a single full-width column (`MATCH_PARENT` rows) added to the container = today's behaviour exactly.
   - Else -> create `columns` vertical `LinearLayout` children, each `width=0dp, weight=1`; distribute the `SettingsToggleRow`s **column-major balanced** (fill left column ceil(n/2) rows top-to-bottom, then next column). Add each toggle row with `MATCH_PARENT` width inside its column. Preserve the existing async label/icon upgrade path (rows are the same objects, just parented into columns).
   - Keep D-pad/TalkBack order logical: column-major preserves vertical reading order within a column; set `nextFocusRight`/`nextFocusLeft` between adjacent column heads only if needed.
   - Insert probe `Timber.d("S0999: send-commands group rebuilt cols=$columns targets=${'$'}targetCount")` at the rebuild entry (last code edit before final build).
   - Verify: `Grep getInteger PlaybackSettingsFragment.kt`; column-major distribution present; single-column branch preserved; probe present.
4. Rotation handling (§7 risk): if `SettingsActivity`/fragment does NOT recreate on rotation (has `configChanges`), re-run `setupSendCommandsGroup()` from `onConfigurationChanged`. Check how the fragment currently handles config changes before adding - do not double-rebuild if recreation already happens.
   - Verify: rotation path re-reads the column count (either via recreation or `onConfigurationChanged`).

## Done criteria

- Column count driven by `@integer` resource (3 buckets); Kotlin reads it, builds weighted columns, distributes column-major balanced.
- `columns=1` / `<=1` target path identical to current single-column render (no narrow-portrait regression).
- Both layout orientations edited; probe present; `standard debug` builds green; detekt-clean (no magic numbers - `weight`/`0` are fine; any threshold via named const).

## Guardrails

- Do NOT change `SettingsToggleRow`, ShareTarget registry, or async icon/label loading (non-goals §2).
- No new strings (reuse existing toggles).
- Settings-doc-sync (Rule 22): this is a layout arrangement change, not a setting's presence/behaviour/naming - no manifest entry changes; if the post-change gate flags the settings surface, confirm no manifest delta is actually needed.
