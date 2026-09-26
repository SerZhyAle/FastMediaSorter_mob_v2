---
page_id: capture.edge-gestures-and-quick-access-panel
title: Screen-Edge Gestures and the Quick-Access Panel
nav_title: Edge gestures & quick-access panel
description: How to set up swipe gestures on the screen edges for screenshots, the camera, recording and launching apps, and how to open, use and edit the quick-access panel they can lead to.
category: Camera & Screen Capture
category_slug: capture
ticket: S2956
flavor: All editions (the quick-access panel and its Quick Settings tile); Standard and noLegal for the edge gestures themselves and their screenshot, camera and recording actions
recipe_number: "5"
canonical_url: documentation/capture/edge-gestures-and-quick-access-panel.html
why: |
  One short swipe from the side of the screen, over any app, can take a screenshot, snap a photo, start recording, launch another app, or open a grid of your own shortcuts. All of it is set up from the same screen, so getting comfortable with it once pays off for every other capture recipe on this site.

  The [quick-access panel](term:quick-access-panel) it can open is worth knowing on its own: a pop-up grid of tiles for apps, Android settings and FastMediaSorter's own features, reachable by gesture or by its own Quick Settings [tile](term:tile) - the one part of this page that works in every edition.
ingredients:
  - "FastMediaSorter installed. The quick-access panel and its Quick Settings tile are part of every [edition](term:edition); the [edge gesture](term:edge-gesture) that opens it, and the screenshot, camera and recording actions behind it, need the Standard or noLegal edition."
  - "The **Display over other apps** [permission](term:permission) for the gesture strip itself."
  - "For the screenshot, camera and recording actions: the same permissions used elsewhere in the app - camera, microphone, and Android's own capture consent where it applies."
steps:
  - number: 1
    id: turn-on-the-strip
    title: Turn on the gesture strip
    text: |
      Open **Settings**, the **Management** tab, and turn on **Gesture overlay** - "Show up to 4 thin gesture strips at the screen edges, over other apps." Tap **Configure gestures** below it to open the edge-gesture window.
    image_bookmark:
      shot_id: capture.settings-gesture-overlay-toggle
      device_profile: phone
      screen_state: settings-operations-gesture-overlay
      alt: The Management tab in Settings with the Gesture overlay switch turned on and the Configure gestures button below it
      caption: "Gesture overlay in the Management tab."
      title: "Screenshot: Gesture overlay setting"
      desc: Settings, Management tab, Gesture overlay section expanded.
  - number: 2
    id: four-bands-and-a-map
    title: Four bands, three directions each
    text: |
      The edge-gesture window has four tabs - **Left top**, **Left bottom**, **Right top** and **Right bottom** - one per band, each covering roughly the upper or lower stretch of its screen edge. A swipe on a band is read as one of three directions: up, inward (shown as **Right**, since it points right on the left edge and left on the right edge) or down. A small map at the top - "Edge gesture map: tap a zone direction to assign an action" - shows all twelve slots at a glance, and tapping one jumps straight to its tab.

      Turn on a band's own **Show the gesture strip** switch and a faint gray guide appears along that stretch of the real screen edge - handy while you are still learning where a band starts and ends; leave it off once you know.
    image_bookmark:
      shot_id: capture.edge-gesture-config-tabs
      device_profile: phone
      screen_state: edge-gesture-config-tabs-and-map
      alt: The edge-gesture window with its four tabs - Left top, Left bottom, Right top, Right bottom - and the edge gesture map above them
      caption: "The four bands and the gesture map."
      title: "Screenshot: Edge-gesture window"
      desc: Edge-gesture configuration window, tabs and schema visible.
  - number: 3
    id: see-what-will-fire
    title: See what a swipe will do before you let go
    text: |
      Touch down on a live band and a small legend appears showing its three directions - up, right, down - each with its icon and the action it currently runs, and the one your finger is heading toward highlighted as you move. Let go over empty space instead of a direction and the gesture cancels with nothing happening, so trying a band out costs nothing.
    image_bookmark:
      shot_id: capture.edge-gesture-direction-hint
      device_profile: phone
      screen_state: edge-gesture-direction-hint-legend
      alt: A three-row legend over the screen edge showing the Up, Right and Down actions for the band being touched, with the Right action highlighted
      caption: "The direction legend, shown while touching a live band."
      title: "Screenshot: Gesture direction hint"
      desc: Edge gesture in progress, direction legend visible, one row highlighted.
  - number: 4
    id: assign-an-action
    title: Give each direction a job
    text: |
      In a band's tab, set **Down gesture action**, **Right gesture action** and **Up gesture action** independently, each opening the same **Gesture action** picker. The list covers a lot of ground: the [screenshot](term:screenshot) actions from [Taking screenshots and what happens next](page:capture.screenshot-annotation), camera actions (take a photo, and send it, edit it, or run OCR translation on it), starting or stopping [screen recording](term:screen-recording) or a quick voice note, launching a chosen app, opening the quick-access panel, and a long list of device shortcuts such as the flashlight, volume and brightness. Leave a direction on **Do not use** and that swipe does nothing.
    callout:
      type: tip
      title: Recording and audio in depth
      text: "Screen recording, video and voice-note gestures get their own walkthrough in [Recording the screen, video and sound](page:capture.screen-recording-and-audio)."
  - number: 5
    id: open-app-or-come-forward
    title: Bring FastMediaSorter forward without capturing anything
    text: |
      Set a direction to **Launch a chosen app** and pick any installed app - that swipe opens it, or brings it to the front if it is already running, with no screenshot and no consent prompt along the way. Leave no app chosen and the gesture opens FastMediaSorter itself instead.
  - number: 6
    id: open-the-panel
    title: Open the quick-access panel
    text: |
      Set a direction to **Open quick-access panel** and that swipe opens a fixed grid of large tiles - 3x5 in portrait, 5x3 in landscape - for one-tap launching, again with no screenshot or consent prompt. A tile you have not filled in yet shows **Empty - tap to add**.
    image_bookmark:
      shot_id: capture.quick-access-panel-grid
      device_profile: phone
      screen_state: quick-access-panel-grid-open
      alt: The quick-access panel open over another app, showing a grid of large tiles for apps, settings and features, with one empty tile
      caption: "The quick-access panel, open over another app."
      title: "Screenshot: Quick-access panel"
      desc: Quick-access panel open, grid of tiles visible, one empty slot.
  - number: 7
    id: reach-it-without-a-gesture
    title: Reach it without a gesture at all
    text: |
      Every edition, including the ones without screen capture, can add a **Quick launch** tile to the Android Quick Settings shade - pull it down, tap Edit, and drag it in. Tapping that tile opens the same quick-access panel as the gesture, so it works even where the edge gesture itself is not offered.
  - number: 8
    id: fill-the-panel
    title: Fill the panel with your own tiles
    text: |
      Open the panel's **Edit** screen - or **Edit quick-access panel** in the edge-gesture window - and tap any tile, filled or empty, to add, **Move**, **Replace** or **Remove** it. A tile can point to an **Android app**, an **Android OS settings** screen (Wi-Fi, Bluetooth, Display, Sound, Battery and more, whichever exist on your phone), a **FastMediaSorter feature** (the calculator, the mini-game, [Camera OCR](term:camera-ocr) translation, Streams, Favorites and others), or a **FastMediaSorter [resource](term:resource)**. The panel ships already filled in with the app's own features first and Android settings after, leaving the remaining slots open for whatever you add; **Reset panel** puts that starting layout back at any time.
    image_bookmark:
      shot_id: capture.quick-access-panel-edit
      device_profile: phone
      screen_state: quick-access-panel-edit-screen
      alt: The Edit panel screen with Move, Replace and Remove options for a selected tile, and the Add to panel choices below it
      caption: "Editing a tile on the quick-access panel."
      title: "Screenshot: Edit panel screen"
      desc: Edit panel screen, one tile selected, action row visible.
  - number: 9
    id: your-programs-on-the-panel
    title: Your programs, on the panel too
    text: |
      **Quick capture**, **Voice recording**, **Screen video recording** and **Download by link** - the same items already in the main window's Programs menu - can sit on the panel as their own tiles, so a swipe or a Quick Settings tap reaches them directly. Screen video recording and Download by link still need the Standard or noLegal edition, the same as everywhere else on this page; the camera and voice tiles are not tied to screen capture and follow their own, wider set of editions.
