---
page_id: capture.camera-lenses-zoom-and-profiles
title: Lenses, Zoom, Shooting Profiles and Camera Settings
nav_title: Lenses, zoom and shooting profiles
description: How to pick a lens, zoom range and shooting profile for the app's camera, open the full Camera settings dialog, and check exactly what your phone's cameras can do.
category: Камера и запись экрана
category_slug: capture
ticket: S2956
flavor: All editions for lenses, zoom range, shooting profiles, aspect ratio, per-lens memory and the hardware report; the zoom slider and the full Camera settings dialog need Standard, Lite, Photos or Legacy
recipe_number: "2"
canonical_url: documentation/capture/camera-lenses-zoom-and-profiles-ru.html
why: |
  A phone with three or four lenses can do more than one kind of photo. This page is about pointing the [camera](term:camera) at the lens, zoom and settings you actually want - a wide shot with the ultra-wide, a close-up with macro, a properly exposed shot at night - instead of whatever a single default happens to pick, and about where to look when you want to know exactly what your phone's cameras can do.

  If you haven't opened the camera yet, [Taking Quick Photos and Video Snaps](page:capture.quick-photo-capture) covers the viewfinder basics, gestures and video recording first.
ingredients:
  - "FastMediaSorter in any [edition](term:edition). The zoom slider and the full Camera settings dialog need Standard, Lite, Photos or Legacy."
  - "A phone with more than one rear lens gets the most out of this page, but a single-lens phone still gets shooting profiles, aspect ratio and the hardware report."
  - "Camera permission, already covered if you've used the camera before."
steps:
  - number: 1
    id: lenses-and-zoom
    title: The lenses and zoom range are your phone's own
    text: |
      The camera reads what your phone's hardware can actually do and offers every physical lens it finds - front, ultra-wide, wide, tele, macro - with each lens's true zoom floor (including below 1x on an ultra-wide), a working macro mode, and photos at the sensor's full resolution. Nothing here is guessed or capped to a "safe" number.

      When the lens you're using has no zoom range of its own - the ultra-wide is the usual case - the zoom row shows one pill per rear lens instead, in familiar values such as 0.5 / 1 / 3, so a tap switches you straight back to a lens you can zoom on.

      *Standard, Lite, Photos and Legacy editions.* Above the shutter, a zoom row also offers ready presets from 0.5x to 30x plus a draggable slider for anything in between. Night mode used to have its own button here; it now lives in the Shooting profile menu, covered next.
    image_bookmark:
      shot_id: capture.camera-zoom-presets-and-lenses
      device_profile: phone
      screen_state: camera-lenses-zoom-presets-and-pills
      alt: The camera zoom row showing presets and a slider, with lens pills for a fixed-range rear lens
      caption: "Zoom presets, the slider, and lens pills."
      title: "Screenshot: Zoom and lenses"
      desc: In-app camera, zoom row with presets and slider, ultra-wide lens active showing rear-lens pills.
  - number: 2
    id: shooting-profiles
    title: Pick a shooting profile
    text: |
      A button reads **"Shooting profile: Normal"**; tap it to open a menu of scenarios, each with its own icon: **Normal**, **Night**, **Portrait**, **Selfie**, **Macro**, **Sport** and, where the device supports it, **Document**. Only the profiles your phone can actually deliver are listed.

      Choosing one is exclusive - tapping the active profile again turns it off and back to Normal. **Macro** jumps straight to the dedicated close-focus lens, **Selfie** flips to the front camera, **Night** and **Portrait** use the phone's own camera modes, and **Sport** keeps the exposure short so motion freezes; a warning notes that frames get darker in low light because of it. Changing lens manually while a profile is active returns the camera to Normal.

      **Document** points the main back lens straight ahead at zoom 1x with the torch on - built for photographing flat pages and screens rather than a scene.

      If you move exposure or white balance off automatic while shooting Normal, the button changes to read **"Manual"** instead, so you always know the camera isn't picking those for you anymore. On a phone with no profile menu at all, the plain settings button reads **"Camera settings - manual mode"** in the same situation.
    image_bookmark:
      shot_id: capture.camera-profile-menu
      device_profile: phone
      screen_state: camera-shooting-profile-menu
      alt: The camera shooting profile menu open, showing Normal, Night, Portrait, Selfie, Macro, Sport and Document with icons
      caption: "The Shooting profile menu."
      title: "Screenshot: Shooting profile menu"
      desc: In-app camera, Shooting profile menu open, Normal currently selected, all supported profiles listed with icons.
  - number: 3
    id: aspect-ratio
    title: Choose the shape of the frame
    text: |
      *Standard, Lite, Photos and Legacy editions.* In **Camera settings**, **Aspect ratio** offers **4:3**, **16:9** and **Full screen**. Whichever you pick changes both the live viewfinder and the saved file together - it's not just a crop drawn over a wider preview. The choice is remembered the next time you open the camera, and the default is 16:9. For video, **Full screen** behaves as 16:9, since a video file needs a fixed shape.
    image_bookmark:
      shot_id: capture.camera-aspect-ratio
      device_profile: phone
      screen_state: camera-aspect-ratio-picker
      alt: The Camera settings dialog with the Aspect ratio row open, showing 4:3, 16:9 and Full screen options
      caption: "Aspect ratio: 4:3, 16:9 or Full screen."
      title: "Screenshot: Aspect ratio picker"
      desc: Camera settings dialog, Aspect ratio row expanded, 16:9 selected.
  - number: 4
    id: camera-settings-dialog
    title: Open the full Camera settings dialog
    text: |
      *Standard, Lite, Photos and Legacy editions.* Tap the gear icon for **Camera settings**: **Self-timer**, **Grid**, **Aspect ratio**, **Resolution**, **Exposure**, **White balance** (with presets such as **Auto**, **Daylight**, **Cloudy** or **Fluorescent** where the phone supports them), **Manual ISO and shutter**, and **HDR**. Only the rows your hardware can actually deliver show up - there's nothing here to tap that will just fail.

      You can rotate the phone, switch the app's theme or language, or change the system font size with this dialog open, and it comes right back exactly as you left it instead of closing on you.
    image_bookmark:
      shot_id: capture.camera-settings-dialog
      device_profile: phone
      screen_state: camera-settings-dialog-full
      alt: The full Camera settings dialog listing Self-timer, Grid, Aspect ratio, Resolution, Exposure, White balance, Manual ISO and shutter, and HDR
      caption: "The Camera settings dialog."
      title: "Screenshot: Camera settings dialog"
      desc: Camera settings dialog open over the viewfinder, all hardware-supported rows visible.
  - number: 5
    id: per-lens-memory
    title: Each lens remembers its own settings
    text: |
      Switch to the tele lens, dial in a warmer white balance and a touch of manual exposure, then switch to the ultra-wide and back - your tele settings, profile, white balance and manual ISO/shutter are exactly where you left them. Every lens keeps its own set, restored the moment you return to it, and the memory survives closing and reopening the app. If a lens disappears from your phone (a new device, for instance), its saved settings quietly go with it.
  - number: 6
    id: hardware-report
    title: Look up exactly what your cameras can do
    text: |
      Open the Programs menu, then **System information**, then the **"Cameras"** section. For every camera the phone declares - including physical sub-lenses grouped under one logical camera - you get facing, focal length, sensor size, active array, zoom range, closest focus distance and the largest photo size available, in the phone's raw numbers.

      Nothing is rounded off or simplified: the same raw values from two different phones sit side by side well enough to explain why one takes a better night shot than the other, and it's the fastest way to tell a real hardware limit from something worth reporting as a bug.
    image_bookmark:
      shot_id: capture.camera-hardware-report
      device_profile: phone
      screen_state: camera-hardware-report-system-info
      alt: The System information screen with the Cameras section expanded, showing facing, focal length, sensor size and zoom range for each camera
      caption: "Camera hardware, in raw numbers."
      title: "Screenshot: Camera hardware report"
      desc: System information screen, Cameras section expanded, one rear and one front camera listed with full detail rows.
