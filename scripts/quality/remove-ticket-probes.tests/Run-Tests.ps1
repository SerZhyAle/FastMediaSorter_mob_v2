#requires -Version 7.0
# Exit codes: 0 all cases passed; 1 a case failed.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$script = Join-Path $repoRoot 'scripts\quality\remove-ticket-probes.ps1'
$output = (& pwsh -NoProfile -File $script -Id S1060 -WhatIf) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "What-if run failed with $LASTEXITCODE" }
if ($output -notmatch 'remove-ticket-probes: what-if') { throw 'What-if summary was not emitted.' }

# S2925: the remover resolves the repository from its own location, so a copy inside a scratch tree
# rewrites only the fixture below, never the real sources.
$sandbox = Join-Path $repoRoot ("temp/scratch/remove-ticket-probes-test-{0}" -f [guid]::NewGuid().ToString('N'))
try {
    $quality = Join-Path $sandbox 'scripts/quality'
    $source = Join-Path $sandbox 'app_v2/src/main/java/p'
    New-Item -ItemType Directory -Force -Path $quality, $source | Out-Null
    Copy-Item -LiteralPath $script -Destination $quality
    $lf = "package p`n`nimport q.Work`nimport timber.log.Timber`n`nclass A {`n    fun a(x: Int): Int {`n        val y = x + 1`n" +
        "        Timber.d(`"S9999: middle y=`$y`")`n        return y`n    }`n`n    fun b() {`n        work()`n" +
        "        Timber.d(`"S9999: last`")`n    }`n}`n"
    $fixture = Join-Path $source 'A.kt'
    [IO.File]::WriteAllText($fixture, $lf, [Text.UTF8Encoding]::new($false))
    & pwsh -NoProfile -File (Join-Path $quality 'remove-ticket-probes.ps1') -Id S9999 -Force -BackupDirectory 'bk' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Fixture run failed with $LASTEXITCODE" }
    $after = [IO.File]::ReadAllText($fixture)
    $expected = "package p`n`nimport q.Work`n`nclass A {`n    fun a(x: Int): Int {`n        val y = x + 1`n        return y`n    }`n`n" +
        "    fun b() {`n        work()`n    }`n}`n"
    if ($after.Contains("`r")) { throw 'An LF file gained a carriage return.' }
    if ($after -ne $expected) { throw "Probe lines were not removed whole. Got:`n$after" }
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Output 'remove-ticket-probes tests: PASS'
