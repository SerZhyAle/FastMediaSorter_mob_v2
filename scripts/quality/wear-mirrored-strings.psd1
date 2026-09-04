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

        # S2484: one action, one glyph, one wording. The whole point of the unified sync control is
        # that the owner recognizes the same thing on the phone and on the watch, so these two are
        # the case Mirrored exists for - a divergence here would undo the ticket.
        @{
            Phone  = 'wear_sync_now'
            Watch  = 'wear_sync_now'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_sync_now_description'
            Watch  = 'wear_sync_now_description'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_sync_button'
            Watch  = 'wear_settings_sync_button'
            Mode   = 'Independent'
            Reason = 'Phone and watch controls use distinct localized phrasing.'
        },
        @{
            Phone  = 'wear_settings_last_synced'
            Watch  = 'wear_settings_last_synced'
            Mode   = 'Independent'
            Reason = 'Phone and watch status strings use distinct localized phrasing.'
        },
        @{
            Phone  = 'wear_settings_sync_never'
            Watch  = 'wear_settings_sync_never'
            Mode   = 'Independent'
            Reason = 'Phone and watch status strings use distinct localized phrasing.'
        },
        @{
            Phone  = 'wear_setting_streams_section'
            Watch  = 'wear_setting_streams_section'
            Mode   = 'Independent'
            Reason = 'Phone and watch section labels use distinct localized phrasing.'
        },
        @{
            Phone  = 'wear_background_mode_animation'
            Watch  = 'wear_background_mode_animation'
            Mode   = 'Independent'
            Reason = 'Phone and watch background option labels use distinct localized phrasing.'
        },
        @{
            Phone  = 'wear_background_mode_image'
            Watch  = 'wear_background_mode_image'
            Mode   = 'Independent'
            Reason = 'Phone and watch background option labels use distinct localized phrasing.'
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
        },

        # ---- Mirrored: the stream rubric vocabulary, copied into :wear by S2146 ----
        # One closed set published by scripts/streams/collect-stream-candidates.ps1 and shown by
        # both modules. The watch copies are lifted verbatim from the phone, so a reworded rubric
        # that lands on one side only is a defect this gate is meant to catch.
        @{
            Phone  = 'streams_rubric_adult'
            Watch  = 'streams_rubric_adult'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_business'
            Watch  = 'streams_rubric_business'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_chillout'
            Watch  = 'streams_rubric_chillout'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_classical'
            Watch  = 'streams_rubric_classical'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_comedy'
            Watch  = 'streams_rubric_comedy'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_country_folk'
            Watch  = 'streams_rubric_country_folk'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_documentary'
            Watch  = 'streams_rubric_documentary'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_education'
            Watch  = 'streams_rubric_education'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_electronic'
            Watch  = 'streams_rubric_electronic'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_general'
            Watch  = 'streams_rubric_general'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_hip_hop'
            Watch  = 'streams_rubric_hip_hop'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_jazz_blues'
            Watch  = 'streams_rubric_jazz_blues'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_kids'
            Watch  = 'streams_rubric_kids'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_latin'
            Watch  = 'streams_rubric_latin'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_lifestyle'
            Watch  = 'streams_rubric_lifestyle'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_local_radio'
            Watch  = 'streams_rubric_local_radio'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_metal'
            Watch  = 'streams_rubric_metal'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_movies_series'
            Watch  = 'streams_rubric_movies_series'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_news'
            Watch  = 'streams_rubric_news'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_oldies'
            Watch  = 'streams_rubric_oldies'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_pop'
            Watch  = 'streams_rubric_pop'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_reggae'
            Watch  = 'streams_rubric_reggae'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_religious'
            Watch  = 'streams_rubric_religious'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_rnb_soul'
            Watch  = 'streams_rubric_rnb_soul'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_rock'
            Watch  = 'streams_rubric_rock'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_shopping'
            Watch  = 'streams_rubric_shopping'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_sports'
            Watch  = 'streams_rubric_sports'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_talk'
            Watch  = 'streams_rubric_talk'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_test'
            Watch  = 'streams_rubric_test'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_traffic_cams'
            Watch  = 'streams_rubric_traffic_cams'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_webcam'
            Watch  = 'streams_rubric_webcam'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'streams_rubric_world'
            Watch  = 'streams_rubric_world'
            Mode   = 'Mirrored'
            Reason = ''
        },

        # ---- Mirrored: the settings group titles the companion window shows (S2169) ----
        # The companion window mirrors the watch settings menu, so its subgroup headers must read
        # exactly what the watch menu shows. The phone values are lifted verbatim from the watch in
        # every locale, which is what a Mirrored pair demands.
        @{
            Phone  = 'wear_settings_group_media_types'
            Watch  = 'media_types'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_group_slideshow'
            Watch  = 'slideshow_settings'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_group_screen'
            Watch  = 'screen_settings_title'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_group_other'
            Watch  = 'settings_group_other'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_disable_animations'
            Watch  = 'pref_disable_animations'
            Mode   = 'Mirrored'
            Reason = ''
        },
        @{
            Phone  = 'wear_settings_background_mode'
            Watch  = 'wear_setting_background_mode'
            Mode   = 'Mirrored'
            Reason = ''
        }
    )
}
