---
name: index-streams-vr
description: Second-level pointer list for streams, VR/XR and player-family memories. Open when the task touches stream playback, the stream catalog, VR/XR, the camera capture path or the player family.
metadata:
  type: reference
---

# Streams / VR / players - pointers

Split out of `MEMORY.md` (S1542): these are the memories a launcher, settings or build task never
needs, and the top-level index is billed on every turn of every session. Open this file when the task
touches streams, VR/XR, camera capture or the player family; the entries are the same one-liners the
top-level index used to carry.

- [present() dead](project_link_download_present_suppressed.md) · [Ship every live channel](feedback_stream_catalog_all_live_channels.md) + [publish](reference_stream_catalog_publish.md)
- [Favicon atlas](project_stream_favicon_atlas_delivery.md) + [publish](project_stream_catalog_atlas_publish.md) - no atlas.png wipes favicons
- [StreamsPlayer consumes the same zip](project_streams_player_catalog_consumer.md) - there a missing atlas shows WRONG icons, silently
- [Atlas publish needs tile packs](project_atlas_publish_needs_tile_packs.md) - sheets go to `-v3`; the app reads stable names only `-WithTilePacks`
- [Artwork = tile packs](project_stream_artwork_tile_packs.md) · [Streams test gate](project_streams_device_test_gate.md) · [radio vs video](project_stream_radio_vs_video_player_split.md)
- [VR inclusion](project_vr_inclusion_hierarchy.md) - `src/vr` ships in TWO flavors · [supportsVrPlayer noLegal](project_supportsvrplayer_nolegal_only.md)
- ["VR" = device or flavor?](project_xr_device_guard_lives_in_main.md) - ask first
- [VR re-entry](project_vr_immersive_reentry_hotspot.md) + [logcat trap](reference_vr_immersive_logcat_capture_trap.md) · [HUD pitfalls](project_vr_hud_quirks.md)
- [Quest panel opaque](reference_quest_panel_not_introspectable.md) · [2 texture channels](project_vr_native_two_texture_channels.md)
- [Camera detekt ceilings](project_camera_session_manager_function_ceiling.md) · [Capture permission-free](project_camera_capture_permission_constraint.md)
- [Headless + noHistory](project_headless_camera_capture_trampoline.md) · [progressBar owner](project_player_progressbar_single_owner.md) · [Shared-state audit](reference_shared_state_audit_tool.md)
