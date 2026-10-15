#requires -Version 7.0
<#
.SYNOPSIS
    S4256 - holds every released page to its address and its section anchors: a page a release carried still
    answers at its address with every anchor it had, or is retired behind a forwarder.

.DESCRIPTION
    Contract SITE-STRUCTURE 0.2 section 8 item D. The subject is docs/site-released-addresses.jsonl, written by
    scripts/docs/record-released-addresses.ps1 at /skill-release Step 5d, against the tree as it is now. For
    each recorded address:

      - a published page answers it: every recorded anchor is still on that page, computed by the same
        Get-PageAnchors (scripts/docs/lib/released-addresses.ps1) that wrote the record;
      - no page answers it: docs/site-redirects.jsonl retires it (to_path null). A move forwarder is refused,
        because from 0.2 a move of a released page is no longer a lawful change; nothing at all is refused
        as an address that stopped answering.

    A Markdown heading's anchor is its GFM id, so rewording a released heading removes an anchor. Keep it by
    writing the old id on the new heading as a kramdown attribute: `## New words {#old-id}`.

    PER-TICKET by Rule 33, the argument assert-site-addresses.ps1 was placed on: jekyll-gh-pages.yml publishes
    the tree with no Android release in between, and the author of a rename knows which page they meant.

.PARAMETER RepoRoot
    Repository root; overridable so the tests can judge a synthetic site.

.PARAMETER Quiet
    Print only the verdict line and the findings.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-released-addresses.ps1

.NOTES
    Exit codes:
      0  every released address answers with its page and its anchors, or is retired
      1  at least one finding (each is named)
      2  could not verify: the record is missing, unreadable or malformed
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/site-addresses.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/released-addresses.ps1')

Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'released-addresses'; files = "$($script:ReleasedRecordPath),docs/site-redirects.jsonl,published pages" })

function Stop-CouldNotVerify([string]$Message) {
    Write-Host "assert-released-addresses: COULD NOT VERIFY - $Message" -ForegroundColor Red
    exit 2
}

$recordPath = Join-Path $RepoRoot $script:ReleasedRecordPath
if (-not (Test-Path -LiteralPath $recordPath -PathType Leaf)) { Stop-CouldNotVerify "$($script:ReleasedRecordPath) not found" }
try { $records = Read-JsonlRecords -Path $recordPath }
catch { Stop-CouldNotVerify "$($script:ReleasedRecordPath) is unreadable - $($_.Exception.Message)" }
$number = 0
foreach ($record in $records) {
    $number++
    $names = $record.PSObject.Properties.Name
    foreach ($field in 'address', 'release', 'anchors') {
        if ($names -notcontains $field) { Stop-CouldNotVerify "$($script:ReleasedRecordPath) line $number lacks '$field'" }
    }
}

$retired = @{}
$moved = @{}
foreach ($redirect in (Read-JsonlRecords -Path (Join-Path $RepoRoot 'docs/site-redirects.jsonl'))) {
    if ($redirect.PSObject.Properties.Name -notcontains 'from_path') { continue }
    if ($null -eq $redirect.to_path) { $retired[[string]$redirect.from_path] = $redirect }
    else { $moved[[string]$redirect.from_path] = $redirect }
}

$published = Get-PublishedAddresses -RepoRoot $RepoRoot
$findings = [System.Collections.Generic.List[string]]::new()
foreach ($record in $records) {
    $address = [string]$record.address
    $file = if ($published.ContainsKey($address)) { [string]$published[$address] } else { $null }
    $isForwarder = $false
    if ($file -and [IO.Path]::GetExtension($file) -eq '.html') {
        $head = [IO.File]::ReadAllText((Join-Path $RepoRoot $file))
        if ($head.Length -gt 4096) { $head = $head.Substring(0, 4096) }
        $isForwarder = $head -match $script:ForwarderMarkerRegex
    }
    if ($file -and -not $isForwarder) {
        $current = Get-PageAnchors -FullName (Join-Path $RepoRoot $file)
        $lost = @(@($record.anchors) | Where-Object { $_ -cnotin $current })
        if ($lost.Count -gt 0) {
            $findings.Add("$address (released in $($record.release)) lost anchor(s) $(($lost | ForEach-Object { "#$_" }) -join ', ') - $file. Restore them; a reworded heading keeps its old id as '## New words {#old-id}'.")
        }
        continue
    }
    if ($retired.ContainsKey($address)) { continue }
    if ($moved.ContainsKey($address)) {
        $findings.Add("$address (released in $($record.release)) was moved to $($moved[$address].to_path) by $($moved[$address].ticket); a released page keeps its address (SITE-STRUCTURE 0.2 item D). Move it back, or retire it with scripts/docs/retire-docs-page.ps1 when its capability is gone.")
        continue
    }
    $findings.Add("$address (released in $($record.release)) no longer answers with a page. Restore the page at its address, or retire it with scripts/docs/retire-docs-page.ps1 when its capability is gone.")
}

if ($findings.Count -gt 0) {
    Write-Host "assert-released-addresses: FAIL ($($findings.Count) finding(s) over $($records.Count) released page(s)):" -ForegroundColor Red
    foreach ($finding in $findings) { Write-Host "  - $finding" }
    exit 1
}
if (-not $Quiet) { Write-Host "  judged $($records.Count) released page(s) from $($script:ReleasedRecordPath)" }
Write-Host 'assert-released-addresses: PASS' -ForegroundColor Green
exit 0
