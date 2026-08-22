<#
.SYNOPSIS
    S1548 reproducer - exercise the rule-digest gate's failure path on a throwaway copy.

.DESCRIPTION
    Copies the four role files into temp/S1548/fakeroot and strips one 'Rule N' citation
    from each full digest, which is the drift the gate exists to catch. Kept out of the
    live tree deliberately: breaking the real AGENTS.md to watch a gate go red would hand
    every concurrent session a wrong rules file for the duration.

    Usage:
      pwsh -NoProfile -File PLAN/S1548_rule-digest-mirroring-contract/repro/build-fakeroot.ps1
      pwsh -NoProfile -File scripts/quality/assert-rule-digest-sync.ps1 -Gate -RepoRoot <printed path>

    Expected: exit 1, naming Rule 21 uncited in AGENTS.md and Rule 30 uncited in
    .github/copilot-instructions.md. Against the real repo root the same call exits 0.

    Exit codes:
      0 - fakeroot built.
      2 - a role file is missing at the source root, so nothing could be copied.
#>
param(
    [string] $SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$fake = Join-Path $SourceRoot 'temp\S1548\fakeroot'
$roleFiles = @('CLAUDE.md', 'AGENTS.md', 'GEMINI.md', '.github/copilot-instructions.md')

foreach ($rel in $roleFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $SourceRoot $rel))) {
        Write-Error "build-fakeroot: role file missing at source root: $rel" -ErrorAction Continue
        exit 2
    }
}

if (Test-Path $fake) { Remove-Item $fake -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $fake '.github') | Out-Null

foreach ($rel in $roleFiles) {
    Copy-Item (Join-Path $SourceRoot $rel) (Join-Path $fake ($rel -replace '/', '\'))
}

# Break exactly one citation per digest, so the gate must report both files, not just the first.
$agents = Join-Path $fake 'AGENTS.md'
[System.IO.File]::WriteAllText($agents, ([System.IO.File]::ReadAllText($agents) -replace 'CLAUDE\.md Rule 21', 'CLAUDE.md the deprecated-flags rule'))

$copilot = Join-Path $fake '.github\copilot-instructions.md'
[System.IO.File]::WriteAllText($copilot, ([System.IO.File]::ReadAllText($copilot) -replace 'CLAUDE\.md Rule 30', 'CLAUDE.md the locales rule'))

Write-Host "fakeroot ready at $fake - dropped the Rule 21 citation from AGENTS.md and the Rule 30 citation from copilot-instructions.md"
exit 0
