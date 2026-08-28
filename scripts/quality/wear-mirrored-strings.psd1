# Declared string pairs shared by :app_v2 and :wear (S2125).
#
# Phone  = key name in app_v2/src/main/res/values*/strings.xml
# Watch  = key name in wear/src/main/res/values*/strings.xml
# Mode   = 'Mirrored'    - the two texts must read identically in every locale
#          'Independent' - each side words it on its own, and Reason says why
# Reason = required for Independent, empty for Mirrored
#
# The two modules compile separately with no shared resource artifact, so every
# label the owner sees on both sides physically exists twice. Nothing linked the
# two copies before this file: editing one left no trace on the other, and the
# divergence surfaced only on a live phone-plus-watch pair.
#
# The pairing is DECLARED, never inferred from a matching key name (S2125 ADR-1).
# Measured 2026-08-27: 20 key names occur in both modules, 6 of them already
# differ in `en` and 8 more diverge in 12 places across bn/pt/ru/uk - a gate that
# equated "same name" with "same text" would be born red on 18 non-defects.
#
# The declaration carries BOTH key names, so a pair named differently on the two
# sides is an ordinary record rather than a special case. That is the pair that
# produced this ticket: the watch background setting is a group heading on the
# phone and a row label on the watch.
#
# Enforced by scripts/quality/assert-wear-mirrored-strings.ps1, which also fails
# on a colliding key name this file classifies neither way - without that, the
# list ages in silence, which is the exact disease the ticket treats.

@{
    Pairs = @(

        # ---- Mirrored: the same control, and it must read the same on both sides ----

        @{
            Phone  = 'wear_settings_sync_button'
            Watch  = 'wear_settings_sync_button'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_last_synced'
            Watch  = 'wear_settings_last_synced'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_sync_never'
            Watch  = 'wear_settings_sync_never'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_setting_streams_section'
            Watch  = 'wear_setting_streams_section'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_background_mode_animation'
            Watch  = 'wear_background_mode_animation'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_background_mode_image'
            Watch  = 'wear_background_mode_image'
            Mode   = 'Mirrored'
            Reason = ''
        },
        # The cross-name pair. Phone key from S2000, watch key from S2093; the names
        # differ because one is a section heading and the other a row label, and
        # renaming either would touch 13 locale files for cosmetics alone.
        @{
            Phone  = 'wear_background_section_title'
            Watch  = 'wear_setting_background_mode'
            Mode   = 'Mirrored'
            Reason = ''
        },

        # ---- Independent: the name collides, the wording is deliberately not shared ----

        @{
            Phone  = 'app_name'
            Watch  = 'app_name'
            Mode   = 'Independent'
            Reason = 'Two products with two launcher labels; the watch label must fit a round screen.'
        },
        @{
            Phone  = 'connection_test_not_supported'
            Watch  = 'connection_test_not_supported'
            Mode   = 'Independent'
            Reason = 'The phone names the resource type and the watch names the protocol - different vocabulary for different screens.'
        },
        @{
            Phone  = 'no_files_found'
            Watch  = 'no_files_found'
            Mode   = 'Independent'
            Reason = 'The phone empty state is conversational; the watch has room for the bare fact only.'
        },
        @{
            Phone  = 'slideshow_interval'
            Watch  = 'slideshow_interval'
            Mode   = 'Independent'
            Reason = 'Not the same string: the phone is a settings label, the watch a live readout carrying a format argument.'
        },
        @{
            Phone  = 'slideshow_settings'
            Watch  = 'slideshow_settings'
            Mode   = 'Independent'
            Reason = 'The watch drops the noun because the label already sits inside its settings screen.'
        },
        @{
            Phone  = 'ssh_key_required'
            Watch  = 'ssh_key_required'
            Mode   = 'Independent'
            Reason = 'The watch drops the copula to fit the width.'
        },
        @{
            Phone  = 'cancel'
            Watch  = 'cancel'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; the watch takes the shorter form where the round screen demands it.'
        },
        @{
            Phone  = 'error'
            Watch  = 'error'
            Mode   = 'Independent'
            Reason = 'Generic noun translated per module, on no shared control.'
        },
        @{
            Phone  = 'loading'
            Watch  = 'loading'
            Mode   = 'Independent'
            Reason = 'Generic status translated per module; pt already differs as European against Brazilian phrasing.'
        },
        @{
            Phone  = 'next'
            Watch  = 'next'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; the watch labels a player control, the phone a wizard step.'
        },
        @{
            Phone  = 'play'
            Watch  = 'play'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; bn already differs.'
        },
        @{
            Phone  = 'previous'
            Watch  = 'previous'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; the watch labels a player control, the phone a back action.'
        },
        @{
            Phone  = 'retry'
            Watch  = 'retry'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; the watch takes the shorter noun form in ru.'
        },
        @{
            Phone  = 'save'
            Watch  = 'save'
            Mode   = 'Independent'
            Reason = 'Generic verb translated per module; pt already differs as European against Brazilian phrasing.'
        }
    )
}
