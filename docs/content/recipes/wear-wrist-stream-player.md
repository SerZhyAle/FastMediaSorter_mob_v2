---
page_id: wear.wrist-stream-player
title: Streaming Radio and TV on Your Wrist - Finding, Pinning and Playing Live Channels
nav_title: Streaming radio on your wrist
description: How to open the watch's Streams screen, narrow thousands of channels down with search, filters and sorting, pin the few you actually listen to, play a live channel on the watch and catch up to the live moment after a pause.
category: Wear OS Watch
category_slug: wear
ticket: S2965
flavor: The full version of the watch app (sideload only). Pins made on the phone and channels sent from the phone need the phone app in the Standard or noLegal edition.
recipe_number: "12"
canonical_url: documentation/wear/wrist-stream-player.html
why: |
  Internet radio is the perfect thing for a watch: no files to copy, no storage to fill, just a station and your headphones. The watch has its own [catalog](term:catalog) of live [channels](term:channel) - radio and TV from all over the world - and plays them over Wi-Fi or mobile data without the phone.

  The catch is size: many thousands of channels on a screen that shows four at a time. This recipe shows how to get from that to the two or three you actually listen to, in a couple of taps.
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "A watch with Wi-Fi or mobile data, and headphones or a speaker on the watch."
  - "Optional: the phone app with the [Wear Companion](term:wear-companion) switched on, to bring your phone's pinned channels and your own channels over."
