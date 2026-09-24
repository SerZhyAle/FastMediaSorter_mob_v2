---
page_id: vr.headset-setup-and-openxr
title: Setting Up Meta Quest and OpenXR Devices
nav_title: Headset setup and OpenXR
description: Which editions and headsets FastMediaSorter's immersive mode runs on, how to install it, how the 3D/VR master switch and the VR settings block work, what OpenXR does under the hood, the three ways to launch VR Cinema, the VR badge on APK files, the in-headset immersive browser, the controls legend, leaving immersive mode, and the built-in self-test.
category: VR and OpenXR
category_slug: vr
ticket: S2967
flavor: noLegal and VR, on a compatible headset
recipe_number: "01"
canonical_url: documentation/vr/headset-setup-and-openxr.html
why: |
  A phone screen shows you a photo or a film. A [VR headset](term:vr-headset) puts you inside it - a 360° panorama wraps all the way around, a 3D film gets real depth, and a folder of holiday photos becomes a little cinema of its own. FastMediaSorter's immersive mode turns any [Meta Quest](term:meta-quest) or other [OpenXR](https://www.khronos.org/openxr/)-compatible headset into that cinema, right from the files you already have.

  This page covers the ground floor: which edition and which headset you need, turning immersive mode on, how the app tells a real headset from an ordinary phone, and the handful of screens - VR Cinema, the file browser's VR badge, the in-headset browser, the controls legend - you will meet before you ever press play. Playback itself and the floating control panel are their own recipes, linked at the bottom.
ingredients:
  - "FastMediaSorter in the [noLegal edition](term:nolegal-edition) or the [VR edition](term:vr-edition) - immersive mode does not exist in Standard, Lite, Photos, Legacy or FOSS."
  - "A [VR headset](term:vr-headset) such as a Meta Quest, or another Android XR / OpenXR-compatible headset. A phone-in-a-box viewer does not qualify - see the disambiguation on that term."
  - "For the noLegal edition: the APK installed on the headset the same way you would install it on a phone. The VR edition installs like any other headset app."
