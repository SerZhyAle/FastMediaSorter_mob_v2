---
name: post-change-dev-log-first-file-only
description: A batched post-change.ps1 -Files closure writes ONE dev-log row naming the whole set (fixed 2026-08-08); a registered document in the set still blocks it until -RegistryAck names that document
metadata:
  type: feedback
---

**Fixed 2026-08-08.** `post-change.ps1 -Files "a.kt,b.kt,c.kt"` still writes exactly **one**
`dev/CHANGELOG.md` row - one row per logical change is the rule - but the row now names the whole set:
the primary file goes in the File column and the rest are appended to the description as
`[set of N: b.kt, c.kt]` (capped at 6 names, then `+N more`). Verified on the closure of that very
change: `... [set of 2: scripts/post-change.ps1]`.

**Why it mattered:** before the fix the row named only the first file, so a batched closure understated
its own scope and the set could not be recovered from the changelog. Caught by `/spec-check` on S1205
(2026-08-06): six files closed in one call, `post-change: PASS` printed, and a per-file dev-log criterion
was ticked on that verdict while only `LauncherCellCommand.kt` had a row. Worse, it silently punished
batching - the cheap way to close - and pushed callers into one `post-change` run per file, which is what
re-ran the detekt gate against an unchanged tree 530 times in three weeks.

**How to apply:** batching is now the preferred shape - name the whole changed set in one `-Files` call.
Use `close-and-log.ps1 -DevLogs '<json array>'` only when the files genuinely belong to *different*
logical changes and each deserves its own row. A `post-change: PASS` line is still a verdict about the
gates, not proof that any particular per-file criterion was met.
Related: [[feedback-verify-full-evidence]], [[project-detekt-verdict-cache]].

**Second trap in the same call - the registry gate needs an explicit acknowledgement.** When the
`-Files` set contains a **registered** document (`docs/DOCUMENT_REGISTRY.jsonl` knows it - e.g.
`docs/ALL_FEATURES.jsonl` under id `feature-inventory`), the `document-registry` gate SKIPs with
`registered document(s) changed and not acknowledged` and names the siblings that may need the same
edit. That skip does not fail the run, but it does mean the closure did not certify the doc change.
Re-run with `-RegistryAck '<registry-id>'` after actually reading the siblings it listed. Caught on
S1423 (2026-08-07): the ALL_FEATURES record cost a full second 60-second closure to acknowledge, and
the sibling in question (`docs/ALL_FEATURES.schema.json`) genuinely needed no edit - the record used
only existing fields. Plan for the ack in the FIRST invocation whenever the batch touches a doc.

**Third trap - a second closure for the same change writes a SECOND row when the primary file
differs.** The dev-log guard identifies a change by its primary file (the first `-Files` entry), not by
the description, so closing the same ticket twice - once for the code set, once for artefacts added
afterwards - leaves two rows even when the `-Description` text is byte-identical. Seen on S1859
(2026-08-21): the durable-evidence gate refused the `Verified` flip, the evidence extract was created
after the first `post-change: PASS`, and the follow-up closure led with the spec file instead of the
script. `dev/CHANGELOG.md` must never be hand-edited, so the extra row is permanent. **How to apply:**
create every artefact the closing gates will demand - the durable evidence extract above all - BEFORE
the first `post-change` call, and if a second call is unavoidable, lead it with the SAME primary file
as the first.
