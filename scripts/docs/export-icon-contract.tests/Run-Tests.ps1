#requires -Version 7.0
<#
.SYNOPSIS
    S4655 - contract test of the read-only mode (-Check) of the ICON-SET exporter.

.DESCRIPTION
    Subject: scripts/docs/export-icon-contract.ps1

    ICON-SET section 6 rung 1 (from 0.39) requires every generated file to equal what the exporter would
    write. -Check is the mode that proves it without writing. Each case builds a two-record catalog in a
    temporary directory (both glyphs inline, so no drawable of this product is read), runs the exporter to
    generate it, damages it one way, and asserts that -Check exits 1, names the damaged file with its
    reason, and leaves the damage exactly where it was. The negative cases are the point: a check whose
    refusal was never seen may be green forever.

    The colour resources and the repo venv are the real ones of -RepoRoot; the catalog is the only thing
    written, and it lives under the system temp directory.

.PARAMETER RepoRoot
    Root of this product. The exporter reads its colour resources and venv from here.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/export-icon-contract.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: the exporter is missing, or it could not generate the fixture catalog
         (no repo venv)
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$exporter = Join-Path $RepoRoot 'scripts/docs/export-icon-contract.ps1'
if (-not (Test-Path -LiteralPath $exporter)) { Write-Error "missing: $exporter" -ErrorAction Continue; exit 2 }
$utf8 = [System.Text.UTF8Encoding]::new($false)

$script:failures = 0
function Assert-That([bool] $Condition, [string] $Name) {
    if ($Condition) { Write-Host "  ok   $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL $Name" -ForegroundColor Red; $script:failures++ }
}

