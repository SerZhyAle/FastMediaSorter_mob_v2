[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for Draft -> Approved transition of strategic specs.
#
# Contract (relevance-driven, no hardcoded field list):
#   - A strategic spec must contain a "### 3.3 Owner inputs (Approval gate)" subsection.
#   - The set of bullets inside §3.3 is decided by /spec at draft time, based on
#     the spec's detected character (UI / data / flavor / executable scope).
#   - The gate validates only what is actually present in §3.3 - every bullet
#     must carry a concrete value (not a bracketed placeholder, not empty).
#     "n/a - <reason>" counts as a concrete value.
#   - Draft text may be rough, but promotion to Approved also enforces basic
#     author-style hygiene: no raw three-dot ellipsis in non-code-fence text.
#   - Universally required: 'Related tickets'. This is the only field that
#     must be present regardless of spec scope. It encodes dependency chains
#     consumed by /spec-next and bulk-update.ps1.
#
# Exit codes: 0 = ready for Approved. 1 = blockers reported. 2 = bad invocation.

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') {
    Write-Error "Invalid -Id '$Id' (must match S####)."
    exit 2
}

$records = Read-Catalog
$record = $records | Where-Object { $_.id -eq $Id } | Select-Object -First 1
if (-not $record) {
    Write-Error "No record with id '$Id' in spec-catalog.jsonl."
    exit 2
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$specPath = Join-Path $repoRoot ($record.file -replace '/', [IO.Path]::DirectorySeparatorChar)
if (-not (Test-Path $specPath)) {
    Write-Error "Spec file not found on disk: $specPath"
    exit 2
}

$lines = Get-Content $specPath
$start = -1
$end = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($start -lt 0 -and $lines[$i] -match '^###\s+3\.3\s+Owner inputs') {
        $start = $i
        continue
    }
    if ($start -ge 0 -and ($lines[$i] -match '^##\s+' -or $lines[$i] -match '^###\s+(?!3\.3)')) {
        $end = $i
        break
    }
}
if ($start -lt 0) {
    Write-Output "FAIL $Id"
    Write-Output "Missing section: '### 3.3 Owner inputs (Approval gate)' in $specPath"
    Write-Output "Strategic specs without this section cannot transition Draft -> Approved."
    Write-Output "At minimum the section must contain a 'Related tickets' bullet."
    Write-Output "/spec auto-emits §3.3 with only the fields relevant to the spec's"
    Write-Output "detected character (UI / data / flavor / executable scope)."
    exit 1
}
if ($end -lt 0) { $end = $lines.Count }

$section = $lines[$start..($end - 1)]
$bulletPattern = '^\s*-\s+\*\*(?<field>[^*:]+):\*\*\s*(?<value>.*)$'
$blockers = New-Object System.Collections.Generic.List[string]
$found = @{}
foreach ($line in $section) {
    if ($line -match $bulletPattern) {
        $field = $Matches['field'].Trim()
        $value = $Matches['value'].Trim()
        $found[$field] = $value
        # Placeholder detection: value starts with '[' (bracketed hint, not yet edited).
        if ($value -match '^\[.*\]\s*$') {
            $blockers.Add("Field '$field' still carries placeholder: $value")
        } elseif ([string]::IsNullOrWhiteSpace($value)) {
            $blockers.Add("Field '$field' is empty")
        }
    }
}

# Universal requirement: 'Related tickets' must be a present, filled bullet.
if (-not $found.ContainsKey('Related tickets')) {
    $blockers.Add("Missing required field: 'Related tickets' (universally required in §3.3 regardless of scope; use 'none' if no dependencies)")
}

$inFence = $false
for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match '^\s*```') {
        $inFence = -not $inFence
        continue
    }
    if (-not $inFence -and $line.Contains('...')) {
        $blockers.Add(("Line {0}: replace three-dot ellipsis with '..' before Approved" -f ($i + 1)))
    }
}

if ($blockers.Count -gt 0) {
    Write-Output "FAIL $Id"
    foreach ($b in $blockers) { Write-Output "- $b" }
    Write-Output ""
    Write-Output "Spec '$Id' cannot transition Draft -> Approved until:"
    Write-Output "  1. every bullet present in §3.3 carries a concrete value, and"
    Write-Output "  2. 'Related tickets' is present (use 'none' if no dependencies), and"
    Write-Output "  3. approval-only author-style hygiene passes."
    Write-Output "The gate validates only what /spec emitted into §3.3 - it does not"
    Write-Output "require fields irrelevant to the spec's detected scope."
    exit 1
}

Write-Output "PASS $Id"
$count = $found.Count
Write-Output ("All $count Owner Input field(s) in §3.3 are filled. Spec is eligible for Draft -> Approved promotion.")
exit 0
