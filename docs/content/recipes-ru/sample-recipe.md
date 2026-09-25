---
page_id: audio.music-playback-and-organization
title: How to Play and Organize Music Files
description: A practical cookbook recipe for playing, queuing, and organizing music tracks across local and network storage.
category: Аудио и музыка
category_slug: audio
ticket: S2946
flavor: Standard и NoLegal
recipe_number: "01"
canonical_url: documentation/sample-recipe-ru.html
ingredients:
  - FastMediaSorter v2 installed on your Android device (version 2.6 or later).
  - "**Read Media Audio** permission granted upon first launch."
  - Local music files (MP3, FLAC, AAC, OGG, or WAV format).
  - (Optional) Local Wi-Fi network for streaming directly from <a href="#s2950" class="doc-link-bookmark" data-target="S2950">SMB / SFTP servers</a>.
steps:
  - number: 1
    id: step-1
    title: Locate and Open Your Music Directory
    text: Launch FastMediaSorter. From the home drawer or bottom navigation bar, tap **Local Storage** or choose a configured network bookmark. Navigate to your music directory.
    image:
      src: assets/images/sample-step1.png
      alt: Folder Browser showing Music albums with metadata badges and track counts
      caption: "Figure 1: Navigating music folders with live duration badges and track counts."
  - number: 2
    id: step-2
    title: Start Playback and Control the Queue
    text: |
      Tap any audio track to begin immediate playback using the integrated Media3 engine. The persistent bottom mini-player will appear:
      
      * **Tap the mini-player:** Expands the full-screen playback deck with album art, waveform scrubber, and equalizer controls.
      * **Swipe left/right on the mini-player:** Skips to the previous or next track in the queue.
      * **Queue management:** Tap the playlist icon to drag and reorder songs or remove duplicates.
    image:
      src: assets/images/audio-playback-deck.png
      alt: Audio player deck showing active track playback, album art, and queue playlist controls
      caption: "Figure 2: Audio playback deck with waveform scrubber and track queue."
    callout:
      type: tip
      title: Background Playback & Headset Controls
      text: FastMediaSorter continues playback in the background with lock screen controls and supports Bluetooth headset buttons (single tap pause, double tap next track).
  - number: 3
    id: step-3
    title: Quick Sorting & File Operations
    text: |
      To organize messy tracks into proper artist folders, enter multi-selection mode by long-pressing any item:
      
      1. Select the target tracks or whole albums.
      2. Tap the **Copy/Move Panel** button in the top action bar.
      3. Pick your destination folder from your predefined destination bookmarks.
    image_bookmark:
      shot_id: audio.queue-reorder
      device_profile: phone
      screen_state: audio-queue-active
      alt: Batch file copy and move panel showing predefined destination folder bookmarks
      caption: "Figure 3: Quick file move and copy destination targets."
      title: "Screenshot: Quick Move & Copy Panel"
      desc: Shows destination folder bookmarks and batch selection action bar.
    callout:
      type: warning
      title: Keep Your Android Screen On During Large Network Transfers
      text: When moving files across network shares (<span class="doc-link-term" data-term="SMB">SMB</span> or <span class="doc-link-term" data-term="SFTP">SFTP</span>), keep the transfer window in the foreground or ensure FastMediaSorter is excluded from aggressive vendor battery-killer rules.
snippets:
  - title: Sample Equalizer Configuration Preset
    path: docs/content/snippets/audio-equalizer-config.json
    language: json
next_recipes:
  - title: Fine-Tuning the 10-Band Equalizer
    url: design-system/index.html
    badge: Audio
    badge_type: music
    description: Learn how to save custom EQ presets for headphones, car audio, and Bluetooth speakers.
  - title: Setting Up SMB Streaming on Home NAS
    url: "#s2950"
    target: S2950
    badge: Network
    badge_type: docs
    description: Stream your high-resolution FLAC library directly without filling your device storage.
---

Whether you have thousands of lossless FLAC tracks on an SD card or albums shared across a home <span class="doc-link-term" data-term="NAS">NAS</span> server, FastMediaSorter lets you browse, queue, and sort your audio library without altering your physical folder hierarchy.