function Invoke-Exporter([string] $Catalog, [switch] $Check) {
    $exporterArgs = @('-CatalogRoot', $Catalog, '-RepoRoot', $RepoRoot)
    if ($Check) { $exporterArgs += '-Check' }
    $output = & pwsh -NoProfile -File $exporter @exporterArgs 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Write-Text([string] $Path, [string] $Text) {
    $null = New-Item -ItemType Directory -Force (Split-Path -Parent $Path)
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

# What the disk holds, as one comparable string: relative path and content hash of every file.
function Get-Snapshot([string] $Dir) {
    $rows = foreach ($f in Get-ChildItem -LiteralPath $Dir -Recurse -File | Sort-Object FullName) {
        $rel = ($f.FullName.Substring($Dir.Length).TrimStart('\', '/')) -replace '\\', '/'
        $rel + ':' + (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash
    }
    return ($rows -join "`n")
}

$circle = '<circle cx=\"12\" cy=\"12\" r=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"/>'
$square = '<rect x=\"5\" y=\"5\" width=\"14\" height=\"14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"/>'
$squareAmended = '<rect x=\"4\" y=\"4\" width=\"16\" height=\"16\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"/>'
function Get-Vocabulary([string] $SecondGlyph) {
    $one = '{"id":"action.demo-one","group":"action","status":"active","rtl":"fixed","colour":"content","name":{"en":"One","ru":"Odin","uk":"Odyn"},"means":"A demo meaning.","shape":"circle","glyph":{"svg":"' + $circle + '","source":"fixture"}}'
    $two = '{"id":"action.demo-two","group":"action","status":"active","rtl":"fixed","colour":"content","name":{"en":"Two","ru":"Dva","uk":"Dva"},"means":"Another demo meaning.","shape":"square","glyph":{"svg":"' + $SecondGlyph + '","source":"fixture"}}'
    return $one + "`n" + $two + "`n"
}

function New-Catalog {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('icon-export-check-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $domain = Join-Path $root 'iconography'
    $fence = '```'
    Write-Text (Join-Path $domain 'README.md') ("# Fixture`n`n" + $fence + "contract`nid: ICON-SET`nversion: 0.1`n" + $fence + "`n")
    Write-Text (Join-Path $domain 'vocabulary.version') "ICON-SET 0.1`n"
    Write-Text (Join-Path $domain 'vocabulary.jsonl') (Get-Vocabulary $square)
    return $root
}

$catalog = New-Catalog
try {
    $domain = Join-Path $catalog 'iconography'
    $glyphOne = Join-Path $domain 'glyphs/action.demo-one.svg'
    $glyphTwo = Join-Path $domain 'glyphs/action.demo-two.svg'

    Write-Host 'generate the fixture catalog'
    $gen = Invoke-Exporter $catalog
    if ($gen.Exit -eq 2) { Write-Host $gen.Output; Write-Error 'the exporter could not generate the fixture catalog' -ErrorAction Continue; exit 2 }
    Assert-That ($gen.Exit -eq 0) 'a run exits 0 and writes the catalog'
    Assert-That ((Test-Path -LiteralPath $glyphOne) -and (Test-Path -LiteralPath $glyphTwo) -and
        (Test-Path -LiteralPath (Join-Path $domain 'CATALOG.md')) -and (Test-Path -LiteralPath (Join-Path $domain 'gallery.html')) -and
        (Test-Path -LiteralPath (Join-Path $domain 'palette.json'))) 'the run wrote both glyphs, CATALOG.md, gallery.html and palette.json'

    Write-Host '-Check on a freshly generated catalog'
    $before = Get-Snapshot $domain
    $clean = Invoke-Exporter $catalog -Check
    Assert-That ($clean.Exit -eq 0) '-Check exits 0 when every file equals what a run would write'
    Assert-That ($clean.Output -match 'PASS \(check\)') '-Check names its mode in the verdict line'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check wrote nothing on a clean catalog'

    Write-Host 'a generated file that was hand-edited'
    [System.IO.File]::AppendAllText($glyphOne, '<!-- hand edit -->', $utf8)
    $before = Get-Snapshot $domain
    $edited = Invoke-Exporter $catalog -Check
    Assert-That ($edited.Exit -eq 1) '-Check exits 1 on a hand-edited glyph'
    Assert-That ($edited.Output -match 'glyphs/action\.demo-one\.svg - stale or hand-edited') '-Check names the hand-edited glyph and says why'
    Assert-That ($edited.Output -notmatch 'action\.demo-two\.svg') '-Check does not name the glyph that is untouched'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check left the hand edit in place and wrote nothing'

    Write-Host 'a hand amendment of the vocabulary that no run has followed'
    $fix = Invoke-Exporter $catalog
    Assert-That ($fix.Exit -eq 0) 'a run after the damage exits 0'
    Assert-That ((Invoke-Exporter $catalog -Check).Exit -eq 0) '-Check exits 0 again once the run has regenerated the catalog'
    Write-Text (Join-Path $domain 'vocabulary.jsonl') (Get-Vocabulary $squareAmended)
    $before = Get-Snapshot $domain
    $stale = Invoke-Exporter $catalog -Check
    Assert-That ($stale.Exit -eq 1) '-Check exits 1 when the vocabulary was amended and no run followed'
    Assert-That ($stale.Output -match 'glyphs/action\.demo-two\.svg - stale') '-Check names the glyph whose record changed'
    Assert-That ($stale.Output -match 'gallery\.html - stale') '-Check names the gallery, which embeds every inline drawing'
    Assert-That ($stale.Output -notmatch 'action\.demo-one\.svg') '-Check does not name the glyph whose record did not change'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check left the stale files as they were'
    $null = Invoke-Exporter $catalog

    Write-Host 'a generated file that is missing'
    Remove-Item -LiteralPath $glyphTwo -Force
    $before = Get-Snapshot $domain
    $missing = Invoke-Exporter $catalog -Check
    Assert-That ($missing.Exit -eq 1) '-Check exits 1 when a generated file is missing'
    Assert-That ($missing.Output -match 'glyphs/action\.demo-two\.svg - missing') '-Check names the missing glyph and says a run would create it'
    Assert-That (-not (Test-Path -LiteralPath $glyphTwo)) '-Check did not create the missing file'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check wrote nothing while reporting the missing file'
    $null = Invoke-Exporter $catalog

    Write-Host 'a file the vocabulary no longer names'
    $stray = Join-Path $domain 'glyphs/stray.svg'
    $strayLook = Join-Path $domain 'looks/stray.decorated.svg'
    Write-Text $stray "<svg/>`n"
    Write-Text $strayLook "<svg/>`n"
    $before = Get-Snapshot $domain
    $extra = Invoke-Exporter $catalog -Check
    Assert-That ($extra.Exit -eq 1) '-Check exits 1 when a run would prune a file'
    Assert-That ($extra.Output -match 'glyphs/stray\.svg - extraneous' -and $extra.Output -match 'looks/stray\.decorated\.svg - extraneous') '-Check names each file a run would prune'
    Assert-That ((Test-Path -LiteralPath $stray) -and (Test-Path -LiteralPath $strayLook)) '-Check pruned nothing'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check wrote nothing while reporting the extraneous files'
    $pruned = Invoke-Exporter $catalog
    Assert-That ($pruned.Exit -eq 0 -and -not (Test-Path -LiteralPath $stray) -and -not (Test-Path -LiteralPath $strayLook)) 'a run prunes what -Check named as extraneous'
    Assert-That ((Invoke-Exporter $catalog -Check).Exit -eq 0) '-Check exits 0 after that run'

    Write-Host 'the generated reading page and the palette'
    [System.IO.File]::AppendAllText((Join-Path $domain 'CATALOG.md'), "hand edit`n", $utf8)
    [System.IO.File]::AppendAllText((Join-Path $domain 'palette.json'), "`n", $utf8)
    $page = Invoke-Exporter $catalog -Check
    Assert-That ($page.Exit -eq 1) '-Check exits 1 on an edited CATALOG.md and palette.json'
    Assert-That ($page.Output -match 'CATALOG\.md - stale' -and $page.Output -match 'palette\.json - stale') '-Check names each edited generated page'
    $null = Invoke-Exporter $catalog

    Write-Host 'a carrier that disagrees with the document version'
    Write-Text (Join-Path $domain 'vocabulary.version') "ICON-SET 0.0`n"
    $before = Get-Snapshot $domain
    $carrier = Invoke-Exporter $catalog -Check
    Assert-That ($carrier.Exit -eq 1 -and $carrier.Output -match 'vocabulary\.version') '-Check keeps the carrier refusal, exit 1, and names the carrier'
    Assert-That ((Get-Snapshot $domain) -eq $before) '-Check wrote nothing while refusing on the carrier'
    Write-Text (Join-Path $domain 'vocabulary.version') "ICON-SET 0.1`n"

    Write-Host 'a catalog the exporter cannot read'
    $empty = Join-Path ([System.IO.Path]::GetTempPath()) ('icon-export-check-empty-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $empty
    try {
        $none = Invoke-Exporter $empty -Check
        Assert-That ($none.Exit -eq 2 -and $none.Output -match 'COULD NOT VERIFY') '-Check on a catalog with no vocabulary is exit 2, could not verify'
        Assert-That (-not (Test-Path -LiteralPath (Join-Path $empty 'iconography'))) '-Check created nothing in a catalog it could not read'
    }
    finally { Remove-Item -LiteralPath $empty -Recurse -Force -ErrorAction SilentlyContinue }
}
finally { Remove-Item -LiteralPath $catalog -Recurse -Force -ErrorAction SilentlyContinue }

if ($script:failures) { Write-Host "export-icon-contract.tests: $($script:failures) assertion(s) FAILED"; exit 1 }
Write-Host 'export-icon-contract.tests: PASS'
exit 0
