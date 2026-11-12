#requires -Version 7.0
<#
.SYNOPSIS
    `.\a.ps1 r0` - start one portfolio worker for THIS repository: `pq start -Repo <key>` (S4541).

.DESCRIPTION
    Every robot pass of this repository goes through the portfolio queue (pq), so each pass has a ticket
    lease and a row in the portfolio's runs.jsonl. Before this launcher r0 was a MONO chain of its own
    (instance `mono`, no lease, its own journal): 38 of 106 robot passes of 2026-10-27 .. 11-10 never
    reached `pq report`, and three pq passes were launched on a ticket r0 was already working.

    The launcher finds two facts and then runs pq:
    - this repository's key in the portfolio registry (repos.json in SZA_PORTFOLIO_ROOT, else
      %USERPROFILE%\.sza\portfolio), matched by path, never spelled in a script;
    - the shipped pq.ps1 (SZA_HARNESS_ROOT, else the newest copy in the sza plugin cache), the same
      resolution order as the harness forwarders.

    Every argument it does not know goes to `pq start` unchanged, so `.\a.ps1 r0 -MaxTickets 5`,
    `-Until 18:30`, `-Drain` and `-Ticket fms-a:S1234` behave exactly as on pq. The worker is long-running
    by design: start it in its own window. Stop it with `pq stop` (pass level by default).

    The passes run `/spec-all {Id}` with a ticket lease (`runner.promptTemplate`), so this chain is no
    longer MONO. `/spec-all -m`, `/spec-code -m` and `/spec-next -m` are unchanged: one agent, one ticket.

    Simple-script mode on purpose, as in a.ps1: `$args` carries -flag tokens through untouched, which a
    declared parameter set would reject before they reached pq.

.PARAMETER DryRun
    The one flag this launcher keeps for itself: print the repository key, the registry and the `pq.ps1`
    it found and the command it would run, then exit 0 without starting a worker.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-pq-queue.ps1 -DryRun

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-pq-queue.ps1 -MaxTickets 3

.NOTES
    FMS_REPO_ROOT overrides the repository root the registry is matched against; the contract suite
    points it, SZA_PORTFOLIO_ROOT and SZA_HARNESS_ROOT at a fixture tree.

Exit codes (CLAUDE.md Rule 7):
  0  pq's own exit 0, or -DryRun resolved both facts.
  2  this repository is not in the portfolio registry, the registry could not be read, or no pq.ps1
     could be located; nothing was started.
  otherwise  pq's own exit code.
#>

$ErrorActionPreference = 'Stop'

$forwarded = @($args | ForEach-Object { "$_" })
$dryRun = @($forwarded | Where-Object { $_ -match '^-DryRun$' }).Count -gt 0
$forwarded = @($forwarded | Where-Object { $_ -notmatch '^-DryRun$' })

$repoRoot = if ($env:FMS_REPO_ROOT) { $env:FMS_REPO_ROOT } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $null }

# One spelling for comparison: the registry holds `p:\ANDROID\..` while this script holds `P:\ANDROID\..`,
# and a trailing separator is the other usual difference.
function ConvertTo-ComparablePath([string] $Path) {
    return ([System.IO.Path]::GetFullPath($Path) -replace '\\', '/').TrimEnd('/')
}

$portfolioRoot = if ($env:SZA_PORTFOLIO_ROOT) { $env:SZA_PORTFOLIO_ROOT }
                 elseif ($homeDir) { Join-Path $homeDir '.sza/portfolio' }
                 else { $null }
$registryPath = if ($portfolioRoot) { Join-Path $portfolioRoot 'repos.json' } else { $null }
if (-not $registryPath -or -not (Test-Path -LiteralPath $registryPath)) {
    Write-Host "run-pq-queue: no portfolio registry at '$registryPath' - register this repository first: pq register -Path `"$repoRoot`" -Enable" -ForegroundColor Red
    exit 2
}

try { $registry = Get-Content -LiteralPath $registryPath -Raw -Encoding UTF8 | ConvertFrom-Json }
catch {
    Write-Host "run-pq-queue: the registry '$registryPath' could not be read - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$here = ConvertTo-ComparablePath $repoRoot
$match = @($registry.repos | Where-Object {
        $_.path -and (ConvertTo-ComparablePath ([string]$_.path)) -ieq $here
    })
if ($match.Count -ne 1) {
    $why = if ($match.Count -eq 0) { 'is not registered' } else { "is registered $($match.Count) times" }
    Write-Host "run-pq-queue: this repository ($repoRoot) $why in '$registryPath' - register it once: pq register -Path `"$repoRoot`" -Enable" -ForegroundColor Red
    exit 2
}
$repoKey = [string]$match[0].key

# Candidate order matches the harness forwarders: an explicit root first, then the plugin cache by VERSION
# (a date-derived name like 2026.1112.3 sorts wrong as a string), a directory that does not parse last.
$candidates = @()
if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
$cache = if ($homeDir) { Join-Path $homeDir '.claude/plugins/cache/sza-unified-rules/sza' } else { $null }
if ($cache -and (Test-Path -LiteralPath $cache)) {
    $candidates += @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $parsed = $null
                [void][version]::TryParse($_.Name, [ref]$parsed)
                [pscustomobject]@{ Path = $_.FullName; Version = $parsed }
            } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                            @{ Expression = { $_.Version }; Descending = $true },
                            @{ Expression = { $_.Path }; Descending = $true } |
            ForEach-Object { Join-Path $_.Path 'tools/harness' })
}
$pqScript = $null
foreach ($dir in $candidates) {
    $probe = Join-Path $dir 'portfolio/pq.ps1'
    if (Test-Path -LiteralPath $probe) { $pqScript = $probe; break }
}
if (-not $pqScript) {
    Write-Host 'run-pq-queue: pq.ps1 not found - install the sza plugin or set SZA_HARNESS_ROOT to its tools/harness folder.' -ForegroundColor Red
    exit 2
}

$pwshExe = (Get-Process -Id $PID).Path
$pqArgs = @('start', '-Repo', $repoKey) + $forwarded
Write-Host "run-pq-queue: repository '$repoKey' ($registryPath)" -ForegroundColor DarkGray
Write-Host "run-pq-queue: $pwshExe -NoProfile -File $pqScript $($pqArgs -join ' ')" -ForegroundColor DarkGray
if ($dryRun) {
    Write-Host 'run-pq-queue: -DryRun - no worker started.' -ForegroundColor Yellow
    exit 0
}

& $pwshExe -NoProfile -File $pqScript @pqArgs
exit $LASTEXITCODE
