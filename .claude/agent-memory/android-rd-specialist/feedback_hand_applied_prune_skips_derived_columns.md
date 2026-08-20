---
name: hand-applied-prune-skips-derived-columns
description: Applying a catalog prune by hand from a verdict report reproduces the row removal but silently skips the derived columns the script rewrites in the same pass
metadata:
  type: feedback
---

Replaying a `collect-stream-candidates.ps1` outcome by hand - taking the per-row verdict report and
writing the survivor CSV yourself - is legitimate when a re-probe would cost hours, but it is **not** the
same operation as letting the script finish. The script's post-probe path does more than drop rows, and
every extra step is a column it re-derives from *this run's* verdicts.

**Why:** on 2026-08-20 the full media probe ran for a night and then the provider-loss guard refused to
write, so the prune was applied by hand from the report. Row removal was reproduced exactly. What was
missed was the `access` re-stamp - `$acc = if ($statusByUrl[$r.url] -eq 'geo') { 'geo' } else { '' }` -
which blanks the column on every row the current run did not call `geo`. Nine rows kept a `geo` flag from
the S1117 era, and the published bank shipped them. Eight of those rows were alive that same day. The
error was found by the downstream consumer parsing our bank, not by us, and it contradicted a claim we
had just written into the contract we handed them.

**How to apply:** before hand-applying any outcome, read the script path between the probe and
`Write-CsvUtf8` and list every mutation it performs, not just the one you came for. Currently that is:
the `access` re-stamp from verdicts, the prune by verdict, and the schema-ordered write. The favicon
index re-stamp lives earlier and only under `-WithFavicons`. Then verify the derived columns on the
result, not only the row count - a row count that matches is what makes the omission invisible. Reuse the
script's own `Write-CsvUtf8` and `$Schema` via AST extraction so the byte shape cannot drift either.
Related: [[streams-player-catalog-consumer]], [[stream-catalog-all-live-channels]].
