<#
.SYNOPSIS
    S1544: applies the house text style to documentation prose and string resource values.

.DESCRIPTION
    The single style fixer. It replaced five overlapping scripts - fix-ellipsis.ps1,
    fix-ellipsis-docs.ps1, fix-ellipsis-strings.ps1, fix-yo.ps1 and fix-yo-letter.ps1 - none of
    which handled the long dash, and one of which walked docs without recursing.

    Every rule comes from scripts/quality/lib/house-text-style.ps1; nothing is declared here.

    The yo rule is language-bound, so by default it is applied only to a Russian locale directory
    and to a _RU documentation file. Naming it in -Rules overrides that and applies it everywhere.

.PARAMETER Area
    Prose (markdown), ResourceValue (string resources), or Both. Default Both.

.PARAMETER Path
    Explicit targets, replacing the default set. A directory is walked recursively.

.PARAMETER Rules
    Rule names to apply. Omitted means the per-file default described above.

.PARAMETER Apply
    Write the changes. Without it the script reports and writes nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/fix-house-style.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area ResourceValue -Apply

.OUTPUTS
    Exit codes:
      0 - nothing needed changing, or -Apply completed and every file was written.
      1 - unusable input: an explicit -Path does not exist, or a file could not be written.
      3 - dry run only: changes are pending. Re-run with -Apply to write them.
#>
[CmdletBinding()]
param(
    [ValidateSet('Prose', 'ResourceValue', 'Both')][string]$Area = 'Both',
    [string[]]$Path,
    [string[]]$Rules,
    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repoRoot 'scripts/quality/lib/house-text-style.ps1')

$utf8 = [System.Text.UTF8Encoding]::new($false)
$pending = 0
$changedFiles = 0

function Resolve-Targets {
    param([string]$ForArea)
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    if ($Path) {
        # `pwsh -File` binds a quoted CSV as one string instead of a string array. Accept the
        # same comma-delimited form as the paired docs gate so release workflow snippets can pass
        # an explicit mirror set without depending on the caller shell's array syntax.
        foreach ($p in ($Path | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            $full = if ([System.IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $repoRoot $p }
            if (-not (Test-Path -LiteralPath $full)) {
                Write-Error "fix-house-style: path not found: $p" -ErrorAction Continue
                exit 1
            }
            if (Test-Path -LiteralPath $full -PathType Container) {
                $filter = if ($ForArea -eq 'Prose') { '*.md' } else { 'strings*.xml' }
                Get-ChildItem -LiteralPath $full -Filter $filter -File -Recurse | ForEach-Object { $files.Add($_) }
            } else {
                $files.Add((Get-Item -LiteralPath $full))
            }
        }
        # Comma operator throughout: a bare return unrolls the list, and a single-file result would
        # then arrive as a lone FileInfo, which has no .Count for the caller to read.
        return , $files
    }
    if ($ForArea -eq 'Prose') {
        # Recursive, unlike the fix-ellipsis-docs.ps1 it replaces, which never saw docs/settings.
        $docs = Join-Path $repoRoot 'docs'
        if (Test-Path -LiteralPath $docs) {
            Get-ChildItem -LiteralPath $docs -Filter '*.md' -File -Recurse | ForEach-Object { $files.Add($_) }
        }
        return , $files
    }
    foreach ($module in @('app_v2', 'wear')) {
        $src = Join-Path $repoRoot "$module/src"
        if (-not (Test-Path -LiteralPath $src)) { continue }
        Get-ChildItem -LiteralPath $src -Filter 'strings*.xml' -File -Recurse |
            Where-Object { $_.Directory.Name -like 'values*' } |
            ForEach-Object { $files.Add($_) }
    }
    return , $files
}

function Get-RulesForFile {
    param([System.IO.FileInfo]$File, [string]$ForArea)
    if ($Rules) { return $Rules }
    $isRussian = if ($ForArea -eq 'Prose') { $File.Name -match '_RU\.md$' } else { $File.Directory.Name -match '^values-ru\b' }
    if ($isRussian) { return @('ellipsis', 'long-dash', 'yo') }
    return @('ellipsis', 'long-dash')
}

function Invoke-ProseFile {
    param([System.IO.FileInfo]$File)
    $raw = [System.IO.File]::ReadAllText($File.FullName, [System.Text.Encoding]::UTF8)
    $result = Convert-HouseStyleText -Text $raw -Area Prose -Rules (Get-RulesForFile -File $File -ForArea 'Prose')
    if (-not $result.Changed) { return }
    Write-FileReport -File $File -RuleNames $result.RulesFired -Count 1
    if ($Apply) { [System.IO.File]::WriteAllText($File.FullName, $result.Text, $utf8) }
}

function Invoke-ResourceFile {
    param([System.IO.FileInfo]$File)
    $raw = [System.IO.File]::ReadAllText($File.FullName, [System.Text.Encoding]::UTF8)
    # Script scope, not local: the MatchEvaluator below runs in a child scope and cannot write
    # back to a function-local variable, so a local counter would silently stay at zero.
    $script:fileRules = Get-RulesForFile -File $File -ForArea 'ResourceValue'
    $script:fired = [System.Collections.Generic.HashSet[string]]::new()
    $script:hits = 0
    # Only the text between the tags is rewritten, so attributes, key names and comments keep
    # their bytes and the file's formatting survives untouched.
    $updated = [regex]::Replace(
        $raw,
        '(?s)(<(?:string|item)\b[^>]*>)(.*?)(</(?:string|item)>)',
        {
            param($m)
            $r = Convert-HouseStyleText -Text $m.Groups[2].Value -Area ResourceValue -Rules $script:fileRules
            if ($r.Changed) {
                $script:hits++
                foreach ($n in $r.RulesFired) { [void]$script:fired.Add($n) }
            }
            return $m.Groups[1].Value + $r.Text + $m.Groups[3].Value
        })
    if ($updated -ceq $raw) { return }
    Write-FileReport -File $File -RuleNames @($script:fired) -Count $script:hits
    if ($Apply) { [System.IO.File]::WriteAllText($File.FullName, $updated, $utf8) }
}

function Write-FileReport {
    param([System.IO.FileInfo]$File, [string[]]$RuleNames, [int]$Count)
    $rel = $File.FullName.Substring($repoRoot.Length + 1)
    $tag = if ($Apply) { 'fixed  ' } else { 'pending' }
    Write-Host ("  {0} {1} - {2} value(s) [{3}]" -f $tag, $rel, $Count, (($RuleNames | Sort-Object) -join ', '))
    $script:changedFiles++
    $script:pending += $Count
}

$areas = if ($Area -eq 'Both') { @('Prose', 'ResourceValue') } else { @($Area) }
foreach ($a in $areas) {
    $targets = Resolve-Targets -ForArea $a
    Write-Host ("fix-house-style: {0} - {1} file(s) scanned" -f $a, $targets.Count)
    foreach ($f in $targets) {
        if ($a -eq 'Prose') { Invoke-ProseFile -File $f } else { Invoke-ResourceFile -File $f }
    }
}

if ($changedFiles -eq 0) {
    Write-Host 'fix-house-style: clean - nothing to change.'
    exit 0
}
if ($Apply) {
    Write-Host ("fix-house-style: applied to {0} file(s)." -f $changedFiles)
    exit 0
}
Write-Host ("fix-house-style: {0} file(s) pending. Re-run with -Apply to write." -f $changedFiles)
exit 3
