---
name: java-home-stale-in-running-session
description: gradlew dies with "JAVA_HOME is set to an invalid directory" after a machine JDK update - the User env var is already correct, only the running session's inherited copy is stale; fix inline, never rewrite machine config
metadata:
  type: project
---

`gradlew.bat` aborting with `ERROR: JAVA_HOME is set to an invalid directory: C:/Program Files/Java/jdk-21.0.<old>` does **not** mean the machine is misconfigured. The User-scope `JAVA_HOME` on this machine points at `C:\Program Files\Java\latest\jdk-21`, a junction that the JDK installer re-targets on every update. A Claude Code session started *before* an update keeps the resolved old path in its inherited process environment, and env vars never refresh inside a running process tree.

Observed 2026-08-21: `jdk-21.0.10` -> `jdk-21.0.11` landed on disk, the session's `JAVA_HOME` still read `jdk-21.0.10`, and `.\a.ps1 fw` returned the digest `verdict: blocked / exitCode: 20` while `[Environment]::GetEnvironmentVariable('JAVA_HOME','User')` already read the correct junction path. A parallel session's `build-nolegal-debug.ps1` was failing the same way at the same moment.

**Why:** the failure text names a path, so the reflex is to "repair" the machine by pointing `JAVA_HOME` at the concrete new version directory. That is the wrong repair twice over - it edits the owner's machine config for a session-local problem, and it pins a version number that the next JDK update breaks again, whereas the junction survives it.

**How to apply:**

- Read the User-scope value **before** changing anything: `[Environment]::GetEnvironmentVariable('JAVA_HOME','User')`. If it already names `C:\Program Files\Java\latest\jdk-21`, the machine is fine - do not write to it.
- Fix per invocation instead, in the PowerShell tool, because neither tool persists shell state between calls:
  `$env:JAVA_HOME = 'C:\Program Files\Java\latest\jdk-21'` on the same command line as the gradle-backed call.
- Applies to every gradle-backed target for the rest of that session - `a.ps1 d/db/fk/fw/fu/..`, `post-change.ps1`'s settings-doc-sync gate, detekt.
- `latest\jdk-21` is a junction, not a copy: `dir "C:\Program Files\Java\latest"` prints `<JUNCTION> jdk-21 [C:\Program Files\Java\jdk-21.0.11\]`. Verify the target exists before blaming anything else.
- The repo's own `gradle.properties` already prescribes this junction for `org.gradle.java.home` (user-level, S1425), so Gradle proper resolves correctly - only the `gradlew.bat` launcher's own preflight reads `JAVA_HOME`. That is why the failure appears before any task runs.

Related: [[build-gotchas]], [[no-concurrent-gradle-invocations]].
