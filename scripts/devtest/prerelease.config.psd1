# S0484 /spec-prerelease run configuration.
#
# Populated by phases:
#   Resources  - Phase 02 (predefined LOCAL / network-SMB / SFTP picks + reachability class)
#   Settings   - Phase 02 (significant settings + target values + apply channel)
#   Thresholds - Phase 03 (per-checkpoint PASS limits)
#
# Endpoints and credentials are referenced by predefined-resource NAME only
# (see app_v2/src/main/res/xml/sza_resources.xml); never duplicate secrets here.
#
# Reachability class: 'probe-and-list' = reachable endpoint, probe then verify listing;
#                     'register-only'  = LAN-unreachable from emulator NAT, register row only.
# Apply channel:      'adb' = scriptable (theme SharedPrefs / cmd locale);
#                     'ui'  = DataStore-backed, applied via mobile-mcp in the skill scenario.
@{
    Resources = @{
        Local   = @{ Name = 'Downloads';  Type = 'LOCAL'; Reachability = 'probe-and-list' }
        Network = @{ Name = 'test_media'; Type = 'SMB';   Reachability = 'register-only' }
        Sftp    = @{ Name = 'SFTP';       Type = 'SFTP';  Reachability = 'probe-and-list' }
    }

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
    }
}
