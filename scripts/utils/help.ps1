<#
.SYNOPSIS
  Print the parameter signature of any repo PowerShell script, or generate a
  full cheatsheet - so callers stop re-reading scripts just to recall params.

.DESCRIPTION
  Parses scripts via the PowerShell AST (robust, not regex) and reports each
  param: name, type, mandatory flag, ValidateSet/Range, and default. Discovery
  roots: scripts/ and dev/CATALOG/scripts/ (node_modules excluded).

.PARAMETER Name
  Script basename to look up (with or without .ps1). Exact match wins; otherwise
  a unique substring match is shown, an ambiguous one is listed. A repo-relative
  path ('scripts/spec_catalog/select.ps1') disambiguates basenames that repeat
  across directories - two query.ps1 exist, so -Help delegation must use a path.

.PARAMETER List
  List every discoverable script path instead of showing one.

.PARAMETER Generate
  (Re)generate docs/SCRIPT_CHEATSHEET.md with every script's signature.

.PARAMETER Check
  Drift check: rebuild the cheatsheet in memory and byte-compare (EOL-agnostic)
  against the committed docs/SCRIPT_CHEATSHEET.md. Exit 1 if stale. Used by the
  assert-script-cheatsheet-sync.ps1 gate.

.PARAMETER Root
  Repo root override. Defaults to two levels above this script.

.EXAMPLE
  pwsh -NoProfile -File scripts/utils/help.ps1 -Name insert

.NOTES
  Exit codes: 0 ok; 1 stale (-Check drift or missing cheatsheet); 2 not found;
  3 ambiguous name.
