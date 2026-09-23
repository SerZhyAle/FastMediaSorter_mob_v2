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
    # S3401: a probe that was an effect's only statement takes the effect and its import with it,
    # in the file's own CRLF; an effect with other work keeps its body and its import.
    $crlf = ("package p`n`nimport androidx.compose.runtime.Composable`nimport androidx.compose.runtime.LaunchedEffect`n" +
        "import timber.log.Timber`n`n@Composable`nfun Top() {`n    LaunchedEffect(Unit) {`n        Timber.d(`"S9999: top`")`n" +
        "    }`n`n    Body()`n}`n`n@Composable`nfun Mid(items: List<Int>) {`n    val a = 1`n`n" +
        "    LaunchedEffect(items.size) {`n        Timber.d(`"S9999: mid`")`n    }`n`n    Body(a)`n}`n") -replace "`n", "`r`n"
    $effects = Join-Path $source 'B.kt'
    [IO.File]::WriteAllText($effects, $crlf, [Text.UTF8Encoding]::new($false))
    $kept = "package p`n`nimport androidx.compose.runtime.LaunchedEffect`nimport timber.log.Timber`n`nfun Kept(key: Int) {`n" +
        "    LaunchedEffect(key) {`n        Timber.d(`"S9999: kept`")`n        load(key)`n    }`n    Timber.i(`"permanent`")`n}`n"
    $keptFixture = Join-Path $source 'C.kt'
    [IO.File]::WriteAllText($keptFixture, $kept, [Text.UTF8Encoding]::new($false))
    & pwsh -NoProfile -File (Join-Path $quality 'remove-ticket-probes.ps1') -Id S9999 -Force -BackupDirectory 'bk' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Fixture run failed with $LASTEXITCODE" }
    $after = [IO.File]::ReadAllText($fixture)
    $expected = "package p`n`nimport q.Work`n`nclass A {`n    fun a(x: Int): Int {`n        val y = x + 1`n        return y`n    }`n`n" +
        "    fun b() {`n        work()`n    }`n}`n"
    if ($after.Contains("`r")) { throw 'An LF file gained a carriage return.' }
    if ($after -ne $expected) { throw "Probe lines were not removed whole. Got:`n$after" }
    $effectsAfter = [IO.File]::ReadAllText($effects)
    $effectsExpected = ("package p`n`nimport androidx.compose.runtime.Composable`n`n@Composable`nfun Top() {`n    Body()`n}`n`n" +
        "@Composable`nfun Mid(items: List<Int>) {`n    val a = 1`n`n    Body(a)`n}`n") -replace "`n", "`r`n"
    if ($effectsAfter -ne $effectsExpected) { throw "An emptied effect or its import survived. Got:`n$effectsAfter" }
    $keptAfter = [IO.File]::ReadAllText($keptFixture)
    $keptExpected = "package p`n`nimport androidx.compose.runtime.LaunchedEffect`nimport timber.log.Timber`n`nfun Kept(key: Int) {`n" +
        "    LaunchedEffect(key) {`n        load(key)`n    }`n    Timber.i(`"permanent`")`n}`n"
    if ($keptAfter -ne $keptExpected) { throw "An effect with remaining work was altered. Got:`n$keptAfter" }
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Output 'remove-ticket-probes tests: PASS'
