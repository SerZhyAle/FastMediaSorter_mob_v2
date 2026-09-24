<#
.SYNOPSIS
    The documentation-corpus gates of scripts/post-change.ps1 - the published help pages under
    documentation/ and the sources they are generated from.

.DESCRIPTION
    Five gates with one subject, each PER-TICKET by Rule 33 for the reason S2974 recorded for the
    termbase gate: jekyll-gh-pages.yml builds the whole repository as the site, so a corpus page
    reaches readers with no Android release in between, and only the author of the change knows
    which word, link, image or generated page they meant.

      - docs-termbase         (S2974) forbidden synonyms in a changed page or termbase record
      - docs-crosslinks       (S2945) page links against docs/docs-pages-manifest.jsonl
      - docs-screenshots      (S2977) every referenced image exists and carries alt text
      - docs-search           (S2970) search index shape, sample queries, responsive stylesheet
      - docs-external-content (S3410) Markdown recipes, snippets and the HTML generated from them

    The last four judge the whole corpus, which S3422 measured at 271-568 ms each, and run only
    when the changed set carries one of their inputs, so a closure outside the corpus never pays
    for them. They carry no -ChangedFiles: the corpus has no author but the documentation
    programme, so a finding in it belongs to whoever changed it.

    Extracted here because the facade reached 1997 of its 2000-line ceiling (CLAUDE.md Rule 2,
    S3422). It is DOT-SOURCED, not invoked, so $root, $changedFiles, $ScopeToFile, Invoke-Gate,
    Skip-Step, Start-PooledGate and Test-AnyChangedFile resolve in the caller's scope exactly as
    they did inline.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

$runsDocsTermbase = Test-AnyChangedFile '^(docs/termbase\.jsonl|documentation/.*\.md)$'
$runsDocsCorpus = Test-AnyChangedFile '^(documentation/|docs/content/|docs/docs-pages-manifest\.jsonl$)'

$argvDocsTermbase = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-termbase.ps1"))
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvDocsTermbase += @('-ChangedFiles', ($changedFiles -join ',')) }

$argvDocsCrosslinks = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-crosslinks.ps1"))
$argvDocsScreenshots = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-screenshots.ps1"))
$argvDocsSearch = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-search.ps1"))
$argvDocsExternalContent = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-docs-external-content.ps1"))

# Started together so the five children overlap; each Invoke-Gate below joins its own.
if ($runsDocsTermbase) { Start-PooledGate @argvDocsTermbase }
if ($runsDocsCorpus) {
    Start-PooledGate @argvDocsCrosslinks
    Start-PooledGate @argvDocsScreenshots
    Start-PooledGate @argvDocsSearch
    Start-PooledGate @argvDocsExternalContent
}

# S2974: fatal - passing it is the closure condition of every documentation topic ticket.
if ($runsDocsTermbase) {
    Invoke-Gate "docs-termbase" { Invoke-GateChild @argvDocsTermbase }
}
else {
    Skip-Step "docs-termbase" "not applicable - no changed termbase or documentation/*.md page"
}

# Each label is written out, never looped over: assert-gate-hints-sync.ps1 pairs a hint with a
# quoted label standing before its script block, and a label held in a variable is invisible to it.
if ($runsDocsCorpus) {
    Invoke-Gate "docs-crosslinks" { Invoke-GateChild @argvDocsCrosslinks }
    Invoke-Gate "docs-screenshots" { Invoke-GateChild @argvDocsScreenshots }
    Invoke-Gate "docs-search" { Invoke-GateChild @argvDocsSearch }
    Invoke-Gate "docs-external-content" { Invoke-GateChild @argvDocsExternalContent }
}
else {
    $corpusSkipReason = "not applicable - no changed documentation/, docs/content/ or docs-pages-manifest file"
    Skip-Step "docs-crosslinks" $corpusSkipReason
    Skip-Step "docs-screenshots" $corpusSkipReason
    Skip-Step "docs-search" $corpusSkipReason
    Skip-Step "docs-external-content" $corpusSkipReason
}
