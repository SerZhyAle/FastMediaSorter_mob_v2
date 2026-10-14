<#
.SYNOPSIS
    Retires a documentation page together with the capability it described, and rebuilds every index that
    pointed at it in the same run (S4097).

.DESCRIPTION
    SITE-STRUCTURE 0.1 rule 13: in the change that removes a capability its page is retired, and the subject
    index, the glossary, the search index and the sitemap are rebuilt in the same build. For the page and each
    of its language copies the command:
      1. deletes the Markdown recipe source and the generated html;
      2. removes the source from the document registry records that name it;
      3. sets "is_published": false on the page's row of docs/docs-pages-manifest.jsonl;
      4. appends a record to docs/site-redirects.jsonl (target: the successor's copy in the same language,
         else its English copy; no successor -> a retired page that links the portal home).
    Then it runs generate-site-redirects, generate-subject-index and generate-glossary (en, ru, uk),
    generate-docs-search-index and the document-registry generator, stopping at the first failure. Glossary
    entries of the removed capability live in docs/termbase.jsonl and stay the author's edit: the closing gate
    does not read them, so the command prints a reminder.

.PARAMETER PageId
    page_id of the page to retire, as in docs/docs-pages-manifest.jsonl.

.PARAMETER To
    page_id of the successor page; omit for a page with no successor.

.PARAMETER Ticket
    Sxxxx recorded in the redirect records.

.PARAMETER Reason
    Why the page goes; defaults to a generic sentence naming the ticket.

.PARAMETER SkipRebuild
    Do the file changes only; used by the tests, which run on a synthetic site.

.PARAMETER RepoRoot
    Site root; overridable so the tests can run on a synthetic site.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/retire-docs-page.ps1 -PageId tools.ocr-text-recognition -Ticket S1234 -WhatIf

.NOTES
    Exit codes:
      0  the page was retired, or -WhatIf printed the plan
      1  a rebuild step failed (the file changes before it are already made)
      2  could not resolve: unknown or already retired page, unknown successor, malformed Ticket, no copy found
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string] $PageId,
    [string] $To,
    [Parameter(Mandatory)][string] $Ticket,
    [string] $Reason,
    [switch] $SkipRebuild,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/site-addresses.ps1')

function Stop-Retire([string]$Message, [int]$Code) {
    Write-Host "retire-docs-page: $Message" -ForegroundColor Red
    exit $Code
}

function Get-CopyLanguage([string]$Path) {
    if ($Path -match '-ru\.html$') { return 'ru' }
    if ($Path -match '-uk\.html$') { return 'uk' }
    return 'en'
}

if ($Ticket -notmatch '^S\d{4}$') { Stop-Retire "Ticket '$Ticket' is not an Sxxxx id" 2 }
if (-not $Reason) { $Reason = "Page retired with the capability it described ($Ticket)" }

$pagesPath = Join-Path $RepoRoot 'docs/docs-pages-manifest.jsonl'
$redirectsPath = Join-Path $RepoRoot 'docs/site-redirects.jsonl'
$registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'

$pages = Read-JsonlRecords -Path $pagesPath
$page = $pages | Where-Object { $_.page_id -eq $PageId } | Select-Object -First 1
if (-not $page) { Stop-Retire "page_id '$PageId' is not in docs/docs-pages-manifest.jsonl" 2 }
if (-not $page.is_published) { Stop-Retire "page '$PageId' is already retired" 2 }

$outputs = Get-RecipeOutputs -RepoRoot $RepoRoot
$copies = @($outputs.Keys | Where-Object { $outputs[$_].PageId -eq $PageId } | Sort-Object)
if ($copies.Count -eq 0) { Stop-Retire "no recipe source carries page_id '$PageId'" 2 }

$successorCopies = @{}
if ($To) {
    $successor = $pages | Where-Object { $_.page_id -eq $To } | Select-Object -First 1
    if (-not $successor) { Stop-Retire "successor page_id '$To' is not in docs/docs-pages-manifest.jsonl" 2 }
    if (-not $successor.is_published) { Stop-Retire "successor '$To' is itself retired" 2 }
    foreach ($key in $outputs.Keys | Where-Object { $outputs[$_].PageId -eq $To }) {
        $successorCopies[(Get-CopyLanguage $key)] = $key
    }
    if (-not $successorCopies.ContainsKey('en')) { Stop-Retire "successor '$To' has no English copy to fall back to" 2 }
}

$existingFrom = @{}
foreach ($record in (Read-JsonlRecords -Path $redirectsPath)) { $existingFrom[[string]$record.from_path] = $true }

