<#
    S1392 - manifest of documentation tables checked against docs/flavors/flavor-matrix.json
    by scripts/quality/assert-flavor-matrix-docs.ps1.

    Adding a table here needs no change to the comparison logic (strategic S1392 §5.3).

    Scope rule (strategic ADR-2): only GLYPH tables belong here - a table whose cells carry a
    true/false/absent marker per flavor. Prose tables (docs/QUICK_START*.md flavor chooser,
    dev/TECH_REQUIREMENTS.md minimum requirements) state capabilities in free text, so a cell
    value cannot be compared without fuzzy matching; they are corrected by hand and are NOT
    listed here. Do not add one - a fuzzy rule gets disabled, and then nothing is protected.

    Fields per table:
      Id        stable identifier used in diagnostics
      Path      repo-relative path
      Anchor    literal heading line that precedes the table
      Axis      'FlagRows'   - rows are flags, columns are flavors
                'FlavorRows' - rows are flavors, columns are flags
      Glyphs    marker vocabulary of THIS document, per state
      RowMap    row label (normalized) -> flag name, or flavor key, per Axis
      ColMap    column label (normalized) -> flavor key(s), or flag name, per Axis

    A column mapping to several flavors (the shared "XR / noLegal" column) requires every mapped
    flavor to agree with the cell; a disagreement is reported as an ambiguous column, telling the
    author to split it rather than silently trusting one side.

    Labels are normalized before lookup: markdown emphasis, backticks and surrounding whitespace
    stripped, inner whitespace collapsed, lowercased.
