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
- `go_home.yaml` - back out of any restored player/browse to the main resource tabs (resumeOnNextLaunch reopens the last file on cold start). Every capability flow runs this right after `permissions.yaml`.
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
  tree. This appears automatically as a standard-folder resource on the Local tab; the
  `/spec-prerelease` OWNER_TRIGGER import is one way to register it but is not required for the
  core flows.
- **Stylus handwriting off** so text-entry flows land their input. The runner sets this on every
  invocation (`settings put secure stylus_handwriting_enabled 0`); no manual step needed.

The runner needs `resumeOnNextLaunch` and `rememberTheFileList` to stay at their defaults; the
`go_home` and `downloads_sort_reset` fragments make flows deterministic against both. Flows do
not use `clearState`, because that would wipe registered resources.

Network/cloud capability flows are intentionally not in the active suite yet; they require
external reachability and are covered by later, environment-specific work.

## Oracle Convention

A green flow must prove behavior, not only "did not crash":

- assert the expected post-action element is visible;
- use a stable completion log marker where the app already has one;
- assert that the crash-report prompt is not visible after risky opens;
- avoid coordinate taps and regex catch-all locators for proof assertions.

See `WRITING_TESTS.md` for authoring rules and `config.yaml` for shared timeout values.
