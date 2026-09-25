---
page_id: audio.playing-and-organizing-music
title: Playing and Organizing Music Files
nav_title: Playing music
description: How to play music from any folder, get album covers and lyrics from the internet, choose an animated background for tracks without a cover, send music to a Chromecast, and sort tracks into folders while you listen.
category: Изображения, аудио и слайд-шоу
category_slug: audio
ticket: S2952
flavor: All editions except Photos
recipe_number: "04"
canonical_url: documentation/audio/playing-and-organizing-music-ru.html
why: |
  Your music does not live in one neat library. Some albums are on the phone, some on a memory card, some on the computer in the next room. FastMediaSorter plays music straight from the folders where it already is - no importing, no library to build - and lets you sort a messy download folder while you listen.

  The same [audio player](term:audio-player) finds missing album covers, shows song lyrics, and fills the screen with a calm animation when a track has no picture.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Lite, Legacy, VR or FOSS [edition](term:edition). The Photos edition does not play audio."
  - "Music files in any common format: MP3, FLAC, AAC, M4A, OGG, Opus, WMA, WAV, ALAC and more. MIDI files (MID, MIDI) play too."
  - "A [resource](term:resource) that holds your music - for example the ready-made [All Music](term:all-music) collection, a folder on this device, or a [network folder](term:network-folder)."
  - "For covers and lyrics from the internet: an internet connection."
  - "For Chromecast: a Chromecast on the same Wi-Fi network, and any edition except VR and FOSS."
steps:
  - number: 1
    id: open-track
    title: Open a track
    text: |
      Open a resource with music on the [main screen](term:main-screen) and tap a track in the [file browser](page:browsing.grid-and-list-views). The audio player opens and starts playing.

      The screen shows the album cover, the track name and details such as size, format and sample rate, a progress bar you can drag, and the playback buttons. A small spinning vinyl record in the corner tells you at a glance that music is playing; it stops when you pause.

      When the track ends, the player moves on to the next audio file of the same folder. Swipe left or right, or use the next and previous buttons, to skip.
    image:
      src: assets/images/audio/audio-player-cover-art.png
      alt: The FastMediaSorter audio player on a large screen showing an album cover, the track name, the progress bar and the playback buttons
      caption: "The audio player with an album cover and the spinning vinyl record in the corner."
  - number: 2
    id: covers
    title: Get missing album covers from the internet
    text: |
      Most music files carry their own cover picture, and the player shows it right away. For files without one, the app can look the cover up online. Go to **Settings**, the **Media** tab, section **Audio playback, covers and visuals**, and turn on **Search audio covers online**. It is off until you turn it on.

      The app then asks three free music catalogs in turn - iTunes, Deezer and the Cover Art Archive - and shows the first good cover it finds. Two more switches appear below: **Search only on Wi-Fi** (on by default, so no mobile data is used) and **Save downloaded media data locally** (on by default, so each cover is fetched only once).
    image_bookmark:
      shot_id: audio.settings-covers-online
      device_profile: phone
      screen_state: settings-audio-covers-online
      alt: The Audio playback, covers and visuals settings section with Search audio covers online turned on and its two extra switches
      caption: "Online cover search in Settings."
      title: "Screenshot: Online cover settings"
      desc: Settings, Media tab, Audio section, covers online on, Wi-Fi only and save locally visible.
  - number: 3
    id: backdrop
    title: Pick a background for tracks without a cover
    text: |
      When no cover can be found, the screen does not have to stay empty. In the same settings section tap **Visualizer when no cover art** and choose one:

      - **Black background** - nothing moves.
      - **Music note + pulse rings** - a music note with rings pulsing around it.
      - **Breathing bars (15)** - soft colored bars rising and falling like an equalizer.
      - **Wave & Particle Animation** - flowing waves with drifting particles.
      - **Visualization (MP4)** - one of several looping video clips. The clips are not part of the app; the first time you choose this, the app offers to download them.

      Instead of an animation you can also show your own photos: turn on **Show random photos during audio playback**, tap **Select Photos Source** and choose a resource with pictures. A different photo from it appears for each song. See [Creating photo slideshows](page:images.slideshow-and-transitions) to let the photos change during a song.
    image:
      src: assets/images/audio/audio-player-wave-backdrop.png
      alt: The audio player with the Wave and Particle animation filling the screen behind the track name and playback buttons
      caption: "Wave & Particle Animation behind a track without a cover."
  - number: 4
    id: lyrics-youtube
    title: Read the lyrics or find the song on YouTube Music
    text: |
      Open the [three-dots menu](term:three-dots-menu) and tap **Lyrics**. The app searches the internet for the words of the song, using the artist and title stored in the file or its file name, and shows them full screen. Tap the close button to return to the player. The text size follows the **Text Settings** you use for translations.

      To hear another version of the song or explore the artist, tap **In YouMusic** on the command panel or in the three-dots menu. The YouTube Music app opens with a search for the current track. If YouTube Music is not installed, a short message tells you so.
    image_bookmark:
      shot_id: audio.lyrics-overlay
      device_profile: phone
      screen_state: audio-lyrics-overlay
      alt: Song lyrics shown full screen over the audio player with a close button
      caption: "Lyrics shown full screen."
      title: "Screenshot: Lyrics overlay"
      desc: Audio player with the lyrics overlay open for a well-known track.
  - number: 5
    id: cast
    title: Play on a Chromecast
    text: |
      Tap the cast button **Cast to..** on the command panel and pick your Chromecast or smart TV from the list. The music plays on it, and the phone becomes the remote control. The same button sends photos, GIFs and videos too; see [casting and broadcast](page:player.casting-and-broadcast).

      Files from a network folder or [cloud storage](term:cloud-storage) are first copied to the phone and then sent to the Chromecast, so the first seconds may take a moment. The phone and the Chromecast must be on the same Wi-Fi network.
    image_bookmark:
      shot_id: audio.cast-device-picker
      device_profile: phone
      screen_state: audio-cast-picker
      alt: The Chromecast device list opened from the audio player's Cast to button
      caption: "Choose a Chromecast to play on."
      title: "Screenshot: Cast device list"
      desc: Cast route chooser dialog over the audio player, one device listed.
  - number: 6
    id: sort-while-listening
    title: Sort tracks while you listen
    text: |
      A download folder full of unsorted songs is easiest to clean up by ear. While a track plays, tap **Copy to..** or **Move to..** at the bottom of the player and pick a [destination](term:destination) folder - for example "Keep", "Car" or "Delete later". The player continues with the next track, so you can sort a whole folder in one listening session.

      How destinations are set up is explained in [Copy, move and delete files](page:storage.file-copy-move-delete).
