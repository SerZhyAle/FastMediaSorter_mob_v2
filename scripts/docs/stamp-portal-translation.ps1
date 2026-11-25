#requires -Version 7.0
<#
.SYNOPSIS
    S4653 - records in a portal translation which English original it was rebuilt from (DOC-EXTERNAL-QUALITY 0.11 item G).

.DESCRIPTION
    Writes `source_sha256: <hex>` into the front matter of each named translation page of the media exchange
    portal (docs/media-exchange-portal/<base>-<lang>.md): the SHA-256 of the English page <base>.md as it now
    stands (front matter and doc-stamp line removed, LF line ends). Run it right after rebuilding a translation
    from the English text - item G clears the stale flag only by that rebuild, so the value is never typed by
    hand. Every other byte of the page is kept. assert-portal-pages.ps1 -Dimension freshness compares the value.

.PARAMETER Pages
    Comma-separated translation file names (for example `clouds-ru.md,clouds-uk.md`) inside the portal folder.

.PARAMETER All
    Stamp every translation page of the portal. Use only after a release rebuilt them all.

.PARAMETER Check
    Write nothing; list each translation whose stamp is absent or differs from its English page.

.PARAMETER PortalDir
    Portal folder. Default: <repo>/docs/media-exchange-portal.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/stamp-portal-translation.ps1 -Pages "clouds-ru.md,clouds-uk.md"

.NOTES
    Exit codes:
      0 - stamped, or -Check found every translation fresh.
      1 - -Check found a stale translation.
      2 - could not verify: no pages named, a named page or its English original is missing.
#>
[CmdletBinding()]
param(
    [string] $Pages = '',
    [switch] $All,
    [switch] $Check,
    [string] $PortalDir = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/portal-fingerprint.ps1')
if (-not $PortalDir) { $PortalDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'docs/media-exchange-portal' }

$translations = if ($All -or $Check) {
    @(Get-ChildItem -LiteralPath $PortalDir -File -Filter *.md | Where-Object { $_.BaseName -match '-[a-z]{2}$' } | ForEach-Object Name)
} else {
    @($Pages -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
if ($translations.Count -eq 0) {
    Write-Host 'stamp-portal-translation: COULD NOT VERIFY - name -Pages, or pass -All or -Check' -ForegroundColor Yellow
    exit 2
}

$stale = 0
foreach ($name in $translations) {
    $page = Join-Path $PortalDir $name
    $original = Join-Path $PortalDir (($name -replace '-[a-z]{2}\.md$', '.md'))
    if (-not (Test-Path -LiteralPath $page) -or -not (Test-Path -LiteralPath $original)) {
        Write-Host "stamp-portal-translation: COULD NOT VERIFY - $name or its English original is missing" -ForegroundColor Yellow
        exit 2
    }
    $fingerprint = Get-PortalBodyFingerprint -Path $original
    if ($Check) {
        if ((Get-PortalSourceStamp -Path $page) -ne $fingerprint) { $stale++; Write-Host "  stale: $name" }
        continue
    }
    $text = [IO.File]::ReadAllText($page)
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    if ($text -match '(?m)^source_sha256:.*$') {
        $text = [regex]::new('(?m)^source_sha256:[^\r\n]*').Replace($text, "source_sha256: $fingerprint", 1)
    } elseif ($text.StartsWith("---$nl")) {
        $text = "---${nl}source_sha256: $fingerprint$nl" + $text.Substring(3 + $nl.Length)
    } else {
        Write-Host "stamp-portal-translation: COULD NOT VERIFY - $name has no front matter" -ForegroundColor Yellow
        exit 2
    }
    [IO.File]::WriteAllText($page, $text, [Text.UTF8Encoding]::new($false))
    Write-Host "  stamped: $name"
}
if ($Check -and $stale -gt 0) { Write-Host "stamp-portal-translation: $stale stale translation(s)"; exit 1 }
Write-Host 'stamp-portal-translation: OK'
exit 0
