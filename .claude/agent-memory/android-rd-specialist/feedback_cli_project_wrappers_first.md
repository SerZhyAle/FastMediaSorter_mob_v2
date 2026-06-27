---
name: cli-project-wrappers-first
description: On this repo, shelling via nested PowerShell strings is a last resort; prefer direct exec, project scripts, and tiny temp .ps1 files for loops/batches
metadata:
  type: feedback
---

The failure pattern was self-inflicted: I kept "inventing a bicycle" with nested
PowerShell-in-PowerShell command strings, manual quoting, and ad-hoc loops, even
though the repo already has stable command conventions.

What to do instead on this project:

- Prefer one direct script call over `pwsh -Command '& { .. }'` string gymnastics.
- Use repo wrappers first: `scripts/spec_catalog/*.ps1`, `scripts/devtest/adb.ps1`,
  `scripts/add_to_dev_log.ps1`, `scripts/catalog_sync.ps1`, `a.ps1`.
- For batch work, if one clean one-liner is awkward, write a tiny UTF-8 `.ps1`
  under `temp/` and execute it with `pwsh -NoProfile -File ...`.
- Keep PowerShell in one layer end-to-end; avoid nested shell interpolation unless
  there is no cleaner path.
- When variables like `$id`, `$item`, `$LASTEXITCODE` matter, avoid outer double-quoted
  command strings that let the parent shell eat them before PowerShell runs.
- If a task is "archive all matching specs", first query the catalog cleanly, then
  loop with explicit ids/slugs; do not reconstruct state indirectly from shell text.
- Treat the project's CLI as an API surface: discover existing scripts first, then
  compose them, instead of building custom shell plumbing from scratch.

Rule of thumb:

- If the command needs escaping acrobatics, stop and switch to either a direct script
  invocation or a temp `.ps1` helper.
