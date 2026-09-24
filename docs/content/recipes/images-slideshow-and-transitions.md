---
page_id: images.slideshow-and-transitions
title: Creating Photo Slideshows
nav_title: Photo slideshows
description: How to let your photos change by themselves, set how many seconds each one stays, add background music, let videos and songs play to the end, and what the slideshow does when a network folder goes away.
category: Images, Audio & Slideshow
category_slug: images
ticket: S2952
flavor: All editions
recipe_number: "03"
canonical_url: documentation/images/slideshow-and-transitions.html
why: |
  Relatives are visiting and you want to show the holiday photos on the TV without swiping every few seconds. Or an old tablet on the shelf could become a photo frame that shows a new family picture every minute. A [slideshow](term:slideshow) does exactly that: it moves to the next photo by itself, after the number of seconds you choose, and can play music in the background.

  A slideshow is only a way of looking. It never creates a video and never changes your files.
ingredients:
  - "FastMediaSorter in any [edition](term:edition). In the Photos edition the slideshow shows pictures only, because that edition does not play video or audio."
  - "A [resource](term:resource) with photos, for example a folder on this device or a [network folder](term:network-folder) on your home computer."
  - "Optional: a second resource with music, if you want background music."
steps:
  - number: 1
    id: start
    title: Start the slideshow
    text: |
      Open the first photo of the folder in the [image viewer](term:image-viewer) and tap **Slideshow** on the control bar. The button turns red while the slideshow runs. Tap it again to stop.

      There is also a shortcut on the [main screen](term:main-screen): some resources show a small **Start slideshow** icon on their card, which opens the folder and starts the slideshow in one tap.

      The slideshow walks through the folder in the same order as the [file browser](page:browsing.sorting-and-filtering). The next photo replaces the previous one at once, without fades or other effects.
    image_bookmark:
      shot_id: images.slideshow-button-active
      device_profile: phone
      screen_state: player-slideshow-running
      alt: The image viewer during a slideshow with the Slideshow button shown in red on the control bar
      caption: "The Slideshow button turns red while the slideshow runs."
      title: "Screenshot: Slideshow running"
      desc: Player showing a photo, control bar visible, Slideshow button active (red).
    callout:
      type: tip
      title: The screen stays on by itself
      text: While the slideshow runs, the screen does not go dark - you will see the message "Screen will stay on during slideshow". When you stop it, your usual screen timeout comes back.
  - number: 2
    id: interval
    title: Choose how long each photo stays
    text: |
      Each photo stays on screen for 10 seconds unless you choose otherwise. You can set a different time for every resource: on the main screen open the resource for editing, expand **Advanced Settings** and type the number of seconds into **Slideshow Interval (seconds)**. Anything from 1 second to 3600 seconds (one hour) works. Tap **Save Changes**.

      The default for new resources is in **Settings**, the **Player** tab, section **Sorting, slideshow and playback order**, field **Slideshow (s)**.
    image:
      src: assets/images/slideshow/slideshow-interval-resource-editor.png
      alt: The Edit Resource screen with Advanced Settings expanded and the Slideshow Interval field set to 10 seconds
      caption: "The Slideshow Interval field in the resource editor."
  - number: 3
    id: countdown
    title: Watch the countdown
    text: |
      A few seconds before the next photo appears, a small countdown - 3, 2, 1 - shows in the corner. It tells you the photo is about to change, so you can stop the slideshow in time if you want to look longer. The countdown appears whenever a photo stays longer than three seconds.
    image_bookmark:
      shot_id: images.slideshow-countdown
      device_profile: phone
      screen_state: player-slideshow-countdown
      alt: A photo during a slideshow with the countdown number 2 shown before the next photo
      caption: "The countdown before the next photo."
      title: "Screenshot: Slideshow countdown"
      desc: Slideshow running, countdown overlay showing 2.
  - number: 4
    id: quick-settings
    title: Change the slideshow on the fly
    text: |
      Press and hold the **Slideshow** button. The **Slideshow Settings** window opens:

      - **Default slideshow (sec.)** - a slider from 1 to 60 seconds for the current session.
      - **Play videos/audio to end** - when this is on, a video or a song in the folder plays to its end before the slideshow moves on, and no countdown is shown for it.
      - **Background Music** - tap to pick one music file from your phone to play during this slideshow; the name of the chosen track is shown below, and a clear button removes it.
    image_bookmark:
      shot_id: images.slideshow-settings-dialog
      device_profile: phone
      screen_state: slideshow-settings-dialog-open
      alt: The Slideshow Settings window with the interval slider, the Play videos and audio to end switch and the Background Music button
      caption: "The Slideshow Settings window opens with a long press on Slideshow."
      title: "Screenshot: Slideshow Settings window"
      desc: Slideshow Settings dialog over a photo, interval at 5 s, play-to-end on.
  - number: 5
    id: music
    title: Play music from a whole music folder
    text: |
      For a slideshow with music every time, go to **Settings**, the **Media** tab, section **Images, GIFs and slideshow**, turn on **Play music during slideshow**, tap **Select Music Source** and choose the resource that holds your music. From now on every slideshow of photos and GIFs plays the tracks of that resource in random order.

      You can mix places freely: photos from a network folder and music from this device, or the other way round.
    image_bookmark:
      shot_id: images.settings-slideshow-music
      device_profile: phone
      screen_state: settings-images-slideshow-music
      alt: The Images, GIFs and slideshow settings section with Play music during slideshow turned on and a music resource selected
      caption: "Play music during slideshow in Settings."
      title: "Screenshot: Slideshow music setting"
      desc: Settings, Media tab, Images section, background music on, source chosen.
  - number: 6
    id: photos-with-music
    title: Show photos while music plays
    text: |
      It also works the other way round: listen to music and let photos change in the background. Go to **Settings**, the **Media** tab, section **Audio playback, covers and visuals**, turn on **Show random photos during audio playback** and pick the resource with your photos.

      Now open a song in the [audio player](term:audio-player) and tap **Slideshow**. The music controls step aside and a full-screen photo appears, replaced by another random photo at every slideshow interval. More about backgrounds for music is in [Playing and organizing music files](page:audio.playing-and-organizing-music).
