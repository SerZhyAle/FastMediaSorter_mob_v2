# S0484 /spec-prerelease run configuration.
#
# Populated by phases:
#   Settings   - Phase 02 (significant settings + target values + apply channel)
#   Thresholds - Phase 03 (per-checkpoint PASS limits)
#
# The clean emulator covers browsing through its seeded standard Downloads resource. Owner-only
# network resources and their credentials are intentionally not part of this checkout.
#
# Apply channel:      'adb' = scriptable (theme SharedPrefs / cmd locale);
#                     'ui'  = DataStore-backed, applied via mobile-mcp in the skill scenario.
@{
    Settings = @{
        Theme        = @{ Key = 'color_theme';       Value = 'DARK';     Channel = 'ui' }
        Language     = @{ Locale = 'ru';                                 Channel = 'adb' }
        SortMode     = @{ Key = 'default_sort_mode';  Value = 'DATE_DESC'; Channel = 'ui' }
        GridMode     = @{ Key = 'default_grid_mode';  Value = $true;      Channel = 'ui' }
        UseTrash     = @{ Key = 'use_trash';          Value = $true;      Channel = 'ui' }
        AcceptShared = @{ Key = 'accept_shared_files'; Value = $true;     Channel = 'ui' }
    }

    # Per-checkpoint PASS limits (research/01, emulator-aware starter set; refine after baseline).
    # ListScroll gates on physical devices only: on an emulator its gfxinfo janky% is structurally
    # inflated by software/host-GPU rendering, so the record is marked advisory (see
    # prerelease-measure.ps1) and the verdict aggregator reports but does not gate on it.
    # S1502 streams checkpoints gate the streams screen at full catalog size. Seed the device with
    # scripts/devtest/streams-perf-seed.ps1 first. The two scroll limits mirror ListScroll and carry
    # the same emulator advisory. StreamsPeakMemory is a coarse absolute backstop only: strategic
    # §11.6 asks whether the peak GREW, which is the baseline/after comparison, not this number.
    # Every S1502 limit here is a pipeline-chosen starter value awaiting the owner's confirmation
    # (strategic §3.3) - refine them from the first baseline rather than treating them as agreed.
    Thresholds = @{
        ColdStart         = @{ Metric = 'am-start-total-ms';      Limit = 5000 }
        ListScroll        = @{ Metric = 'janky-frames-pct';       Limit = 20 }
        PlayerOpen        = @{ Metric = 'ms-to-first-frame';      Limit = 4000 }
        NetworkListing    = @{ Metric = 'ms-to-listing-complete'; Limit = 15000 }
        StreamsOpen       = @{ Metric = 'ms-to-streams-screen';   Limit = 5000 }
        StreamsSearch     = @{ Metric = 'janky-frames-pct';       Limit = 20 }
        StreamsListScroll = @{ Metric = 'janky-frames-pct';       Limit = 20 }
        StreamsGridScroll = @{ Metric = 'janky-frames-pct';       Limit = 20 }
        StreamsPeakMemory = @{ Metric = 'peak-rss-kb';            Limit = 524288 }
        PlayAnonMemory    = @{ Metric = 'anon-rss-plus-swap-kb';  Limit = $null }
        PlayBitmapMemory  = @{ Metric = 'native-heap-kb';         Limit = $null }
    }

    # S2100: Google Play technical quality thresholds, enforced from February 2027.
    # Read from support.google.com/googleplay/android-developer/answer/17492799 on 2026-08-27.
    #
    # These two metrics have no scalar limit, which is why their Thresholds rows above carry $null:
    # Play's limit is a function of the device's physical RAM AND the process state, so
    # prerelease-measure.ps1 resolves it from this table at measurement time instead. Google states
    # the thresholds will change over time - when they do, this block is the only edit required.
    #
    # AnonMemoryKb: apps table, 90th percentile of anonymous RSS + swap. Keyed by the declared RAM
    # bucket (the device's MemTotal is rounded to the nearest one), then by process state. The games
    # table is deliberately absent - it does not apply to this app.
    #
    # BitmapMemoryKb: Play sets no foreground limit at all; bitmaps in foreground are acceptable by
    # its own statement. A foreground row here would gate on a rule that does not exist, so there
    # is none, and an absent row means "not judged" rather than "unlimited".
    PlayMemory = @{
        AnonMemoryKb = @{
            '4'  = @{ foreground = 2097152; perceptible = 1048576; background = 1048576; cached = 1048576 }
            '6'  = @{ foreground = 2359296; perceptible = 1310720; background = 1310720; cached = 1310720 }
            '8'  = @{ foreground = 2359296; perceptible = 1572864; background = 1572864; cached = 1572864 }
            '12' = @{ foreground = 3407872; perceptible = 1835008; background = 1835008; cached = 1835008 }
            '16' = @{ foreground = 4456448; perceptible = 2097152; background = 2097152; cached = 2097152 }
        }
        BitmapMemoryKb = @{
            perceptible = 204800
            background  = 204800
            cached      = 409600
        }
    }

    # S2917: bands for the Play vitals watch (scripts/release/watch-play-vitals.ps1).
    # Read on 2026-09-11 from Play Console help answer 9844486 ("Android vitals" bad behaviour).
    #
    # Red is Google's published threshold as it stands, never a number of our own (S2917 ADR-2):
    # user-perceived crash rate 1.09 % and ANR rate 0.47 % over 28 days, 8 % on a single device model.
    # RateUnit is an ASSUMPTION until the first live response settles it (S2917 research 6): the API
    # calls these values "percentage" yet returns a bare decimal. Under 'fraction' a value above 1
    # cannot be a fraction, so the verdict refuses instead of reading 1.5 as 150 %.
    # MemoryBandsEnabled stays $false for the same reason - Google names no unit for the memory
    # percentiles and only FOREGROUND as an appState value - so the memory limits in PlayMemory above
    # are not compared until research 6 is resolved; the verdict reports "unit unconfirmed" instead.
    # MemoryUnit ('bytes', 'kB' or 'MB') is the unit of the API's memory percentiles; it has to be set
    # before MemoryBandsEnabled may be switched on, and the verdict refuses the combination otherwise.
    # The two MinDistinctUsers floors are a noise guard, not a quality threshold: distinctUsers is
    # rounded by Google, and below the floor the verdict is "insufficient data".
    PlayVitals = @{
        RateUnit                  = 'fraction'
        Red = @{
            UserPerceivedCrashRate = 0.0109
            UserPerceivedAnrRate   = 0.0047
            PerDeviceModel         = 0.08
        }
        YellowShareOfRed          = 0.5
        SpikeFactor               = 3.0
        MinDistinctUsers          = 100
        MinDistinctUsersPerDevice = 50
        MemoryBandsEnabled        = $false
        MemoryUnit                = $null
        AppStateMap = @{
            FOREGROUND = 'foreground'
        }
        TopIssuesInTicket         = 5
    }
}
