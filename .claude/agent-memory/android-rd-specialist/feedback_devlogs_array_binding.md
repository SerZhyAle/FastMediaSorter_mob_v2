---
name: devlogs-array-binding
description: close-and-log.ps1 -DevLogs array must be called in-process with &, never via pwsh -File (array collapses across process boundary)
metadata:
  type: feedback
---

`close-and-log.ps1 -DevLogs @(...)` (and any PowerShell script taking an array param with multiple elements) must be invoked **in the same PowerShell process** with the call operator `&`, not through `pwsh -NoProfile -File script.ps1 -DevLogs $arr`.

**Why:** `pwsh -File` serializes each array element as a separate command-line token. Only the first element binds to `-DevLogs`; the rest hit the parser as positional args and fail with "A positional parameter cannot be found that accepts argument '{...}'". Cost two failed finalize calls on S0082 (2026-06-03).

**How to apply:** From the PowerShell tool (already pwsh), build the array then call directly:
`$dl = @('{json1}','{json2}',...); & scripts/spec_catalog/close-and-log.ps1 -Id Sxxxx -Status Verified -DevLogs $dl -FuncOp ADD -FuncDesc "..." -CatalogModule app_v2`
Single-element -DevLogs survives `pwsh -File`; multi-element does not. Same trap applies to Bash tool (`@(...)` triggers `bash: syntax error near unexpected token '('`) - use the PowerShell tool for any call passing a PS array.
