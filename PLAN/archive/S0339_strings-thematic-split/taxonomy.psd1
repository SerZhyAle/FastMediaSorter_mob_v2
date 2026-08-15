@{
    # S0339 deterministic prefix -> thematic file mapping.
    # Rule (strategic §6, resolved 2026-06-03): threshold >=~20 keys per theme;
    # cross-cutting / ambiguous prefixes stay in residual strings.xml;
    # prefixes colliding with existing thematic files merge into that file.
    # Order matters: applied top-to-bottom. Each key in residual strings.xml
    # is moved by the FIRST matching prefix.
    Order = @(
        @{ File = 'strings_settings.xml';        Prefixes = @('settings', 'setting', 'pref') }
        @{ File = 'strings_input.xml';           Prefixes = @('keybinding', 'kbm', 'touch') }
        @{ File = 'strings_video_player.xml';    Prefixes = @('playback', 'player', 'video') }
        @{ File = 'strings_vr.xml';              Prefixes = @('vr') }            # merge into existing
        @{ File = 'strings_reader.xml';          Prefixes = @('pdf', 'epub', 'text') }
        @{ File = 'strings_sources.xml';         Prefixes = @('smb', 'sftp', 'cloud', 'dropbox') }
        @{ File = 'strings_setup.xml';           Prefixes = @('welcome', 'sysinfo') }
        @{ File = 'strings_file_operations.xml'; Prefixes = @('addresource', 'import', 'export', 'delete', 'file') }
        @{ File = 'strings_scheduled.xml';       Prefixes = @('scheduled') }
        @{ File = 'strings_image_viewer.xml';    Prefixes = @('image', 'gif') }
        @{ File = 'strings_ocr.xml';             Prefixes = @('ocr', 'translation') }
        @{ File = 'strings_audio.xml';           Prefixes = @('audio') }
        @{ File = 'strings_drawing.xml';         Prefixes = @('draw') }
        @{ File = 'strings_widget.xml';          Prefixes = @('widget') }
        @{ File = 'strings_calculator.xml';      Prefixes = @('calculator') }
        @{ File = 'strings_game.xml';            Prefixes = @('game') }
    )
    # Residual (NOT moved): error, dialog, msg, toast, tooltip, label, hint,
    # link (-> strings_link_auth), resource (-> strings_resource_operations),
    # activity, perm, sort, camera, wear, big, select, show, no, cb, ...
}
