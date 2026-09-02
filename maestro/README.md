# Maestro Capability Suite

This directory contains the root Maestro capability-regression suite for FastMediaSorter v2
(S0551). It is separate from the per-ticket runner under `scripts/devtest/maestro/`.

## Runner

Use the project runner instead of calling Maestro directly:

```powershell
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -Json
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke -DeviceId emulator-5554
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\player -Json
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite maestro/features/player/player_image.yaml -Json
```

Runner contract:

- Full Maestro traces are written under `temp/`; console output is a compact verdict only.
- `-Json` emits `{ pass, total, failed, reason, flows:[{flow,pass,log}] }`.
- `-DeviceId <adb serial>` pins a device; omit it when exactly one device is online.
- Exit `0`: all selected flows passed.
- Exit `1`: bad args or no flow matched `-Suite`.
- Exit `2`: Maestro CLI not found.
- Exit `3`: at least one flow assertion failed.
- Exit `4`: execution error such as no device or Maestro runtime failure.

## Flow Map

`smoke/`:

- `app_launch.yaml` - launch, permissions, main resources surface.
- `local_browse.yaml` - legacy smoke for local browsing.

`critical/`:

- `file_operations.yaml` - copy + move from `Ops/src` to `Ops/dst`.
- `settings.yaml` - settings smoke.

`features/browse/`:

- `browse_all_images.yaml` - built-in All Images listing.
- `browse_filter.yaml` - include/exclude name filter.
- `browse_sort_empty.yaml` - sort + empty-state on the Downloads resource.

`features/files/`:

- `file_rename.yaml` - rename oracle.
- `file_trash_undo.yaml` - soft-delete + undo oracle.
- `file_overwrite.yaml` - copy conflict / overwrite guidance.

File-operation flows are device-only for now and are excluded from `-Suite all` on the emulator
default path. Run them explicitly once `Ops/src` and `Ops/dst` are registered as writable
operation resources and the file-operation menu is known tappable on that device.

`features/player/`:

- `player_video.yaml` - video render + play/pause controls.
- `player_image.yaml` - S0550 large-image regression.
- `player_audio_lyrics.yaml` - audio playback + lyrics overlay.
- `player_documents.yaml` - PDF, EPUB, and TXT renderers.
- `player_resume.yaml` - resume-position affordance.
- `player_info_dialog.yaml` - metadata dialog with audio section.

`features/settings/` (S1612 additions):

- `settings_search_navigates.yaml` - a query that MUST match returns results and jumping to one
  lands on the setting it named. Complements `settings_search.yaml`, which proves only the
  negative half: an index returning nothing for every input passes the empty-state test.

`features/statistics/` (S1612):

- `statistics_open.yaml` - the usage dashboard opens from its settings row and inflates its list.
  The flow enables collection first on purpose: the navigation row's visibility tracks
  `AppSettings.enableStatistics`, so on a build with collection off the row is absent entirely.
  `StatisticsActivity` is not exported, so this row is the only way in - `am start` is refused.

`features/text/` (S1612):

- `text_viewer_renders.yaml` - a `.txt` file renders in the dedicated text viewer.
  `player_documents.yaml` also opens `readme.txt` but asserts `mediaContentArea`, the generic
  player container that is visible for any media type; this flow asserts `textViewerContainer` and
  `tvTextContent`, so a regression routing text to the wrong viewer fails here.

`features/slideshow/`:

- `slideshow_basic.yaml` - slideshow start on a large image.

`features/edge/`:

- `edge_cases.yaml` - large (>100 MB) video opens without crash (S0550-class).

`device_only/` (excluded from `-Suite all`; run explicitly on a real device):

- `3d-video-sbs.yaml` - 3D side-by-side video; needs a real SBS test clip, not seeded.
- `3d-video-switching.yaml` - 3D mode switching; same media dependency.

`_shared/`:

- `permissions.yaml` - optional system permission taps.
- `navigate_to_add_resource.yaml` - shared add-resource navigation fragment.
- `go_home.yaml` - back out of any restored player/browse to the main resource tabs (resumeOnNextLaunch reopens the last file on cold start). Every capability flow runs this right after `permissions.yaml`. When backing out cannot reach the tabs - the foreground screen is not on the app's back stack, as under launcher mode - it relaunches the app instead of failing (S1673).
- `downloads_sort_reset.yaml` - scroll the open list back to the top (guarded `fabScrollToTop` tap), so a following down-only `scrollUntilVisible` reaches any target regardless of the per-resource scroll position restored by `rememberTheFileList`.

