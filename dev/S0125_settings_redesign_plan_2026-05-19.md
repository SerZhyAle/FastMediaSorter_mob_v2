# S0125 Settings Redesign Reboot Plan

## Goal

Replace the misleading mirror-host track with a native, schema-led settings redesign while keeping the legacy settings screen as the stable public path until revised content is genuinely distinct.

## Phase R0 - Retract false exposure

- Remove Main and Browse entry points that publish the mirror-based revised host.
- Restore product messaging so the app no longer claims dual-run settings access.
- Keep the revised code path internal-only until a native redesigned slice exists.

Verification:

- `btnSettingsRevised` absent from both `activity_main.xml` source sets.
- Browse no longer passes a revised automation settings callback into the resource ops menu.
- `app_v2:assembleDebug` passes.

## Phase R1 - Build the redesign foundation

- Introduce a settings schema model for page / section / row composition.
- Derive search metadata from the same schema instead of a hand-diverged registry.
- Add reusable section-card shell primitives for narrow and wide layouts.

Verification:

- Revised shell can render a page from schema data without including legacy settings XML.
- Search destinations can be resolved from schema ids.

## Phase R2 - Native General

- Rebuild General with the new card/section model.
- Keep all mapped General behaviors, routes, summaries, and dependent controls.
- Exclude debug-only and legacy-only deferred surfaces from the first public rollout.

Verification:

- Revised General does not bind `FragmentSettingsGeneralBinding`.
- Revised General layout does not include `fragment_settings_general`.
- Search can reveal at least one General control through the native section model.

## Phase R3 - Native Operations

- Rebuild Operations using the same schema and section-card system.
- Present scheduled automation as a management cluster, not as legacy row spillover.
- Preserve `EXTRA_SOURCE_RESOURCE_ID` semantics where they remain relevant.

Verification:

- Revised Operations does not bind legacy destinations/settings layout trees.
- Scheduled automation flow remains reachable and state-correct.

## Phase R4 - Native Media and Playback

- Remove hosted legacy fragments from Media and Playback.
- Recompose both pages with the same reusable primitives.
- Preserve all flavor gates and conditional child controls.

Verification:

- Revised Media and Playback no longer host legacy fragments as primary content.
- Search and focus handoff still work for all revised pages.

## Phase R5 - Public re-exposure

- Re-enable Main/Browse revised entry points only after the revised host is visibly distinct and behavior-complete enough for user testing.
- Update docs, parity artifacts, and changelog.

Verification:

- Public revised entry opens a native redesigned page.
- Product docs no longer overclaim or underclaim the rollout state.