outcome: |
  A swipe at the edge of the screen can now take a screenshot, snap a photo, start or stop a recording, launch an app, or open a panel of your own shortcuts - and that panel is one Quick Settings tap away in every edition, filled with exactly the apps, settings and features you put there.
tips:
  - "**Long lists are easy to pick from.** The pickers of the quick-access panel - an app, a feature, a system shortcut or a resource - have a search box for long lists, show an icon at the start of each row and let you choose one item the same way everywhere."
  - "**A swipe fired the wrong action?** The live legend from step 3 shows exactly what each direction is set to before you commit - touch and hold instead of swiping through."
  - "**One permission covers the whole strip.** Turning off **Display over other apps** for FastMediaSorter switches every band off at once; turning it back on does not re-enable them by itself - **Gesture overlay** does, and comes back on its own after a restart if it was on and the permission still holds."
  - "**Taking a photo without a camera screen?** The photo gesture actions, and everything else the in-app camera can do, are in [Taking quick photos and video snaps](page:capture.quick-photo-capture)."
next_recipes:
  - title: Taking screenshots and what happens next
    url: page:capture.screenshot-annotation
    badge: Gestures
    badge_type: other
    description: Every after-capture action a screenshot gesture can run.
  - title: Recording the screen, video and sound
    url: page:capture.screen-recording-and-audio
    badge: Gestures
    badge_type: other
    description: Start and control a screen recording from the same gesture family.
  - title: Taking quick photos and video snaps
    url: page:capture.quick-photo-capture
    badge: Camera
    badge_type: other
    description: What the in-app camera can do once a gesture opens it.
---

Set up to twelve swipe gestures across four screen-edge bands for screenshots, the camera, recording and launching apps, and open the quick-access panel they can lead to - by gesture in the Standard and noLegal editions, or by its own Quick Settings tile in every edition.
