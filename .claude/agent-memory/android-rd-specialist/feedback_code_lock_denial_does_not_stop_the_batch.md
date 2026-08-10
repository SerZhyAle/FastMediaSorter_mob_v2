---
name: code-lock-denial-does-not-stop-the-batch
description: enter-code-lock.ps1 exit 4 prints a friendly "queued" message; a multi-line PowerShell tool call sails past it and the source edits land unlocked
metadata:
  type: feedback
---

Never put `enter-code-lock.ps1` on its own line above the commands it is supposed to gate. Guard it explicitly:

```powershell
pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason '<why>'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# .. edits here
```

**Why:** exit 4 means "queued, not yet your turn" and the script says so in a calm, helpful tone - it names your ticket number and suggests background work. Nothing about the output reads like a refusal, and the PowerShell tool happily runs the next line. Observed 2026-08-09 during S1420 phase 03: the lock was held by a sibling session (S1433), the acquisition failed, and the twenty seeder invocations underneath it wrote ten `values-*/strings.xml` files anyway. The batch even printed `seed failures: 0`, because the seeder genuinely succeeded - it was the lock that did not. The files happened to survive only because the other session was editing unrelated paths.

**How to apply:** any PowerShell tool call whose later lines touch sources, resources, build files or repo scripts. CLAUDE.md section 7 already prescribes the `if ($LASTEXITCODE -ne 0) { exit }` batching form - this is the case where forgetting it is silent rather than loud, so it is the one worth remembering. Related: [[feedback_lastexitcode_null_after_cmdlet]], [[feedback_code_lock_is_per_step_not_per_ticket]], [[feedback_do_not_idle_on_a_lock]].
