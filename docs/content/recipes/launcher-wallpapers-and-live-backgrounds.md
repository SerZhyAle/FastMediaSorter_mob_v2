---
page_id: launcher.wallpapers-and-live-backgrounds
title: Choosing a Desktop Wallpaper - Animated Waves, a Still Frame, Your Photo or a Live Camera
nav_title: Desktop wallpaper
description: How to pick one of the six wallpapers of the launcher desktop, use your own picture or animated GIF, show a live camera picture, and tune brightness, speed, particles and colors.
category: "Launcher: Desktop"
category_slug: launcher
ticket: S2958
flavor: Standard and noLegal
recipe_number: "02"
canonical_url: documentation/launcher/wallpapers-and-live-backgrounds.html
why: |
  The wallpaper is the first thing you see on the [desktop](term:desktop), so it should suit the place: calm and still on a bedside tablet, lively on a kitchen display, a family photo on a photo frame, or even the view from the camera on a device standing by the window.

  All wallpaper choices sit on one screen, each with a sentence saying what it does, and the app looks after the battery for you.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) desktop open - see [Your launcher desktop](page:launcher.desktop-grid-and-icons)."
  - "For your own wallpaper: a picture or an animated GIF on the device."
  - "For the camera wallpapers: a device with a camera, and your permission for the app to use it."
steps:
  - number: 1
    id: open-screen
    title: Open the Screens and wallpaper screen
    text: |
      Touch and hold an empty square on the desktop and tap **Wallpaper**. The **Screens and wallpaper** screen opens.

      You can reach the same screen from the launcher settings: **Settings**, **General**, **System launcher settings**, the **Appearance** group, **Screens and wallpaper**.
    image_bookmark:
      shot_id: launcher.wallpaper-screen
      device_profile: phone
      screen_state: launcher-screens-and-wallpaper
      alt: The Screens and wallpaper screen with the What is drawn list of six wallpaper modes, each with a one-line explanation
      caption: "Screens and wallpaper, with the six modes."
      title: "Screenshot: Screens and wallpaper"
      desc: The Screens and wallpaper screen scrolled to What is drawn, Waves and particles selected.
  - number: 2
    id: pick-mode
    title: Pick what is drawn
    text: |
      Under **What is drawn** choose one of six modes:

      - **Waves and particles** - colored waves with drifting particles, moving the whole time the desktop is open. This is the starting choice.
      - **Striped wallpaper** - the same waves, but frozen. A fresh frame is drawn each time you come back to the desktop, and nothing moves after that.
      - **Empty** - no wallpaper at all; the desktop shows the plain color of your [color theme](term:color-theme).
      - **My image** - your own picture, still or animated, cropped to fill the screen.
      - **Camera** - a live picture from a camera, refreshed all the time. This mode uses the most battery.
      - **Instant photo** - the camera takes one photo each time you come back to the desktop and is released straight away.
  - number: 3
    id: own-picture
    title: Use your own picture or animated GIF
    text: |
      Select **My image**, then tap **Choose** under **Source** and pick a picture. A preview shows what you chose.

      The app keeps its own copy of the picture, so the wallpaper stays even if you later move or delete the original file. Animated GIFs keep moving on the desktop. If a file cannot be used, the screen says **Could not use this image** and the previous wallpaper stays.
    image_bookmark:
      shot_id: launcher.wallpaper-my-image
      device_profile: phone
      screen_state: launcher-desktop-own-image
      alt: The launcher desktop over a user's own photo, with shortcut icons and captions readable on top of it
      caption: "Your own photo as the desktop wallpaper."
      title: "Screenshot: Own picture wallpaper"
      desc: Launcher desktop with My image selected, a landscape photo as the wallpaper, portrait.
  - number: 4
    id: camera
    title: Show a live camera picture
    text: |
      Select **Camera** or **Instant photo**. If the device has several cameras, a list **Choose a camera** appears so you can pick the lens; **Choose** under **Source** changes it later. The first time, Android asks whether the app may use the camera - allow it.

      These two modes are offered only on devices that have a camera. The camera is released as soon as the desktop leaves the screen. If there is no camera or you decline the permission, the desktop quietly goes back to **Waves and particles**.
    callout:
      type: warning
      title: Battery
      text: "**Camera** keeps the camera running while the desktop is visible. On a device that runs on battery, **Instant photo** gives you a fresh picture at a fraction of the cost."
  - number: 5
    id: tune
    title: Tune brightness, speed, particles and colors
    text: |
      Under **Appearance** on the same screen:

      - **Brightness** - how strongly the wallpaper is drawn. Lower values leave more contrast for icons and captions.
      - **Animation speed** - how fast the waves move.
      - **Particle density** - how many particles drift over the waves. At zero only the waves are drawn.
      - **Animation color palette** - **Dynamic (multicolor)**, **Greenish**, **Pinkish** or **Blueish**. The same palette colors the animated background of the audio player.
    image_bookmark:
      shot_id: launcher.wallpaper-appearance-sliders
      device_profile: phone
      screen_state: launcher-wallpaper-appearance
      alt: The Appearance block of Screens and wallpaper with the Brightness, Animation speed and Particle density sliders and the color palette choice
      caption: "Brightness, speed, particles and palette."
      title: "Screenshot: Wallpaper appearance"
      desc: Screens and wallpaper scrolled to Appearance, sliders at their default values.
  - number: 6
    id: see-through
    title: Let the wallpaper show through the squares
    text: |
      [Shortcuts](term:shortcut) on the desktop have no card behind them: the wallpaper shows through the whole grid, and each icon and caption carries a thin outline so it stays readable over a bright, dark or busy picture. The whole square stays tappable.

      [Gadgets](term:gadget) keep a light card. To make that card more or less solid, open the launcher settings, the **Appearance** group, **Widget backdrop opacity**, and pick from **0% (Transparent)** to **100% (Opaque)**; **25% (Default)** is the starting value. While you arrange the desktop every square shows a full card, so its edges are easy to see.
outcome: |
  The desktop shows the wallpaper that suits the place - moving, still, your own photo or the camera view - at the brightness and colors you like, with every icon still easy to read.
tips:
  - "**The waves stopped moving?** The wallpaper stops on its own when the system power saver is on or the battery runs low, and a live camera falls back to the waves. The **Battery** block at the bottom of the screen explains this and shows the current state: **Now: animating**, **Now: reduced to save power** or **Now: stopped to save power**."
  - "**Want motion without the battery cost?** **Striped wallpaper** looks like the animation but draws only one frame each time you return."
  - "**Your wallpaper choices travel with you.** They are part of the settings backup, so restoring a backup on a new device brings them back - see [Backing up and restoring settings](page:general.backup-and-restore)."next_recipes:
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
  - title: Arranging the desktop - sections, screens, swipes and the lock
    url: page:launcher.desktop-folders-and-pages
    badge: Launcher
    badge_type: docs
    description: Move, resize and fold, spread items over several screens and lock the layout.
  - title: Creating photo slideshows
    url: page:images.slideshow-and-transitions
    badge: Photos
    badge_type: image
    description: Want many photos instead of one? Let them change by themselves.
---

The launcher desktop can draw six kinds of wallpaper, from the moving waves and particles of the FastMediaSorter brand to your own photo or a live camera view. This page shows how to choose one and tune it.