outcome: |
  Your photos change by themselves at the pace you chose, with a short countdown before each change, music in the background if you want it, and videos and songs that play to the end. The screen stays on for as long as the show runs.
tips:
  - **The slideshow stopped with "Connection to .. was lost"?** The network folder became unreachable, for example the computer went to sleep or Wi-Fi dropped. Start the slideshow again when the connection is back.
  - **The slideshow stopped with "No access to .."?** Three files in a row could not be opened. Check that the folder is still there and that the app may still read it.
  - "**Videos interrupt your photo show?** Turn off **Play videos/audio to end**, or limit the resource to pictures in its settings."
  - "**Photos are cut at the edges?** Turn off **Crop images to fill screen** in Settings, Media, Images, GIFs and slideshow - see [Viewing photos, GIFs and zoom gestures](page:images.viewer-and-gestures)."
  - "**The slideshow does not start for a music or video library?** Resources set up with the Audio Library or Video Library [resource profile](term:resource-profile) play their files one after another on their own and do not use the slideshow timer."
next_recipes:
  - title: Viewing photos, GIFs and zoom gestures
    url: page:images.viewer-and-gestures
    badge: Photos
    badge_type: image
    description: Swipe, zoom and pause animations in the image viewer.
  - title: Playing and organizing music files
    url: page:audio.playing-and-organizing-music
    badge: Audio
    badge_type: music
    description: Play music in the background, with covers, lyrics and animated backgrounds.
  - title: Display and appearance settings
    url: page:settings.display-and-appearance
    badge: Settings
    badge_type: docs
    description: Keep the screen on, choose colors and tune how the app looks.
---

A [slideshow](term:slideshow) moves from one photo to the next by itself. Choose how long each photo stays, add background music, and let the app keep the screen on - a phone or tablet becomes a photo frame in a minute.
