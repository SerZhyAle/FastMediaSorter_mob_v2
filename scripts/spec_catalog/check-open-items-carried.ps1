[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for transitions INTO a closed status (Implemented / Verified) - see S1607.
#
# Contract:
#   - Section 6 ("Открытые вопросы / Research items") is the only place a question
#     belonging to the owner is recorded. Nothing checked it at closing time, so a
#     spec could reach Verified carrying a live Open item. From that moment the
#     question is invisible: a closed ticket leaves PLAN/RELEASE_QUEUE.md, and
#     /spec-quiz answers "report and stop" for terminal statuses. Measured
#     2026-08-13: 134 of 1506 closed specs carry 372 such items, 8.9% of closures.
#   - At closing time every section 6 item must be in one of exactly two states:
#     Resolved, carrying its answer; or Open, carrying a literal carrier token
#     `Carrier: Sxxxx` (or `Носитель: Sxxxx`) that names the ticket which now owns
#     the question. The carrier is an ordinary catalog record, so it reappears in
#     PLAN/RELEASE_QUEUE.md on its own and the queue's ordering rule 6 batches it
#     into /spec-quiz. No new source of truth is introduced.
#   - The token is read literally, for the same reason `Blocker: Sxxxx` in section
#     10 is literal: the surrounding prose names neighbours as well as owners, so a
#     bare id cannot say which way the arrow points (S1482).
#   - The section is found by HEADING TEXT, never by the number 6: live specs put
#     unrelated content under `## 6.` and move the research section elsewhere. The
#     whole parse - heading list, status spellings, item grouping, carrier token -
#     lives in `_research-items.ps1` and is shared verbatim with `preview.ps1`, so
#     the gate's verdict and the operator's count cannot drift apart (S1621).
#   - An unfilled template placeholder (`Open / Resolved`, copied verbatim from
#     `.claude/templates/strategic-spec.md`) counts as open and is named as such:
#     the item was never decided either way, which is exactly what this gate is for.
#   - Archived is deliberately NOT gated: it closes a ticket that already passed
#     this gate, and blocking cleanup would make tidying harder than leaving it.
#
# Exit codes: 0 = every section 6 item is resolved or carried. 1 = uncarried open
#             items reported. 2 = bad invocation, or catalog / spec unreadable.

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') {
    # -ErrorAction Continue, not a bare Write-Error: _lib.ps1 sets $ErrorActionPreference = 'Stop',
    # under which a bare Write-Error throws and the documented `exit 2` is never reached (S1070).
    Write-Error "Invalid -Id '$Id' (must match S####)." -ErrorAction Continue
    exit 2
}

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "No record with id '$Id' in the spec catalog." -ErrorAction Continue
    exit 2
}

$specPath = Resolve-SpecPath -PathRef $record.file
if (-not (Test-Path -LiteralPath $specPath -PathType Leaf)) {
    Write-Error "Spec file not found on disk: $specPath" -ErrorAction Continue
    exit 2
}

$section = @(Get-SpecSectionLines -Path $record.file -HeadingPattern (Get-ResearchSectionHeadingPattern))
if ($section.Count -eq 0) {
    Write-Output "PASS $Id"
    Write-Output "No research / open-questions section - nothing to carry."
    exit 0
}

$items = @(Get-ResearchItems -SectionLines $section)

$blockers = New-Object System.Collections.Generic.List[object]
foreach ($item in $items) {
    if (-not $item.IsOpen) { continue }
    if ($item.Carrier) { continue }
    $blockers.Add([pscustomobject]@{
        Title           = $item.Title
        TitleLineNumber = $item.LineNumber
        LineNumber      = $item.OpenLine.LineNumber
        Text            = $item.OpenLine.Text.Trim()
        Placeholder     = $item.IsPlaceholder
    })
}

$rel = $record.file

if ($blockers.Count -gt 0) {
    Write-Output "FAIL $Id"
    foreach ($b in $blockers) {
        Write-Output ("- {0}:{1}: {2}" -f $rel, $b.LineNumber, $b.Text)
        # In a flat-bullet spec the item heading IS the status line; repeating it adds nothing.
        if ($b.LineNumber -ne $b.TitleLineNumber) {
            Write-Output ("    item: {0}" -f $b.Title)
        }
        if ($b.Placeholder) {
            Write-Output "    note: this is the unfilled template placeholder - the item was never decided either way."
        }
    }
    Write-Output ""
    Write-Output "Spec '$Id' closes while section 6 still carries an open question nobody owns."
    Write-Output "A closed ticket leaves the release queue, so the question becomes invisible."
    Write-Output "For each item above pick one:"
    Write-Output "  1. answer it - set the status line to 'Resolved' and write the answer into the item, or"
    Write-Output "  2. hand it to a ticket - append the literal token to the item:"
    Write-Output "         - **Носитель:** Carrier: Sxxxx"
    Write-Output "     Reuse an open ticket that already covers it, or create one:"
    Write-Output "         /spec-draft <the question, verbatim>"
    Write-Output "     Dedup first: pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Query '<keyword>'"
    exit 1
}

Write-Output "PASS $Id"
if ($items.Count -eq 0) {
    Write-Output "Research section present but lists no items."
} else {
    Write-Output ("Research section: {0} item(s); every open one names a carrier." -f $items.Count)
}
exit 0
