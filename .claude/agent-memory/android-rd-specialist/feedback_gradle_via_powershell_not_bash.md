---
name: gradle-via-powershell-not-bash
description: Run gradle-backed scripts (a.ps1 builds, checks, lint) through the PowerShell tool - the Bash tool's JAVA_HOME points at a JDK that does not exist
metadata:
  type: feedback
---

Launch every gradle-backed script through the **PowerShell tool**, not the Bash tool.

**Why:** the Bash tool's environment carries a stale `JAVA_HOME` (`/c/Program Files/Java/jdk-21.0.10`) pointing at a directory that does not exist on this machine - only `jdk-21.0.11` and `latest/jdk-21` do. `gradlew.bat` validates `JAVA_HOME` and aborts **before** it ever reads `gradle.properties`, so the user's `org.gradle.java.home` (`C:/Program Files/Java/latest/jdk-21` since S1425, 2026-08-05) never gets a chance to apply. The failure reads as a hard environment error:

```
ERROR: JAVA_HOME is set to an invalid directory: C:/Program Files/Java/jdk-21.0.10
```

The user-scope `JAVA_HOME` (`C:\Program Files\Java\latest\jdk-21`) is valid and the PowerShell tool sees it, so the identical command succeeds there. This is a shell-environment divergence, **not** a project defect - do not "fix" `gradle.properties`, `a.ps1`, or any builder script over it.

**How to apply:** any `a.ps1` target or `scripts/builders/*.ps1` / `scripts/utils/check-typo-lint.ps1` invocation goes through the PowerShell tool. Bash stays fine for reads, greps, `sed`, and parsing build logs and XML reports afterwards. If a gradle run dies instantly with a `JAVA_HOME` complaint, check which tool launched it before investigating anything else.

Related: [[aps1-launcher-pwsh-cwd]], [[build-output-pipe-truncation]].
