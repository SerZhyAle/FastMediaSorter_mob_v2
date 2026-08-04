---
name: crash-scan-blind-to-java-crashes
description: Until S1332 lands, the /spec-prerelease and smoke crash scans cannot detect a Java crash at all - only ANR. A green verdict there proves less than it looks
metadata:
  type: project
---

`scripts/devtest/adb.ps1 log` pre-filters logcat to lines containing the package name or the literal
`FastMediaSorter` **before** the caller's `-Grep` runs (`adb.ps1:410-414`). Both crash scans then grep
`FATAL|beginning of crash|ANR in`:

- `.claude/skills/run-fastmediasorter/smoke.ps1:66`
- `scripts/devtest/prerelease-prepare.ps1:287` (the `launch-verify` stage that gates `/skill-release`)

Walk a real Java crash through both filters and nothing survives except the ANR branch:
`E AndroidRuntime: FATAL EXCEPTION: main` carries no package name so the pre-filter drops it;
`E AndroidRuntime: Process: com.sza.fastmediasorter.debug` passes the pre-filter but has no `FATAL`
so the caller's grep drops it; `--------- beginning of crash` has neither. Only
`ANR in <package>` contains both. Verified by reading all three files, 2026-07-31.

**Why:** the pre-filter's own comment claims "logcat does not stamp every line with the pid", which is
false - the default `threadtime` format carries pid in every line. The filter was written to match on
text because of that wrong premise. Timber's tag is the *class* name, so app log lines carry neither
pid-matching nor the package string.

**How to apply:**
- A green `launch-verify` or smoke run means "no ANR", **not** "no crash". Do not quote it as crash-free
  evidence until S1332 ships. For real crash evidence use the raw background capture
  (`adb logcat -v time *:V > temp/<Sxxxx>/run_*.log`) that `/spec-test-device` already writes unfiltered,
  or `adb logcat -d --pid=<pid>`.
- Expect the first `/spec-prerelease` after S1332 to possibly go red where it used to be green. That is
  a crash being found for the first time, not a tool regression.
- Same root cause explains a probe hunt reporting `OK 0 line(s)` while the capture file it just wrote
  contains the string - see [[adb-swiss-army]].
