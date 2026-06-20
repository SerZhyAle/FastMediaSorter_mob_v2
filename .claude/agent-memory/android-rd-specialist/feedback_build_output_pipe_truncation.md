---
name: build-output-pipe-truncation
description: Piping gradle to `tail -N` hides the FAILURE block; use `head -N` or save full log when build fails
metadata:
  type: feedback
---

When a gradle build fails, the FAILURE / "What went wrong" block sits in the MIDDLE of the output (right after the failing task), not at the end. The end is just gradle's deprecation footer and "BUILD FAILED in Ns".

So piping `./gradlew.bat <task> 2>&1 | tail -30` silently swallows the actual error message - you'll see "BUILD FAILED" but no diagnostic.

**Why:** Wasted a turn in S0250 trying to find a noLegal build failure that "tail -30" had cropped out, then had to re-run with `head -100` to capture the configure-time error.

**How to apply:**
- For build commands that might fail: use `head -200` or pipe to a file (`> temp/build.log 2>&1`) and grep afterwards, NOT `tail`.
- When you see "BUILD FAILED in Ns" with no diagnostic in your view, the diagnostic was cropped by your pipe. Re-run with broader capture.
- For build commands that should succeed: `tail -30` is fine for the verdict line. But never use it as the sole error-investigation tool.
- The harness exit-code reported to ToolNotification reflects gradle's exit, not what made it into stdout - exit 0 + truncated stdout means PASS; exit non-zero + truncated stdout means need to investigate with a wider capture.
