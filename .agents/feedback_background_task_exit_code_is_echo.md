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

**How to apply:** never trust the notification's exit code for a chained
command. Always Read the captured log and grep for the real verdict
(`BUILD SUCCESSFUL` / `BUILD FAILED` / `Lint found N errors` / the explicit
`EXIT=$?` line you printed). Better: put the real exit on its own line
(`echo "GRADLE_EXIT=${PIPESTATUS[0]}"`) and read that, not the wrapper exit.
