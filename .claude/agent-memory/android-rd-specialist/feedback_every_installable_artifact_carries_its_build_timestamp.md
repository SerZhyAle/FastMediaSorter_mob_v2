---
name: every-installable-artifact-carries-its-build-timestamp
description: Any artifact the owner can install or test must carry its real build date-time in versionName/versionCode - a frozen checked-in version on an installable APK is a defect, not a trade-off
metadata:
  type: feedback
---

**Every artifact that can be installed on a device must carry the timestamp of the build that produced it.**
A frozen or checked-in constant version is acceptable ONLY on compile-only checks that package nothing
(`fw`/`fwr`/`fwu`, `fk`/`fc`/`fr`). The moment a command writes an `.apk`/`.aab`, the version fields must be
stamped from the build clock.

**Why:** the owner tests on real hardware and reads the version string on the device to know which build he is
holding. Two builds separated by any amount of code change that report one identical version make on-device
testing meaningless - he cannot tell whether a fix is present, or whether an install even replaced anything.
He has asked for this across products for about eleven months (stated 2026-08-21, with considerable heat), and
it is also canon hard invariant 8 ("derive the version mechanically, never hand-bump it"). S1816 already fixed
exactly this for the wear debug builder on 2026-08-19; on 2026-08-21 I reintroduced the same failure from a
different direction by assembling the watch APK through `check-standard-fast.ps1 -Mode Assemble`, which packages
a real APK and stamps nothing - the installed version read six days OLDER than the build it replaced and needed
`adb install -d` to go on at all.

**How to apply:** before installing anything anywhere, read the builder's printed `Version override:` line - not
just its exit code and the APK mtime. A fresh mtime with a frozen version is the exact shape of this bug. If a
build path that produces an installable artifact does not stamp, that path is the defect: fix the script rather
than passing `-Pfms.versionCode`/`-Pfms.versionName` by hand at the call site. Repo detail: the phone debug
builders still default `-AutoVersion` OFF while the wear one defaults ON, so the phone side has the same hole
open - see [[wear-build-and-launch-gotchas]].
