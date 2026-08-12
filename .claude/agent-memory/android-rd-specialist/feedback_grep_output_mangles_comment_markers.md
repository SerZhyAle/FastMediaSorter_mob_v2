---
name: grep-output-mangles-comment-markers
description: Grep tool output can render Kotlin `//` as `\` and `/**` as `\**` - never report a syntax defect from Grep rendering, confirm with Read first
metadata:
  type: feedback
---

Never judge exact source characters from Grep tool output - especially comment markers. Confirm the region with Read before claiming a syntax defect.

**Why:** on 2026-08-11, grepping `CameraCaptureSessionManager.kt` returned lines rendered as `\ S1457: the crops below ..` and `\** S0753: digital (crop) zoom factor ..`. A leading `\` is not a Kotlin comment, so the file would not compile - which was the tell that the rendering, not the file, was wrong. Read showed the real content: `// S1457:` and `/** S0753:`. The running app on the device confirmed it compiles. Reporting "line 579 starts with a backslash and cannot compile" would have been a fabricated defect in a file that is fine.

**How to apply:** Grep stays the right tool for *locating* code and for reading the semantic content of a line. The moment the finding depends on the exact punctuation - comment syntax, escapes, quoting, a regex literal, a raw string - open the region with Read and quote from there. The same caution applies to any claim of the shape "this file cannot compile": if the code is live on a device or the build is green, the tool output is the suspect, not the source.
