# INSTALL-TRUST 1.0 declaration (S3451), read by scripts/quality/assert-install-trust.ps1.
#
# The one place that names every surface handing out this product's APK outside a store, the trust
# page each must link, and the facts the page's "what the app never does" section shares with the
# privacy policy and the manifest. A new hand-out surface is a new row under Surfaces; a file that
# carries a hand-out marker and has no row here fails the gate as UNDECLARED, which is what keeps a
# new download page from shipping without the link (contract rule 7).
@{
    # Rule 1: the four sections, as level-2 headings, in this order. Further headings may follow.
    Pages = @(
        @{
            Locale      = 'en'
            Path        = 'docs/INSTALL_TRUST.md'
            Privacy     = 'docs/PRIVACY_POLICY.md'
            PrivacyLink = 'PRIVACY_POLICY.md'
            Headings    = @('What you will see', 'Why it appears', 'What to tap', 'What the app never does')
        }
        @{
            Locale      = 'ru'
            Path        = 'docs/INSTALL_TRUST-ru.md'
            Privacy     = 'docs/PRIVACY_POLICY-ru.md'
            PrivacyLink = 'PRIVACY_POLICY-ru.md'
            Headings    = @('Что вы увидите', 'Почему оно появляется', 'Что нажимать', 'Чего приложение не делает никогда')
        }
        @{
            Locale      = 'uk'
            Path        = 'docs/INSTALL_TRUST-uk.md'
            Privacy     = 'docs/PRIVACY_POLICY-uk.md'
            PrivacyLink = 'PRIVACY_POLICY-uk.md'
            Headings    = @('Що ви побачите', "Чому воно з'являється", 'Що натискати', 'Чого застосунок не робить ніколи')
        }
    )

    # Rule 7: every declared surface must exist and contain Link verbatim.
    Surfaces = @(
        @{ Path = 'README.md';            Link = 'docs/INSTALL_TRUST.md' }
        @{ Path = 'docs/README.md';       Link = '(INSTALL_TRUST.md)' }
        @{ Path = 'docs/README-ru.md';    Link = '(INSTALL_TRUST-ru.md)' }
        @{ Path = 'docs/README-uk.md';    Link = '(INSTALL_TRUST-uk.md)' }
        @{ Path = 'docs/DOWNLOADS.md';    Link = '(INSTALL_TRUST.md)' }
        @{ Path = 'docs/DOWNLOADS-ru.md'; Link = '(INSTALL_TRUST-ru.md)' }
        @{ Path = 'docs/DOWNLOADS-uk.md'; Link = '(INSTALL_TRUST-uk.md)' }
        @{ Path = 'index.html';           Link = 'href="docs/INSTALL_TRUST_EN.html"' }
        @{ Path = 'index-ru.html';        Link = 'href="docs/INSTALL_TRUST_RU.html"' }
        @{ Path = 'index-uk.html';        Link = 'href="docs/INSTALL_TRUST_UK.html"' }
        @{ Path = 'nolegal.html';         Link = 'href="docs/INSTALL_TRUST_EN.html"' }
        @{ Path = 'nolegal-ru.html';      Link = 'href="docs/INSTALL_TRUST_RU.html"' }
        @{ Path = 'nolegal-uk.html';      Link = 'href="docs/INSTALL_TRUST_UK.html"' }
    )

    # A .md or .html file containing any of these hands the APK out. Matched literally.
    Markers = @(
        'github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest'
        'drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp'
        'apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter'
    )

    # Top-level directories the marker scan does not enter, each with the reason it is not a surface.
    Exempt = @(
        @{ Prefix = 'PLAN';         Reason = 'spec files, never published' }
        @{ Prefix = 'dev';          Reason = 'developer notes, never published' }
        @{ Prefix = 'temp';         Reason = 'scratch artifacts' }
        @{ Prefix = 'V1';           Reason = 'read-only archive' }
        @{ Prefix = 'v2_6';         Reason = 'read-only archive' }
        @{ Prefix = 'spec_v2';      Reason = 'read-only archive' }
        @{ Prefix = '.claude';      Reason = 'agent configuration' }
        @{ Prefix = '.agents';      Reason = 'agent configuration' }
        @{ Prefix = '.github';      Reason = 'CI configuration' }
        @{ Prefix = '.git';         Reason = 'repository store' }
        @{ Prefix = '.gradle';      Reason = 'build cache' }
        @{ Prefix = 'build';        Reason = 'build output' }
        @{ Prefix = 'app_v2';       Reason = 'phone module source, no published page' }
        @{ Prefix = 'wear';         Reason = 'watch module source; the watch APK is not handed out outside the store' }
        @{ Prefix = 'scripts';      Reason = 'tooling documentation, never published' }
        @{ Prefix = 'store_assets'; Reason = 'announcement drafts posted on third-party forums; rule 7 names the site page, the README and the first-run surface, none of which these are' }
    )

    # Rule 6: each claim phrase must stand in the page's "never does" section and its counterpart in
    # the same locale's privacy policy. Case-insensitive, markdown emphasis stripped first.
    Claims = @(
        @{ Id = 'no-servers';     Locale = 'en'; Trust = 'no servers';          Privacy = 'No servers' }
        @{ Id = 'no-analytics';   Locale = 'en'; Trust = 'no analytics';        Privacy = 'No analytics' }
        @{ Id = 'no-advertising'; Locale = 'en'; Trust = 'no advertising';      Privacy = 'No advertising' }
        @{ Id = 'no-servers';     Locale = 'ru'; Trust = 'нет серверов';        Privacy = 'Нет серверов' }
        @{ Id = 'no-analytics';   Locale = 'ru'; Trust = 'нет аналитики';       Privacy = 'Никакой аналитики' }
        @{ Id = 'no-advertising'; Locale = 'ru'; Trust = 'рекламы и трекинга';  Privacy = 'Никаких рекламных данных' }
        @{ Id = 'no-servers';     Locale = 'uk'; Trust = 'немає серверів';      Privacy = 'Немає серверів' }
        @{ Id = 'no-analytics';   Locale = 'uk'; Trust = 'немає аналітики';     Privacy = 'Жодної аналітики' }
        @{ Id = 'no-advertising'; Locale = 'uk'; Trust = 'реклами й трекінгу';  Privacy = 'Жодних рекламних даних' }
    )

    # Rule 6 against the code: the advertising id and the ad/analytics SDKs the claims rule out.
    ForbiddenPermissions = @('com.google.android.gms.permission.AD_ID')
    ManifestGlob         = 'app_v2/src/*/AndroidManifest.xml'
    DependencyFiles      = @('app_v2/build.gradle.kts', 'gradle/libs.versions.toml')
    ForbiddenArtifacts   = @(
        'play-services-ads'
        'play-services-ads-lite'
        'play-services-ads-identifier'
        'play-services-measurement'
        'play-services-measurement-api'
        'firebase-analytics'
        'firebase-analytics-ktx'
        'firebase-crashlytics'
        'firebase-crashlytics-ktx'
        'facebook-android-sdk'
        'facebook-core'
    )
}