#>
[CmdletBinding()]
param(
    [string] $Name,
    [switch] $List,
    [switch] $Generate,
    [switch] $Check,
    [string] $Root
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $Root) {
    $Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Get-ScriptFiles {
    param([string] $RepoRoot)
    $roots = @(
        (Join-Path $RepoRoot 'scripts'),
        (Join-Path $RepoRoot 'dev\CATALOG\scripts')
    ) | Where-Object { Test-Path $_ }
    $files = foreach ($r in $roots) {
        Get-ChildItem -Path $r -Recurse -Filter *.ps1 -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\node_modules\\' }
    }
    $files | Sort-Object FullName -Unique
}

function Get-ParamInfo {
    # Returns an ordered list of param descriptors from a script's AST param block.
    param([System.Management.Automation.Language.ParamBlockAst] $ParamBlock)
    $out = @()
    if (-not $ParamBlock) { return $out }
    foreach ($p in $ParamBlock.Parameters) {
        $pname     = $p.Name.VariablePath.UserPath
        $type      = $p.StaticType.Name
        $mandatory = $false
        $set       = $null
        $range     = $null
        foreach ($attr in $p.Attributes) {
            if ($attr -is [System.Management.Automation.Language.AttributeAst]) {
                switch ($attr.TypeName.Name) {
                    'Parameter' {
                        foreach ($na in $attr.NamedArguments) {
                            if ($na.ArgumentName -eq 'Mandatory') {
                                # Bare 'Mandatory' (ExpressionOmitted) means $true.
                                $mandatory = $na.ExpressionOmitted -or ("$($na.Argument.Extent.Text)" -match '\$true')
                            }
                        }
                    }
                    'ValidateSet' {
                        $set = ($attr.PositionalArguments | ForEach-Object { "$($_.Value)" }) -join '|'
                    }
                    'ValidateRange' {
                        $range = ($attr.PositionalArguments | ForEach-Object { "$($_.Extent.Text)" }) -join '..'
                    }
                }
            }
        }
        $default = $null
        if ($p.DefaultValue) { $default = $p.DefaultValue.Extent.Text }
        $out += [pscustomobject]@{
            Name = $pname; Type = $type; Mandatory = $mandatory
            Set = $set; Range = $range; Default = $default
        }
    }
    return $out
}

function Get-ExitCodeLines {
    # Best-effort: pull an 'Exit codes' section from the header comment.
    param([string] $Path)
    $head = Get-Content -LiteralPath $Path -TotalCount 60 -ErrorAction SilentlyContinue
    $hit = @()
    $inBlock = $false
    foreach ($line in $head) {
        if ($line -match '(?i)exit\s*codes?') { $inBlock = $true; continue }
        if ($inBlock) {
            if ($line -match '^\s*#?\s*(\d+)\s+(.*)$') { $hit += ("{0} {1}" -f $Matches[1], $Matches[2].Trim()) }
            elseif ($line.Trim() -eq '' -or $line -notmatch '\d') { if ($hit.Count) { break } }
        }
    }
    return $hit
}

function Get-Signature {
    param([System.IO.FileInfo] $File, [string] $RepoRoot)
    $errs = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($File.FullName, [ref]$null, [ref]$errs)
    $rel = $File.FullName.Substring($RepoRoot.Length).TrimStart('\','/') -replace '\\','/'
    $synopsis = $null
    try { $synopsis = $ast.GetHelpContent().Synopsis } catch { }
    $params = Get-ParamInfo -ParamBlock $ast.ParamBlock
    [pscustomobject]@{
        Rel = $rel; Synopsis = $synopsis; Params = $params
        Exits = (Get-ExitCodeLines -Path $File.FullName)
        ParseError = [bool]($errs -and $errs.Count)
    }
}

function Format-Signature {
    param($Sig)
    $lines = @($Sig.Rel)
    if ($Sig.Synopsis) { $lines += ('  ' + ($Sig.Synopsis -replace '\s+', ' ').Trim()) }
    $params = @($Sig.Params)
    if ($params.Count -eq 0) {
        $lines += '  (no param block)'
    } else {
        $lines += '  Params:'
        $w = ($params | ForEach-Object { $_.Name.Length } | Measure-Object -Maximum).Maximum
        foreach ($p in $params) {
            $flag = if ($p.Mandatory) { '(req)' } else { '     ' }
            $seg  = '    -{0}  {1}  [{2}]' -f $p.Name.PadRight($w), $flag, $p.Type
            if ($null -ne $p.Default) { $seg += ' = ' + $p.Default }
            if ($p.Set)   { $seg += '  {' + $p.Set + '}' }
            if ($p.Range) { $seg += '  {range ' + $p.Range + '}' }
            $lines += $seg
        }
    }
    if (@($Sig.Exits).Count) { $lines += ('  Exit: ' + (@($Sig.Exits) -join '; ')) }
    return ($lines -join "`n")
}

function Build-CheatsheetText {
    # Single source of the cheatsheet markdown, shared by -Generate and -Check so
    # the written file and the drift check can never diverge in format.
    param([object[]] $Files, [string] $RepoRoot)
    $md = New-Object System.Collections.Generic.List[string]
    $md.Add('# Script Cheatsheet')
    $md.Add('')
    $md.Add('Auto-generated by `scripts/utils/help.ps1 -Generate`. Do not hand-edit.')
    $md.Add('Regenerate after adding or changing any script parameter block.')
    $md.Add('')
    $bySub = $Files | Group-Object { Split-Path (($_.FullName.Substring($RepoRoot.Length).TrimStart('\','/')) -replace '\\','/') -Parent }
    foreach ($grp in ($bySub | Sort-Object Name)) {
        $md.Add('## ' + $grp.Name)
        $md.Add('')
        foreach ($f in ($grp.Group | Sort-Object Name)) {
            $sig = Get-Signature -File $f -RepoRoot $RepoRoot
            $md.Add('### ' + (Split-Path $sig.Rel -Leaf))
            if ($sig.Synopsis) { $md.Add(($sig.Synopsis -replace '\s+', ' ').Trim()) }
            $md.Add('')
            $md.Add('```')
            $md.Add((Format-Signature -Sig $sig))
            $md.Add('```')
            $md.Add('')
        }
    }
    return (($md -join "`n") + "`n")
}

$allFiles = Get-ScriptFiles -RepoRoot $Root

if ($List) {
    $allFiles | ForEach-Object { $_.FullName.Substring($Root.Length).TrimStart('\','/') -replace '\\','/' }
    exit 0
}

if ($Generate) {
    $outPath = Join-Path $Root 'docs\SCRIPT_CHEATSHEET.md'
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($outPath, (Build-CheatsheetText -Files $allFiles -RepoRoot $Root), $utf8NoBom)
    Write-Host ("Wrote {0} ({1} scripts)" -f ($outPath.Substring($Root.Length).TrimStart('\','/')), @($allFiles).Count) -ForegroundColor Green
    exit 0
}

if ($Check) {
    $outPath = Join-Path $Root 'docs\SCRIPT_CHEATSHEET.md'
    if (-not (Test-Path $outPath)) {
        Write-Error 'docs/SCRIPT_CHEATSHEET.md missing - run: help.ps1 -Generate' -ErrorAction Continue
        exit 1
    }
    $fresh     = (Build-CheatsheetText -Files $allFiles -RepoRoot $Root) -replace "`r`n", "`n"
    $committed = ([System.IO.File]::ReadAllText($outPath)) -replace "`r`n", "`n"
    if ($committed -ne $fresh) {
        Write-Error 'docs/SCRIPT_CHEATSHEET.md is stale - run: pwsh -NoProfile -File scripts/utils/help.ps1 -Generate' -ErrorAction Continue
        exit 1
    }
    Write-Host 'script-cheatsheet: in sync' -ForegroundColor Green
    exit 0
}

if (-not $Name) {
    Write-Host 'Usage: help.ps1 -Name <script> | -List | -Generate' -ForegroundColor Cyan
    exit 0
}

if ($Name -match '[\\/]') {
    # Path form: match the repo-relative tail, so a basename shared by several
    # directories (query.ps1) resolves to exactly one script.
    $tail = ($Name -replace '\\','/').TrimStart('./')
    if ($tail -notmatch '\.ps1$') { $tail += '.ps1' }
    $match = $allFiles | Where-Object {
        (($_.FullName.Substring($Root.Length).TrimStart('\','/') -replace '\\','/')) -ieq $tail
    }
} else {
    $needle = $Name -replace '\.ps1$',''
    $exact = $allFiles | Where-Object { $_.BaseName -ieq $needle }
    $match = if ($exact) { $exact } else { $allFiles | Where-Object { $_.BaseName -like "*$needle*" } }
}

if (-not $match) {
    Write-Error "No script matching '$Name' under scripts/ or dev/CATALOG/scripts/." -ErrorAction Continue
    exit 2
}
if (@($match).Count -gt 1) {
    Write-Host "Ambiguous '$Name' - candidates:" -ForegroundColor Yellow
    $match | ForEach-Object { '  ' + ($_.FullName.Substring($Root.Length).TrimStart('\','/') -replace '\\','/') }
    exit 3
}

Write-Host (Format-Signature -Sig (Get-Signature -File (@($match)[0]) -RepoRoot $Root))
exit 0
