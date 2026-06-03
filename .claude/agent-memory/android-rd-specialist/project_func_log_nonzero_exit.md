---
name: func-log-nonzero-exit
description: add_to_functionality_log.ps1 leaves a non-zero $LASTEXITCODE on success, breaking chained pwsh blocks
metadata:
  type: project
---

`scripts/add_to_functionality_log.ps1` writes its `[FUNC_LOG]` line successfully but leaves `$LASTEXITCODE` non-zero, so a chained `& { add_to_functionality_log.ps1 ...; if ($LASTEXITCODE -ne 0) { exit } ; update.ps1 ... }` aborts before the later commands run.

**Why:** observed during S0335 spec-dev finalization — the func-log line landed but the chained `update.ps1 -Status BlockNeedUserTest` and dev-log never executed; journal stayed `In Progress` until run separately.

**How to apply:** when batching `add_to_functionality_log.ps1` with other pwsh steps, run it LAST in the chain, or run it as its own tool call, or don't gate following steps on `$LASTEXITCODE` after it. Always re-verify the journal status with `select.ps1` after a finalization batch that includes the func log. (Same fail-fast trap likely applies anywhere `_lib.ps1` Stop-preference scripts are chained — see [[project_spec_catalog_exit_code_contract]].)
