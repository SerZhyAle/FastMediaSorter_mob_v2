---
name: edit-line-delete-splice
description: Deleting a line via Edit with old_string starting with "\n" splices neighbours on CRLF files - use the full adjacent line instead
type: feedback
---

When deleting a whole line (e.g. a debug probe `Timber.d("Sxxxx: ..")`), do NOT use `old_string = "\n<indent>Timber.d(..)"` -> `""`.

**Why:** On this repo's CRLF `.kt` files that pattern mis-bit the trailing newline instead of the leading one, gluing the previous and next lines onto one line (`apply(binding)        setupMenu()`, `launchimport java.util.UUID`, `{        val actions`). It compiled-failed with "Expecting a top level declaration" / "Unexpected tokens", and the parser aborts the file on first syntax error so later splices in the same file stay hidden until the next compile. Cost a full extra fix+recompile cycle on the 2026-07-03 Block-A archive.

**How to apply:**
- Delete a line deterministically by matching the target line PLUS its full next line, replacing with just the next line: `old="<probe line>\n<next full line>"`, `new="<next full line>"`.
- After a batch of line deletions, before compiling run a splice sweep: `grep -nE '[^[:space:]] {4,}[^[:space:]]|[a-zA-Z]import '` over the edited files (KDoc ` *   ` lines are false positives - filter them).
- The glued gap equals the TAIL line's indent (8 for method body, 12 for lambda body) - handy when reconstructing.
