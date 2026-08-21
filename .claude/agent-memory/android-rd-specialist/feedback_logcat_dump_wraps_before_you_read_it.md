---
name: logcat-dump-wraps-before-you-read-it
description: On the Samsung test device the logcat ring buffer wraps in about two minutes and cannot be enlarged - stream to a file across the scenario instead of dumping afterwards
metadata:
  type: feedback
---

On `RFCR110NBQJ` (Samsung SM-G996U1, Android 15) the logcat ring buffer is capped at 5 MiB and the
firmware refuses to raise it (`MAX log buffer size is 5 MiB`). At this app's log rate it wraps in
roughly two minutes. A `logcat -d` taken after a scenario can be missing the scenario completely
while still looking full and plausible.

Stream continuously to a file across the window you are measuring - start the capture before the
first action, stop it after the last - and grep that file. Never dump afterwards and reason about
what is in it.

**Why:** On 2026-08-11 a device verdict on S1579 reported "12 DiskReadViolation, none naming the
four sites" and concluded the fix worked. Its capture ended at 21:01:56; the camera opened at
21:02:00. The log held zero `S1579:` probes and zero camera binds - it had recorded the walk to the
camera, not the camera. What made the wrong conclusion look confirmed was a coincidence: the correct
streamed log **also** contained exactly 12 violations. Two different twelves, one matching number.

**How to apply:** For any StrictMode, probe-count or violation-count claim, capture by streaming and
say in the verdict where the capture starts relative to the first action - a capture that begins
after the action under test is not evidence about it. When a count matches an expectation
suspiciously well, check the capture's first timestamp before believing it. Related:
[[feedback_avd_evidence_traps_width_and_logs]], [[feedback_am_start_refused_for_non_exported]].


## The opposite mistake, same day (2026-08-21)

Harvesting too EARLY produces the same symptom as wrapping - zero matches - and reads as "the code does not log".

`am start` returns as soon as the intent is dispatched, not when the activity has run. Harvesting immediately after it captured a log whose **last line was timestamped four seconds before the first probe**. Two separate passes reported "0 matching lines" for probes that were present and correct; the third found all five.

**How to apply:** after launching, give the app a few seconds before harvesting, and check the **timestamp of the last line in the capture file** against the moment you expect the log - not just the match count the verb prints. A capture that ends before your event started cannot contain it, and the count will not tell you that.