## Preconditions

Run against `standard-debug` (`com.sza.fastmediasorter.debug`). The capability flows expect:

- **Russian app locale** - flows locate tabs and localized controls by their RU labels
  (`Локальные`, `Загрузки`, sort labels, crash prompt). Set it once with
  `adb shell cmd locale set-app-locales com.sza.fastmediasorter.debug --locales ru`.
- The persisted `All Images` virtual-resource name can retain English or Russian from the locale
  active at provisioning. Flows that open it use `_shared/open_all_images.yaml`, which accepts
  either exact label.
- **Media permissions granted** and the welcome screen completed (first run done).
- **Seeded test media** under `/storage/emulated/0/Download/FastMediaSorter_Test/` via
  `scripts/utils/setup_test_media.ps1`.
- A **`Загрузки` local resource** over `/storage/emulated/0/Download` that flattens the seeded
  tree. This appears automatically as a standard-folder resource on the Local tab and is the only
  resource prerequisite for the core flows.
- **Stylus handwriting off** so text-entry flows land their input. The runner sets this on every
  invocation (`settings put secure stylus_handwriting_enabled 0`); no manual step needed.
- **Launcher mode** may be left on by any device test of the launcher desktop, and nothing turns it
  back off. It is not a precondition: `go_home.yaml` escapes the desktop by relaunching, so the
  suite runs either way. The runner still prints the state (`launcher-mode: on|off`) in its
  header, because otherwise it is invisible in every flow trace (S1673).

The runner needs `resumeOnNextLaunch` and `rememberTheFileList` to stay at their defaults; the
`go_home` and `downloads_sort_reset` fragments make flows deterministic against both. Flows do
not use `clearState`, because that would wipe registered resources.

Network/cloud capability flows are intentionally not in the active suite yet; they require
external reachability and are covered by later, environment-specific work.

## Preconditions - the suite assumes a ru-locale app

Many flows and every `_shared/` fragment address elements by their **Russian** visible text
(`"Общие"`, `"Оставить текущий"`, `"Отправить отчёт о сбое?"`) because those controls carry no
stable view id. If the app renders in another language, the tap simply finds nothing and the flow
fails with `Element not found` - a red result that says nothing about the product.

The app language follows the per-app locale, not only the device locale. Check and set it before
a run:

```powershell
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "cmd locale get-app-locales com.sza.fastmediasorter.debug"
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "cmd locale set-app-locales com.sza.fastmediasorter.debug --locales ru-RU"
```

Observed 2026-08-13 (S1612): an empty per-app locale on a device whose system locale is `en-US`
rendered the app in English and failed `_shared/settings_select_general_tab.yaml` on the very first
tap. Note that the console and the trace log render Cyrillic as `?????` on a Windows host even when
matching succeeds - a garbled log line is **not** evidence of an encoding failure, so diagnose from
the UI hierarchy, never from the log's rendering of the label.

## Preconditions - run in portrait

The suite is written and verified in **portrait**. Orientation is not cosmetic here: on
2026-08-13 (S1612) the settings search button was found to **dismiss `SettingsActivity`** instead
of opening the search overlay when the device is in landscape, which fails
`features/settings/settings_search.yaml` and `settings_search_navigates.yaml` on a step that has
nothing to do with search. That defect is tracked as **S1619**; until it closes, a landscape run
reports red for a reason the flow cannot express.

```powershell
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "settings put system accelerometer_rotation 0; settings put system user_rotation 0"
```

## Oracle Convention

A green flow must prove behavior, not only "did not crash". The rules are stated once, in
`WRITING_TESTS.md` section "Oracle convention" - that text is authoritative and is not
restated here, because the earlier second copy drifted from it.

Since S1612 the convention is enforced mechanically by
`scripts/quality/assert-maestro-oracle.ps1`, which runs inside the fast static gate batch
(`.\a.ps1 fg`). A flow breaking the convention fails the gate before it ever reaches a device.

See `config.yaml` for shared timeout values.