steps:
  - number: 1
    id: open-streams
    title: Open Streams and let the catalog load
    text: |
      On the watch's home screen, tap **Streams**. The first time, the list is still being put together - the watch shows it loading until the catalog is ready, rather than flashing an empty screen at you. If the catalog was never downloaded, tap **Refresh catalog**; while it works you'll see **Updating streams..**, and if the network lets you down, **Could not update streams. Check your connection and try again**.

      Don't see **Streams** on the home screen at all? Switch on **Show Streams** in the watch's settings.
    image_bookmark:
      shot_id: wear.streams-list
      device_profile: watch
      screen_state: wear-streams-screen-channel-list-with-counter
      alt: The watch Streams screen with search, filter and sort buttons at the top, a two-line channel counter and a list of live channels
      caption: "The whole world's radio, one scroll away."
      title: "Screenshot: Streams"
      desc: Round watch, Streams screen, toolbar with search, filter and sort, two-line counter, channel list.
  - number: 2
    id: search-filter-sort
    title: Narrow thousands of channels down to a few
    text: |
      Three small buttons sit at the top: **Search streams**, **Filter streams** and **Sort streams**. They stay pinned in place while the channel list scrolls under them, and the first channel is never hidden behind them. Beside them, two small lines count the channels: how many your search and filters leave, over the size of the whole catalog.

      - **Search** - type or speak part of a channel's name.
      - **Filter** - first choose what kind of channel you want: **Video**, **Audio** or **Own** (channels you sent over from the phone). Tap the lit choice again to see everything. Below that, pick a **Topic**, a **Language** or one of the **Collections**. Topics and languages are shown in your own language, with the busiest ones first and the number of channels on each, and the chips wrap onto new lines so every name is readable.
      - **Sort** - **Most used** (the default: channels you actually play on the watch rise to the top), **Name (A-Z)**, **Name (Z-A)** or **By media type**.

      The watch remembers your filter and sort order when you leave the screen, restart the app or even reboot the watch.
    image_bookmark:
      shot_id: wear.streams-filter-sheet
      device_profile: watch
      screen_state: wear-streams-filter-video-audio-own-topic-chips
      alt: The watch stream filter with Video, Audio and Own choices on top and topic chips with channel counts below
      caption: "Pick a kind, a topic and a language - in your own language."
      title: "Screenshot: Filter streams"
      desc: Round watch, Filter streams screen, Video Audio Own rows, Topic chips with counts wrapping onto several lines.
  - number: 3
    id: empty-filter
    title: When the filter leaves nothing
    text: |
      Narrow things too far and the list says so honestly: **No streams match the filter. Clear it to see the whole catalog.** One tap on **Clear filters** removes every narrowing at once - kind, topic, language and collection - and your channels are back.

      This is different from an empty catalog. Downloading the catalog again wouldn't help here, so the watch doesn't suggest it.
    image_bookmark:
      shot_id: wear.streams-filtered-empty
      device_profile: watch
      screen_state: wear-streams-filtered-empty-clear-filters
      alt: The watch Streams screen showing No streams match the filter with a Clear filters button
      caption: "Nothing left? One tap brings everything back."
      title: "Screenshot: No matches"
      desc: Round watch, Streams screen, filtered-empty message and Clear filters chip.
  - number: 4
    id: play-a-channel
    title: Play a channel
    text: |
      Tap a channel. A radio station opens in the [audio player](term:audio-player), a TV channel in the [video player](term:video-player). For the time a stream plays, the watch asks for a fast network connection and lets it go again afterwards, so it doesn't drain the battery when you stop.

      If the connection can't carry the stream, the watch tells you why instead of just going quiet:

      - **Connection too slow for this stream. Move closer to Wi-Fi and try again.**
      - **Watch is offline. Reconnect, then try again.**
      - **Connection not verified yet. Playback may stop - try again if it does.**

      If a station stops sending altogether, the watch stops playback to save the battery and says **The stream stopped sending. Playback was stopped to save the battery.**
    image_bookmark:
      shot_id: wear.stream-connection-slow
      device_profile: watch
      screen_state: wear-stream-player-connection-too-slow-message
      alt: The watch player with the message Connection too slow for this stream. Move closer to Wi-Fi and try again
      caption: "The watch says why a stream won't play."
      title: "Screenshot: Connection too slow"
      desc: Round watch, stream player, message about a connection too slow for the stream.
  - number: 5
    id: live-controls
    title: The station line, and jumping back to live
    text: |
      A live stream has no beginning and no end, so where a song would show its seek bar and running time, the audio player shows the station's own details instead: its name, genre, the audio format and the bitrate, such as **128 kbps**. No bar you can't drag, no clock stuck at 0:00.

      Paused for a phone call? When you press play again you'd normally hear the broadcast from where you left off, a few minutes behind everyone else. Tap **Jump to live** - right after the play and pause button - and the watch reconnects and plays what's on air at this moment.
    image_bookmark:
      shot_id: wear.stream-station-line
      device_profile: watch
      screen_state: wear-audio-player-live-station-line-jump-to-live
      alt: The watch audio player playing a live radio station, showing the station name, genre, format and bitrate instead of a seek bar, with a Jump to live button
      caption: "Station details in place of a seek bar, and Jump to live."
      title: "Screenshot: Live station"
      desc: Round watch, audio player on a live radio stream, station line with name, genre, codec and bitrate, Jump to live button.
  - number: 6
    id: pin-favorites
    title: Pin the channels you love
    text: |
      While a channel plays, tap the pin in the player - **Pin stream**. The pin fills in, and from now on the channel sits at the top of the **Streams** list. Tap it again - **Unpin stream** - to let it go. The pin remembers the channel by its address, so it survives a fresh download of the catalog.

      Channels you pinned on the phone come over to the watch too. The list then has three groups: channels pinned on the watch first, channels pinned only on the phone next, and everything else after them, each group in the sort order you chose. A channel pinned on both devices appears once, among the watch's own. Unpin a channel on the phone and it leaves the watch's phone group; a pin you made on the watch stays.
    image_bookmark:
      shot_id: wear.stream-pin-button
      device_profile: watch
      screen_state: wear-stream-player-pin-stream-filled
      alt: The watch player on a live stream with a filled pin button marking the channel as pinned
      caption: "One pin, and the channel leads the list."
      title: "Screenshot: Pin stream"
      desc: Round watch, stream player, filled pin button after Pin stream.
outcome: |
  From a catalog of thousands you're down to a handful of pinned channels at the top of the list, playing on the watch alone, and always one tap away from the live moment.
tips:
  - "**Your own channels from the phone** are in the **Own** filter - send one over with **Send to watch** on the phone, as described in [streams on a TV, a watch or a VR headset](page:streams.tv-watch-vr-and-broadcast)."
  - "**Most used** learns as you go - after a few days, the stations you really listen to rise to the top on their own."
  - "**Pin on the phone, listen on the watch** - pins you make in the phone's **Streams** show up on the watch automatically."
next_recipes:
  - title: Playing music on the watch
    url: page:wear.standalone-music-playback
    badge: Watch
    badge_type: docs
    description: Put music on the watch and play it with the phone left at home.
  - title: Streams on a TV, a watch or a VR headset
    url: page:streams.tv-watch-vr-and-broadcast
    badge: Streams
    badge_type: docs
    description: Send a channel from the phone to the watch and start it there.
  - title: Players and viewers on the watch
    url: page:wear.watch-players-and-viewers
    badge: Watch
    badge_type: docs
    description: Every button of the watch's audio and video players.
---

Open the watch's own [Streams](term:streams-screen) screen, narrow thousands of live [channels](term:channel) down with search, filters and sorting, pin the few you love, and play them on the watch alone - jumping back to the live moment after a pause.
