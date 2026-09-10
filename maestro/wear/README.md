# Watch flows (S2548)

Executable user paths for the Wear OS module, run by the same `maestro/run-tests.ps1` as the phone
tree. This root is **not** a copy of the phone flows and shares no file with them.

## Running

```powershell
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite wear -DeviceId <watch serial>
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite wear\wear_home_navigation.yaml -DeviceId <watch serial>
```

`-Suite all` never reaches here: its filter names `smoke`, `critical` and `features`, so a fourth root
is invisible to the phone run by construction.

## The serial is the only thing that says "watch"

Both modules publish under one `applicationId` (S1681, Play Services routes the Data Layer by it), so
the watch debug build and the phone debug build are the same package: `com.sza.fastmediasorter.debug`.
A watch flow aimed at a phone therefore launches the phone app, passes its opening steps and returns a
green verdict about the wrong platform. The runner refuses that pairing with **exit 5** before running
anything, reading `ro.build.characteristics` off the resolved target - including when the target was
resolved implicitly as "the single online device", which is exactly how the wrong one gets picked.

## Addressing

Tap an id, never a caption. The ids come from `WearTestTags` in
`wear/src/main/java/com/sza/fastmediasorter/wear/ui/testing/WearTestTags.kt` and reach the UiAutomator
tree because `testTagsAsResourceId` is declared once on the watch navigation root:

- `wear_nav_root` - the navigation host
- `wear_home_section_<section id>` - a home chip, in both the single-column and the grid layout
- `wear_settings_row_<route>` - a settings destination

A caption is translated into thirteen locales and an id is not, so a caption-aimed flow passes only on
the language it was written in. Use a caption only where the node is drawn by code this project does
not own.

## The bezel is not in the flow language

Maestro has no rotary expression. Reaching an off-screen item is a different question and stays inside
the flow, where `scrollUntilVisible` answers it - measured on a `ScalingLazyColumn` at 227 dp. Turning
the bezel itself is `scripts/devtest/adb.ps1 rotary -Axis <n> [-Repeat N]`, called by the runner around
a flow, never from a `.yaml`.

## Preconditions a flow may declare

A flow states its own precondition in its header; the runner reports an unmet one as `skip` with the
token as the reason, never as a failure. The spellings are the `excluded[]` reason codes of
`scripts/devtest/wear-prerelease-screens.json`, so the declared screen walk and this suite describe an
out-of-reach path the same way.

- `# maestro-requires: watch` - the target must report a watch.
- `# maestro-requires: seeded-content` - the stand must carry media; assert it with `-WithSeededContent`.
- `# maestro-requires: home-role` - the phone tree's launcher precondition, unused here.

## Relation to the declared screen walk

`scripts/devtest/wear-prerelease-walk.ps1` stays the pre-release verdict and answers "does the screen
open". These flows answer "does the path complete". Neither replaces the other while the suite has not
yet proved three consecutive identical runs.
