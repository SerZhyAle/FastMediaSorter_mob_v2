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
    Thresholds = @{
        ColdStart      = @{ Metric = 'am-start-total-ms';        Limit = 5000 }
        ListScroll     = @{ Metric = 'janky-frames-pct';         Limit = 20; MaxFrameMs = 700 }
        PlayerOpen     = @{ Metric = 'ms-to-first-frame';        Limit = 4000 }
        NetworkListing = @{ Metric = 'ms-to-listing-complete';   Limit = 15000 }
    }
}
