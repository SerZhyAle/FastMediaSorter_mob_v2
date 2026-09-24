---
page_id: capture.quick-photo-capture
title: Taking Quick Photos and Video Snaps
nav_title: Quick photos and video snaps
description: How to open the app's own camera from the Programs menu, use its viewfinder, gestures and video recording, and even watch through the phone's camera from a paired watch.
category: Camera & Screen Capture
category_slug: capture
ticket: S2956
flavor: All editions for the Quick capture menu and tap-to-focus; the fuller viewfinder with zoom presets, Send To and Camera settings needs Standard, Lite, Photos or Legacy; video, the Photo/Video switch and the Samsung-style gestures are Standard only; the watch live view needs Standard or noLegal
recipe_number: "1"
canonical_url: documentation/capture/quick-photo-capture.html
why: |
  Your phone already has a camera app, so why does FastMediaSorter carry its own? Because a quick snap deserves to land straight in your Pictures folder without switching apps, and because the app's [camera](term:camera) can do a few things the stock one never will - like showing you exactly what you're about to save before you save it, or letting your watch peek through it.

  Open it from the Programs menu, take the photo or the clip, and you're back in the app a second later. This page is the tour of the viewfinder, the gestures, video recording and the watch trick. The deeper controls - lenses, zoom range, shooting profiles and the full settings dialog - are one page over, in [Lenses, Zoom, Shooting Profiles and Camera Settings](page:capture.camera-lenses-zoom-and-profiles).
ingredients:
  - "FastMediaSorter in any [edition](term:edition). The fuller viewfinder (zoom presets, Send To, Camera settings) needs Standard, Lite, Photos or Legacy; video recording and the Samsung-style gestures need Standard."
  - "Camera permission, and microphone permission if you plan to record video with sound. Android asks the first time you need either."
  - "For the last step only: a paired [watch](term:watch) and a phone on the Standard or noLegal edition."
