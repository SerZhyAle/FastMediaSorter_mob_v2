---
name: pre-s1332-log-evidence-untrustworthy
description: Device-test conclusions drawn before 2026-07-31 from "adb.ps1 log -Grep found nothing" are not evidence - the filter was dropping the app's own Timber lines
metadata:
  type: project
---

Any device-test verdict reached before **2026-07-31** that rests on `adb.ps1 log -Grep "Sxxxx"` returning
nothing is not proof the probe never fired. Until S1332 the verb applied `-Grep` to a set the app's own
lines had already been removed from: it pre-filtered on package-name *text*, and Timber tags a line with
the class name, so `D SomeClass: Sxxxx: ..` matched none of the three patterns. The answer came back as
`OK 0 line(s)` - indistinguishable from an honest no-match.

The same blind spot hid Java crashes from the pre-release launch scan: `prerelease-prepare.ps1` greps for
`FATAL|beginning of crash|ANR in`, and `E AndroidRuntime: FATAL EXCEPTION: main` names neither the package
nor the project, so only ANR was ever detectable. A launch-verify PASS from before that date does not mean
the app did not crash.

**Why:** S1332 fixed the filter (pid arm plus the text arm, and a `WARN` verdict when the filter swallows
lines the caller's pattern matched). The fix cannot retroactively repair conclusions already written into
`## Last Audit` blocks and `Broken`/`Partial` statuses.

**How to apply:** when a ticket is `Broken` or `Partial` and its audit reasoning is "the probe was not in
the log", re-run the device check before believing it - especially across the `BlockNeedUserTest` backlog.
A `WARN` verdict now means the filter really did drop matching lines and the capture file under
`temp/scratch/` is the fallback; a plain `OK 0` finally means what it says. Related: [[verify-full-evidence]].
