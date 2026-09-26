---
page_id: player.video-playback-controls
title: Watching Videos - Controls, Gestures and Saving Frames
nav_title: Watching videos
description: How to play a video full screen or with the command panel, move between videos with taps and swipes, zoom, pick up where you left off, save a still frame as a picture, and sort your videos while you watch.
category: Video & Media Player
category_slug: player
ticket: S2951
flavor: All editions except Photos; saving frames and color controls in Standard, noLegal, Legacy and VR
recipe_number: "01"
canonical_url: documentation/player/video-playback-controls.html
why: |
  Most video players are made for one film on a Friday evening. FastMediaSorter's [video player](term:video-player) is also made for the other job: a folder with two hundred clips from the phone, a holiday, a dashcam or a security camera, where you want to look at each one for a few seconds, keep the good ones and throw the rest away.

  So the player does two things at once. It plays the video with everything you expect - full screen, speed, resume - and it keeps your sorting tools one tap away: next, previous, copy, move, delete.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Lite, Legacy, VR or FOSS [edition](term:edition). The Photos edition does not play video."
  - "A [resource](term:resource) with videos, for example the [All Videos](term:all-videos) collection, a folder on the phone or a [network folder](term:network-folder)."
  - "Optional: a [TV](term:tv) box or a car head unit with a remote - everything here works with a [D-pad](term:d-pad) too."
steps:
  - number: 1
    id: open-video
    title: Open a video
    text: |
      Tap a video in the [file browser](page:browsing.grid-and-list-views). It opens straight into full screen: the picture fills the display and nothing else is in the way. If you prefer to start with the buttons visible, turn off **Open video files in fullscreen mode** in **Settings**, the **Media** tab, section **Video and player settings**.

      The player handles the usual formats (MP4, MKV, WebM, AVI and more) and also Blu-ray `.m2ts` files copied from a disc. When the phone's own video decoder cannot handle a file, the player quietly tries a second engine, and for VP9 videos it has a software decoder of its own, so fewer files end with an error.
    image_bookmark:
      shot_id: player.video-playback-deck
      device_profile: phone
      screen_state: video-player-command-panel
      alt: The video player with the command panel visible under the picture, showing the playback buttons and the file buttons
      caption: "The video player with the command panel."
      title: "Screenshot: Video player"
      desc: Video player portrait, command panel visible, a video paused mid-way.
  - number: 2
    id: panel-fullscreen
    title: Switch between full screen and the command panel
    text: |
      The player has two faces. In **full screen** only the picture is visible. With the [command panel](term:command-panel) you also see the playback buttons - **Play/Pause**, **Rewind 10 seconds**, **Forward 10 seconds**, **Previous file**, **Next file**, **Control** - and the file buttons such as copy, move and delete.

      - To leave full screen, tap the **Exit fullscreen** button that stays in the corner, or swipe the Android system bars back in from the edge.
      - To go full screen again, choose **Fullscreen mode** in the [three-dots menu](term:three-dots-menu).
      - Turn the phone sideways and the player goes full screen by itself; turn it back upright and the command panel returns. The video keeps playing.

      While a video plays, the panel hides after 10 seconds so it does not cover the picture. Change the delay with **Player panel auto-hide duration (s)** in **Settings**, the **Player** tab - anything from 1 to 600 seconds.
  - number: 3
    id: touch-zones
    title: Tap and swipe instead of hunting for buttons
    text: |
      In full screen the picture is divided into nine invisible [touch zones](term:touch-zones), three by three. A tap in each one does something different:

      1. top left - back to the file list;
      2. top middle - copy the file to a [destination](term:destination);
      3. top right - rename;
      4. middle left - previous file;
      5. centre - pause or play;
      6. middle right - next file;
      7. bottom left - open the command panel;
      8. bottom middle - delete;
      9. bottom right - start or stop the [slideshow](term:slideshow).

      Swiping left and right also moves to the next and previous video. To see the grid while you learn it, turn on **Always show touch zones overlay** in **Settings**, the **Player** tab, section **Touch zones and on-screen hints**.

      Nine zones too many? Turn on **Disable 9-zone tracking** in the same tab. Then the screen has three zones - previous, zoom, next - and a tap on the left edge opens the command panel for file actions. With the command panel open, the picture always works as three zones: left for previous, right for next.
    image_bookmark:
      shot_id: player.touch-zones-overlay
      device_profile: phone
      screen_state: video-fullscreen-touch-zones-overlay
      alt: A full-screen video with the nine touch zones drawn over it and their names
      caption: "The nine touch zones, shown with Always show touch zones overlay."
      title: "Screenshot: Touch zones overlay"
      desc: Landscape fullscreen video with the 3x3 touch zones overlay visible.
  - number: 4
    id: brightness-volume-zoom
    title: Zoom, volume and brightness
    text: |
      Put two fingers on the picture and spread them to zoom in, for example to read a number plate in a dashcam video; pinch them together to zoom out.

      Volume and brightness sit one tap away: tap **Control**, then **Volume** or **Light**. In portrait the sliders stand upright, so you move them with your thumb from bottom to top.
  - number: 5
    id: resume
    title: Pick up where you left off
    text: |
      The player remembers how far you watched every video. Open the same video later and it continues from there, with a short message such as "Resumed from 12:40".

      It even survives a restart of the phone: with **Resume playback on next launch** turned on (**Settings**, the **Management** tab, section **App behavior and operating rules**; on by default), the app reopens the last video on start, including a video from a network or cloud folder. If that folder is not reachable any more, the app says "Cannot resume playback - resource unavailable or session expired" instead of waiting forever.
  - number: 6
    id: speed-loop
    title: Speed, repeat and the sound
    text: |
      Tap **Control** to open the Control window: there you set the speed, the volume, the soundtrack, subtitles and colors - all explained in [subtitles, audio tracks and 3D](page:player.subtitles-and-audio-tracks).

      To watch one video over and over, tap the **Playback order** button on the command panel and choose **Repeat one**. The other choices are **Loop list**, **Play through** and **Shuffle**.
  - number: 7
    id: save-frame
    title: Save a still frame as a picture
    text: |
      Pause on the moment you like and choose **Save Frame** in the three-dots menu. The frame is saved as a picture named after the moment you saved it, for example `video_frame_260924_203015.jpg` for 24 September 2026 at 20:30:15, and a short message tells you where it went.

      Where and how it is saved you choose in **Settings**, the **Media** tab, section **Video and player settings**:

      - **Resource for saving frames** - the folder for the pictures. When none is chosen, they go to Downloads.
      - **Frame file type:** - **PNG** or **JPG** (the default).
      - **Save video frames to clipboard** - also puts each frame on the clipboard, ready to paste into a chat.
    image_bookmark:
      shot_id: player.save-frame-menu
      device_profile: phone
      screen_state: video-overflow-save-frame
      alt: The three-dots menu of the video player open with Save Frame highlighted
      caption: "Save Frame turns the paused picture into a photo."
      title: "Screenshot: Save Frame"
      desc: Video paused, overflow menu open, Save Frame entry visible.
  - number: 8
    id: sort-while-watching
    title: Sort your videos while you watch
    text: |
      The **Copy to..** and **Move to..** button groups under the command panel, the touch zones and **Delete** copy, move, rename and delete the video you are watching. A deleted or moved video leaves the list at once, so **Previous file** takes you to the video before it, not to an error. More in [moving, copying and deleting files](page:storage.file-copy-move-delete).

      **Send to..** in the three-dots menu hands the video to another app or person. It works for videos in network and cloud folders too: the app fetches a temporary copy first.
  - number: 9
    id: screen
    title: Keep the screen the way you want
    text: |
      - **Keep screen on while player is active** (**Settings**, the **Management** tab, section **Operating system interaction**) stops the screen going dark during a film.
      - **Rotate player screen with OS auto-rotate** (**Settings**, the **Player** tab) makes the player follow the Android rotation lock; when it is off, the player turns with the phone. On a device without a rotation sensor the option is hidden.
      - **Show clock and status while dimmed** shows a large clock, the date and the battery level when the screen is dimmed during playback. The picture shifts a little now and then so it does not burn into the screen; tap once to bring it back after it fades. You find the switch in the **Screens and wallpaper** window - see [wallpapers and live backgrounds](page:launcher.wallpapers-and-live-backgrounds).
  - number: 10
    id: from-other-apps
    title: Videos opened from other apps
    text: |
      When you open a video from a messenger, a file manager or a download, Android can let FastMediaSorter play it in a separate, simpler player window (not in the Lite edition). It keeps the picture and the sound through a screen rotation, keeps your pause when you switch apps and come back, and continues where it was when the file is renamed.

      The same simple window opens pictures, music, documents and text from other apps. Its **Copy to** and **Move to** buttons file the item straight into one of your destinations; copying keeps the window open, moving closes it. For pictures it can also **Print**. **Open in FastMediaSorter** switches to the full app. To make FastMediaSorter the default player, see [first launch and setup](page:getting-started.welcome-and-setup).