steps:
  - number: 1
    id: open-quick-capture
    title: Open Quick capture from the Programs menu
    text: |
      On the main screen, open the [Programs menu](term:program). Near the top you see **"Quick capture"** and **"Voice recording"** - tap either one and the camera or the recorder opens right away, no stutter while it starts up.

      Photos, videos and voice notes each save straight into the phone's standard Pictures, Movies or Recordings folder. This is different from **Capture with camera** in the [file browser](term:file-browser)'s [three-dots menu](term:three-dots-menu), which saves into whatever [resource](term:resource) you have open instead - see [Taking Photos, Videos and Voice Notes Straight into a Folder](page:storage.capture-to-destination) for that version.

      Photo, video and voice capture are each their own switch in Settings, so an edition without video simply doesn't show that row. Press and hold a row to **Remove** it from the menu if you never use it.
    image_bookmark:
      shot_id: capture.main-menu-quick-capture
      device_profile: phone
      screen_state: main-programs-menu-quick-capture
      alt: The main screen Programs menu with Quick capture and Voice recording rows near the top
      caption: "Quick capture and Voice recording in the Programs menu."
      title: "Screenshot: Programs menu"
      desc: Main screen, Programs menu open, the Quick capture and Voice recording rows visible near the top.
  - number: 2
    id: viewfinder-tour
    title: Get to know the viewfinder
    text: |
      *Standard, Lite, Photos and Legacy editions.* Open the camera and the viewfinder appears immediately - it no longer needs a beat to warm up the lens first.

      A zoom row sits under the frame with ready presets from 0.5x to 30x plus a slider you can drag for anything in between; at a lens's own maximum you see **"Zoom 3x, optical limit"** instead of a number that would just crop in digitally. The controls stay fixed in place while you turn the phone - only their icons rotate to match - and a small label tells you exactly where the shot is about to be saved.

      If the app opened the camera for something specific, such as [recognizing text](page:tools.ocr-text-recognition), a line above the zoom row names the scenario, for example **"Text recognition"**. For a plain photo or video, nothing is shown there.
    image_bookmark:
      shot_id: capture.camera-quick-snap
      device_profile: phone
      screen_state: camera-viewfinder-standard-controls
      alt: The in-app camera viewfinder with the always-visible zoom row, fixed controls and the save-destination label
      caption: "The camera viewfinder, ready to shoot."
      title: "Screenshot: Camera viewfinder"
      desc: In-app camera open on a resource, zoom row and fixed controls visible, no dialog open.
  - number: 3
    id: mode-and-gestures
    title: Switch mode and move around with gestures
    text: |
      *Standard edition.* A **Photo** / **Video** switch sits right in the viewfinder, and the shutter itself changes shape depending on which mode you're in - and again once a recording starts - so it's always obvious what a tap will do.

      The viewfinder reads gestures the way a modern phone camera does: pinch to zoom in or out, double-tap to jump between two zoom levels, swipe up or down to switch lens, and swipe left or right to switch between **Photo** and **Video**. While it swaps lenses you see **"Switching.."** for a moment.
    image_bookmark:
      shot_id: capture.camera-mode-switch-gestures
      device_profile: phone
      screen_state: camera-gestures-mode-switch
      alt: The in-app camera with the Photo and Video mode switch highlighted over the viewfinder
      caption: "The Photo / Video switch in the viewfinder."
      title: "Screenshot: Mode switch"
      desc: In-app camera, Photo/Video switch visible, Video side highlighted.
  - number: 4
    id: tap-to-focus
    title: Tap to focus and set the exposure
    text: |
      Tap anywhere on the viewfinder and the camera focuses and meters exposure right at that spot, with a focus ring drawn exactly where you tapped - not somewhere near it. This works the same way in every edition.
    image_bookmark:
      shot_id: capture.camera-tap-to-focus
      device_profile: phone
      screen_state: camera-tap-to-focus-ring
      alt: The camera viewfinder with a focus ring drawn at the point the user tapped
      caption: "Tap the frame to focus and meter exposure there."
      title: "Screenshot: Tap to focus"
      desc: In-app camera, focus ring shown at a tapped point off-center in the frame.
  - number: 5
    id: record-video
    title: Record a video, with sound or without
    text: |
      *Standard edition.* Switch to **Video** and a **Microphone** toggle appears; turn it off and the control bar shows **"Recording without sound"** so you always know whether audio is being captured.

      While recording, a timer counts up on screen with its own Pause and Resume, so a video call ringing in the middle of a shot doesn't force you to start over - stop, resume, and both halves land in the same file.
    image_bookmark:
      shot_id: capture.camera-video-recording
      device_profile: phone
      screen_state: camera-video-recording-timer
      alt: The camera in video mode recording, with the running timer and the Pause control visible
      caption: "Recording video, timer running."
      title: "Screenshot: Video recording"
      desc: In-app camera, Video mode, recording in progress, timer and Pause/Resume control visible.
  - number: 6
    id: multi-shot-and-accuracy
    title: Shoot several photos in a row, accurately
    text: |
      *Standard edition.* The camera doesn't close after one shot the way many camera apps do - it stays open so a burst of photos is just tap, tap, tap. A thumbnail of your last shot appears on the control bar; tap **"View last capture"** and it opens in the app's [player](term:player) right away, so you can check focus and framing without leaving the camera.

      Whatever the viewfinder shows at the moment you press the shutter is what gets saved - true at any zoom, even past a lens's own optical limit, and for every aspect ratio you pick, in every edition.

      On Standard, Lite, Photos and Legacy, when your chosen aspect is narrower than the full preview, a frame drawn over the viewfinder marks exactly what will be kept, so there's no surprise cropping later. Your aspect choice is remembered the next time you open the camera, and digital zoom is cropped into the saved video file itself, not just shown zoomed on screen.
    image_bookmark:
      shot_id: capture.camera-multi-shot-thumbnail
      device_profile: phone
      screen_state: camera-multi-shot-last-capture
      alt: The camera viewfinder with the last-capture thumbnail shown on the control bar after taking a photo
      caption: "The last-capture thumbnail, ready to open."
      title: "Screenshot: Last capture thumbnail"
      desc: In-app camera, control bar with the last-capture thumbnail visible after a shot.
  - number: 7
    id: send-to-and-settings
    title: Send the shot on, or open Camera settings
    text: |
      *Standard, Lite, Photos and Legacy editions.* Tap **"Send to.."** to hand your latest capture to another app right from the camera, without hunting for it in a folder first.

      The gear icon opens **Camera settings**: self-timer, grid, aspect ratio, resolution, exposure, white balance, manual ISO and shutter, and HDR - only the rows your phone's hardware actually supports. The full tour of that dialog, plus lenses and shooting profiles, is in [Lenses, Zoom, Shooting Profiles and Camera Settings](page:capture.camera-lenses-zoom-and-profiles).

      One more toggle lives in **Settings**, the **Management** tab, under **Photography**: **Geotag photos** - "Save GPS location in photos taken with the in-app camera. Needs location permission; off by default." It never holds up the shutter waiting for a GPS fix - if none is available yet, the photo just saves without one.
    image_bookmark:
      shot_id: capture.camera-send-to-settings
      device_profile: phone
      screen_state: camera-send-to-and-settings-gear
      alt: The camera control bar with the Send to.. action and the Camera settings gear icon
      caption: "Send to.. and the Camera settings gear."
      title: "Screenshot: Send To and settings gear"
      desc: In-app camera control bar, Send to.. and the settings gear icon both visible.
  - number: 8
    id: watch-live-view
    title: "Look through the phone camera from your watch"
    text: |
      *Standard and noLegal editions, with a paired [Wear OS](term:wear-os) watch.* On the watch, open **"Phone camera"** - "Watch what your phone sees, over Wi-Fi" - and tap **Start**. The watch shows "Asking the phone.." while a notification titled "Your watch wants to see this camera" appears on the phone; tap **Allow** there.

      The watch now shows the live picture from the phone, plus a **"Cameras"** list so you can switch between the phone's back and front camera without touching the phone at all. Tap **Stop** on either device to end the session.

      If it can't connect, the watch says why instead of just hanging - for example **"Turn on Wi-Fi on the watch"**, **"This Wi-Fi is too slow for video"** or **"The request was declined on the phone"**. Pairing the watch itself is covered in [Installing on Wear OS and Pairing with Phone](page:wear.installation-and-pairing).
    image_bookmark:
      shot_id: capture.wear-phone-camera-live
      device_profile: watch
      screen_state: wear-phone-camera-live-view
      alt: The Wear OS watch showing a live view from the phone's camera with a Cameras list and a Stop control
      caption: "The phone's camera, live on the watch."
      title: "Screenshot: Phone camera on the watch"
      desc: Watch screen, phone camera live view active, Cameras list and Stop control visible.
