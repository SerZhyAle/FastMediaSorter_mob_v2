# Updates manual fields for a single Activity record in the ACTIVITY_CATALOG JSONL.
# Auto-re-renders Markdown unless -NoRender is specified.
#
# Examples:
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "PlayerActivity" `
#       -Role "Hosts media playback for all formats; supports PiP and fullscreen" `
#       -RoleRu "Воспроизведение медиа всех форматов; поддерживает картинка-в-картинке и полный экран" `
#       -Tags "player,fullscreen,pip,portrait,landscape,video,audio,image,pdf,epub" -Status tested
#
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "MainActivity" `
#       -Role "Primary entry point" -NoRender

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("app_v2","wear")]
    [string]$Module,
    [Parameter(Mandatory=$true)]
    [string]$Class,
    [string]$Root,
    [string]$Role,
    [string]$RoleRu,
    [string]$Tags,          # comma-separated; replaces existing tags array
    [string]$Status,        # new | tested | todo | unknown
    [string]$Notes,
    [switch]$NoRender
)

$ErrorActionPreference = "Stop"

$allowedStatuses = @("new","tested","todo","unknown")

if ($Status -and $allowedStatuses -notcontains $Status) {
    throw "Invalid -Status '$Status'. Allowed: $($allowedStatuses -join ', ')"
}

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

$InFile = Join-Path $Root "dev\ACTIVITY_CATALOG\$Module.jsonl"
if (-not (Test-Path $InFile)) { throw "JSONL not found: $InFile (run scan.ps1 first)" }

# ── Load records ──────────────────────────────────────────────────────────────

$lines = Get-Content -Path $InFile -Encoding UTF8 | Where-Object { $_.Trim() -ne "" }
$records = $lines | ForEach-Object { $_ | ConvertFrom-Json }

# ── Find record (exact match first, then substring fallback) ──────────────────

$classTerm = $Class.ToLower()

# 1. Try exact match first
$matches = @($records | Where-Object { $_.class.ToLower() -eq $classTerm })

# 2. Fall back to substring if no exact match
if ($matches.Count -eq 0) {
    $matches = @($records | Where-Object { $_.class.ToLower() -like "*$classTerm*" })
}

if ($matches.Count -eq 0) {
    throw "No Activity matching '$Class' found in $Module catalog."
}
if ($matches.Count -gt 1) {
    $names = ($matches | ForEach-Object { $_.class }) -join ", "
    throw "Ambiguous match for '$Class' — found: $names. Provide a more specific name."
}

$target = $matches[0]
$changed = [System.Collections.Generic.List[string]]::new()

# ── Apply provided fields ─────────────────────────────────────────────────────

if ($PSBoundParameters.ContainsKey('Role')) {
    $target.role = $Role
    $changed.Add("role") | Out-Null
}
if ($PSBoundParameters.ContainsKey('RoleRu')) {
    $target.roleRu = $RoleRu
    $changed.Add("roleRu") | Out-Null
}
if ($PSBoundParameters.ContainsKey('Tags')) {
    $target.tags = @($Tags -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
    $changed.Add("tags") | Out-Null
}
if ($PSBoundParameters.ContainsKey('Status')) {
    $target.status = $Status
    $changed.Add("status") | Out-Null
}
if ($PSBoundParameters.ContainsKey('Notes')) {
    $target.notes = $Notes
    $changed.Add("notes") | Out-Null
}

# ── Write updated JSONL ───────────────────────────────────────────────────────

$updatedLines = $records | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 5 }
($updatedLines -join "`n") + "`n" | Set-Content -Path $InFile -Encoding UTF8 -NoNewline

Write-Host "Updated $($target.class): $($changed -join ', ')"

# ── Re-render unless suppressed ───────────────────────────────────────────────

if (-not $NoRender) {
    $renderScript = Join-Path $PSScriptRoot "render.ps1"
    & "C:\Program Files\PowerShell\7\pwsh.exe" -File $renderScript -Module $Module -Root $Root
}
