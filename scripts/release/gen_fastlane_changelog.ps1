#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Generate fastlane changelog files from docs/WHATS_NEW*.md for IzzyOnDroid / F-Droid.

.DESCRIPTION
    Spec S0215 - fdroid-publish-research, Phase 04 Step 04.1.

    Reads docs/WHATS_NEW.md, docs/WHATS_NEW-ru.md, docs/WHATS_NEW-uk.md and
    writes per-locale fastlane changelog files at:

      fastlane/metadata/android/<locale>/changelogs/<VersionCode>.txt

    Locale mapping:
      en-US  <- docs/WHATS_NEW.md
      ru-RU  <- docs/WHATS_NEW-ru.md
      uk-UA  <- docs/WHATS_NEW-uk.md

    Section extraction:
      Locates the "Current release" block bounded by:
        Start:  **Current release: <versionName>**  (or first **Current release:** line if -VersionName omitted)
        End:    first "## Previous Release:" heading OR first stand-alone "---" separator
                that follows the start marker, whichever comes first.

    Markdown stripping (output is plain text for fastlane):
      **bold**   -> bold
      *italic*   -> italic
      [text](url) -> text
      Leading "- " or "* " bullets -> "• "
      Headings (## ...) lines preserved without leading hashes

    Length cap:
      Trim to <= 500 chars. If exceeded, drop trailing bullets one at a time
      until within budget; if a single bullet exceeds 500 chars, truncate at
      word boundary and append ".." (project author style).

.PARAMETER VersionCode
    Required. Integer matching the project's versionCode (e.g. 260515201).
    Used as the output filename "<VersionCode>.txt".

.PARAMETER VersionName
    Optional. If supplied, the extractor matches "**Current release: <VersionName>**"
    instead of the first "Current release:" marker. Useful when WHATS_NEW.md
    has been advanced past the build but the recipe still references an
    earlier versionName.

.PARAMETER WhatsNewRoot
    Optional. Default: <repo>/docs

.PARAMETER FastlaneRoot
    Optional. Default: <repo>/fastlane/metadata/android

.EXAMPLE
    pwsh -File scripts/release/gen_fastlane_changelog.ps1 -VersionCode 260515201

.EXAMPLE
    pwsh -File scripts/release/gen_fastlane_changelog.ps1 -VersionCode 260515201 -VersionName 2.60.5152.017

.NOTES
    Exit codes:
      0 - at least one locale produced a changelog file.
      1 - a locale's "Current release" block is missing or empty, or no changelog was produced
          at all.
      3 - a changelog for this versionCode already exists with different content and -Overwrite was
          not passed, so nothing was written for that locale (S3027). A published version's store
          text is not replaced by accident.
      4 - the target's code domain is held by another session, so nothing was written. The queue
          place is held - wait for the turn in the background and rerun (S2635).
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [int] $VersionCode,
    [string] $VersionName,
    [string] $WhatsNewRoot,
    [string] $FastlaneRoot,
    # Replace a changelog that already exists with DIFFERENT content. Without it such a rewrite is
    # refused with exit 3 (S3027); identical content and a first write never need it.
    [switch] $Overwrite
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if (-not $WhatsNewRoot)  { $WhatsNewRoot  = Join-Path $repoRoot "docs" }
if (-not $FastlaneRoot)  { $FastlaneRoot  = Join-Path $repoRoot "fastlane/metadata/android" }

# Locale -> WHATS_NEW file mapping.
# CurrentReleasePrefix: locale-specific text that precedes the version on the "Current release" line.
$localeMap = @(
    @{ Locale = "en-US"; File = "WHATS_NEW.md";    CurrentReleasePrefix = "Current release:"    },
    @{ Locale = "ru-RU"; File = "WHATS_NEW-ru.md"; CurrentReleasePrefix = "Текущий релиз:"      },
    @{ Locale = "uk-UA"; File = "WHATS_NEW-uk.md"; CurrentReleasePrefix = "Поточний реліз:"     }
)

function Extract-CurrentReleaseBlock {
    param(
        [string[]] $Lines,
        [string]   $VersionName,
        [string]   $CurrentReleasePrefix = "Current release:"
    )

    $escapedPrefix = [regex]::Escape($CurrentReleasePrefix)

    # Find start marker.
    $startIdx = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        $ln = $Lines[$i]
        if ($VersionName) {
            $escapedVersion = [regex]::Escape($VersionName)
            if ($ln -match "^\*\*${escapedPrefix}\s*${escapedVersion}\*\*") {
                $startIdx = $i
                break
            }
        } else {
            if ($ln -match "^\*\*${escapedPrefix}") {
                $startIdx = $i
                break
            }
        }
    }
    if ($startIdx -lt 0) { return @() }

    # Find end: next "## Previous Release:" / "## Предыдущий релиз:" / "## Попередній реліз:" heading
    # OR stand-alone "---" separator that appears after a content line.
    $endIdx = $Lines.Count
    $seenContent = $false
    for ($i = $startIdx + 1; $i -lt $Lines.Count; $i++) {
        $ln = $Lines[$i]
        if ($ln -match "^## (Previous Release:|Предыдущий релиз:|Попередній реліз:)") { $endIdx = $i; break }
        if ($ln -match "^---\s*$") {
            if ($seenContent) { $endIdx = $i; break }
            continue
        }
        $isContentLine = $ln.Trim().Length -gt 0 -and $ln -notmatch "^>\s" -and $ln -notmatch "^---\s*$"
        if ($isContentLine) { $seenContent = $true }
    }

    return $Lines[($startIdx + 1)..($endIdx - 1)]
}