outcome: |
  The quick photo is already in your camera roll, the clip you recorded is in Movies, and you never had to leave FastMediaSorter to take either one. On the watch, you got a look through the phone's lens without picking the phone up at all.
tips:
  - "**Your files are already named for you.** Captures get a source prefix plus the exact time - `photo_260924_143210.jpg`, `screenshot_..`, `audio_..`, `video_..`, `screen_video_..` - and if two would land with the same name, the second one gets \" (2)\" added automatically. Nothing overwrites anything by accident."
  - "**The quick-access panel can start these too.** If you've set up the app's on-screen quick-access panel, quick photo capture, voice recording and screen recording can live there as one-tap tiles - see [Screen-Edge Gestures and the Quick-Access Panel](page:capture.edge-gestures-and-quick-access-panel)."
  - "**Want the shot saved somewhere specific instead of the camera roll?** That's the file browser's own **Capture with camera**, covered in [Taking Photos, Videos and Voice Notes Straight into a Folder](page:storage.capture-to-destination)."
next_recipes:
  - title: Lenses, Zoom, Shooting Profiles and Camera Settings
    url: page:capture.camera-lenses-zoom-and-profiles
    badge: Camera
    badge_type: other
    description: Pick the right lens, dial in a shooting profile, and open the full Camera settings dialog.
  - title: Taking Screenshots and What Happens Next
    url: page:capture.screenshot-annotation
    badge: Camera
    badge_type: other
    description: Capture the screen itself and send it straight to editing, translation or another app.
  - title: Recording the Screen, Video and Sound
    url: page:capture.screen-recording-and-audio
    badge: Camera
    badge_type: other
    description: Record everything happening on screen, with microphone audio, from the Programs menu.
---

Open the app's own camera from the Programs menu for a quick photo or video snap, get to know its viewfinder and gestures, record video with a timer and Pause/Resume, and even watch through your phone's camera from a paired watch.
