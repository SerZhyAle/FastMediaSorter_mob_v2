---
name: store-screenshot-capture-traps
description: Emulator state drifts mid-capture (forced 1024x600, demo status bar, layout-bounds overlay) and the seeded test media is not store-safe - both silently ruin Play screenshots
metadata:
  type: project
---

Producing Play store screenshots on the dev emulator (S1256, 2026-07-28) hits two independent classes of trap that a screenshot does not announce.

**A concurrent session can own the emulator's geometry - check before resetting it.**
- `settings global display_size_forced = 1024,600` + `secure display_density_forced = 160` were set on emulator-5556. At 1024dp wide the app renders its **wide/tablet** layout while `screencap` still returns a 1080x2400 PNG - so the image looks phone-shaped but the layout is wrong, and `uiautomator dump` bounds come back in the 1024x600 space, making every tap coordinate wrong.
- Deleting both settings fixed it, and the override **came back within ~5 minutes**. Cause was not drift: a sibling session was reproducing the car head-unit config for S1258 (created and updated the same day, see [[owner-runs-app-on-car-head-unit]]), and the red/blue/pink view rectangles were its layout-bounds measuring overlay. No lock file is taken for emulator state, so nothing warned either side, and my repeated `wm size 1080x2400` was overwriting their reproduction geometry.
- SystemUI demo mode (clean 12:00 clock, full battery/signal) also drops out and needs re-arming.

**Why:** two sessions sharing one AVD silently corrupt each other's work - each produces plausible-looking output that is quietly wrong. `temp/BUILD.LOCK` and `temp/CODE.LOCK` do not cover device state.

**How to apply:** before changing emulator geometry, check for an active sibling workstream (`select.ps1 -Status "In Progress"` plus recently-updated Draft specs, and whether `display_size_forced` matches a known reproduction config) rather than assuming a stale leftover. If one owns the device, use a second AVD instead of resetting theirs. A `wm` override also stops dialogs rendering on this AVD - see [[dialogs-invisible-under-wm-override]]; that is what made a store-capture run look like the player was refusing Back (S1264, closed as not-a-bug).

**Navigation replay traps (S1256).** Each produced a valid-looking screenshot of the wrong screen, and none returned a non-zero exit code:
- The app resumes its last screen on launch, so force-stop plus launch can land in the player, not the resource list. Stop playback first (`input keyevent 86`); with a live media session the main screen bounces straight back.
- `am start --activity-clear-task --activity-new-task` on a freshly force-stopped package leaves the launcher in front. Use the plain launch verb.
- Browse filters (document name filter, stream media-type filter) do not survive a locale switch, so a slot that depends on one must re-apply it rather than trust prior state.
- Verify every device side effect a later step depends on: `cmd locale set-app-locales` is silent on failure, so read it back with `get-app-locales`.

**How to apply:** re-assert geometry and demo mode immediately before *every* capture, never once per session - `temp/S1256/prep-shot.ps1` does exactly this (`wm size 1080x2400`, `wm density 420`, demo broadcasts). Read back the composed PNG before trusting a slot. Use `temp/S1256/ui-probe.ps1` for tap targets instead of reading pixels off a screenshot, and note it writes its dump to `/data/local/tmp/` - an earlier version wrote `/sdcard/ui_s1256.xml`, which then showed up inside the app's own document list and got opened by a stray tap.

**The seeded test media is not usable as public-facing content.**
- `setup_test_media.ps1` content plus what accumulated on the device includes TikTok/Instagram reposts with visible watermarks and creator handles, a political chain-post video with a national flag, an OCR sample screenshot carrying real news text about a death, and stream-catalog tiles showing war footage (a drone-strike news frame) and a mildly suggestive station logo.
- Two PDFs in the document corpus are real signed consent letters naming private individuals with home addresses; another is erotic fiction. The only well-tagged audio track carries the Adele *Skyfall* cover - James Bond artwork with the 007 mark and an actor likeness.
- What worked for S1256: the owner's own photography (`c:\Common\test_media\20260101_*.jpg`, kayak/beach and Görlitz square), `3dvr/360_mono/NASA_Webb_*.mp4` (NASA public domain), the Alice in Wonderland PDF the owner supplied, a voice memo (renders the app's own visualizer, no third-party art), and the stream catalog filtered to Audio.

**Why:** a store listing is public and permanent; third-party watermarks, political imagery and personal news text are all independently disqualifying, and none of it is obvious until each frame is inspected.

**How to apply:** for any public-facing capture, pick the file by name from the owner's own photos rather than tapping whatever is first in the list, and filter the stream catalog (media type Audio) before shooting it. Video has no clean local source at all - ask the owner for one rather than improvising. Related: [[setup-test-media]], [[avd-mediastore-not-indexed]], [[play-device-reach-screen-portrait]].
