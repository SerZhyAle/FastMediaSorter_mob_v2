---
name: background-task-exit-code-is-echo
description: Background bash task-notification "exit 0" reflects the trailing echo, not the real gradle/command exit; always read the log
metadata:
  type: feedback
---

When a background Bash task runs `gradlew ... > log 2>&1; echo "EXIT=$?"`, the
`<task-notification>` "completed (exit code 0)" reports the **script's** exit -
i.e. the final `echo`, which is always 0. It does NOT reflect whether gradle
passed or failed.

**Why:** the harness reports the exit of the whole command string. A trailing
`echo` (or any successful command) overwrites the meaningful exit. Observed
twice on this task: lint actually returned exit 1 while the notification said
"exit 0", which nearly led to declaring a broken CI "fixed".

**Second shape - the build never started.** An `a.ps1` gradle target (`fk`, `fu`,
`d`, ..) that finds `temp/BUILD.LOCK` held by a *concurrent session* prints
`BUILD.LOCK held - refusing to start a second gradle build` and still surfaces as
"exit code 0". Hit twice in one session (2026-07-27) while a sibling ran repeated
S1187 R8 proof builds: a `fk` and a `fu` both looked green and neither had run.

**How to apply:** never trust the notification's exit code for a chained
command. Always Read the captured log and grep for the real verdict
(`BUILD SUCCESSFUL` / `BUILD FAILED` / `Lint found N errors` / the explicit
`EXIT=$?` line you printed). Better: put the real exit on its own line
(`echo "GRADLE_EXIT=${PIPESTATUS[0]}"`) and read that, not the wrapper exit.

Require a **positive** marker, never absence-of-errors: `BUILD SUCCESSFUL` /
`Fast check passed.` must actually appear. On a busy tree, gate the run instead
of racing for it: `until [ ! -f temp/BUILD.LOCK ]; do sleep 5; done; pwsh -NoProfile -File ./a.ps1 fk`.
