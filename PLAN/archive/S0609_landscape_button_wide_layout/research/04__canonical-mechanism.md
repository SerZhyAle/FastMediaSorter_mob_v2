# Research 04 - Canonical multi-column mechanism

Resolves strategic §6.4. Source: pattern survey across `app_v2` layouts (2026-06-22).

## Decision: weighted horizontal LinearLayout (reuse existing project pattern)

Already the de-facto convention in 4+ settings landscape layouts. Shape:

```
<LinearLayout orientation=horizontal baselineAligned=false tools:ignore="NestedWeights">
  <LinearLayout layout_width=0dp layout_weight=1 marginEnd=@dimen/dialog_field_spacing>
     <SettingsToggleRow layout_width=match_parent .../>
  </LinearLayout>
  <LinearLayout layout_width=0dp layout_weight=1 marginStart=@dimen/dialog_field_spacing>
     <SettingsToggleRow layout_width=match_parent .../>
  </LinearLayout>
</LinearLayout>
```

Reference: `layout-land/fragment_settings_general.xml` (separate-window+favorites group), `layout-land/fragment_settings_playback.xml`, `layout-land/fragment_settings_other.xml`, `layout-land/fragment_settings_images.xml`.

## Why this and not alternatives

- Pure XML - no Kotlin change. ViewBinding resolves the same ids in portrait and landscape, so binding/setup helpers are untouched (no positional child access found in `ui/settings/`).
- Works with all 4 row widgets unmodified (host-controlled width).
- `ConstraintLayout.Flow` - keep ONLY for button/chip groups (its existing role); it cannot enforce equal column widths for row widgets.
- `GridLayout` - rejected: brittle fixed column indices break when rows are conditionally hidden by flavor/capability.
- sw-qualified layouts - supplement only, deferred (see research 02).

## Constraints to respect

- No new nested weight depth beyond 2 columns (measure-pass cost); `tools:ignore="NestedWeights"` already used.
- `dialog_field_spacing` is the established inter-column margin dimen.
- `baselineAligned="false"` on every horizontal column container.
- No code in `Settings*ViewSetupHelper` needs orientation awareness; do NOT add runtime column logic (avoid pushing `GeneralSettingsViewSetupHelper` ~666 LOC toward the limit).
