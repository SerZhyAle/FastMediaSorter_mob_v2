---
name: index-emulator-testing
description: Second-level pointer list for emulator, AVD, Maestro and device testing memories. Open when authoring or debugging emulator runs, Maestro flows, screenshots or device test setups.
metadata:
  type: reference
---

# Emulator & Device Testing - pointers

Split out of `MEMORY.md` (S1731, 2026-08-17): memories specific to emulator setup, AVD sweeps, Maestro testing and device capture quirks. Open this file when running emulator sweeps, testing UI/gesture behaviors on AVD, or writing Maestro flows.

- [Capture](reference_emulator_capture_family_testing.md) - reshape, never rotate + [MediaProjection](reference_emulator_mediaprojection_capture.md)
- [AVD quirks](feedback_avd_device_sweep_gotchas.md) + [media](feedback_avd_mediastore_not_indexed.md) + [taps](feedback_bottomsheet_menu_untappable_emulator.md)
- [Stylus overlay eats typed text](feedback_stylus_overlay_eats_typed_text.md) - tree lies
- [Typing and adb path traps](feedback_device_text_entry_and_adb_path_traps.md) - Gboard rewrites `input text`; raw adb mangles `/sdcard`
- [Maestro needs ru app locale](feedback_maestro_suite_needs_ru_app_locale.md) - log mojibake is display-only + [flow traps](feedback_maestro_flow_authoring_traps.md) - ASCII-only inputText
- [Acceptance ceiling](feedback_emulator_acceptance_ceiling.md) · [False negatives](feedback_avd_evidence_traps_width_and_logs.md)
- [Settings shots are black](feedback_settings_screenshots_black_flag_secure.md) - FLAG_SECURE; verify on an AVD, not the owner's phone
- [Onboarding](feedback_onboarding_device_test_gotchas.md) · [Widget-only](reference_trigger_widget_only_features_on_emulator.md) · [Too fast for transfer UI](feedback_emulator_too_fast_for_transfer_ui.md)
- [Reset settings, keep onboarding](feedback_reset_appsettings_without_onboarding.md)
- [Launcher desktop](feedback_launcher_desktop_device_test_setup.md) - enabledComponents; am start needs no HOME role · [Theme switch](feedback_color_theme_device_switch.md)
- [Animator scale first](feedback_check_animator_scale_before_diagnosing.md) - AVDs run scale=0 · [Dialogs under wm](feedback_dialogs_invisible_under_wm_override.md)
- [Quest panel not introspectable](reference_quest_panel_not_introspectable.md)
- [Operator agents stop at ~10 calls](feedback_device_operator_tool_call_budget.md) - one ticket per brief; do preflight yourself
- [OCR needs 3 GB RAM](project_ocr_hard_gated_at_3gb_ram.md) - the switch is disabled below it, so the default Pixel_9 AVD (2048 MB) can never reach Tesseract. The remedy is local, not the owner's phone: Pixel_6 is already defined at 4096 MB and Pixel_10_Pro_Fold at 8192 MB.
- [Desktop never idles](project_launcher_desktop_never_idles.md) - uidump is unavailable on a populated launcher desktop; the live clock, not the wallpaper, and no setting fixes it - use screenshots and coordinates
