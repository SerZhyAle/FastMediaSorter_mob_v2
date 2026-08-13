# Research 02 - Baseline-drift classifier design

## Question

S1334 §2's second open question: can a mechanical tool distinguish a baseline entry that no longer
matches any live finding because the underlying issue was genuinely fixed (safe to prune) from one
that no longer matches because the code's shape drifted while the same defect is still live under a
different signature (silently thawed debt)? What data is actually available to build it on?

## Empirical findings (2026-08-01, against this repo's live files)

**Baseline entry format** (`config/detekt/baseline-app_v2.xml:3507`, one line, HTML-entity encoded):

```
<ID>LongParameterList:BrowseStateSyncManager.kt$BrowseStateSyncManager$( private val
favoritesUseCase: FavoritesUseCase, private val getResourcesUseCase: GetResourcesUseCase, ..„ private
val reloadFiles: (Boolean) -&gt; Unit )</ID>
```

Shape: `<Rule>:<File>$<Class>$<verbatim-code-snippet>`. The snippet is the exact source text detekt
matched when the entry was frozen (full parameter declarations for `LongParameterList`, a single
literal call for `ArgumentListWrapping`, etc). Confirmed the frozen snippet here lists 10 parameters
and omits `materializeFavoritesUseCase` (added later by S0783) - this is the drift S1334 opened on.

**Live report format** (`app_v2/build/reports/detekt/detekt.xml:149-150`, checkstyle-style, freshly
regenerated 2026-08-01, newer than the source file it covers):

```xml
<file name="P:/.../BrowseStateSyncManager.kt">
    <error line="29" column="29" severity="warning"
        message="The constructor(favoritesUseCase: FavoritesUseCase, materializeFavoritesUseCase: ...)
                  has too many parameters. The current threshold is set to 10."
        source="detekt.LongParameterList" />
</file>
```

Key fact: **the live XML report does not carry the baseline's `$Class$(...)` signature key at all** -
only `file`, `line`, `column`, `message`, `source` (rule id prefixed `detekt.`). So the two files
cannot be joined by exact string equality on the signature. They CAN be joined on `(rule, file)`,
which both formats carry (rule needs the `detekt.` prefix stripped; file needs the baseline's bare
filename resolved against the live report's absolute path, or vice versa).

Also confirmed: entries that still validly match (e.g. this same file's four `ArgumentListWrapping`
baseline entries and one `MaxLineLength`/`MaximumLineLength` entry) produce **zero** corresponding
`<error>` elements in the live report for that rule - baseline suppression removes a matched finding
from the report entirely, it does not mark it as suppressed-but-listed. This means "no live entry for
this (rule, file)" is ambiguous by itself: it means EITHER "still correctly suppressed" OR "the
underlying issue was actually fixed" - the two indistinguishable-from-the-report-alone cases the
open question is about.

## Correction found during implementation (2026-08-01, spec-dev step 01.1)

The baseline snippet text is **not** a byte-verbatim copy of the source - it is detekt's PSI element
text with all whitespace runs (including newlines and indentation) collapsed to single spaces.
Confirmed by comparing baseline line 3505 (`BrowseResourceStateManager`, single-line, comma-space
separated parameter list) against the live file: the real constructor spans multiple lines, one
parameter per line, each preceded by newline + 4-space indentation. A literal substring search for
the baseline's single-line snippet against the live file's raw (unnormalized) text would never match
even for entries that are perfectly unchanged - which would make step 3 of the design below produce
false `DRIFTED`/`DEAD` results for the entire baseline, not just the genuinely stale entries.

**Fix:** normalize both sides before comparing - collapse every whitespace run (spaces, tabs,
newlines) in the live file's text to a single space before searching for the (already single-line)
decoded snippet as a substring. This is a text-level approximation of PSI-element-text equality, good
enough for a diagnostic classifier that only needs "does this text still occur, roughly," not an
exact recompile of detekt's own matching.

## Design that resolves the ambiguity

Three-way join, no extra non-baseline detekt run required:

1. Parse every baseline `<ID>` into `(rule, file, class, snippetText)`, HTML-entity-decoding
   `snippetText`.
2. For each entry, resolve `file` to its path under `app_v2/src` (or `wear/src`) and check whether
   `snippetText` still occurs verbatim in that file's current content.
   - Found -> entry still describes real, unchanged code. Not stale. Done.
   - File does not exist on disk at all -> **DEAD** (whole file removed).
3. For entries not found verbatim, parse the live checkstyle-style report into `(rule, file, line,
   message)` and check whether **any** live entry shares `(rule, file)` with the stale baseline entry
   (ignoring line/message - the whole point is the signature moved).
   - Shares `(rule, file)` -> **DRIFTED**: the same rule is still live in the same file, just under a
     shape the frozen entry no longer covers. This is exactly the `BrowseStateSyncManager` case:
     baseline has a `LongParameterList` entry for this file, the live report has a `LongParameterList`
     entry for this same file at line 29 - they don't cancel out because the snippet text differs.
   - No live entry shares `(rule, file)` -> **DEAD, prune candidate**: nothing under that rule is live
     in that file at all, so the original violation most likely is gone. Report as advisory only (not
     an auto-delete) - a renamed-but-still-present symbol is a residual edge case a human should
     glance at before removing the baseline line.

## Why this needs no extra detekt invocation

Because suppressed findings are silently dropped rather than marked, one might assume a second,
baseline-free detekt run is needed to see "everything, suppressed or not." It is not: step 3 above
only needs to know whether the SAME rule is live ANYWHERE in the file right now, which the ordinary
(baseline-filtered) report already answers - if the frozen debt's specific instance thawed, it shows
up as a live, unsuppressed finding under that rule in that file by definition. The classifier is a
pure diff between the existing baseline XML and the existing gradle detekt report; no new gradle task
is required, keeping the diagnostic cheap to run repeatedly.

## Conclusion

Feasible as a small, self-contained script (parse two files textually, join, classify, print). First
iteration should be diagnostic-only (print classification, do not mutate the baseline or fail a
build) - promoting it to a blocking gate is a follow-up decision once the classifier's false-positive
rate on this repo's real baseline is known.
