---
name: never-probe-with-the-closure-facade
description: Running post-change.ps1 with dummy target/description to inspect a gate writes a real, permanent changelog row
metadata:
  type: feedback
---

Never run `scripts/post-change.ps1` with a throwaway `-Target`/`-Description` just to see what a gate
says. Run the gate's own script instead - the facade prints the `repro:` line for exactly this.

**Why:** the facade's dev-log step is not a dry run. A probe invocation with `-Target "probe"
-Description "probe"` wrote a permanent `| probe | probe |` row into `dev/CHANGELOG.md`, which then had
to be removed by hand - and hand-editing that file is itself forbidden (CLAUDE.md section 12). The
duplicate-suppression guard does not help: it matches on the description, so a *different* description
is treated as a new change and always writes.

**How to apply:** to see a gate's detail, run the `repro:` command the failed closure printed, or the
gate script directly (`scripts/quality/assert-*.ps1`, `scripts/all_features/validate.ps1`). Re-run the
facade only with the change's **real** target and description - re-running the identical command is
safe and writes exactly one row. `-ShowSkips` is a flag on a real closure, not a way to look around.
