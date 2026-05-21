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
- The researcher does NOT run builds, but it DOES read build logs supplied by writer agents or sitting in `logs/`/`temp/`. When reading a build log, never trust a `tail -30`-cropped excerpt as evidence of the root cause - if the excerpt ends in "BUILD FAILED in Ns" with no diagnostic above, treat it as truncated and flag it in the report.
- Prefer reading the full log file via the `Read` tool (full file or `head 200`/middle region) over relying on a sub-agent's tail summary.
- The harness exit-code reported by a writer-agent reflects gradle's exit, not what made it into stdout - exit 0 + truncated stdout means PASS; exit non-zero + truncated stdout means the diagnostic must be re-fetched with wider capture before citing it as research evidence.
- If asked to summarise a build failure, cite the file path of the log and the exact line range containing the `FAILURE:` block - never paraphrase what you cannot quote.
