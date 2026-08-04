---
layout: default
title: "Internet Radio & Streams - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio.html
---
# 📻 Internet Radio & Streams

> **Level:** Beginner - **Time:** ~10 minutes - **Flavor:** Standard, Legacy, VR, noLegal (Streams are absent in Lite and Photos)

[Русский](scenario-internet-radio-ru.md) | [Українська](scenario-internet-radio-uk.md)

FastMediaSorter includes a dedicated Streams screen for internet audio and video sources. Add any internet radio URL, import an .m3u playlist, or browse a curated station catalog - no separate radio app needed. Works great on Android car head units, audio players, phones, and tablets.

> **Replaces:** TuneIn, Shoutcast app, Online Radio, RadioDroid, VLC network streams, IPTV players.

---

## What You Will Need

- Android device with a network connection (mobile data or Wi-Fi)
- FastMediaSorter Standard, Legacy, VR, or noLegal (the Streams screen is absent in Lite and Photos)
- A stream URL, .m3u playlist file or URL, or the built-in curated catalog

---

## Step 1 - Open the Streams Screen

Three ways to get there:
- Main screen dropdown menu -> **Streams**
- **Settings -> Media -> Streams** -> tap the Streams shortcut button
- Welcome onboarding -> Streams row (first launch only)

> **Don't see Streams in the menu?** Go to Settings -> Media -> Streams and make sure "Enable Streams" is turned ON. It defaults to ON on most devices.

---

## Step 2 - Add a Station or Stream

**Option A - Add a single URL manually:**
1. Tap **Add (+)** in the Streams screen toolbar
2. Paste the stream URL (http/https radio, .m3u8 HLS, rtsp://..)
3. Give it a name and tap **Save**

**Option B - Import an .m3u playlist:**
1. Tap **Import** -> **From URL**
2. Paste the .m3u playlist URL and confirm
3. All stations from the playlist are added to your list

**Option C - Browse the curated catalog:**
1. Tap **Import catalog** (or download it from the Extensions screen)
2. Browse or search by name, topic, or language
3. Tap stations to add them to your list

---

## Step 3 - Play a Station

- **Audio stream (radio):** tap the row - playback starts inline. A sticky mini-control appears at the bottom showing the station name and ICY now-playing track info. The list stays fully interactive.
- **Video or RTSP stream:** tap the row - opens in the fullscreen player. Press Back to return to the list; scroll position and last-selected station are preserved.

---

## Step 4 - Keep Radio Playing in the Background

To keep audio playing when you switch apps or lock the screen:

1. Go to **Settings -> Media -> Player**
2. Find the **Background audio playback** group
3. Enable **Background audio playback**

> **Leaving the Streams screen while a station is playing:** the app offers the same Stop / Keep-playing choice as the main player. If background playback is OFF, the stream stops when you minimize the screen.

---

## Step 5 - Filter and Organize

- **Pin favorites to the top:** long-press a station row -> Pin. Pinned stations appear above the rest regardless of sort order.
- **Filter by category or language:** tap the Filter button (a dot appears when a filter is active). Language picker shows flags. Use AND/OR toggle to match all or any selected filter.
- **Sort:** tap the sort button to order by name, topic, language, or recently played.
- **Search:** type in the search bar to filter by name across all stations.

---

## Step 6 - What to Do If a Station Is Dead

If a stream is unavailable or redirected, a dialog appears with three options:
- **Retry** - tries the stream again
- **Remove** - deletes it from your list
- **Cancel** - dismisses and keeps the entry

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Stream does not play | Check that the URL is correct and the station is online. Try Retry in the unavailability dialog |
| Audio stops when switching apps | Enable Background audio playback in Settings -> Media -> Player |
| No Streams entry in the menu | The Streams screen is absent in the Lite and Photos flavors. Use Standard, Legacy, VR, or noLegal |
| Catalog import hangs | The catalog host may be slow or offline. The import times out automatically and shows an error - check your connection and retry |
| No flags shown in language filter | Flags are shown based on the language tag in the station catalog. Manually added stations with no language tag are always visible under any language filter |
