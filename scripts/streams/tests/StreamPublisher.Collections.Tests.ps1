#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for the curated-collections builder and its publish gate (S2669).

.DESCRIPTION
    Exercises Build-StreamCollections and Assert-StreamCollections over a small synthetic bank, so
    the composition rules and every refusal are proven without touching the real 19k-row catalog.

    Cases: a rule-only collection, an overlay-only collection, one URL in two collections at once,
    exclude beating include, a member absent from the bank, a missing mandatory locale, a duplicate
    id, and an overlay naming an undeclared collection.

.NOTES
    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../modules/StreamPublisher.Collections.ps1')

$script:Failures = 0
$script:Passes = 0

function Test-Case {
    param([string]$Name, [scriptblock]$Body)
    try {
        & $Body
        $script:Passes++
        Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor Green
    }
    catch {
        $script:Failures++
        Write-Host ("  FAIL  {0}: {1}" -f $Name, $_.Exception.Message) -ForegroundColor Red
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Throws {
    param([scriptblock]$Body, [string]$Fragment)
    $threw = $false
    try { & $Body } catch { $threw = $true; $text = $_.Exception.Message }
    if (-not $threw) { throw "expected a refusal mentioning '$Fragment', nothing was thrown" }
    if ($text -notlike "*$Fragment*") { throw "refusal did not mention '$Fragment': $text" }
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ("s2669-collections-" + [System.Guid]::NewGuid().ToString('N'))
$sourceDir = Join-Path $work 'collections'
New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null
$csv = Join-Path $work 'streams.csv'
$out = Join-Path $work 'collections.json'
$rules = Join-Path $sourceDir 'rules.json'
$overlay = Join-Path $sourceDir 'overlay.json'

# Synthetic bank. `radio-ru` is deliberately both Russian television-adjacent and radio so the
# multi-membership case has a natural carrier.
@'
"category","topic","name","url","language","country"
"tv","news","Alpha TV","https://a/1.m3u8","ru","russia"
"tv","music","Beta TV","https://b/2.m3u8","ru","russia"
"radio","music","Gamma Radio","https://c/3.mp3","ru","russia"
"tv","news","Delta TV","https://d/4.m3u8","en","kenya"
"radio","talk","Eps Radio","https://e/5.mp3","uk","ukraine"
'@ | Set-Content -Path $csv -Encoding utf8

function Write-Sources {
    param($RuleSet, $OverlaySet)
    ($RuleSet | ConvertTo-Json -Depth 8) | Set-Content -Path $rules -Encoding utf8
    ($OverlaySet | ConvertTo-Json -Depth 8) | Set-Content -Path $overlay -Encoding utf8
}

function Build {
    return (Build-StreamCollections -CsvPath $csv -SourceDir $sourceDir -OutPath $out)
}

function Get-Collection {
    param($Payload, [string]$Id)
    return @($Payload.collections | Where-Object { $_.id -eq $Id })[0]
}

$names = [ordered]@{ en = 'Name'; ru = 'Имя'; uk = 'Імʼя' }

Write-Host ''
Write-Host 'StreamPublisher.Collections contract suite (S2669)' -ForegroundColor Cyan

Test-Case 'a rule-only collection takes every matching bank row, in bank order' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = $names; rule = [ordered]@{ category = @('tv'); country = @('russia') } }
    ) -OverlaySet @{}
    $payload = Build
    $tv = Get-Collection -Payload $payload -Id 'tv-ru'
    Assert-True ($tv.members.Count -eq 2) "expected 2 members, got $($tv.members.Count)"
    Assert-True ($tv.members[0].url -eq 'https://a/1.m3u8') 'bank order not preserved'
    Assert-True ($tv.members[1].order -eq 2) 'member order not renumbered from 1'
}

Test-Case 'an overlay-only collection is built entirely from include, and an empty rule selects nothing' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'hand-picked'; order = 20; names = $names }
    ) -OverlaySet ([ordered]@{ 'hand-picked' = [ordered]@{ include = @('https://e/5.mp3', 'https://a/1.m3u8'); exclude = @() } })
    $payload = Build
    $picked = Get-Collection -Payload $payload -Id 'hand-picked'
    Assert-True ($picked.members.Count -eq 2) "expected 2 members, got $($picked.members.Count)"
    Assert-True ($picked.members[0].url -eq 'https://e/5.mp3') 'curator include order not preserved'
}

