---
name: pwsh-bash-dollar-escape-trap
description: When invoking pwsh from bash with -Command "...", never write `\$VAR` inside the double-quoted bash string - bash strips the backslash and pre-expands the empty bash variable, producing a malformed PowerShell command that fails silently inside an `& {...}` script block.
metadata:
  type: feedback
---

Inside bash, the form `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -Command "& { script1.ps1; if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }; script2.ps1 }"` looks correct but is broken. Bash double-quotes still interpret `\$VAR` - the backslash is stripped, then bash tries to expand `$LASTEXITCODE` (a non-existent bash variable) to empty string, and PowerShell receives `if ( -ne 0 ) { exit }` which is a parse error. Inside an `& { ... }` script block this fails silently: the first script runs to completion, but everything after the broken `if` is dropped without any visible error.

The symptom is exactly what I hit in S0268 F2: chained `update.ps1 -Status Tactical; ./add_to_dev_log.ps1 ...; ./add_to_dev_log.ps1 ...; ...` produced only the first script's output, with no error message and no dev log entries from the later calls.

**Why:** bash double-quote variable expansion happens before PowerShell ever sees the string, and the escape sequence `\$` only means "literal $" in PowerShell parsing - it has no effect on bash. The combination `bash double-quote + PowerShell script block + escaped dollar` collapses to malformed input. [[pwsh-efficiency]] already mandates `-NoProfile` and batching, but its examples are run from PowerShell itself (where `$LASTEXITCODE` works as written); the bash-as-launcher case needs different quoting.

**How to apply:**

1. **Best:** newline-separated `-Command` with no script block, no `if`/`exit` between calls. Each script call is one line. PowerShell's `-Command` accepts multi-line strings via the bash `"..."` form (newlines are preserved). Example that WORKED for the 8-call F2 dev-log batch:

   ```
   "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -Command "
   ./scripts/add_to_dev_log.ps1 'a' 't' 'd1'
   ./scripts/add_to_dev_log.ps1 'b' 't' 'd2'
   ./scripts/add_to_dev_log.ps1 'c' 't' 'd3'
   "
   ```

2. **If you really need fail-fast between calls from bash:** use single-quoted bash to pass the script block verbatim. Single quotes prevent bash variable expansion entirely, so `$LASTEXITCODE` reaches PowerShell intact:

   ```
   "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -Command '& { ./scriptA.ps1; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; ./scriptB.ps1 }'
   ```

   This is what `scripts/catalog_sync.ps1` and the harness-safe examples in CLAUDE.md §"PowerShell Efficiency" Rule B implicitly assume - they run from PowerShell, where the issue doesn't exist.

3. **Native pwsh shell, no bash:** PowerShell as the parent shell has no quoting collision; the `& { script1; if ($LASTEXITCODE -ne 0) { ... }; script2 }` pattern works as documented in CLAUDE.md.

Pattern to avoid going forward: `bash -c '... pwsh -Command "& { ... \$LASTEXITCODE ... }"'` or its `Bash` tool equivalent with backslash-escaped dollars. The harness `Bash` tool runs under MSYS bash on Windows, so this trap applies to every cross-shell batch.
