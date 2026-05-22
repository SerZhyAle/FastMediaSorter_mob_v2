#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Apply repository metadata (description, topics, homepage) to a GitHub
    repository so that GitHub Store (OpenHub-Store/GitHub-Store) can
    discover and rank the project. Idempotent — re-running converges to
    the values defined in DECISIONS.md.

.DESCRIPTION
    Reads frozen choices from
        PLAN/S0214_github-store-publication/DECISIONS.md
    and PATCHes the target repository description + homepage, then PUTs
    the topic list via the GitHub REST API.

    Authentication priority (first match wins):
      1. gh CLI session (gh auth status returns exit 0).
      2. $env:GITHUB_TOKEN with `repo` scope.

    Aborts if neither is available.

.PARAMETER DryRun
    Parse and validate DECISIONS.md, resolve credentials, but skip every
    HTTP mutation. Prints the values that would be applied.

.PARAMETER Owner
    Repository owner. Defaults to "SerZhyAle".

.PARAMETER Repo
    Repository name. Defaults to "FastMediaSorter_mob_v2".

.EXAMPLE
    pwsh -File scripts/release/apply-github-store-metadata.ps1 -DryRun

.EXAMPLE
    pwsh -File scripts/release/apply-github-store-metadata.ps1

.NOTES
    Spec: S0214 — github-store-publication. See
    PLAN/S0214_github-store-publication/PHASE_02__repo-metadata.md.
#>

[CmdletBinding()]
param(
    [switch] $DryRun,
    [string] $Owner = "SerZhyAle",
    [string] $Repo  = "FastMediaSorter_mob_v2"
)

$ErrorActionPreference = "Stop"

# ----------------------------------------------------------------------
# Credential resolver
# ----------------------------------------------------------------------
function Resolve-GitHubCredential {
    # Returns a hashtable: @{ Mode = "gh" | "token"; Token = <string-or-null> }
    # Throws if neither path resolves.

    # 1) gh CLI session
    $ghOnPath = Get-Command gh -ErrorAction SilentlyContinue
    if ($ghOnPath) {
        $null = & gh auth status 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @{ Mode = "gh"; Token = $null }
        }
    }

    # 2) Environment token
    if ($env:GITHUB_TOKEN) {
        return @{ Mode = "token"; Token = $env:GITHUB_TOKEN }
    }

    throw "No GitHub credentials available. Run 'gh auth login' or set `$env:GITHUB_TOKEN with 'repo' scope."
}

# ----------------------------------------------------------------------
# DECISIONS.md parser
# ----------------------------------------------------------------------
function Read-Decisions {
    # Returns @{ Topics = [string[]]; Description = string }
    # Throws on validation failure with the specific rule that broke.

    $scriptRoot = $PSScriptRoot
    $repoRoot   = Resolve-Path (Join-Path $scriptRoot "..\..")
    $decisions  = Join-Path $repoRoot "PLAN/S0214_github-store-publication/DECISIONS.md"

    if (-not (Test-Path -LiteralPath $decisions)) {
        throw "DECISIONS.md not found at: $decisions"
    }

    $lines = Get-Content -LiteralPath $decisions

    # --- Topics block ------------------------------------------------------
    $topicsStart = ($lines | Select-String -Pattern '^## Topics$' | Select-Object -First 1).LineNumber
    if (-not $topicsStart) {
        throw "DECISIONS.md missing '## Topics' heading"
    }

    $topics = [System.Collections.Generic.List[string]]::new()
    for ($i = $topicsStart; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^## ') { break }
        if ($line -match '^-\s+`([^`]+)`') {
            $topics.Add($Matches[1])
        }
    }

    if ($topics.Count -lt 1 -or $topics.Count -gt 20) {
        throw "Topics count $($topics.Count) outside [1..20] (GitHub hard limit)"
    }
    foreach ($t in $topics) {
        if ($t.Length -gt 50) {
            throw "Topic '$t' exceeds 50 chars"
        }
        if ($t -notmatch '^[a-z0-9][a-z0-9-]*$') {
            throw "Topic '$t' is not lowercase kebab-case"
        }
    }

    # --- Repo description block -------------------------------------------
    $descStart = ($lines | Select-String -Pattern '^## Repo description$' | Select-Object -First 1).LineNumber
    if (-not $descStart) {
        throw "DECISIONS.md missing '## Repo description' heading"
    }

    $description = $null
    for ($i = $descStart; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^## ') { break }
        $stripped = $line.Trim()
        if ($stripped -eq '') { continue }
        if ($stripped.StartsWith('---')) { continue }
        # Skip the introductory note line (starts with letter, ends with colon)
        if ($stripped -match '^English single-line') { continue }
        $description = $stripped
        break
    }

    if (-not $description) {
        throw "DECISIONS.md '## Repo description' section has no content line"
    }
    if ($description.Length -gt 350) {
        throw "Description length $($description.Length) exceeds 350 chars"
    }

    return @{
        Topics      = $topics.ToArray()
        Description = $description
    }
}

# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------
Write-Host "apply-github-store-metadata.ps1 — target: $Owner/$Repo" -ForegroundColor Cyan

$decisions = Read-Decisions
Write-Host "Parsed topics ($($decisions.Topics.Count)):" -ForegroundColor Cyan
foreach ($t in $decisions.Topics) {
    Write-Host "  - $t"
}
Write-Host "Parsed description ($($decisions.Description.Length) chars):" -ForegroundColor Cyan
Write-Host "  $($decisions.Description)"

if ($DryRun) {
    Write-Host "Dry-run requested — credentials and API calls skipped." -ForegroundColor Yellow
    exit 0
}

$creds = Resolve-GitHubCredential
Write-Host "Credential mode: $($creds.Mode)" -ForegroundColor Green

$homepage = "https://serzhyale.github.io/FastMediaSorter_mob_v2/"

# ----------------------------------------------------------------------
# HTTP helpers
# ----------------------------------------------------------------------
function Invoke-GitHubApi {
    param(
        [Parameter(Mandatory)] [ValidateSet("GET","PATCH","PUT","POST")] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [object] $Body,
        [hashtable] $ExtraHeaders
    )

    $uri = "https://api.github.com$Path"
    $headers = @{
        "Accept"               = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }
    if ($ExtraHeaders) {
        foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }
    }

    if ($creds.Mode -eq "token") {
        $headers["Authorization"] = "Bearer $($creds.Token)"
    } else {
        # gh CLI: use the cached token via `gh auth token`
        $token = (& gh auth token).Trim()
        $headers["Authorization"] = "Bearer $token"
    }

    $bodyJson = $null
    if ($Body) {
        $bodyJson = ($Body | ConvertTo-Json -Depth 5 -Compress)
    }

    try {
        if ($bodyJson) {
            return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $bodyJson -ContentType "application/json"
        } else {
            return Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers
        }
    } catch {
        $resp = $_.Exception.Response
        Write-Host "GitHub API call failed: $Method $Path" -ForegroundColor Red
        if ($resp) {
            try {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                Write-Host $reader.ReadToEnd() -ForegroundColor Red
            } catch {}
        }
        throw
    }
}

# ----------------------------------------------------------------------
# Apply: description + homepage
# ----------------------------------------------------------------------
Write-Host "BEFORE state:" -ForegroundColor DarkGray
$before = Invoke-GitHubApi -Method GET -Path "/repos/$Owner/$Repo"
$beforeTopics = (Invoke-GitHubApi -Method GET -Path "/repos/$Owner/$Repo/topics").names
Write-Host ("  description: " + ($before.description -as [string]))
Write-Host ("  homepage   : " + ($before.homepage    -as [string]))
Write-Host ("  topics     : " + ($beforeTopics -join ", "))

Write-Host "Applying PATCH /repos/$Owner/$Repo (description + homepage) .." -ForegroundColor Cyan
$null = Invoke-GitHubApi -Method PATCH -Path "/repos/$Owner/$Repo" -Body @{
    description = $decisions.Description
    homepage    = $homepage
}

Write-Host "Applying PUT /repos/$Owner/$Repo/topics .." -ForegroundColor Cyan
$null = Invoke-GitHubApi -Method PUT -Path "/repos/$Owner/$Repo/topics" -Body @{
    names = $decisions.Topics
}

# ----------------------------------------------------------------------
# Verify
# ----------------------------------------------------------------------
Write-Host "AFTER state:" -ForegroundColor DarkGray
$after = Invoke-GitHubApi -Method GET -Path "/repos/$Owner/$Repo"
$afterTopics = (Invoke-GitHubApi -Method GET -Path "/repos/$Owner/$Repo/topics").names
Write-Host ("  description: " + $after.description)
Write-Host ("  homepage   : " + $after.homepage)
Write-Host ("  topics     : " + ($afterTopics -join ", "))

$descOk = ($after.description -eq $decisions.Description)
$homeOk = ($after.homepage    -eq $homepage)
$topicsOk = ($null -ne $afterTopics) -and ($afterTopics.Count -eq $decisions.Topics.Count) -and (
    -not (Compare-Object $afterTopics $decisions.Topics)
)

if ($descOk -and $homeOk -and $topicsOk) {
    Write-Host "OK — metadata applied and verified." -ForegroundColor Green
    exit 0
} else {
    Write-Host "MISMATCH after apply:" -ForegroundColor Red
    Write-Host "  description ok: $descOk"
    Write-Host "  homepage ok   : $homeOk"
    Write-Host "  topics ok     : $topicsOk"
    exit 2
}
