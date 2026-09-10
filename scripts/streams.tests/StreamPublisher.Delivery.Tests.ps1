$modulePath = Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Delivery.ps1'
$AllowFaviconlessPublish = $false
$ExistingCsv = ''
$PublishTag = 'test-tag'
$MaxAtlasBytes = 1024
. (Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Common.ps1')
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

    It 'normalizes all four facets without changing row identity' {
        $rows = @([pscustomobject]@{
                category = 'Radio (SomaFM)'; topic = 'Adult Contemporary'; language = 'American English, Gernan'
                country = 'Germany'; url = 'https://example.test/live'; name = 'Station'
            })
        $result = Normalize-CatalogFacetRows -Rows $rows
        $result.Rows.Count | Should Be 1
        $result.Rows[0].url | Should Be 'https://example.test/live'
        $result.Rows[0].name | Should Be 'Station'
        $result.Rows[0].category | Should Be 'Radio'
        $result.Rows[0].topic | Should Be 'Pop'
        $result.Rows[0].language | Should Be 'english,german'
        $result.Rows[0].country | Should Be 'DE'
        ($result.Moves | Where-Object { $_.facet -eq 'country' }).Count | Should Be 1
    }

    It 'recategorizes an already-shipped camera row from its rubric' {
        # The regression this guards: the webcam collectors emit category 'Live TV', so without the
        # rubric rule the shipped rows keep it and no Webcam filter can ever find them (S1476).
        $rows = @(
            [pscustomobject]@{
                category = 'Live TV'; topic = 'Webcam'; language = 'english'
                country = 'US'; url = 'https://example.test/cam'; name = 'Beach Cam'
            },
            [pscustomobject]@{
                category = 'Live TV'; topic = 'Traffic cams'; language = 'english'
                country = 'GB'; url = 'https://example.test/jam'; name = 'JamCam'
            },
            [pscustomobject]@{
                category = 'Live TV'; topic = 'News'; language = 'english'
                country = 'FR'; url = 'https://example.test/news'; name = 'News channel'
            }
        )
        $result = Normalize-CatalogFacetRows -Rows $rows
        $result.Rows[0].category | Should Be 'Webcam'
        $result.Rows[1].category | Should Be 'Webcam'
        # A non-camera rubric is untouched, so the rule cannot swallow the rest of the catalog.
        $result.Rows[2].category | Should Be 'Live TV'
        $result.Rows[1].topic | Should Be 'Traffic cams'
    }

    # BeExactly throughout: Pester 3's Be is case-insensitive, and half of what this key promises is which
    # parts keep their case. Be would pass on a function that folded the path too.
    It 'derives the same channel identity the app derives' {
        (Get-CatalogIdentityKey 'http://Example.Test:80/live/') | Should BeExactly 'web://example.test/live'
        (Get-CatalogIdentityKey 'https://example.test/live') | Should BeExactly 'web://example.test/live'
        (Get-CatalogIdentityKey 'rtsp://example.test:554/live') | Should BeExactly 'rtsp://example.test/live'
        (Get-CatalogIdentityKey 'https://example.test/live?a=1') | Should BeExactly 'web://example.test/live?a=1'
        (Get-CatalogIdentityKey 'not a url') | Should BeExactly 'not a url'
    }

    It 'keeps the path case, which is what tells two rungs of one channel apart' {
        (Get-CatalogIdentityKey 'https://example.test/Live') | Should BeExactly 'web://example.test/Live'
        (Get-CatalogIdentityKey 'https://example.test/Live') | Should Not BeExactly (Get-CatalogIdentityKey 'https://example.test/live')
    }

    It 'repairs the name column and reports every move by rule' {
        $rows = @(
            [pscustomobject]@{ name = 'Rust &amp; Roses Country'; url = 'https://a.test/live' },
            [pscustomobject]@{ name = '- 0 N - Blues on Radio'; url = 'https://b.test/live' },
            [pscustomobject]@{ name = 'Online Radio'; url = 'http://quincy.torontocast.com:2150/stream' },
            [pscustomobject]@{ name = '(null)'; url = 'http://hoth.alonhosting.com:3410/stream' },
            [pscustomobject]@{ name = 'Radio Paradise'; url = 'https://e.test/live' }
        )
        $result = Normalize-CatalogNameRows -Rows $rows
        $result.Rows[0].name | Should Be 'Rust & Roses Country'
        $result.Rows[1].name | Should Be 'Blues on Radio'
        $result.Rows[2].name | Should Be 'Online Radio (quincy.torontocast.com:2150)'
        $result.Rows[3].name | Should Be 'hoth.alonhosting.com:3410'
        $result.Rows[4].name | Should Be 'Radio Paradise'
        ($result.Moves | Where-Object { $_.rule -eq 'repair' }).Count | Should Be 2
        ($result.Moves | Where-Object { $_.rule -eq 'derive-suffix' }).Count | Should Be 1
        ($result.Moves | Where-Object { $_.rule -eq 'derive-replace' }).Count | Should Be 1
    }

    It 'never drops a row while repairing names' {
        $rows = @(
            [pscustomobject]@{ name = '(null)'; url = 'https://a.test/live' },
            [pscustomobject]@{ name = '-'; url = 'https://b.test/live' }
        )
        $result = Normalize-CatalogNameRows -Rows $rows
        $result.Rows.Count | Should Be 2
        $result.Rows[0].name | Should Be 'a.test'
        $result.Rows[1].name | Should Be 'b.test'
    }

    It 'collapses rows that fold to one identity and keeps the https copy' {
        $rows = @(
            [pscustomobject]@{ name = 'Alpha'; url = 'http://example.test/live'; homepage = '' },
            [pscustomobject]@{ name = 'Alpha'; url = 'https://example.test/live'; homepage = 'https://example.test' },
            [pscustomobject]@{ name = 'Beta'; url = 'https://other.test/live'; homepage = '' }
        )
        $result = Merge-CatalogIdentityDuplicates -Rows $rows
        $result.Rows.Count | Should Be 2
        $result.Dropped.Count | Should Be 1
        $result.Dropped[0].dropped_url | Should Be 'http://example.test/live'
        $result.Dropped[0].kept_url | Should Be 'https://example.test/live'
        @($result.Rows | Where-Object { $_.url -eq 'https://example.test/live' }).Count | Should Be 1
        @($result.Rows | Where-Object { $_.url -eq 'https://other.test/live' }).Count | Should Be 1
    }

    It 'keeps two genuinely different channels apart' {
        $rows = @(
            [pscustomobject]@{ name = 'Alpha'; url = 'https://example.test/one'; homepage = '' },
            [pscustomobject]@{ name = 'Alpha'; url = 'https://example.test/two'; homepage = '' }
        )
        $result = Merge-CatalogIdentityDuplicates -Rows $rows
        $result.Rows.Count | Should Be 2
        $result.Dropped.Count | Should Be 0
    }

    It 'refuses to publish a bank whose names were never repaired' {
        $threw = $false
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = '(null)' }) } catch { $threw = $true }
        $threw | Should Be $true

        $threw = $false
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = '-' }) } catch { $threw = $true }
        $threw | Should Be $true

        $threw = $false
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = 'Rust &amp; Roses' }) } catch { $threw = $true }
        $threw | Should Be $true

        $threw = $false
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = '- 0 N - Blues on Radio' }) } catch { $threw = $true }
        $threw | Should Be $true
    }

    It 'refuses to publish a name still carrying the replacement character' {
        $threw = $false
        $broken = "Roxy R$([char]0xFFFD)di$([char]0xFFFD)"
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = $broken }) } catch { $threw = $true }
        $threw | Should Be $true
        { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = 'Roxy Rádió' }) } | Should Not Throw
    }

    It 'separates the rows whose name cannot be repaired, keeping the rest' {
        $broken = "Radio Zzz$([char]0xFFFD)qqq"
        $rows = @(
            [pscustomobject]@{ name = 'Roxy Rádió'; url = 'http://a.test:8000/s'; license_note = 'xiph' },
            [pscustomobject]@{ name = $broken; url = 'http://b.test:8000/s'; license_note = 'xiph' }
        )
        $split = Split-CatalogUnrepairableNames -Rows $rows
        $split.Rows.Count | Should Be 1
        $split.Rows[0].name | Should BeExactly 'Roxy Rádió'
        $split.Dropped.Count | Should Be 1
        $split.Dropped[0].url | Should BeExactly 'http://b.test:8000/s'
        $split.Dropped[0].license_note | Should BeExactly 'xiph'
    }

    It 'names the repair mode in its refusal and passes a repaired bank' {
        $message = ''
        try { Assert-CatalogNamesClean -Rows @([pscustomobject]@{ name = '(null)' }) } catch { $message = $_.Exception.Message }
        ($message -match '-NormalizeNames') | Should Be $true
        { Assert-CatalogNamesClean -Rows @(
                [pscustomobject]@{ name = 'Blues on Radio' },
                [pscustomobject]@{ name = '.977 Country' },
                [pscustomobject]@{ name = 'Online Radio (quincy.torontocast.com:2150)' }
            ) } | Should Not Throw
    }
}