outcome: |
  Whichever lens fits the shot - wide, ultra-wide, tele or macro - is one tap away, your settings for that lens are exactly as you left them next time, and System information can tell you in plain numbers what each of your phone's cameras can actually do.
tips:
  - "**Flattening a page or a screen?** The **Document** shooting profile sets the main lens, zoom 1x and the torch for you in one tap, instead of fumbling with exposure and zoom by hand."
  - "**Comparing two phones?** The hardware report's raw numbers are meant to be compared directly - focal length, sensor size and zoom range side by side tell you more than a marketing spec sheet."
  - "**New here?** [Taking Quick Photos and Video Snaps](page:capture.quick-photo-capture) covers opening the camera, the viewfinder, gestures and video recording."
next_recipes:
  - title: Taking Quick Photos and Video Snaps
    url: page:capture.quick-photo-capture
    badge: Camera
    badge_type: other
    description: The viewfinder, gestures, video recording and the watch's live view of the phone camera.
  - title: Taking Photos, Videos and Voice Notes Straight into a Folder
    url: page:storage.capture-to-destination
    badge: Storage
    badge_type: other
    description: Take a photo or video from inside a folder so it saves right there instead of the camera roll.
  - title: Taking Screenshots and What Happens Next
    url: page:capture.screenshot-annotation
    badge: Camera
    badge_type: other
    description: Capture the screen itself and send it straight to editing, translation or another app.
---

Pick the lens, zoom range and shooting profile that fit the shot, open the full Camera settings dialog when you need manual control, and use the System information report to see exactly what your phone's cameras can do.
