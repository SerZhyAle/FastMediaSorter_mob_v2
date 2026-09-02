[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for a transition INTO a gated status - see S2357.
#
# Contract:
#   - A spec file may carry the same section twice. Nothing else in the repository can
#     see that, because the shared section parser (Get-SpecSectionLines in
#     `_research-items.ps1`) stops at the next '## ' once it has entered a section, so
#     every consumer reads the FIRST copy and the rest of the file does not exist for it.
#     The duplicate therefore does not add noise - it silently substitutes whichever copy
#     sorted higher for the one a reader meant.
#   - Measured 2026-09-02 over all 1395 files under PLAN/: three carried the defect, in
#     three different shapes. S1884 had its `## Last Audit` block split in two, leaving a
#     three-line stub with no `**Outcome:**` on top - and check-audit-recorded.ps1, the
#     S2298 gate whose whole job is to refuse a Verified without a recorded verdict,
#     answered PASS on that stub. S1955's PHASE_02 held `## Phase Done Criteria` twice
#     with opposite ticks, all [x] against all [ ], so the file asserted both that the
#     phase was done and that it had not started. S2156 carried an unfilled skeleton.
#   - The duplicate is judged INSIDE one level-1 block, not across the file. A compact
#     spec (the /spec-all Simple path) holds several `# Phase NN` blocks in one file and
#     each legitimately owns its `## Objective`, `## Files Touched`, `## Steps` and
#     `## Phase Done Criteria`. Judging per file reports six offenders on the current
#     tree of which three are those legitimate compact specs; judging per level-1 block
#     reports exactly the three real ones and nothing else.
#   - Fenced code blocks are skipped: a spec quotes markdown, and S2357's own section 0
#     quotes the very heading list that opened it.
#   - Scope is this ticket's own files - the strategic spec plus every .md in its tactical
#     folder - which is what makes it a per-ticket gate rather than a tree sweep
#     (CLAUDE.md rule 33). S1955's case lived in a phase file, so the folder is not
#     optional.
#   - Unlike the three sibling closing gates this one runs for BlockNeedUserTest too:
#     whether a file contradicts itself does not depend on the ticket being finished.
#
# Exit codes: 0 = every heading is unique inside its level-1 block. 1 = a repeat found.
#             2 = bad invocation, or catalog / spec unreadable.

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') {
    # -ErrorAction Continue, not a bare Write-Error: _lib.ps1 sets $ErrorActionPreference
    # to 'Stop', under which a bare Write-Error throws and `exit 2` is never reached (S1070).
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

function Get-RepeatedHeading {
    # One entry per level-2 heading that already occurred inside the same level-1 block.
    param([Parameter(Mandatory)][string] $Path)

    # -Encoding UTF8 is load-bearing for the same reason it is in _research-items.ps1:
    # spec headings are Russian, and without it the offending line is echoed as mojibake,
    # which defeats the point of naming the line the owner has to fix.
    $lines = @(Get-Content -LiteralPath $Path -Encoding UTF8)
    $block = '(file start)'
    $seen  = @{}
    $hits  = New-Object System.Collections.Generic.List[object]
    $fence = $false

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*(?:```|~~~)') { $fence = -not $fence; continue }
        if ($fence) { continue }

        if ($line -match '^#\s+(.+?)\s*$') {
            # A new level-1 block reopens the namespace - this is the whole rule.
            $block = $Matches[1].Trim()
            $seen = @{}
            continue
        }
        if ($line -match '^##\s+(.+?)\s*$') {
            $heading = $Matches[1].Trim()
            $key = $block + '||' + $heading
            if ($seen.ContainsKey($key)) {
                $hits.Add([pscustomobject]@{
                    Heading   = $heading
                    Block     = $block
                    FirstLine = $seen[$key]
                    Repeat    = $i + 1
                })
            }
            else { $seen[$key] = $i + 1 }
        }
    }
    return $hits.ToArray()
}

# The tactical folder is the spec path without its extension - S1955's duplicate lived in
# a phase file, so checking the strategic file alone would have missed one of the three.
$files = New-Object System.Collections.Generic.List[string]
$files.Add($specPath)
$folder = [System.IO.Path]::ChangeExtension($specPath, $null).TrimEnd('.')
if (Test-Path -LiteralPath $folder -PathType Container) {
    foreach ($f in (Get-ChildItem -LiteralPath $folder -Filter '*.md' -File -Recurse | Sort-Object FullName)) {
        $files.Add($f.FullName)
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$findings = New-Object System.Collections.Generic.List[object]
foreach ($file in $files) {
    foreach ($hit in (Get-RepeatedHeading -Path $file)) {
        $rel = $file
        if ($rel.StartsWith($repoRoot)) { $rel = $rel.Substring($repoRoot.Length).TrimStart('\', '/') }
        $findings.Add([pscustomobject]@{ File = ($rel -replace '\\', '/'); Hit = $hit })
    }
}

if ($findings.Count -eq 0) {
    Write-Output "PASS $Id"
    Write-Output ("Checked {0} file(s); every section heading is unique inside its block." -f $files.Count)
    exit 0
}

Write-Output "FAIL $Id"
foreach ($f in $findings) {
    Write-Output ("- {0}:{1}: '## {2}' already appeared at line {3} inside '# {4}'." -f `
        $f.File, $f.Hit.Repeat, $f.Hit.Heading, $f.Hit.FirstLine, $f.Hit.Block)
}
Write-Output ""
Write-Output "A section written twice is not visible to any other check: the shared parser stops"
Write-Output "at the next '## ', so every consumer reads the first copy and ignores the rest."
Write-Output "Keep ONE copy - the one carrying the confirmed content, which is not always the"
Write-Output "first: in S1884 the complete audit block was the lower one. Then delete the other."
exit 1