$date = (Get-Date).ToString('yyyy-MM-dd')
$newRecords = [System.Collections.Generic.List[string]]::new()
Write-Host "retire-docs-page: plan for '$PageId'" -ForegroundColor Cyan
foreach ($copy in $copies) {
    $lang = Get-CopyLanguage $copy
    $target = $null
    if ($To) { $target = if ($successorCopies.ContainsKey($lang)) { $successorCopies[$lang] } else { $successorCopies['en'] } }
    Write-Host "  delete   $($outputs[$copy].Source)"
    Write-Host "  delete   $copy"
    if ($existingFrom.ContainsKey($copy)) {
        Write-Host "  keep     redirect record for $copy (already listed)"
        continue
    }
    $record = [ordered]@{ from_path = $copy; to_path = $target; reason = $Reason; ticket = $Ticket; date = $date }
    $newRecords.Add(($record | ConvertTo-Json -Compress))
    Write-Host "  redirect $copy -> $(if ($target) { $target } else { '(retired, no successor)' })"
}
Write-Host "  unpublish docs/docs-pages-manifest.jsonl row $PageId"

if ($WhatIfPreference) { exit 0 }

# 1-2. sources, generated pages, registry paths
$registryText = if (Test-Path -LiteralPath $registryPath) { [IO.File]::ReadAllText($registryPath) } else { $null }
foreach ($copy in $copies) {
    $source = $outputs[$copy].Source
    Remove-Item -LiteralPath (Join-Path $RepoRoot $source) -Force
    $html = Join-Path $RepoRoot $copy
    if (Test-Path -LiteralPath $html) { Remove-Item -LiteralPath $html -Force }
    if ($null -ne $registryText) {
        # A path is a list element: first with its trailing comma, then as the last element, then alone.
        $quoted = '"' + $source + '"'
        $registryText = $registryText.Replace($quoted + ',', '').Replace(',' + $quoted, '').Replace($quoted, '')
    }
}
if ($null -ne $registryText) { [IO.File]::WriteAllText($registryPath, $registryText, [Text.UTF8Encoding]::new($false)) }

# 3. unpublish: one targeted substitution so every other byte of the manifest stays as it was
$manifestText = [IO.File]::ReadAllText($pagesPath)
$rowPattern = '(?m)^(\{"page_id":"' + [regex]::Escape($PageId) + '".*?)"is_published":true(.*)$'
$flipped = [regex]::Replace($manifestText, $rowPattern, '$1"is_published":false$2')
if ($flipped -ceq $manifestText) { Stop-Retire "could not flip is_published for '$PageId'; edit docs/docs-pages-manifest.jsonl by hand" 2 }
[IO.File]::WriteAllText($pagesPath, $flipped, [Text.UTF8Encoding]::new($false))

# 4. redirect records
if ($newRecords.Count -gt 0) {
    $current = if (Test-Path -LiteralPath $redirectsPath) { [IO.File]::ReadAllText($redirectsPath) } else { '' }
    if ($current.Length -gt 0 -and -not $current.EndsWith("`n")) { $current += "`n" }
    [IO.File]::WriteAllText($redirectsPath, $current + (($newRecords -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
}

if ($SkipRebuild) {
    Write-Host 'retire-docs-page: file changes made, rebuild skipped' -ForegroundColor Green
    exit 0
}

# Order matters: the forwarders exist before the search index is read, and the registry generator runs last
# so the sitemap sees the final file set.
$steps = [System.Collections.Generic.List[object]]::new()
$steps.Add(@('scripts/docs/generate-site-redirects.ps1'))
foreach ($lang in 'en', 'ru', 'uk') { $steps.Add(@('scripts/docs/generate-subject-index.ps1', '-Lang', $lang)) }
foreach ($lang in 'en', 'ru', 'uk') { $steps.Add(@('scripts/docs/generate-glossary.ps1', '-Lang', $lang)) }
$steps.Add(@('scripts/docs/generate-docs-search-index.ps1'))
$steps.Add(@('scripts/document_registry/generate.ps1'))
foreach ($step in $steps) {
    Write-Host "retire-docs-page: $($step -join ' ')" -ForegroundColor Cyan
    Push-Location $RepoRoot
    try { & pwsh -NoProfile -File $step[0] @($step | Select-Object -Skip 1) } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Stop-Retire "'$($step -join ' ')' exited $LASTEXITCODE" 1 }
}
Write-Host "retire-docs-page: '$PageId' retired. Review docs/termbase.jsonl for entries of the removed capability and the recipes that still link the old address (a forwarder keeps those links alive, it does not replace updating them), then run scripts/quality/assert-site-addresses.ps1." -ForegroundColor Green
exit 0