outcome: |
  You can watch a whole folder of videos without reaching for tiny buttons: tap or swipe to move on, zoom into the details, save the best moments as pictures, and file each video away as you go - and the player remembers where you stopped.
tips:
  - "**The controls are there when you open a video.** The player shows its buttons as soon as a video opens, and a tap in the middle of the picture brings them back after they hide."
  - "**Using a TV box or a car screen?** Every button can be reached with the arrow keys of a remote. See [keyboard, D-pad and TV control](page:general.keyboard-dpad-tv-navigation)."
  - "**Video from a network folder stutters?** The player keeps a buffer suited to each kind of connection, and if playback stalls it notices and restarts the stream on its own. A slow Wi-Fi is the usual cause - see [offline caching for remote files](page:network.network-sync-and-cache)."
  - "**Network folder switched off?** When a type of network connection is disabled in the settings, the player tells you so instead of trying to connect."
  - "**Screen reader users:** the player does not flood TalkBack with announcements during playback."
  - "**Want a book read aloud?** **Read Aloud** in the document viewer stops when you leave it - see [viewing PDF and EPUB documents](page:documents.pdf-epub-viewing)."
  - "**Watching on a headset?** A VR badge in this same controls row opens stereo and 360° videos in the immersive player - see [spatial 3D/360 cinema and video playback](page:vr.spatial-cinema-playback) and [the controls once you are inside](page:vr.passthrough-and-controllers)."
next_recipes:
  - title: Subtitles, audio tracks and 3D
    url: page:player.subtitles-and-audio-tracks
    badge: Video
    badge_type: video
    description: Soundtrack language, subtitles, balance and speed.
  - title: Picture-in-picture and background play
    url: page:player.pip-and-background-play
    badge: Video
    badge_type: video
    description: Keep watching in a small window.
  - title: Chromecast casting and live broadcast
    url: page:player.casting-and-broadcast
    badge: Video
    badge_type: video
    description: Play the video on your TV.
---

Play videos full screen or with the command panel, move between them with taps and swipes, zoom in, resume where you stopped, save still frames as pictures and sort your videos while you watch.
