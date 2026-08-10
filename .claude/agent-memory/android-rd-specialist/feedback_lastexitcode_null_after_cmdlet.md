---
name: lastexitcode-null-after-cmdlet
description: A `if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }` guard placed after a PowerShell cmdlet silently kills the rest of the batch with no output
metadata:
  type: feedback
---

In a batched `& { a; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; b }`, only put the guard after a
**native executable**. After a cmdlet (`Copy-Item`, `New-Item`, `Get-Item`, `Set-Content`, ..) the guard
misfires: cmdlets do not set `$LASTEXITCODE`, and in a fresh process it is `$null` until the first native
call, so `$null -ne 0` is true and the batch exits at step one.

**Why:** the failure is invisible. `exit $null` returns 0, the tool reports success, and the whole call
produces **no output at all** - the later steps just never ran. Hit 2026-08-08 batching
`Copy-Item` + `add_to_dev_log.ps1` + `select.ps1`: the copy landed, the other two never ran, and the tool
result was empty. An empty result read as "quiet success" rather than "aborted after step one".

**How to apply:** CLAUDE.md 7's batching idiom is for chaining `pwsh -File <script>` / `gradlew` style
calls. When a cmdlet is in the chain, either drop the guard for that step (cmdlets throw under
`$ErrorActionPreference = 'Stop'` anyway) or separate with plain `;`. Empty output from a batch that
should have printed something means the batch aborted early - re-run the steps individually rather than
assuming they succeeded. Related: [[background-task-exit-code-is-echo]].
