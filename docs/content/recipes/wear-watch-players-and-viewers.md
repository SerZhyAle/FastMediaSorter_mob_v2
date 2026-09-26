---
page_id: wear.watch-players-and-viewers
title: Watching and Listening on the Watch - Players and Viewers
nav_title: Players and viewers
description: How to use the watch's image viewer, video player and audio player - tap zones, crown zoom, bezel volume, remembered frame mode and background playback - plus the on-watch text reader, the players' control layout, and sending what's on the watch to a TV.
category: Wear OS Watch
category_slug: wear
ticket: S2966
flavor: The full version of the watch app (sideload only) - the Google Play version has no player at all. See the noLegal edition.
recipe_number: "07"
canonical_url: documentation/wear/watch-players-and-viewers.html
why: |
  Opening a photo, a video or a song on a watch this small only works if the controls get out of the way until you actually need them, and stay reachable with one thumb when you do. FastMediaSorter's three watch players - the [image viewer](term:image-viewer), the [video player](term:video-player) and the [audio player](term:audio-player) - are built around that: tap zones instead of a screen full of buttons, the rotary crown or bezel doing the one thing you would reach for anyway, and a control row that reshapes itself to the size of your particular watch.

  This recipe covers all three players, plus the on-watch document reader and sending what's on the watch to a TV.
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition); the Google Play version plays nothing at all."
  - "Something to open: a file on the watch, or one browsed from a synced [network resource](term:network-resource) or the phone - see [browsing files on the watch](page:wear.watch-file-manager)."
  - "For sending video or a picture to a TV: the phone already connected to a [Chromecast](term:chromecast)."
