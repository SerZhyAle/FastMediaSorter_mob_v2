$modulePath = Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Artwork.ps1'
$MaxAtlasBytes = 1024
$LogoCacheDir = $TestDrive
$FaviconTimeoutSec = 1
$FaviconS2Fallback = $false
$FaviconThrottle = 1
$DomainFallback = $false
$FfmpegPath = ''
$PreviewFrameDir = $TestDrive
. $modulePath

Describe 'StreamPublisher.Artwork' {
    It 'keeps the favicon atlas geometry contract' {
        $script:FaviconTile | Should Be 32
        $script:FaviconCols | Should Be 16
    }

    It 'accepts an atlas at the byte ceiling' {
        $path = Join-Path $TestDrive 'atlas-ok.bin'
        [System.IO.File]::WriteAllBytes($path, (New-Object byte[] 1024))
        { Assert-AtlasBudget -Path $path -Tiles 16 } | Should Not Throw
    }

    It 'rejects and removes an over-budget atlas' {
        $path = Join-Path $TestDrive 'atlas-over.bin'
        [System.IO.File]::WriteAllBytes($path, (New-Object byte[] 1025))
        $threw = $false
        try { Assert-AtlasBudget -Path $path -Tiles 16 } catch { $threw = $true }
        $threw | Should Be $true
        Test-Path $path | Should Be $false
    }
}
