<#
.SYNOPSIS
    Test suite for scripts/docs/carry-program-name.ps1.

.DESCRIPTION
    Subject: scripts/docs/carry-program-name.ps1

    On scratch files asserts that running text is rewritten to the map name, that identifiers, URLs, link
    targets, code spans and tag internals are untouched, that XML and HTML write &amp;, that visible HTML
    attribute text is rewritten, that hyphen compounds and listed followers are reported as ambiguous and
    left alone, that BOM and CRLF survive, and that -Check exits 1 on a rewritable file and 0 on a clean one.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/carry-program-name.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: the script is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'

$script = Join-Path $RepoRoot 'scripts/docs/carry-program-name.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "missing: $script" -ErrorAction Continue
    exit 2
}

$failures = [System.Collections.Generic.List[string]]::new()
function Assert-That([bool] $Condition, [string] $Name) {
    if ($Condition) { Write-Host "PASS  $Name" } else { Write-Host "FAIL  $Name"; $failures.Add($Name) }
}

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ("carry-program-name-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)

try {
    $md = Join-Path $scratch 'page.md'
    $mdText = "# FastMediaSorter`r`n`r`nOpen FastMediaSorter v2. Then FastMediaSorter.`r`n" +
        "See [FastMediaSorter](https://serzhyale.github.io/FastMediaSorter_mob_v2/) and ``FastMediaSorter`` here.`r`n" +
        "Style Widget.FastMediaSorter.Button, class FastMediaSorterApp, file FastMediaSorter_standard.apk.`r`n" +
        "Der FastMediaSorter-Katalog and the FastMediaSorter Watch.`r`n"
    [System.IO.File]::WriteAllBytes($md, [byte[]](0xEF, 0xBB, 0xBF) + $utf8.GetBytes($mdText))

    & pwsh -NoProfile -File $script -Paths $md -Check | Out-Null
    Assert-That ($LASTEXITCODE -eq 1) 'check exits 1 on a rewritable file'

    $report = Join-Path $scratch 'ambiguous.txt'
    & pwsh -NoProfile -File $script -Paths $md -ReportPath $report | Out-Null
    Assert-That ($LASTEXITCODE -eq 0) 'write exits 0'

    $outBytes = [System.IO.File]::ReadAllBytes($md)
    Assert-That ($outBytes[0] -eq 0xEF -and $outBytes[1] -eq 0xBB -and $outBytes[2] -eq 0xBF) 'BOM kept'
    $out = $utf8.GetString($outBytes, 3, $outBytes.Length - 3)
    Assert-That ($out.Contains("`r`n") -and -not ($out -match "[^`r]`n")) 'CRLF kept'
    Assert-That ($out.StartsWith('# Fast Media Sorter & Organizer')) 'heading rewritten'
    Assert-That ($out.Contains('Open Fast Media Sorter & Organizer. Then Fast Media Sorter & Organizer.')) 'v2 and sentence end rewritten'
    Assert-That ($out.Contains('[Fast Media Sorter & Organizer](https://serzhyale.github.io/FastMediaSorter_mob_v2/)')) 'link text rewritten, target kept'
    Assert-That ($out.Contains('`FastMediaSorter`')) 'code span untouched'
    Assert-That ($out.Contains('Widget.FastMediaSorter.Button') -and $out.Contains('FastMediaSorterApp') -and
        $out.Contains('FastMediaSorter_standard.apk')) 'identifiers untouched'
    Assert-That ($out.Contains('FastMediaSorter-Katalog') -and $out.Contains('FastMediaSorter Watch')) 'ambiguous untouched'
    $reportText = [System.IO.File]::ReadAllText($report)
    Assert-That ($reportText.Contains('page.md:6:') -and ([regex]::Matches($reportText, 'page.md:')).Count -eq 2) 'two ambiguous hits reported'

    & pwsh -NoProfile -File $script -Paths $md -Check | Out-Null
    Assert-That ($LASTEXITCODE -eq 0) 'check exits 0 once clean'

    $xml = Join-Path $scratch 'strings.xml'
    [System.IO.File]::WriteAllText($xml, "<resources>`n    <string name=`"open_in_fms`">Open in FastMediaSorter</string>`n    <string name=`"wear_watchface_hint`">Una esfera de FastMediaSorter</string>`n</resources>`n", $utf8)
    & pwsh -NoProfile -File $script -Paths $scratch | Out-Null
    Assert-That ([System.IO.File]::ReadAllText($xml).Contains('Open in Fast Media Sorter &amp; Organizer</string>')) 'xml writes &amp;'
    Assert-That ([System.IO.File]::ReadAllText($xml).Contains('Una esfera de FastMediaSorter</string>')) 'watch face string kept'

    $html = Join-Path $scratch 'page.html'
    [System.IO.File]::WriteAllText($html, '<a href="x/FastMediaSorter" title="FastMediaSorter v2 Home"><img alt="FastMediaSorter"></a><p>In FastMediaSorter.</p>', $utf8)
    & pwsh -NoProfile -File $script -Paths $html | Out-Null
    $h = [System.IO.File]::ReadAllText($html)
    Assert-That ($h.Contains('href="x/FastMediaSorter"')) 'href untouched'
    Assert-That ($h.Contains('title="Fast Media Sorter &amp; Organizer Home"') -and $h.Contains('alt="Fast Media Sorter &amp; Organizer"')) 'visible attributes rewritten'
    Assert-That ($h.Contains('<p>In Fast Media Sorter &amp; Organizer.</p>')) 'html text rewritten'

    $json = Join-Path $scratch 'help.json'
    [System.IO.File]::WriteAllText($json, '{ "FastMediaSorter": "a tool of FastMediaSorter" }', $utf8)
    & pwsh -NoProfile -File $script -Paths $json | Out-Null
    Assert-That ([System.IO.File]::ReadAllText($json) -eq '{ "FastMediaSorter": "a tool of Fast Media Sorter & Organizer" }') 'json key kept, value rewritten'

    $face = Join-Path $scratch 'face.md'
    $faceText = "Search for FastMediaSorter or open [it](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter.watchface).`nЦиферблат FastMediaSorter на часах.`n"
    [System.IO.File]::WriteAllText($face, $faceText, $utf8)
    & pwsh -NoProfile -File $script -Paths $face -Check | Out-Null
    Assert-That ($LASTEXITCODE -eq 0) 'watch face name kept'

    & pwsh -NoProfile -File $script -Paths (Join-Path $scratch 'missing.md') 2>$null | Out-Null
    Assert-That ($LASTEXITCODE -eq 2) 'missing path exits 2'
} finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host "carry-program-name tests: $($failures.Count) failed"
    exit 1
}
Write-Host 'carry-program-name tests: all passed'
exit 0