steps:
  - number: 1
    id: viewing-a-picture
    title: "Viewing a picture: tap zones, fit and pinch"
    text: |
      Open a photo and the [image viewer](term:image-viewer) shows you the picture alone, nothing else. Tap the left or right third of the screen to page to the previous or next picture; tap the middle to bring the controls up. Left alone, they hide themselves again after fifteen seconds. A picture that fills the screen, rather than fitting it whole, can be pinched to zoom and dragged around.

      Turning the crown or the rotary bezel does the same job as pinching, in steps: each notch zooms in or out a little further, up to four times the picture's normal size, with a small haptic tick at every step. Turn it back down to normal size and the picture recentres itself.

      A command on the same screen switches between **fitting** the whole picture in the frame and **filling** the screen edge to edge; whichever you pick is remembered and comes back the next time you open a picture.
    image_bookmark:
      shot_id: wear.image-viewer-tap-zones
      device_profile: watch
      screen_state: wear-image-viewer-tap-zones-hint
      alt: The watch image viewer showing a full-screen photo with faint left, middle and right tap zone hints
      caption: "Tap left or right to page, tap the middle for controls."
      title: "Screenshot: Image viewer tap zones"
      desc: Round watch, image viewer, full-screen photo, left/middle/right zone hint overlay.
  - number: 2
    id: image-menu-and-slideshow
    title: "Starting a slideshow, and the viewer's own menu"
    text: |
      A **Play**-shaped command on the viewer starts a [slideshow](term:slideshow) right there, showing the next picture at once rather than waiting out a countdown; the same command stops a show that's already running, and turning the slideshow setting off anywhere also stops one that's open on screen.

      The scale mode you just read about sits as its own button on the control row instead of hiding behind a second **more** button - and that is the rule behind every player's menu on the watch: whatever already has a button on the row is never repeated inside the menu. The image viewer's menu opens on **Back** and closes again with a tap anywhere past the buttons.
    image_bookmark:
      shot_id: wear.image-viewer-menu
      device_profile: watch
      screen_state: wear-image-viewer-menu-open
      alt: The watch image viewer with its command row visible - scale mode, slideshow and more buttons - and its menu open showing Back at the top
      caption: "One row of buttons, one menu behind more."
      title: "Screenshot: Image viewer menu"
      desc: Round watch, image viewer, command row and open more menu, Back as first entry.
  - number: 3
    id: watching-video
    title: "Watching video: bezel volume, a long press to seek"
    text: |
      In the [video player](term:video-player), turning the crown or bezel changes the **media volume**, not the playback position, matching how the audio player and the platform itself treat a rotary input - and the current level is drawn right in the control panel while it is visible, so you are never adjusting it blind; turning the bezel brings the panel up if it was hidden. Seeking moved to a long press on the **Previous** and **Next** buttons instead, and both stay on screen even for a single file or a live stream, so seeking is always reachable. TalkBack announces each long press by its own name.

      Leave the player and come back, or restart the app entirely, and the frame mode you last chose - **fit** or **crop-and-pan** - is still set; one memory covers both a file and a stream, since they share the same player. Never touched the button? It starts on fit.

      With the slideshow turned on, a video that finishes opens the next one in the same set by itself, wrapping back to the first past the end - the same rule the phone app follows for video. With the slideshow off, or with only one file in the set, the player simply stops at the end instead.

      The video player follows the same one-row, one-menu rule as the image viewer: scale mode lives on the row, the menu opens on Back, and nothing shown on the row is repeated inside it.
    image_bookmark:
      shot_id: wear.video-player-controls
      device_profile: watch
      screen_state: wear-video-player-controls-bezel-volume
      alt: The watch video player showing playback controls with a volume readout in the control panel
      caption: "Turn the bezel for volume, hold Previous or Next to seek."
      title: "Screenshot: Video player controls"
      desc: Round watch, video player, control row with time ring and volume readout visible.
  - number: 4
    id: listening-to-music
    title: "Listening to music: cover art, and one tidy menu"
    text: |
      Before you even open it: in the file browser, an audio file's cell shows its own embedded cover art - read straight from the file's ID3, FLAC or MP4 tags - with the track name written over it, so a cell with a cover and one without still line up neatly next to each other.

      The [audio player](term:audio-player) itself carries that same cover, the track controls and the queue. A finished track opens the next file of the set by itself and wraps to the first one past the end - including a shuffled order, which now plays on without a tap on **Next**. Turn on **Keep playing in background** and audio, a file or a stream, keeps going once you leave the app, until you pause it; pausing lets go of the notification that keeps it alive.

      The audio player's own menu holds one list too: the commands its row has no room for, then the file actions the open file allows, nothing shown twice. **Screen off** sits right on the row instead of hiding behind **more**.
    image_bookmark:
      shot_id: wear.audio-player-cover-art
      device_profile: watch
      screen_state: wear-audio-player-cover-art-title
      alt: The watch audio player showing the track cover art, title, and control row with a Screen off button
      caption: "Cover, controls and Screen off, all on one row."
      title: "Screenshot: Audio player"
      desc: Round watch, audio player, cover art, track title, control row with Screen off button.
  - number: 5
    id: player-screen-off
    title: "Turning the screen off while it keeps playing"
    text: |
      The audio player, and an audio stream, offer their own **Screen off**: the display goes dark while the sound keeps going, and one tap brings the picture and controls straight back. A single tap on the dark screen otherwise just sends a thin ring spreading out from the touch point and fading away, so a stray touch in your pocket does not wake it by accident; a double tap, a long press or the watch's own button all bring it back properly.

      The video player does not offer this - going dark during a video would hide the one thing its screen is open for.
    image_bookmark:
      shot_id: wear.player-screen-off
      device_profile: watch
      screen_state: wear-player-screen-off-dark
      alt: The watch audio player with its screen dark after Screen off was tapped, a faint ring fading from a recent touch point
      caption: "Dark, but still playing."
      title: "Screenshot: Player screen off"
      desc: Round watch, audio player, screen off, fading touch ring, sound still playing.
  - number: 6
    id: commands-that-fit-the-glass
    title: "Commands that fit the glass"
    text: |
      All three players choose their control row by the size of your particular watch. A small round watch gets three main commands and two secondary ones, with the playing position drawn as a ring around the play button instead of its own time row; a larger watch gets three and three, plus the time row. Whatever does not have room on the row - playback mode, the stream pin, scale mode, screen off, the favorite button on a small watch, and file actions - moves into the menu behind **more**, so nothing becomes unreachable; every button stays at least 48dp on a side either way.

      Prefer the layout from before the Play Store's shape review? The same **Original layout** switch covered in [finding your way around the watch app](page:wear.watch-home-and-appearance) also reshapes the players: two rows of four commands each, with the menu keeping only what those eight slots cannot hold.

      However the row is showing, the panel that carries it hides itself after a set pause - adjustable right there with the auto-hide **-**/**+** buttons, or from the phone's Wear Companion window, where the mirrored value always matches what the watch is really using, even an unusual number carried over from an older watch app.
    image_bookmark:
      shot_id: wear.player-control-grid
      device_profile: watch
      screen_state: wear-player-control-grid-small-watch
      alt: The watch video player's control row on a small round watch, three main and two secondary commands with a ring around the play button
      caption: "Three main commands, two secondary, sized to the glass."
      title: "Screenshot: Player control grid"
      desc: Round watch, small display, video player, three-plus-two control layout, ring position indicator.
  - number: 7
    id: reading-documents
    title: "Reading text documents on your wrist"
    text: |
      A text file - stored on the watch or fetched from a network source - opens in its own [reader](term:reader) instead of a refusal screen. Scroll it with a finger or the rotary bezel; your place and your chosen font size, **Small**, **Medium** or **Large**, both come back the next time you open it. A file too large to hold whole says so rather than pretending it loaded completely, and an empty file is told apart from one the watch simply could not read. A format the watch does not render at all still lands on the refusal screen - it just names the format now, instead of staying silent about it.
    image_bookmark:
      shot_id: wear.text-document-reader
      device_profile: watch
      screen_state: wear-text-reader-scrolled-page
      alt: A text document open in the watch reader, scrolled partway down, with a font size control
      caption: "Scroll by finger or by bezel, the place is remembered."
      title: "Screenshot: Text document reader"
      desc: Round watch, text reader, scrolled page, font size row at the bottom.
  - number: 8
    id: sending-to-a-tv
    title: "Sending what's on the watch to a TV"
    text: |
      From a player's menu, **Show on TV** sends what's playing to the Chromecast your phone is already connected to - a live stream, or a picture or video that came from one of the phone's own network sources. The phone owns that session: it is the phone that was pointed at the TV, so while it runs, the same entry reads **Stop showing on TV** and ends it from either device. Nothing has been chosen yet? The watch says so in words, "Choose a TV on your phone first", rather than failing quietly.

      One real limit: a file stored only on the watch itself cannot be sent this way, because its address means nothing off the watch. Only a stream or a phone-sourced file qualifies.
    image_bookmark:
      shot_id: wear.cast-to-tv
      device_profile: watch
      screen_state: wear-player-menu-show-on-tv
      alt: The watch player menu with the Show on TV entry, and a Playing on your TV status once it is running
      caption: "One entry to start, the same one to stop."
      title: "Screenshot: Show on TV"
      desc: Round watch, player menu, Show on TV entry, Playing on your TV state.
outcome: |
  Three players that show and hide their own controls, a crown that zooms or turns the volume up depending on what's open, a reader for the odd text file, and one tap to put any of it on the TV your phone already knows about.
tips:
  - "**Long-press for seeking, not a drag bar.** In the video player, hold down Previous or Next instead of hunting for a scrub bar - it works even with a single file open."
  - "**A shuffled playlist now finishes on its own.** Automatic advance follows the shuffle order too, so you don't need to keep tapping Next to hear it."
  - "**Every menu is one list, not two.** A command already sitting on the control row is never repeated inside the player's menu - the menu only adds what the row had no room for."
  - "**Prefer bigger, fewer buttons?** Switch on Original layout in Settings > Screen - see [finding your way around the watch app](page:wear.watch-home-and-appearance)."
next_recipes:
  - title: Finding your way around the watch app
    url: page:wear.watch-home-and-appearance
    badge: Watch
    badge_type: docs
    description: The home screen, sections and Settings these players open from.
  - title: Browsing files on the watch
    url: page:wear.watch-file-manager
    badge: Watch
    badge_type: docs
    description: Where a file, a resource or a channel comes from before it opens here.
  - title: Casting and Live Broadcast from the phone
    url: page:player.casting-and-broadcast
    badge: Phone
    badge_type: docs
    description: The Chromecast session the watch's Show on TV hands off to.
---

A tour of the watch app's three players - image, video and audio - plus its text document reader and sending what's on the watch to a TV.
