---
layout: default
title: "Listen to Music on Your Watch - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-music.html
---
# <img src="../icons/doc/ic_audio.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Listen to Music on Your Watch

> **Level:** Beginner &bull; **Time:** ~5 minutes &bull; **Device:** Wear OS smartwatch (paired with Android phone)

[Русский](scenario-watch-music-ru.md) | [Українська](scenario-watch-music-uk.md)

FastMediaSorter lets you browse and play your music collection directly from your Wear OS smartwatch. You can stream tracks shared from your paired phone or play local audio files stored on the watch, with cover artwork, shuffle, rotary bezel volume control, background playback that survives leaving the app, and a screen-off mode that keeps the music going with the display dark.

---

## What You Will Need

- A smartwatch running **Wear OS 2.0** or newer with FastMedia Wear installed
- An Android phone running FastMediaSorter (if streaming music from your phone)
- Music files (MP3, FLAC, AAC, OGG) on your phone or transferred to your watch storage
- Bluetooth headphones or watch speaker for audio output

---

## Step 1 - Open FastMedia Wear on Your Watch

1. Open the apps list on your smartwatch and tap **FastMedia Wear**.
2. The home screen shows six sections, always in the same places:
   - **Resources**: network sources and sync options
   - **Phone**: music and media shared from your paired Android phone
   - **Local**: files in the watch's own storage, including voice notes you recorded there
   - **Streams**: TV and radio channels ([separate guide](scenario-watch-tv.md))
   - **Apps**: calculator, network monitor, game and the other mini-programs
   - **Favourites**: everything you marked

![FastMedia Wear main screen on smartwatch](screenshots/screenshot-wear-music-step1.png)

---

## Step 2 - Choose Your Music Source

1. To play music from your phone: tap **Phone** on the main screen, then tap **Audio**.
2. To play tracks stored directly on the watch: tap **Local** on the main screen, then tap **Music**.
3. FastMedia Wear connects to the selected source and loads your music catalog.

> **Tip:** The home screen keeps a row of the resources you opened most recently above the six sections - one cell per column, so two in a two-column grid and three in a three-column one. Once you have played something, it is there for a single tap, and the last stream channel you watched sits in the same row.

---

## Step 3 - Browse and Start Playback

1. Scroll through your tracks using touch or the rotating bezel.
2. Each item displays the track title, duration, and album art thumbnail.
3. Tap **any track** to start playback immediately.

![Browse audio tracks on watch](screenshots/screenshot-wear-music-step3.png)

---

## Step 4 - Control Playback and Volume

When a track starts, the full-screen **Audio Player** opens:

- **Play / Pause**: tap the center highlighted button to pause or resume playback.
- **Skip tracks**: tap **Previous** or **Next** to switch tracks in your playlist.
- **Shuffle**: tap the **Shuffle** button to mix track order.
- **Seek in track**: drag the progress bar horizontally to jump to any position in the song.
- **Volume**: turn your watch's rotating crown or bezel to adjust volume smoothly. A volume level indicator appears on screen.
- **Favorite**: tap the heart icon to add the track to your Favourites.

![Audio player with playback controls and volume](screenshots/screenshot-wear-music-step4.png)

---

## Step 5 - Keep the Music Going

There are two different ways to keep listening, and they answer two different questions.

**Leaving the app** - turn on **Keep playing in background** in the watch settings. Audio then continues after you minimize the app or return to the watch face, with controls in the media notification. When you come back, the home screen carries a row naming what is playing: tap it to return to the track where it left off, or tap the stop button beside it to end playback without opening anything else. The switch is opt-in, and it needs notifications to be allowed - without them the system cannot keep the playback service alive.

**Staying in the player with the screen dark** - tap the **Screen off (🌙)** button at the bottom of the player controls. The display turns completely black while the music keeps playing, which saves battery on an OLED watch. Tap anywhere to wake it and see the controls again.

![Screen-off mode button](screenshots/screenshot-wear-music-step5.png)

> Video and slideshows are deliberately not covered by either: they stop when the app leaves the screen, because a picture nobody can see is only costing battery.

---

## Done! Player Features

- **Album Art & Wave Background**: Displays full-bleed cover art or dynamic sound waves behind controls.
- **Rotary Bezel Integration**: Native volume control using the watch physical bezel or crown.
- **Background Playback**: Audio survives leaving the app, with controls in the media notification and a row on the home screen naming what is playing.
- **Screen-Off Listening**: Instant display blackout inside the player, preserving playback and battery life.

---

## Troubleshooting

| Problem | What to try |
|---------|------------|
| Phone section says "Phone not connected" | Make sure Bluetooth is enabled on both devices and FastMediaSorter is installed on your phone |
| No music files appear under Local | Copy MP3 or FLAC files to your watch internal storage or use the Phone section to play from your phone |
| Audio stops when you leave the app | Turn on **Keep playing in background** in the watch settings, and allow notifications - the playback service needs them to stay alive |
| Cover art is missing | Connect to Wi-Fi to fetch online artwork, or ensure your audio files contain embedded ID3 cover art |
