#requires -Version 7.0
<#
.SYNOPSIS
    S3151: contract suite for capture-draft.ps1 against a throwaway project root.

.DESCRIPTION
    The fixture holds the repository's profile, both templates and the catalog forwarders, so the
    real harness allocates ids and writes the journal - inside the fixture, never PLAN/ here.

    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  cannot verify - the subject script or a fixture source is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$subject = Join-Path $repoRoot 'scripts/utils/capture-draft.ps1'
$sources = @(
    '.sza-profile.json',
    '.claude/templates/draft-spec.md',
    '.claude/templates/compact-bugfix-spec.md',
    'scripts/spec_catalog/insert.ps1',
    'scripts/spec_catalog/search.ps1',
    'scripts/add_to_dev_log.ps1'
)
foreach ($rel in @('scripts/utils/capture-draft.ps1') + $sources) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $rel))) { Write-Host "cannot verify: $rel is missing."; exit 2 }
}
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixture = Join-Path $repoRoot "temp/scratch/capture-draft-tests-$PID"
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
foreach ($rel in $sources) {
    $dest = Join-Path $fixture $rel
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot $rel) -Destination $dest
}
New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'PLAN'), (Join-Path $fixture 'dev'), (Join-Path $fixture 'temp') | Out-Null
Set-Content -LiteralPath (Join-Path $fixture 'PLAN/spec-catalog.jsonl') -Value '' -NoNewline
$catalog = Join-Path $fixture 'PLAN/spec-catalog.jsonl'

$failures = 0
function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) { Write-Host "  PASS  $Name" }
    else { Write-Host "  FAIL  $Name - $Detail"; $script:failures++ }
}
function Invoke-Subject([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $subject -RepoRoot $fixture @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ Code = [int]$LASTEXITCODE; Out = $out.Trim() }
}
function Get-SpecText([string]$Out) {
    $id = [regex]::Match($Out, '^(S\d{4}) ', 'Multiline').Groups[1].Value
    $file = @(Get-ChildItem -LiteralPath (Join-Path $fixture 'PLAN') -Filter "${id}_*.md" -File)
    if (-not $id -or $file.Count -ne 1) { return $null }
    return [System.IO.File]::ReadAllText($file[0].FullName, [System.Text.Encoding]::UTF8)
}

try {
    $r = Invoke-Subject @('-Slug', 'thumbnail-preload-idea', '-Text', 'plain idea')
    $spec = Get-SpecText $r.Out
    Assert-That 'feature slug creates a Draft from draft-spec.md' ($r.Code -eq 0 -and $spec -and $spec -match '# Черновик: S\d{4} - thumbnail-preload-idea' -and $spec -match '\*\*Priority:\*\* 50' -and $spec -notmatch '\{\{') "exit $($r.Code): $($r.Out)"
    Assert-That 'feature output is the one fixed line' ($r.Out -match '^S\d{4} thumbnail-preload-idea - Draft\. Captured: 10 chars \+ 0 attachment\(s\)\.$') $r.Out

    $r = Invoke-Subject @('-Slug', 'bugfix-grid-crash', '-Text', 'crash on rotate')
    $spec = Get-SpecText $r.Out
    Assert-That 'bugfix slug uses the compact template at priority 90' ($r.Code -eq 0 -and $spec -and $spec -match 'compact bugfix\): S\d{4} - bugfix-grid-crash' -and $spec -match '\*\*Priority:\*\* 90' -and $spec -notmatch '<Sxxxx>|Вложения') "exit $($r.Code): $($r.Out)"

    $verbatim = "Строка с ёлкой и «кавычками» и `"двойными`" и 'одинарными'`n`$env:X {{ID}} a.b* [x]`n"
    $textFile = Join-Path $fixture 'text.txt'
    [System.IO.File]::WriteAllText($textFile, $verbatim, [System.Text.UTF8Encoding]::new($false))
    $attachment = Join-Path $fixture 'shot.png'
    [System.IO.File]::WriteAllBytes($attachment, [byte[]](1, 2, 3))
    $r = Invoke-Subject @('-Slug', 'verbatim-capture-check', '-TextFile', $textFile, '-Attach', $attachment)
    $spec = Get-SpecText $r.Out
    Assert-That 'text lands byte-for-byte' ($r.Code -eq 0 -and $spec -and $spec.Contains($verbatim)) "exit $($r.Code): $($r.Out)"
    $attachId = [regex]::Match($r.Out, '^(S\d{4}) ', 'Multiline').Groups[1].Value
    $copied = Join-Path $fixture "PLAN/${attachId}_verbatim-capture-check/attachments/01__shot.png"
    Assert-That 'attachment is copied and linked' ((Test-Path -LiteralPath $copied) -and $spec -match 'attachments/01__shot\.png' -and $r.Out -match '1 attachment') $r.Out

    $before = @(Get-Content -LiteralPath $catalog | Where-Object { $_ }).Count
    $r = Invoke-Subject @('-Slug', 'Bad_Slug', '-Text', 'x')
    Assert-That 'bad slug exits 2 and inserts nothing' ($r.Code -eq 2 -and @(Get-Content -LiteralPath $catalog | Where-Object { $_ }).Count -eq $before) "exit $($r.Code): $($r.Out)"

    $r = Invoke-Subject @('-Slug', 'dedup-probe-only', '-Text', 'x', '-DedupQuery', 'thumbnail-preload', '-WhatIf')
    Assert-That 'dedup with -WhatIf reports hits and creates nothing' ($r.Code -eq 0 -and $r.Out -match 'dedup: S\d{4} Draft thumbnail-preload-idea' -and @(Get-Content -LiteralPath $catalog | Where-Object { $_ }).Count -eq $before) "exit $($r.Code): $($r.Out)"
}
finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures) { Write-Host "capture-draft suite: $failures failure(s)"; exit 1 }
Write-Host 'capture-draft suite: PASS'
exit 0