Test-Case 'one url belongs to two collections at once' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = $names; rule = [ordered]@{ category = @('tv'); country = @('russia') } },
        [ordered]@{ id = 'news'; order = 20; names = $names; rule = [ordered]@{ topic = @('news') } }
    ) -OverlaySet @{}
    $payload = Build
    $tv = Get-Collection -Payload $payload -Id 'tv-ru'
    $news = Get-Collection -Payload $payload -Id 'news'
    Assert-True (@($tv.members.url) -contains 'https://a/1.m3u8') 'url missing from tv-ru'
    Assert-True (@($news.members.url) -contains 'https://a/1.m3u8') 'url missing from news'
    Assert-True (Assert-StreamCollections -CollectionsPath $out -CsvPath $csv) 'valid set was refused'
}

Test-Case 'exclude wins over include and over the rule' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = $names; rule = [ordered]@{ category = @('tv'); country = @('russia') } }
    ) -OverlaySet ([ordered]@{ 'tv-ru' = [ordered]@{ include = @('https://c/3.mp3'); exclude = @('https://c/3.mp3', 'https://a/1.m3u8') } })
    $payload = Build
    $tv = Get-Collection -Payload $payload -Id 'tv-ru'
    Assert-True ($tv.members.Count -eq 1) "expected 1 member, got $($tv.members.Count)"
    Assert-True ($tv.members[0].url -eq 'https://b/2.m3u8') 'wrong survivor'
}

Test-Case 'a member absent from the bank is refused' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'ghost'; order = 10; names = $names }
    ) -OverlaySet ([ordered]@{ ghost = [ordered]@{ include = @('https://nowhere/9.m3u8'); exclude = @() } })
    Build | Out-Null
    Assert-Throws { Assert-StreamCollections -CollectionsPath $out -CsvPath $csv } 'absent from the bank'
}

Test-Case 'a missing mandatory locale is refused' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = [ordered]@{ en = 'Russian TV'; ru = 'TV России' }; rule = [ordered]@{ category = @('tv'); country = @('russia') } }
    ) -OverlaySet @{}
    Build | Out-Null
    Assert-Throws { Assert-StreamCollections -CollectionsPath $out -CsvPath $csv } "no 'uk' name"
}

Test-Case 'an empty collection is refused' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-antarctica'; order = 10; names = $names; rule = [ordered]@{ country = @('antarctica') } }
    ) -OverlaySet @{}
    Build | Out-Null
    Assert-Throws { Assert-StreamCollections -CollectionsPath $out -CsvPath $csv } 'has no members'
}

Test-Case 'a duplicate collection id is refused' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = $names; rule = [ordered]@{ category = @('tv') } },
        [ordered]@{ id = 'tv-ru'; order = 20; names = $names; rule = [ordered]@{ category = @('radio') } }
    ) -OverlaySet @{}
    Build | Out-Null
    Assert-Throws { Assert-StreamCollections -CollectionsPath $out -CsvPath $csv } 'more than once'
}

Test-Case 'an overlay naming an undeclared collection is refused at build time' {
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'tv-ru'; order = 10; names = $names; rule = [ordered]@{ category = @('tv') } }
    ) -OverlaySet ([ordered]@{ 'no-such-set' = [ordered]@{ include = @('https://a/1.m3u8'); exclude = @() } })
    Assert-Throws { Build } 'which rules.json does not declare'
}

Test-Case 'a language rule matches any token of the bank comma list' {
    @'
"category","topic","name","url","language","country"
"radio","music","Multi","https://m/1.mp3","ru,uk","moldova"
'@ | Set-Content -Path $csv -Encoding utf8
    Write-Sources -RuleSet @(
        [ordered]@{ id = 'uk-audio'; order = 10; names = $names; rule = [ordered]@{ language = @('uk') } }
    ) -OverlaySet @{}
    $payload = Build
    $set = Get-Collection -Payload $payload -Id 'uk-audio'
    Assert-True ($set.members.Count -eq 1) 'comma-separated language list not matched'
}

Remove-Item -Path $work -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host ("StreamPublisher.Collections: {0} passed, {1} failed." -f $script:Passes, $script:Failures) `
    -ForegroundColor $(if ($script:Failures -gt 0) { 'Red' } else { 'Green' })
if ($script:Failures -gt 0) { exit 1 }
exit 0
