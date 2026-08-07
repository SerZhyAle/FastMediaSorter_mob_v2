---
name: post-change-dev-log-first-file-only
description: Traps in a batched post-change.ps1 -Files closure - it dev-logs only the FIRST file, and a registered document in the set blocks it until -RegistryAck names that document
metadata:
  type: feedback
---

`post-change.ps1 -Files "a.kt,b.kt,c.kt"` runs every **gate** across the whole set but writes exactly
**one** `dev/CHANGELOG.md` row - for the first file in the list. The others end the closure with no
dev-log entry at all.

**Why:** caught by `/spec-check` on S1205 (2026-08-06). Phase 02 closed six files through one
`post-change.ps1 -Files ... -ScopeToFile` call, the closure printed `post-change: PASS`, and the phase's
"Dev log entry added for every file in Files Touched" criterion was ticked on that verdict. Only
`LauncherCellCommand.kt` had a row; `LauncherCellCommandTest.kt` had none. The green verdict is about the
gates, not about dev-log coverage - reading it as both is what let the gap through.

**How to apply:** after a batched `-Files` closure, either grep `dev/CHANGELOG.md` for every path in the
batch, or use `close-and-log.ps1 -DevLogs '<json array>'` (one `{file,target,desc}` object per file),
which does log every entry. Never tick a per-file dev-log criterion off a `post-change: PASS` line.
Related: [[feedback-verify-full-evidence]].

**Second trap in the same call - the registry gate needs an explicit acknowledgement.** When the
`-Files` set contains a **registered** document (`docs/DOCUMENT_REGISTRY.jsonl` knows it - e.g.
`docs/ALL_FEATURES.jsonl` under id `feature-inventory`), the `document-registry` gate SKIPs with
`registered document(s) changed and not acknowledged` and names the siblings that may need the same
edit. That skip does not fail the run, but it does mean the closure did not certify the doc change.
Re-run with `-RegistryAck '<registry-id>'` after actually reading the siblings it listed. Caught on
S1423 (2026-08-07): the ALL_FEATURES record cost a full second 60-second closure to acknowledge, and
the sibling in question (`docs/ALL_FEATURES.schema.json`) genuinely needed no edit - the record used
only existing fields. Plan for the ack in the FIRST invocation whenever the batch touches a doc.
