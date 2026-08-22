$modulePath = Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Delivery.ps1'
$AllowFaviconlessPublish = $false
$ExistingCsv = ''
$PublishTag = 'test-tag'
$MaxAtlasBytes = 1024
. $modulePath

Describe 'StreamPublisher.Delivery' {
    It 'rejects indexed rows without an atlas' {
        $rows = @([pscustomobject]@{ favicon_index = '0' })
        $threw = $false
        try { Assert-FaviconIndexPairing -Rows $rows -BundledAtlas $false } catch { $threw = $true }
        $threw | Should Be $true
        { Assert-FaviconIndexPairing -Rows $rows -BundledAtlas $false -AllowFaviconlessPublish } | Should Not Throw
    }

    It 'accepts a CSV-first ZIP with the exact atlas name' {
        $csv = Join-Path $TestDrive 'streams.csv'
        $atlas = Join-Path $TestDrive 'favicon-atlas.png'
        $zip = Join-Path $TestDrive 'catalog.zip'
        Set-Content -LiteralPath $csv -Value 'name,url' -Encoding utf8NoBOM
        Set-Content -LiteralPath $atlas -Value 'atlas' -Encoding utf8NoBOM
        Compress-Archive -Path $csv -DestinationPath $zip -Force
        Compress-Archive -Path $atlas -DestinationPath $zip -Update
        $entries = @(Assert-CatalogZipEntries -ZipPath $zip -BundledAtlas $true)
        $entries[0] | Should Be 'streams.csv'
        ($entries -contains 'favicon-atlas.png') | Should Be $true
    }

    It 'rejects a ZIP whose first entry is not streams.csv' {
        $csv = Join-Path $TestDrive 'wrong.csv'
        $zip = Join-Path $TestDrive 'wrong-order.zip'
        Set-Content -LiteralPath $csv -Value 'name,url' -Encoding utf8NoBOM
        Compress-Archive -Path $csv -DestinationPath $zip -Force
        $threw = $false
        try { Assert-CatalogZipEntries -ZipPath $zip -BundledAtlas $false } catch { $threw = $true }
        $threw | Should Be $true
    }
}
