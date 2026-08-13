# 03 - Existing flows + runner revision (resolves strategic §6.3)

Discovery 2026-06-20 (read-only).

## Runner `maestro/run-tests.ps1` - what to fix

- Hardcoded foreign paths: `c:\GD\tc\Programm\maestro\bin` (PATH inject) and `C:\Program Files\Java\jdk-21.0.10` (JAVA_HOME). Replace with binary auto-discovery by the same pattern as `scripts/devtest/maestro-run.ps1` (PATH, `MAESTRO_HOME\bin`, `%USERPROFILE%\.maestro\bin`) and JDK from environment, not a literal version.
- No off-context contract: it streams full Maestro output to console (expensive in agent context). Adopt the S0420 contract - write full per-flow trace to `temp/<flow>_maestro_<TS>.log`, emit only a one-line verdict (or `-Json` object), aggregate per-flow pass into one suite PASS/FAIL.
- No stable exit codes. Adopt S0420 table: 0 pass, 1 bad args, 2 Maestro CLI missing, 3 flow failed, 4 execution error.
- Keep the existing invocation surface (suite/category/flow selection) + add device pin.

Reference contract to mirror: `scripts/devtest/maestro-run.ps1` (single flow). The suite runner wraps it over a discovered flow set.

## Per-flow verdict (11 flows + 2 shared)

- `smoke/app_launch.yaml` - REWRITE. All asserts `optional`. No hard guarantee.
- `smoke/local_browse.yaml` - REWRITE. `assertVisible id: ".*recycler.*" optional` + regex id (unsupported) - never fires.
- `smoke/media_play.yaml` - DROP. Every step `optional`; regex ext text matchers don't work. Replace with real player flows (Phase 04).
- `smoke/image_view.yaml` - DROP. All `optional`, regex id. Replace with real image flow (Phase 04).
- `smoke/3d-video-sbs.yaml` - KEEP (minor). Hard asserts on real dialog strings + crash guards. Needs 3D test media.
- `smoke/3d-video-switching.yaml` - KEEP (minor). Same class, real asserts.
- `smoke/video_prefetch_indicator.yaml` - REWRITE. Prefetch asserts `optional` + needs network source.
- `critical/file_operations.yaml` - REWRITE. Only `rvMediaFiles` assert, `optional`; op taps `optional`; regex long-press. Effectively no-op.
- `critical/settings.yaml` - REWRITE. Toggle ids `optional`; persistence check not attempted.
- `critical/video_offload_flow.yaml` - REWRITE. All `optional`; regex ids. Only trailing `recyclerView` assert, also optional.
- `_shared/permissions.yaml` - KEEP. Correct: optional taps on system permission dialogs is the right pattern for a utility flow.
- `_shared/navigate_to_add_resource.yaml` - KEEP. Has a genuine hard `assertVisible id: layoutResourceTypes`. Reusable.

## Maestro authoring constraints learned

- Regex in `id:` / `text:` matchers (`.*recycler.*`) does not reliably match - the existing flows rely on it and silently pass. Use exact entry-name id or exact text.
- `optional: true` on the assertion that is supposed to prove the behavior defeats the oracle. Reserve `optional` for genuinely variable UI (permission dialogs).
