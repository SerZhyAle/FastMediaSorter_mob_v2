---
description: "Use when a spec must go from idea to a built, tagged, hand-off-ready state with no device and no emulator - none attached, none wanted, or the device belongs to another session. Same pipeline as /spec-all minus every on-device step: the build is the only test, and anything that ships ends at BlockNeedUserTest. Triggers: 'spec-code', 'no device', 'code and build only', 'do not touch the emulator', 'build-only pipeline'."
---

# Spec-Code - the device-free variant of /spec-all

**This is `/spec-all` with every on-device step removed.** Research, spec, tactical plan, implementation, documentation and the build run exactly as they do there. Nothing is installed, launched, tapped, screenshotted or logcat-harvested, and no device or emulator is even looked for. Compilation is the only verification this command performs; verification that needs a running app is handed to the owner as `BlockNeedUserTest` and drained later by `/spec-sweep`.

Full process: [.claude/commands/spec-all.md](.claude/commands/spec-all.md) - every stage, every hard-stop row, every constraint is identical, including the argument forms (`<idea text>`, `<path/to/idea.md>`, `<Sxxxx>`, `<slug>`). Read it; do not re-derive it here. The differences are exactly these seven.

## Start banner

Before Stage 0, print exactly:

```text
/spec-code: device-free - the build is the only test; anything that ships ends at BlockNeedUserTest. Use /spec-all when a device may be used.
```

Not optional. A run that silently skipped the device test reads like a run that passed one.

## Differences from `/spec-all`

1. **No device tool, and no probe for one.** Forbidden for the whole run, in every stage and in every sub-skill this command delegates to: `scripts/devtest/device-ready.ps1`, `scripts/devtest/adb.ps1` (and `a.ps1 adb`), `a.ps1 ivn` or any other install, `/spec-test-device`, `/spec-sweep`, `/verify`, the `run-fastmediasorter` skill, every `mobile-mcp` tool, and any emulator or AVD start. Section 11 of `.claude/reference/spec-all.md` (Device-test gate) does not apply here - do not read it and do not run it. `/spec-dev`'s own Device-test gate and its `--verify-smoke` option are off for the same reason: delegate with the literal context `device-free=spec-code` alongside `lease-owner=spec-code`, so `/spec-dev` stops at the status flip. **Not even "just to check whether one is attached"** - the probe is the step that turns into a run, which is exactly what the operator asked this command not to do.

2. **The build is the only test.** Mandatory whenever this run edited a file that ships (`.kt`, `res/**`, `AndroidManifest.xml`, `*.gradle.kts`, keep/proguard rules). Cheapest sufficient target first, per CLAUDE.md section 9 and Rule 6's 120 s threshold:
   - `a.ps1 dq` - standard debug, the default gate.
   - `a.ps1 fkn` - when a `noLegal` source set was touched.
   - `/build` -> `vr debug` - when `src/vr/` was touched.
   - `a.ps1 fw` / `fwr` / `fwu` - when `wear/` was touched; the phone targets exit 0 without compiling a single watch file (S1807), so quoting one over a `wear/` change records a verdict about the other module.

   MAX_BUILD_RETRIES is unchanged. Background the long targets, never the fast ones (Rules 6 and 26). No unit suite is added on top: `/spec-all` does not run one either, and `a.ps1 fu` stays a separate, deliberate decision.

3. **Two terminal statuses, decided by one test - did this run edit a file that ships in an APK?**
   - **Yes** -> final status `BlockNeedUserTest`. Probe tags go in before the last build as usual (CLAUDE.md "Debug Verification Tags"), and that build validates code plus tags in one pass. **Do not run `/spec-check` afterwards**: it flips the ticket out of `BlockNeedUserTest` and deletes the very tags the pending device test needs.
   - **No** (only `docs/`, `PLAN/`, `dev/`, `scripts/`, `.claude/`) -> run the F5 audit loop exactly as `/spec-all` writes it, ending `Verified` / `Partial` / `Broken`. Parking such a ticket as `BlockNeedUserTest` is wrong twice over: the mandatory status note would have nothing to ask the owner for, and the ticket would sit in the `/spec-sweep` backlog forever waiting for a test that does not exist.

4. **The status note is the entire handoff, and it is mandatory** (CLAUDE.md section 4, Block* note). Nothing runs on device in this mode, so `-StatusNote` is the only thing between the work and a ticket nobody can test. Name the screen or flow to open, the exact steps, what should be seen, and the `Sxxxx:` probe tags to watch for in logcat. "Test the fix" is not a note.

5. **Device-gated evidence is deferred in writing - never faked, never reasoned away.**
   - *UI phase evidence* (`/spec-dev`, S1338): the placement half - a `/ui-clarify` record or an owner ruling quoted verbatim - is still binding and is not device work. The screenshot half is recorded in the phase's Step Log as `screenshot deferred (no device - /spec-code)`; handle the phase exactly as `spec-dev.md` already prescribes for "no device attached", and name the screen in the status note.
   - *Bugfix repro record* (`/spec-dev`, S1338): still binding. Its escape hatch `REPRO: not reproducible on demand` means the defect does not reproduce - not that this run declined to look. Reaching for it because `/spec-code` has no device is gate laundering. A bugfix whose repro needs a running app ends at `BlockNeedUserTest` with the repro steps in the status note, which is the honest form of the same requirement.

6. **Final report.** Format is `/spec-all`'s (`.claude/reference/spec-all.md` section 9) plus one line, `Device: not used (/spec-code)`. When the ticket ended `BlockNeedUserTest`, add one Manual / unresolved item verbatim: `device test pending - run /spec-sweep (or /spec-test-device <Sxxxx>) when a device is online`. Release the ticket lease in the same `finally`-equivalent step `/spec-all` requires.

7. **Everything else is unchanged, and trimming it is not part of "no device":** the ticket lease, drift-check, the Simple/Full complexity assessment, the approval gate stub, out-of-scope parking via `/spec-draft` (CLAUDE.md 3.1), detekt-clean-first, closure through `post-change.ps1 -ScopeToFile` / `close-and-log.ps1`, the dev log, the `ALL_FEATURES` record, the hard-stop table and the defer-first rule. This command removes hardware from the pipeline, not rigour.

## When not to use this

- A usable device is attached **and** the ticket's acceptance is on-device -> `/spec-all` finishes it in one pass, while `/spec-code` deliberately stops one step short and buys a sweep later.
- A ticket already parked in `BlockNeedUserTest` and waiting only for its device run -> that is `/spec-sweep`, not this command.

Reach for `/spec-code` when there is no device, when the connected device belongs to another session, or when the owner's phone must not be touched by this run.