#>
@{
    Tables = @(
        @{
            Id     = 'dev-ops-core'
            Path   = 'docs/DEV_OPS.md'
            Anchor = '### Core feature matrix'
            Axis   = 'FlavorRows'
            Glyphs = @{ True = @('[+]'); False = @('[-]'); Absent = @('-', 'n/a') }
            RowMap = @{
                'standard' = 'standard'
                'lite'     = 'lite'
                'photos'   = 'photos'
                'legacy'   = 'legacy'
                'vr'       = 'vr'
                'nolegal'  = 'noLegal'
            }
            ColMap = @{
                'video'   = 'SUPPORT_VIDEO'
                'audio'   = 'SUPPORT_AUDIO'
                'images'  = 'SUPPORT_IMAGES'
                'cloud'   = 'SUPPORT_CLOUD'
                'docs'    = 'SUPPORT_DOCUMENTS'
                'anim'    = 'ENABLE_ANIMATIONS'
                'vr'      = 'SUPPORT_VR_PLAYER'
                'streams' = 'SUPPORT_STREAMS'
                'network' = 'SUPPORT_LOCAL_NETWORK'
            }
        }
        @{
            Id     = 'dev-ops-extended'
            Path   = 'docs/DEV_OPS.md'
            Anchor = '### Extended per-flavor flags'
            Axis   = 'FlagRows'
            Glyphs = @{ True = @('[+]'); False = @('[-]'); Absent = @('-', 'n/a') }
            RowMap = @{
                'support_mic_recording'            = 'SUPPORT_MIC_RECORDING'
                'enable_epub'                      = 'ENABLE_EPUB'
                'enable_translation'               = 'ENABLE_TRANSLATION'
                'enable_persistent_audio_playback' = 'ENABLE_PERSISTENT_AUDIO_PLAYBACK'
                'supports_default_player'          = 'SUPPORTS_DEFAULT_PLAYER'
                'support_wear_companion'           = 'SUPPORT_WEAR_COMPANION'
                'support_cast'                     = 'SUPPORT_CAST'
                'support_vr_player'                = 'SUPPORT_VR_PLAYER'
                'vr_ui_composition_layer_enabled'  = 'VR_UI_COMPOSITION_LAYER_ENABLED'
                'is_no_legal_flavor'               = 'IS_NO_LEGAL_FLAVOR'
            }
            ColMap = @{
                'std'    = 'standard'
                'lite'   = 'lite'
                'photos' = 'photos'
                'legacy' = 'legacy'
                'vr'     = 'vr'
                'nol'    = 'noLegal'
            }
        }
        @{
            Id     = 'flavor-matrix-render'
            Path   = 'docs/FLAVOR_MATRIX.md'
            Anchor = '## Matrix'
            Axis   = 'FlagRows'
            Glyphs = @{ True = @('[+]', '[+]*'); False = @('[-]', '[-]*'); Absent = @('n/a') }
            SkipRows = @('minsdk')
            RowMap = @{
                'is_no_legal_flavor'               = 'IS_NO_LEGAL_FLAVOR'
                'support_launcher'                 = 'SUPPORT_LAUNCHER'
                'support_video'                    = 'SUPPORT_VIDEO'
                'support_audio'                    = 'SUPPORT_AUDIO'
                'support_streams'                  = 'SUPPORT_STREAMS'
                'support_mic_recording'            = 'SUPPORT_MIC_RECORDING'
                'support_images'                   = 'SUPPORT_IMAGES'
                'support_cloud'                    = 'SUPPORT_CLOUD'
                'support_local_network'            = 'SUPPORT_LOCAL_NETWORK'
                'support_documents'                = 'SUPPORT_DOCUMENTS'
                'enable_animations'                = 'ENABLE_ANIMATIONS'
                'enable_epub'                      = 'ENABLE_EPUB'
                'enable_translation'               = 'ENABLE_TRANSLATION'
                'enable_persistent_audio_playback' = 'ENABLE_PERSISTENT_AUDIO_PLAYBACK'
                'supports_default_player'          = 'SUPPORTS_DEFAULT_PLAYER'
                'support_vr_player'                = 'SUPPORT_VR_PLAYER'
                'support_wear_companion'           = 'SUPPORT_WEAR_COMPANION'
                'support_cast'                     = 'SUPPORT_CAST'
                'vr_ui_composition_layer_enabled'  = 'VR_UI_COMPOSITION_LAYER_ENABLED'
            }
            ColMap = @{
                'standard' = 'standard'
                'nolegal'  = 'noLegal'
                'lite'     = 'lite'
                'photos'   = 'photos'
                'legacy'   = 'legacy'
                'vr'       = 'vr'
            }
        }
        @{
            Id     = 'howto-availability-en'
            Path   = 'docs/HOW_TO.md'
            Anchor = '## Note: Feature Availability by Flavor'
            Axis   = 'FlagRows'
            Glyphs = @{ True = @([char]0x2713); False = @([char]0x2717); Absent = @() }
            RowMap = @{
                'network folders (smb, sftp, ftp)'                  = 'SUPPORT_LOCAL_NETWORK'
                'cloud storage (google drive, onedrive, dropbox)'   = 'SUPPORT_CLOUD'
                'audio playback & lyrics'                          = 'SUPPORT_AUDIO'
                'background audio playback'                        = 'ENABLE_PERSISTENT_AUDIO_PLAYBACK'
                'internet streams (radio, hls/dash, rtsp)'          = 'SUPPORT_STREAMS'
                'document viewer (pdf, text)'                       = 'SUPPORT_DOCUMENTS'
                'epub reader'                                       = 'ENABLE_EPUB'
                'translation & ocr'                                 = 'ENABLE_TRANSLATION'
                'image editing'                                     = 'SUPPORT_IMAGES'
            }
            ColMap = @{
                'standard'     = 'standard'
                'lite'         = 'lite'
                'photos'       = 'photos'
                'legacy'       = 'legacy'
                'xr / nolegal' = @('vr', 'noLegal')
            }
        }
        @{
            Id     = 'howto-availability-ru'
            Path   = 'docs/HOW_TO_RU.md'
            Anchor = '## Примечание: Доступность функций по версиям'
            Axis   = 'FlagRows'
            Glyphs = @{ True = @([char]0x2713); False = @([char]0x2717); Absent = @() }
            RowMap = @{
                'сетевые папки (smb, sftp, ftp)'                            = 'SUPPORT_LOCAL_NETWORK'
                'облачные хранилища (google drive, onedrive, dropbox)'      = 'SUPPORT_CLOUD'
                'воспроизведение аудио и текст песен'                       = 'SUPPORT_AUDIO'
                'фоновое воспроизведение аудио'                             = 'ENABLE_PERSISTENT_AUDIO_PLAYBACK'
                'трансляции (интернет-радио, rtsp, hls/dash)'               = 'SUPPORT_STREAMS'
                'просмотр документов (pdf, текст)'                          = 'SUPPORT_DOCUMENTS'
                'читалка epub'                                              = 'ENABLE_EPUB'
                'перевод и ocr'                                             = 'ENABLE_TRANSLATION'
                'редактирование изображений'                                = 'SUPPORT_IMAGES'
            }
            ColMap = @{
                'standard'     = 'standard'
                'lite'         = 'lite'
                'photos'       = 'photos'
                'legacy'       = 'legacy'
                'xr / nolegal' = @('vr', 'noLegal')
            }
        }
        @{
            Id     = 'howto-availability-uk'
            Path   = 'docs/HOW_TO_UK.md'
            Anchor = '## Примітка: Доступність функцій за версіями'
            Axis   = 'FlagRows'
            Glyphs = @{ True = @([char]0x2713); False = @([char]0x2717); Absent = @() }
            RowMap = @{
                'мережеві папки (smb, sftp, ftp)'                        = 'SUPPORT_LOCAL_NETWORK'
                'хмарні сховища (google drive, onedrive, dropbox)'       = 'SUPPORT_CLOUD'
                'відтворення аудіо та текст пісень'                      = 'SUPPORT_AUDIO'
                'фонове відтворення аудіо'                               = 'ENABLE_PERSISTENT_AUDIO_PLAYBACK'
                'трансляції (інтернет-радіо, hls/dash, rtsp)'            = 'SUPPORT_STREAMS'
                'перегляд документів (pdf, текст)'                       = 'SUPPORT_DOCUMENTS'
                'читалка epub'                                           = 'ENABLE_EPUB'
                'переклад та ocr'                                        = 'ENABLE_TRANSLATION'
                'редагування зображень'                                  = 'SUPPORT_IMAGES'
            }
            ColMap = @{
                'standard'     = 'standard'
                'lite'         = 'lite'
                'photos'       = 'photos'
                'legacy'       = 'legacy'
                'xr / nolegal' = @('vr', 'noLegal')
            }
        }
    )
}