function Strip-Markdown {
    param([string[]] $Lines)

    $out = New-Object System.Collections.Generic.List[string]
    foreach ($ln in $Lines) {
        # Skip the "> Changes since .." blockquote and similar attribution lines.
        if ($ln -match "^>\s") { continue }
        # Skip markdown separators entirely in plain-text changelogs.
        if ($ln -match "^---\s*$") { continue }
        # Skip the "What's New" / "What's Fixed" headings - leave content but drop the heading itself.
        if ($ln -match "^##\s") { continue }
        $t = $ln
        # Convert bullets.
        $t = $t -replace "^\s*[-*]\s+", "• "
        # Drop bold/italic asterisks.
        $t = $t -replace "\*\*", ""
        $t = $t -replace "(?<!\*)\*(?!\*)", ""
        # Convert [text](url) -> text.
        $t = $t -replace "\[([^\]]+)\]\([^\)]+\)", '$1'
        # Drop inline backticks.
        $t = $t -replace "``", ""
        $out.Add($t)
    }
    return $out.ToArray()
}

function Trim-ToBudget {
    param(
        [string[]] $Lines,
        [int]      $MaxChars = 500
    )

    # Drop leading empty lines.
    while ($Lines.Count -gt 0 -and $Lines[0].Trim().Length -eq 0) {
        if ($Lines.Count -eq 1) { return "" }
        $Lines = $Lines[1..($Lines.Count - 1)]
    }

    # Drop trailing empty lines.
    while ($Lines.Count -gt 0 -and $Lines[-1].Trim().Length -eq 0) {
        if ($Lines.Count -eq 1) { return "" }
        $Lines = $Lines[0..($Lines.Count - 2)]
    }

    $joined = ($Lines -join "`n").TrimEnd()
    if ($joined.Length -le $MaxChars) { return $joined }

    # Drop bullets from the tail one by one.
    $kept = New-Object System.Collections.Generic.List[string]
    foreach ($ln in $Lines) { $kept.Add($ln) }
    while ($kept.Count -gt 1) {
        $kept.RemoveAt($kept.Count - 1)
        # Drop trailing empty lines that may remain.
        while ($kept.Count -gt 0 -and $kept[-1].Trim().Length -eq 0) {
            $kept.RemoveAt($kept.Count - 1)
        }
        $joined = ($kept -join "`n").TrimEnd()
        if ($joined.Length + 2 -le $MaxChars) {  # +2 for trailing ".."
            return ($joined + "`n..")
        }
    }

    # Single line still too long - truncate at word boundary.
    $single = $Lines[0]
    if ($single.Length -le $MaxChars - 2) { return $single }
    $cut = $single.Substring(0, $MaxChars - 2)
    $lastSpace = $cut.LastIndexOf(' ')
    if ($lastSpace -gt 0) { $cut = $cut.Substring(0, $lastSpace) }
    return ($cut.TrimEnd() + "..")
}