outcome: |
  Your music plays from wherever it is stored, with covers found automatically, lyrics one tap away, a calm animation or your own photos when there is no cover, and a Chromecast when you want the big speakers. A messy folder gets sorted while you listen.
tips:
  - "**A WAV file opened a menu instead of playing?** Current versions open WAV files in the audio player directly. Update the app if you still see the menu."
  - "**Covers do not appear?** Check that **Search audio covers online** is on, and that you are on Wi-Fi if **Search only on Wi-Fi** is on. Very rare recordings may simply not be in the catalogs."
  - "**Cast button missing?** The VR and FOSS editions do not include Chromecast support. Also check that Wi-Fi is on."
  - "**Want the music to keep playing after you leave the player?** See [Playback order, sleep timer and listening in the background](page:audio.playlists-and-audio-queues)."
next_recipes:
  - title: Playback order, sleep timer and listening in the background
    url: page:audio.playlists-and-audio-queues
    badge: Audio
    badge_type: music
    description: Shuffle, repeat, fall asleep to music and control playback from the notification.
  - title: Creating photo slideshows
    url: page:images.slideshow-and-transitions
    badge: Photos
    badge_type: image
    description: Let photos change on screen while your music plays.
  - title: Music on the watch
    url: page:wear.standalone-music-playback
    badge: Watch
    badge_type: wear
    description: Play and control music from your Wear OS watch.
---

Play music straight from the folders where it lives - on the phone, a memory card or another computer. The [audio player](term:audio-player) finds missing covers, shows lyrics, casts to a Chromecast and lets you sort tracks while you listen.
