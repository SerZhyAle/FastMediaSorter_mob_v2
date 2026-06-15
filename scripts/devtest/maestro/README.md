# Maestro flows - repeatable device-test regression core (S0420)

Maestro YAML flows drive per-tap UI automation **out of the LLM loop**. Maestro matches
elements by id/text natively, so a flow runs at native speed and costs ~0 LLM tokens - the
agent only reads the compact pass/fail verdict. This is the execution engine for the stable,
re-tested subset of the `/spec-sweep` device-test backlog; mobile-mcp stays for exploratory
and one-off tickets.

Strategic spec: `PLAN/S0420_maestro-device-test-flows.md`.

## Layout

- `maestro-run.ps1` (parent dir) - the runner. Discovers the Maestro binary, runs a flow
  against the selected device, writes the full trace to `temp/<flow>_maestro_<TS>.log`
  (off-context), and emits a one-line verdict. Stable exit codes mirror `device-ready.ps1`.
- `scripts/devtest/maestro/<Sxxxx>.yaml` - one flow per ticket, **named by ticket id**. The
  id-keyed name is what the Phase 2 `/spec-sweep` classifier matches on (auto-route when a
  flow exists).
- `_template.yaml` - annotated authoring template. Copy, rename to the ticket id, fill in.

## Install (local, pinned)

Maestro is a JVM CLI. Requirements: JDK 11+ on `PATH` (`java -version`). Install via Git Bash
(the repo's shell); on Windows the binary lands in `%USERPROFILE%\.maestro\bin`, which
`maestro-run.ps1` discovers automatically.

```bash
# Pin deliberately. Confirm the current line at https://maestro.dev before bumping.
export MAESTRO_VERSION=1.39.0
curl -Ls "https://get.maestro.mobile.dev" | bash
maestro --version   # must print MAESTRO_VERSION
```

`maestro-run.ps1` resolves the binary from, in order: `PATH`, `MAESTRO_HOME\bin`,
`%USERPROFILE%\.maestro\bin`. If none resolve it exits `2` with a pointer back here.

## Run

```powershell
# sole online device
pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml

# pin a device, machine-readable
pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml -DeviceId emulator-5554 -Json

# pass flow variables
pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml -Env "USERNAME=demo","PIN=0000"
```

Exit codes (see the runner header for the authoritative table):

- `0` - flow passed.
- `1` - bad args / flow file missing.
- `2` - Maestro CLI not found.
- `3` - flow failed (a step / assertion failed).
- `4` - Maestro execution error (no device, runtime/install error - flow never completed).

The full per-step trace is in the `temp/<flow>_maestro_<TS>.log` named in the verdict line;
read it only on a non-zero exit.

## Authoring a flow

1. Drive the scenario once manually (or via mobile-mcp) and capture stable element handles
   with `mobile_list_elements_on_screen` - prefer the Android resource-id entry name
   (`id: "btnNext"` matches `...:id/btnNext`) or visible text. Avoid coordinates.
2. Copy `_template.yaml` to `<Sxxxx>.yaml`, set `appId` to `com.sza.fastmediasorter.debug`,
   and script the steps with `launchApp`, `tapOn`, `assertVisible`, `inputText`,
   `assertNotVisible`, `repeat`.
3. `launchApp: { clearState: true }` resets app data for a deterministic start. System
   permission dialogs are **not** app UI - grant them with a `tapOn` against the dialog
   text, or pre-grant via adb in the harness, depending on the flow.
4. Run via `maestro-run.ps1`; iterate until green. Commit the YAML - it is reviewable in git.

## Scope note

This directory is the Phase 1 pilot of S0420 (engine + one example flow + docs). Phase 2
(deferred) wires `/spec-sweep` §4 classification to auto-route any ticket that has a flow
here, and feeds the runner output into the ticket's `## Last Audit` Manual block exactly as
`/spec-test-device` does. Author flows only for the regression core (tickets re-tested
often), not one-shot verifications - a flow file is upkeep.
