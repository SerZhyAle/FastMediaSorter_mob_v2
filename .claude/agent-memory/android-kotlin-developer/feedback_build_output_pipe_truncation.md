---
name: feedback_build_output_pipe_truncation
description: Piping gradle to `tail -N` hides the FAILURE block; use `head -N` or save full log when build fails
metadata:
  type: feedback
---

When a gradle build fails, the FAILURE / "What went wrong" block sits in the MIDDLE of the output (right after the failing task), not at the end. The end is just gradle's deprecation footer and "BUILD FAILED in Ns".

So piping `./gradlew.bat <task> 2>&1 | tail -30` silently swallows the actual error message — you'll see "BUILD FAILED" but no diagnostic.

**Why:** Wasted a turn in S0250 trying to find a noLegal build failure that "tail -30" had cropped out, then had to re-run with `head -100` to capture the configure-time error.

**How to apply:**
- After a build fails, never investigate the failure via `tail -N` or `Select-Object -Last N` on the build log - the FAILURE block is in the middle. Use `pwsh -NoProfile -File .\a.ps1 bf` (the project's build-failures viewer) or pipe full stdout/stderr to `temp/build.log` and `Grep "FAILURE:" -C 80` that file.
- When the agent / harness reports "BUILD FAILED" with no compiler diagnostic visible, the diagnostic was cropped by the pipe. Re-run with a broader capture before guessing causes.
- For build commands expected to succeed (verifying my own change compiled), `tail -30` is fine for the verdict line - but never as the sole error-investigation tool.
- The harness exit code reported to ToolNotification reflects gradle's exit, not what made it into stdout - exit 0 with truncated stdout is PASS; exit non-zero with truncated stdout means I need to investigate with a wider capture before reporting a hard failure.
