---
name: probe-marker-must-start-a-line
description: A Probe literal/template/none marker buried mid-sentence in a status note registers zero contracts and the acceptance-probe gate passes vacuously - put it on its own line
metadata:
  type: feedback
---

`Probe literal:` / `Probe template:` / `Probe none:` in a `BlockNeedUserTest` status note only forms a
contract when the marker **starts a line**. The extractor in
`scripts/quality/lib/ticket-acceptance-probes.ps1` matches `(?im)^\s*Probe\s+(literal|template|none)\s*:`,
so a marker written mid-sentence is invisible to it.

**Why:** the failure is silent and reads like success. `assert-ticket-acceptance-probes.ps1 -Gate`
prints `expected: 0 | actual: 0` and exits 0 either way - the only tell is the trailing
`(contracts: N)` count. With the marker mid-line it says `contracts: 0`, meaning the gate examined
nothing at all, not that the ticket is clean. Measured 2026-08-16 on S1673.

**How to apply:** write the note as two lines - the device-test instruction first, then the marker on
its own line. From the Bash tool a `\n` inside `-StatusNote` will not survive; use the PowerShell tool
with a double-quoted here-string or `` `n ``:

```powershell
$note = "<what the user must test on device>`nProbe none: <named non-log evidence>"
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id Sxxxx -Status BlockNeedUserTest -StatusNote $note
```

The two-line note lands verbatim in the spec header under `**Status note:**` and is accepted there.

`Probe none:` is the marker for a ticket whose change touches **no Kotlin at all** (test infra, scripts,
docs). It only requires named evidence of at least 8 characters - naming the real proof (a suite
verdict, a runner header line) beats inventing a `Timber.d` tag in product code that the change never
touched. Related: [[feedback_probe_predicate_names_timber_form]],
[[feedback_blockneedusertest_status_before_gate]], [[feedback_status_note_quotes_corrupt_catalog]].
