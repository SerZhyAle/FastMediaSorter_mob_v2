---
page_id: wear.standalone-music-playback
title: Playing Music on the Watch - With or Without the Phone
nav_title: Playing music on the watch
description: How to put music on the watch, open a network resource as music rather than photos or video, check how loud the next track will be before you tap it, and keep a track playing with the screen off or the app closed.
category: Wear OS Watch
category_slug: wear
ticket: S2965
flavor: The full version of the watch app (sideload only). Copying music from the phone needs the phone app in the Standard or noLegal edition.
recipe_number: "11"
canonical_url: documentation/wear/standalone-music-playback-ru.html
why: |
  Going for a run, a swim or a walk to the shop, the phone is the first thing you'd rather leave at home. With a few albums on the watch itself, or a home server the watch can reach over Wi-Fi, the music comes along anyway: headphones paired to the watch, and nothing in your pocket.

  This recipe is about getting music onto the watch and choosing it there. Once a track is playing, the controls are in [players and viewers on the watch](page:wear.watch-players-and-viewers).
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "Bluetooth headphones paired with the watch, or a watch with its own speaker."
  - "Music on the watch, or a [network resource](term:network-resource) with music on it - [SMB](term:smb), [FTP](term:ftp) or [SFTP](term:sftp)."
steps:
  - number: 1
    id: music-onto-watch
    title: Put your music on the watch
    text: |
      The simplest way is from the phone. On the watch, open **Phone**, then **Audio**, and play a track; open the track's menu and pick **Copy to watch**. The copy lands in the watch's own storage next to your other music, so it plays with the phone switched off and stays after you close the app. How copying works in detail - names already taken, not enough room, **Move to watch** - is in [phone and network folders on the watch](page:wear.phone-and-network-folders-on-watch).

      Everything that is on the watch shows up under **Local** on the home screen. Tap **Audio** there to see all your tracks at once.
    image_bookmark:
      shot_id: wear.local-music-list
      device_profile: watch
      screen_state: wear-local-section-audio-category-list
      alt: The Local section of the watch app opened on Audio, listing tracks stored on the watch
      caption: "Your music, stored on the watch itself."
      title: "Screenshot: Local music"
      desc: Round watch, Local section, Audio category open, a list of tracks stored on the watch.
  - number: 2
    id: network-as-music
    title: Open a home server as music
    text: |
      A network resource on the watch can hold anything: holiday photos, films, albums. Tap it under **Resources** and the watch first asks what you'd like to browse, with **Video**, **Audio** and **Images** among the choices. Pick **Audio** and the list holds your tracks and nothing else.

      The choices follow the kinds you have switched on under **Media Types** in the watch's settings. Leave only one switched on - just **Audio**, say - and the watch skips the question and goes straight to your tracks. If you switch every kind off, the list tells you so: **All content types are off in settings. Turn one on to see media here.**
    image_bookmark:
      shot_id: wear.network-source-media-type
      device_profile: watch
      screen_state: wear-network-source-media-type-question
      alt: The watch asking which kind of media to browse on a network resource, with Video, Audio and Images to choose from
      caption: "Music, video or pictures - you choose what a server shows."
      title: "Screenshot: Pick a media type"
      desc: Round watch, Resources section, media type question after tapping an SMB resource, Video, Audio and Images rows.
  - number: 3
    id: check-the-volume
    title: See how loud the next track will be
    text: |
      A thin volume bar runs along the edge of the file list, showing the watch's media volume at the moment you open the screen. Glance at it before you tap a track, so a song doesn't blast into your ears in a quiet room.

      The bar only shows the level; it doesn't change it. Turn the volume up or down in the player with the crown or rotary bezel, or with the watch's buttons.
    image_bookmark:
      shot_id: wear.file-list-volume-bar
      device_profile: watch
      screen_state: wear-local-music-list-volume-side-bar
      alt: The watch music list with a thin volume bar at the edge showing the current media volume
      caption: "The volume, visible before the first note."
      title: "Screenshot: Volume bar"
      desc: Round watch, Local Music list, volume side bar at the screen edge at about half level.
  - number: 4
    id: play-and-keep-playing
    title: Play a track and let it carry on
    text: |
      Tap a track and the [audio player](term:audio-player) opens with its cover art. When one track ends, the next one in the list starts by itself, and the list starts again from the top after the last one.

      Want to put your wrist down and save battery? Tap **Screen off** in the player - the music keeps going. Switch on **Keep playing in background** in the watch's settings and a track keeps playing even after you leave the app, until you pause it. The watch then needs permission to show notifications; if it doesn't have it, it says **Turn on notifications to let playback continue in the background**.

      Every button of the player is described in [players and viewers on the watch](page:wear.watch-players-and-viewers).
    image_bookmark:
      shot_id: wear.audio-player-local-track
      device_profile: watch
      screen_state: wear-audio-player-local-track-playing
      alt: The watch audio player playing a track stored on the watch, with cover art, track title and the control row
      caption: "A track from the watch's own storage, no phone needed."
      title: "Screenshot: Playing from the watch"
      desc: Round watch, audio player, local track playing, cover art and control row with Screen off.
outcome: |
  Your music lives on the watch or on your home server, and the watch plays it on its own - pick a track, check the volume, put your wrist down and go.
tips:
  - "**A server full of photos and music?** Keep only **Audio** switched on under **Media Types** and the watch stops asking every time."
  - "**Running low on space?** Music takes room fast - copy albums over one at a time, and delete what you've heard enough of (press and hold a track, then **Delete**)."
  - "**Prefer the radio?** Live channels play on the watch too - see [streaming radio on your wrist](page:wear.wrist-stream-player)."
next_recipes:
  - title: Streaming radio on your wrist
    url: page:wear.wrist-stream-player
    badge: Watch
    badge_type: docs
    description: Find a live channel, pin it and listen right from the watch.
  - title: Browsing files on the watch
    url: page:wear.watch-file-manager
    badge: Watch
    badge_type: docs
    description: Lists, grids and file actions for everything on the watch.
  - title: Players and viewers on the watch
    url: page:wear.watch-players-and-viewers
    badge: Watch
    badge_type: docs
    description: Every button of the watch's audio and video players.
---

Put music on the watch, open a home server as music, check the volume before you tap, and play a track that keeps going with the screen off or the app closed - with the [phone](term:phone) left at home.