# Main loop.
$produced = 0
# One acquisition for all three locales: they are a single release artifact, so a run that wrote
# en-US and was then refused ru-RU would ship a version whose notes exist in one language only.
$outPaths = @($localeMap | ForEach-Object {
        Join-Path (Join-Path (Join-Path $FastlaneRoot $_.Locale) "changelogs") "$VersionCode.txt"
    })
$codeScope = $null
try {
    $codeScope = Enter-CodeLockOrExit -Path $outPaths `
        -Reason "gen_fastlane_changelog.ps1 (fastlane changelogs for versionCode $VersionCode)"

    foreach ($entry in $localeMap) {
        $localeDir = $entry.Locale
        $srcName   = $entry.File
        $srcPath   = Join-Path $WhatsNewRoot $srcName
        if (-not (Test-Path -LiteralPath $srcPath)) {
            Write-Host "[gen_fastlane_changelog] skip ${localeDir}: $srcName not found"
            continue
        }

        $lines = Get-Content -LiteralPath $srcPath
        $block = Extract-CurrentReleaseBlock -Lines $lines -VersionName $VersionName `
            -CurrentReleasePrefix $entry.CurrentReleasePrefix
        if ($block.Count -eq 0) {
            Write-Host "[gen_fastlane_changelog] FAIL ${localeDir}: no 'Current release' marker"
            exit 1
        }

        $stripped = Strip-Markdown -Lines $block
        $trimmed  = Trim-ToBudget -Lines $stripped -MaxChars 500
        if (-not $trimmed -or $trimmed.Trim().Length -eq 0) {
            Write-Host "[gen_fastlane_changelog] FAIL ${localeDir}: empty body after extraction"
            exit 1
        }

        $outDir  = Join-Path (Join-Path $FastlaneRoot $localeDir) "changelogs"
        if (-not (Test-Path -LiteralPath $outDir)) {
            New-Item -ItemType Directory -Path $outDir -Force | Out-Null
        }
        $outPath = Join-Path $outDir "$VersionCode.txt"
        $body = $trimmed + "`n"

        # S3027: this file is the localized "What's new" Play and IzzyOnDroid show for a version that
        # may already be published, so whichever copy is written last is what users read. A wear
        # release once replaced the phone's notes here and nothing noticed until an unrelated merge
        # collided on the file. Identical content still writes silently - a re-run of the same
        # release must stay idempotent - and a first write is untouched; only a DIFFERING rewrite
        # has to be asked for.
        if (Test-Path -LiteralPath $outPath) {
            $existing = [System.IO.File]::ReadAllText($outPath)
            if ($existing -ne $body -and -not $Overwrite) {
                Write-Host "[gen_fastlane_changelog] FAIL ${localeDir}: $outPath already holds different notes for versionCode $VersionCode."
                Write-Host "[gen_fastlane_changelog]   A published version's notes are not replaced by accident - pass -Overwrite to replace them deliberately."
                exit 3
            }
        }

        # UTF-8 without BOM (consistent with other text artefacts).
        [System.IO.File]::WriteAllText($outPath, $body, (New-Object System.Text.UTF8Encoding $false))

        Write-Host "[gen_fastlane_changelog] wrote ${localeDir}: $outPath ($($trimmed.Length) chars)"
        $produced++
    }
}
finally { Exit-CodeLockScope -Scope $codeScope }

if ($produced -eq 0) {
    Write-Host "[gen_fastlane_changelog] FAIL: no changelogs produced"
    exit 1
}

Write-Host "[gen_fastlane_changelog] OK: $produced changelog files generated for versionCode $VersionCode"
exit 0
