---
name: tool-bypass-discipline
description: Measured top manual bypasses of existing tooling - cd-prefix, hand-rolled adb path, manual device probe, split next-id+insert
metadata:
  type: feedback
---

Do not bypass tooling that already exists. A transcript audit of my own work (607 sessions, 23 369 commands, 2026-07-17) found the biggest time-sinks are not missing automation - they are me re-doing by hand what a wrapper already does. The four measured offenders, most-frequent first:

1. **No `cd <repo>` prefix on bash commands** (2978 hits, top signature of all). The Bash tool's working dir persists between calls and is already the repo root - use absolute paths, never prefix `cd`. A `cd` in a compound command can also trigger a permission prompt.
2. **Never hand-roll the adb path** like `$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"` (~600 hits). Always `scripts/devtest/adb.ps1 <verb>` or `.\a.ps1 adb <verb>` - it auto-discovers adb (not on PATH) with stable exit codes. See [[adb-swiss-army]].
3. **Never hand-probe `adb devices` for readiness** (~180 hits). Use `scripts/devtest/device-ready.ps1` - it is the canonical pre-flight and adb.ps1 mirrors its discovery.
4. **Don't split next-id + insert** (~380 hits of `next-id.ps1` alone). `insert.ps1` auto-allocates the id when `-Id` is omitted; prefer `insert.ps1 -Slug <slug>` (added S-tooling 2026-07-17) to allocate the id AND build `PLAN/Sxxxx_<slug>.md` in one call.

Same class, already covered by their own entries (confirmed high-volume too): echo/flush probes -> [[no-flush-echo-commands]] (701 hits); bash `grep`/`rg` instead of the Grep tool -> [[rg-gitignore-skips-catalog-zone]] (1040 hits); general wrappers-first -> [[cli-project-wrappers-first]] and [[check-existing-tooling-first]].

**Why:** these are pure wasted tool calls - the automation exists, I just was not using it consistently. Adding more scripts does not help this class; discipline does. The measured volume proves the cost is real, not hypothetical.

**How to apply:** before issuing any Bash/PowerShell call, check I am not (a) `cd`-prefixing, (b) rebuilding a path a wrapper already knows (adb especially), (c) re-probing what a `*-ready` script does, or (d) calling `next-id.ps1` when `insert.ps1` would allocate. To recall a script's parameters, use `scripts/utils/help.ps1 -Name <script>` instead of re-reading the file.