steps:
  - number: 1
    id: which-editions-which-headsets
    title: Which edition, which headset
    text: |
      Immersive mode is a headset feature, so it lives in the two editions that run on one: the [noLegal edition](term:nolegal-edition), the general-purpose sideload build that happens to work on a headset too, and the [VR edition](term:vr-edition), built specifically for one. Every other edition - Standard, Lite, Photos, Legacy, FOSS - never shows a VR button anywhere, because the capability is not compiled in. See the full grid in the [edition](term:edition) comparison if you are choosing between them.

      On the headset itself, FastMediaSorter checks what it is running on before it shows anything immersive: a real [Meta Quest](term:meta-quest), a non-Quest Android XR headset, or a device with no XR support at all. Nothing here is a manual setting - plug in a supported headset and the VR controls simply appear; leave the app on an ordinary phone and they simply do not.
  - number: 2
    id: installing
    title: Installing on the headset
    text: |
      The VR edition installs the way any headset app does - from wherever your headset gets its apps. The noLegal edition is a sideload build, so on the headset it goes on the same way it would on a phone: copy the APK across and open it from the headset's own file manager, or use a sideloading tool if you already have one set up.

      Either way, the very first thing worth doing after install is the [welcome wizard](term:welcome-wizard): pick the **VR headset** device profile and FastMediaSorter pre-enables 3D/360° layouts and immersive controls for you, so you are not hunting through Settings before your first film.
  - number: 3
    id: master-toggle
    title: Turn immersive mode on
    text: |
      Immersive mode has one master switch: **Settings**, the **Media** tab, section **3D-VR playback and controls**. Turn it on and every VR entry point in the app - badges, menu items, the immersive browser - lights up; turn it off and they all disappear together, because the same switch also flips the app's global 3D kill-switch behind the scenes, so the two settings can never disagree.

      On a device with no XR support, the switch is disabled and the section shows an advisory instead: **Available on devices such as Meta Quest 3 and Android XR.** Pick the **VR headset** device profile in the welcome wizard and this switch is turned on for you automatically - no need to find it by hand.
    image_bookmark:
      shot_id: vr.settings-master-toggle
      device_profile: phone
      screen_state: settings-media-vr-block
      alt: The Settings Media tab with the 3D-VR playback and controls section, master toggle and Test Immersive button
      caption: "The 3D-VR playback and controls block in Settings, mirrored from the headset."
      title: "Screenshot: VR settings block"
      desc: Settings Media tab, 3D-VR playback and controls section expanded, master toggle on, Test Immersive button visible.
  - number: 4
    id: what-runs-it
    title: What is actually running the show
    text: |
      Under that switch sits [OpenXR](https://www.khronos.org/openxr/), the open standard most VR and mixed-reality headsets speak - it is what lets FastMediaSorter talk to a Meta Quest and to other Android XR headsets through the same code instead of one app build per brand. When you step into an immersive session, FastMediaSorter hands off to a dedicated screen of its own with a steady frame loop built for a headset display, so the picture stays smooth while everything else - your file list, your settings - waits for you on the other side, unchanged, for when you step back out.
  - number: 5
    id: vr-cinema-entry-points
    title: Launch VR Cinema from wherever you are
    text: |
      **Editions:** noLegal.

      FastMediaSorter calls its immersive playback session [VR Cinema](term:vr-cinema), and it is never more than one tap away from a video, wherever you found it:

      - From a video tile in the [file browser](term:file-browser), the tile's overflow menu offers **Open in VR Cinema**.
      - From a video [resource](term:resource) on the main screen, the same entry sits in that resource's own menu.
      - From inside the flat [video player](term:video-player), it is a badge and an overflow entry - covered in [spatial 3D/360 cinema and video playback](page:vr.spatial-cinema-playback).

      If the headset is busy or not reachable at the moment, FastMediaSorter says so plainly - **VR Cinema is unavailable right now** - rather than leaving you staring at nothing.
  - number: 6
    id: apk-vr-badge
    title: Spot a VR app before you install it
    text: |
      **Editions:** noLegal.

      Browsing a folder full of APK files on the headset? A small [badge](term:badge) - a circular VR headset icon - appears in the corner of any APK tile that declares itself VR-capable, so you can tell a Quest app from an ordinary one before you tap it. FastMediaSorter reads this straight out of the APK's own manifest, so the badge is only there for files it can check locally; one sitting on a slow network folder shows no badge rather than making you wait.
    image_bookmark:
      shot_id: vr.apk-badge-file-browser
      device_profile: phone
      screen_state: browse-apk-vr-badge
      alt: The file browser grid with a VR headset badge on the corner of one APK file tile
      caption: "A VR-capable APK, badged before you install it."
      title: "Screenshot: VR APK badge"
      desc: File browser grid view, several APK tiles, one carrying the small circular VR headset badge in its top-left corner.
  - number: 7
    id: immersive-browser
    title: Browse your files without taking the headset off
    text: |
      Instead of dropping you back to a flat screen every time you want the next file, FastMediaSorter can show your folder as an [immersive browser](term:immersive-browser) - a 3D grid of tiles rendered right inside the immersive scene. Point the controller ray at a tile to highlight it and pull the trigger to open it; the thumbstick walks the highlight across the grid too, left and right along a row, up and down between rows, stepping to the next page when you walk off the side. Choosing a video starts it playing immediately, in stereo when the file has it, without a detour back through the flat file browser first.
    image_bookmark:
      shot_id: vr.immersive-browser-grid
      device_profile: phone
      screen_state: xr-immersive-browser-grid
      alt: A mirrored capture of the in-headset immersive browser showing a 3D grid of file tiles with the controller ray pointing at one
      caption: "The immersive browser - a 3D grid you never have to leave VR to use."
      title: "Screenshot: Immersive browser"
      desc: Mirrored headset view, immersive browser grid of media tiles, controller ray highlighting one tile.
  - number: 8
    id: controls-legend
    title: Learn the controls, right inside the headset
    text: |
      The first time you go immersive after installing, FastMediaSorter shows a legend of every controller binding: touch controllers (Quest 2, 3 and Pro), hand tracking with controllers set aside, a Bluetooth keyboard and a Bluetooth mouse, each in its own section. Any press closes it - you are not stuck reading it - and a **HELP** button on the HUD strip brings it straight back whenever you want a refresher, or long-press the Y button (or F1 on a keyboard) to reopen it yourself.
  - number: 9
    id: leaving-immersive
    title: Leaving immersive mode
    text: |
      However you got in, one input reliably takes you back out: the B button on a touch controller, a key press on a Bluetooth keyboard, or the matching hand-tracking gesture - FastMediaSorter treats them all as the same "please exit" signal. There is a short grace period right after the first frame appears, so a stray button press while the picture is still loading will not throw you straight back out again.
  - number: 10
    id: self-test-and-fallback
    title: Something not showing up? Test it and see
    text: |
      **Editions:** noLegal.

      The same **3D-VR playback and controls** section in Settings has a **Test Immersive** button: it opens a sample 360° image in immersive mode on the spot, so you can confirm the headset and the app are talking to each other without hunting down a real 360° file first. If the session cannot start, FastMediaSorter says so directly - **Cannot start VR. Check that your headset is connected.** - and if it drops mid-session, **VR session ended unexpectedly.**

      The self-test's decoder is built to survive a low-memory device rather than crash it, and if your own device has no sample media handy, FastMediaSorter ships one anyway: a royalty-free 360° image bundled with the app, used as the fallback whenever the diagnostic needs something to show.
outcome: |
  Immersive mode is on, the app knows your headset and your edition, and you know the handful of doors into it - VR Cinema from a file, a resource or the player, the VR badge on APK tiles, and the immersive browser for finding the next thing to watch without ever taking the headset off.
tips:
  - "**Switch not doing anything?** It is disabled on hardware without XR support - the advisory text under it says so, and no amount of retrying will change that."
  - "**Chose the wrong device profile during setup?** Reopen the welcome wizard from Settings and pick VR headset again, or just flip the 3D-VR master switch by hand - both end up in the same place."
  - "**Ready to actually watch something?** See [spatial 3D/360 cinema and video playback](page:vr.spatial-cinema-playback)."
  - "**Want to know what every button on the panel does?** See [controls in the headset](page:vr.passthrough-and-controllers)."
next_recipes:
  - title: Spatial 3D/360 cinema and video playback
    url: page:vr.spatial-cinema-playback
    badge: VR
    badge_type: video
    description: How 360, 180, cylinder and stereo files actually play once you are inside.
  - title: Controls in the headset
    url: page:vr.passthrough-and-controllers
    badge: VR
    badge_type: docs
    description: The floating panel, the controllers, hand tracking and haptics.
  - title: Streams on a TV, a watch or a VR headset
    url: page:streams.tv-watch-vr-and-broadcast
    badge: Streams
    badge_type: docs
    description: Open a live video channel straight into VR Cinema.
---

Which editions and headsets run FastMediaSorter's immersive mode, how to install it, how the 3D/VR master switch and OpenXR work together, and the VR Cinema, VR badge, immersive browser, controls legend and self-test you will meet before your first film.
