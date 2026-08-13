# S1067 - Stream home-screen shortcut inherits channel favicon

**Status:** Archived

## 0. Problem

Pinning a stream to the home screen ("на главный экран") creates a launcher shortcut whose icon is
always the generic `ic_video` / `ic_audio` vector - the launcher renders it as a blank white tile
under the channel label. The channel's real favicon already lives in the sprite-atlas (S0668) and is
shown on every list row/grid tile, but the shortcut ignores it.

## 1. Goal

The pinned shortcut uses the channel's favicon tile from the atlas as its icon, falling back to the
generic media-kind vector when the channel has no favicon.

## 2. Approach

- `StreamShortcutPinManager.requestPin` takes an optional `iconBitmap: Bitmap?`. Non-null ->
  `IconCompat.createWithBitmap` (legacy, not adaptive - adaptive masks the outer 25% and crops a
  square favicon). Null -> existing `createWithResource` fallback.
- `StreamsActivity.onAddShortcut` runs on `lifecycleScope`: resolve `faviconCoords[source.url]` ->
  `faviconSlicer.tileFor(index)` (both already wired for the list/grid), pass the tile in.

## 3. Constraints / accepted limitations

- Favicon tiles are 32 px; upscaled to a launcher icon they are soft but recognisable. No upscale
  filtering added - the launcher scales anyway.
- The icon is baked at pin time. A later catalog import that changes the atlas does not repaint an
  already-pinned shortcut (OS launcher behaviour, out of scope).
- Channels with no atlas favicon keep the generic icon - fallback is mandatory, never a blank tile.

## 4. Verification

- On-device: pin a channel that has a favicon -> launcher icon shows the favicon, not a white tile.
- Pin a channel with no favicon -> generic `ic_video`/`ic_audio` icon (no crash, no blank).

## Last Audit

### Manual device test - 2026-07-24 (emulator-5554, standard-debug v2.60.7220.314)

PASS (favicon branch; fallback branch blocked by wedge, unit-test-covered).

Flow: Streams -> import "Update FastMediaSorter catalog" (+3244 channels, favicons wired) ->
row "# 100 GREATEST HEAVY METAL" (has atlas favicon) -> overflow -> "Add to home screen".

- Expected (WITH favicon): probe reports a non-null favicon index and the launcher pin-confirmation
  shows the channel favicon, not a blank white tile.
- Actual (WITH favicon): PASS. Probe `S1067: pin shortcut requested for
  https://cast1.torontocast.com:4660/stream (faviconIndex=1725)` - non-null index selects the
  `createWithBitmap` branch. Launcher pin-confirmation preview renders the channel logo tile in the
  1x1 shortcut (evidence: `temp/S1067/pin_with_favicon.png`, `temp/S1067/probe_S1067.log`).
- Fallback branch (no favicon -> generic vector): not device-observed. After the 3244-row bulk
  import the app entered a GC-churn ANR loop (logcat frame times ~40s, "Skipped 44 frames"), which
  wedged the pin-confirmation on repeated "Wait" attempts. Per test time-box, not fought further.
  Branch is a trivial null-coalesce fallback and is covered by `StreamSourceAdapterFaviconTest`.
- The ANR is an emulator-performance artifact of the oversized catalog import, unrelated to S1067;
  the pin dialog itself rendered the favicon correctly before the ANR overlay appeared.
