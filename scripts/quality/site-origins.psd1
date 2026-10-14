# SITE-EXPERIENCE 0.1 rule 14 declaration (S4098), read by scripts/quality/assert-site-origins.ps1.
#
# Every third-party host a published page of the site contacts on its own - a script, a stylesheet,
# a font, an image, a fetch - and never a host a visitor only reaches by clicking a link. A new host
# in a published file with no record here fails the gate as UNDECLARED; a record whose host no file
# loads any more fails as STALE; a host the three privacy pages do not name fails as PRIVACY (static
# and run-time hosts alike; run-time hosts are the RuntimeOrigins array below). The
# catalog registry's adoption row lists the same hosts; the privacy pages describe each one.
@{
    Origins = @(
        @{
            Host        = 'fonts.googleapis.com'
            Purpose     = 'Google Fonts stylesheet for the site typefaces'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'every page'
            Advertising = $false
            # The docs/*.md pages render through this theme, which loads Open Sans from Google Fonts.
            Theme       = 'jekyll-theme-cayman'
        }
        @{
            Host        = 'fonts.gstatic.com'
            Purpose     = 'Google Fonts font files'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'every page'
            Advertising = $false
            Theme       = 'jekyll-theme-cayman'
        }
        @{
            Host        = 'pagead2.googlesyndication.com'
            Purpose     = 'Google AdSense advertisement script'
            Receives    = 'IP address, browser user agent, page address, Google advertising cookies'
            Groups      = 'landing pages only'
            Advertising = $true
        }
        @{
            Host        = 'api.github.com'
            Purpose     = 'list of published releases for the download buttons'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'landing and sideload pages'
            Advertising = $false
        }
        @{
            Host        = 'img.shields.io'
            Purpose     = 'version, licence and platform badges'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'README pages under docs/'
            Advertising = $false
        }
        @{
            Host        = 'gitlab.com'
            Purpose     = 'IzzyOnDroid repository badge image'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'README pages under docs/'
            Advertising = $false
        }
        @{
            Host        = 'raw.githubusercontent.com'
            Purpose     = 'GitHub Store download badge image'
            Receives    = 'IP address, browser user agent, referring page address'
            Groups      = 'README pages under docs/'
            Advertising = $false
        }
    )

    # Hosts a declared script contacts only at run time. A read of the published files cannot see
    # them, so they come from a recorded browser sweep (network requests per page) and are never
    # STALE. Each one must name its loader in Origins, inherits the loader's Advertising flag and is
    # held to the privacy pages like a static host. A browser pass that meets a new run-time host
    # adds a record here and names the host on the three privacy pages in the same edit.
    RuntimeOrigins = @(
        @{
            Host     = 'ep1.adtrafficquality.google'
            LoadedBy = 'pagead2.googlesyndication.com'
            Purpose  = 'Google ad traffic quality checks against invalid clicks and impressions'
            Receives = 'IP address, browser user agent, page address, Google advertising cookies'
            Seen     = 'S4103 browser pass 2026-10-07, index.html served from the working tree'
        }
        @{
            Host     = 'ep2.adtrafficquality.google'
            LoadedBy = 'pagead2.googlesyndication.com'
            Purpose  = 'Google ad traffic quality checks against invalid clicks and impressions'
            Receives = 'IP address, browser user agent, page address, Google advertising cookies'
            Seen     = 'S4103 browser pass 2026-10-07, index.html served from the working tree'
        }
        @{
            Host     = 'fundingchoicesmessages.google.com'
            LoadedBy = 'pagead2.googlesyndication.com'
            Purpose  = 'Google consent message for advertising cookies'
            Receives = 'IP address, browser user agent, page address, the consent choice'
            Seen     = 'S4103 browser pass 2026-10-07, published index.html'
        }
    )

    # PAGE-STYLE section 2 role "App - big" lists AdSense for the landing; no other page may load an
    # advertising host. Globs are matched against the repository-relative path with -like.
    AdvertisingPages = @('index.html', 'index-*.html')

    # The site's own origin: canonical and alternate links point here and are not third-party.
    FirstParty = @('serzhyale.github.io')
}
