---
page_id: vr.passthrough-and-controllers
title: "Controls in the Headset: the Floating Panel, Controllers and Hands"
nav_title: Headset controls
description: The head-locked status banner, the floating control panel and how it adapts to what is playing, the seek bar, the session settings panel, hiding and summoning the panel, thumbstick seeking with the grip modifier, controller haptics and hand tracking, and the FPS overlay.
category: VR and OpenXR
category_slug: vr
ticket: S2967
flavor: noLegal and VR, on a compatible headset
recipe_number: "03"
canonical_url: documentation/vr/passthrough-and-controllers.html
why: |
  There is no touchscreen inside a headset. Every button [VR Cinema](term:vr-cinema) offers - play, seek, subtitles, the depth of a 3D film - has to live somewhere you can see it without breaking the scene and reach it without a keyboard or a mouse. FastMediaSorter's answer is a [panel](term:panel) that floats in front of you, aimed at with a controller ray or a pinch of the fingers, that shows only the buttons that make sense for whatever is currently playing and gets out of the way the moment you stop looking at it.

  This page is the controls themselves, end to end: the banner that always tells you what you are watching, the floating panel and its buttons, the settings panel behind the menu button, and the controller, thumbstick, haptic and hand-tracking input that drives all of it. Getting into VR Cinema in the first place is covered in [spatial 3D/360 cinema and video playback](page:vr.spatial-cinema-playback).
ingredients:
  - "Everything from [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr) and [spatial 3D/360 cinema and video playback](page:vr.spatial-cinema-playback): the headset, the edition, and something already playing in VR Cinema."
  - "A pair of touch controllers for the fullest set of controls; hand tracking and a Bluetooth keyboard or mouse work too, with a smaller set of gestures."
steps:
  - number: 1
    id: head-locked-banner
    title: The banner that always tells you what is playing
    text: |
      A banner stays locked to your head rather than to the scene, so it is always readable no matter which way you turn: the current filename, the projection FastMediaSorter detected - 360°, 180°, cylinder or flat - and the stereo layout it is using. If something about the file goes wrong, the same banner turns red instead of silently vanishing, so you always know at a glance whether what you are looking at is actually working.
  - number: 2
    id: floating-panel
    title: The floating control panel
    text: |
      The main controls live on a world-locked [panel](term:panel) that hangs in front of you rather than moving with your head: point the controller ray at a button to highlight it, and pull the trigger to click it, the same ray-and-trigger motion the [immersive browser](term:immersive-browser) uses. Play/Pause, Previous, Next, and sliders for Volume and depth all sit here, and the panel's own **PREV**/**NEXT** buttons walk the whole [resource list](term:resource-list) you arrived from, not just the one file.
    image_bookmark:
      shot_id: vr.floating-control-panel
      device_profile: phone
      screen_state: xr-immersive-hud-panel
      alt: A mirrored capture of the floating control panel in the headset with play, previous, next, volume and depth controls
      caption: "The floating panel - play, seek, volume and depth, all one ray-cast away."
      title: "Screenshot: Floating control panel"
      desc: Mirrored headset view, floating control panel with playback buttons and sliders, controller ray pointing at one button.
  - number: 3
    id: adaptive-controls
    title: It only shows what applies
    text: |
      The panel does not show every control for every file. An audio-track picker appears only when a file actually has more than one track to choose from; a subtitle picker appears only when subtitles exist; the stereo depth slider appears only for stereo content. Whatever controls do not apply simply are not there, and the remaining ones spread out to use the width that frees up, so the panel never looks half-empty or crowded depending on what you happen to be watching.
  - number: 4
    id: seek-bar
    title: A seek bar you can actually drag
    text: |
      The panel carries a position bar with the elapsed and total time shown beside it - point the controller ray at it and drag to seek anywhere in the film, the same way you would drag a seek bar with a finger on a phone.
  - number: 5
    id: session-settings-panel
    title: Session settings, without pausing anything
    text: |
      The left controller's menu button opens **Session settings** - a second panel for everything you would otherwise have to leave the film to change: **Layout** (Mono, Side by side, Over-under), **Projection** (Mono, 180°, 360°, Cylinder - the same modes covered in [the four screen shapes](page:vr.spatial-cinema-playback)), **Panel distance**, **Panel size**, **Subtitles** (On/Off) and **Resume position**. Every change here applies to this session only, and playback never stops while you make it - close the panel with the menu button again and you are exactly where you left off.
    image_bookmark:
      shot_id: vr.session-settings-panel
      device_profile: phone
      screen_state: xr-session-settings-panel
      alt: A mirrored capture of the immersive session settings panel with layout, projection, panel distance and subtitle controls
      caption: "Session settings, open on the menu button, film still running behind it."
      title: "Screenshot: Session settings panel"
      desc: Mirrored headset view, session settings panel open with layout/projection spinners and distance/size/subtitles/resume rows.
  - number: 6
    id: hide-exit-and-idle
    title: Hide it, summon it, or let it hide itself
    text: |
      The HUD strip carries **HIDE** and **EXIT** buttons at opposite ends, so a slip of the ray never triggers the wrong one. **HIDE** removes the whole strip from view, and a single trigger pull - pointed at nothing in particular - summons it straight back, no need to aim at where it used to be.

      Left alone, the strip does the same thing on its own: after 15 seconds with no ray hovering over it, it fades away so it does not sit between you and the film; any hover or click resets that countdown, and the same trigger-pull summon brings it back exactly as if you had pressed HIDE yourself.
  - number: 7
    id: thumbstick-and-grip
    title: Thumbstick seeking, and the grip modifier
    text: |
      Push the thumbstick and the film seeks ten seconds a step - no need to aim at the seek bar for a quick nudge forward or back. Hold the grip button while you push the same stick and it steps between files instead, so you can flip through a folder with the same hand motion you already use to seek, without ever reaching for the panel's PREV/NEXT buttons.
  - number: 8
    id: haptics-and-hands
    title: Haptics, and playing hands-free
    text: |
      Every click on the panel gives the controller a short vibration, and hovering the ray over a button gives it a lighter one - a bit of feedback that does not depend on looking at exactly the right pixel. Set the controllers aside and FastMediaSorter still works: hand tracking recognizes a pinch as a click, aiming the ray with your hand the same way you would with a controller, and it processes hand gestures for leaving the session too, so nothing here strictly requires holding anything.
  - number: 9
    id: fps-overlay
    title: Curious how smooth it is? Turn on the FPS overlay
    text: |
      **Show FPS overlay**, in the same VR settings block covered in [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr), draws a live, smoothed frame-rate counter over the immersive view - a diagnostic for the curious rather than something you need in everyday use, and it is off by default so it never gets in the way of the film.
outcome: |
  Every control VR Cinema offers has a place on the floating panel or the session settings behind it, reachable with a controller ray, a hand-tracked pinch, or a thumbstick nudge, adapting itself to what is actually playing and staying out of your view until you ask for it.
tips:
  - "**Panel gone and you did not hide it?** It auto-hides after 15 seconds of no interaction - a single trigger pull anywhere brings it straight back."
  - "**Session settings and Settings in the app are not the same thing.** Session settings only affects the film playing right now; the app's own Settings screen is covered in [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr)."
  - "**No controllers on hand?** Set them down - hand tracking clicks with a pinch and points with the same hand you would otherwise hold a controller in."
  - "**Need a refresher on which button does what?** The controls legend is one long-press of Y (or F1 on a keyboard) away - see [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr)."
next_recipes:
  - title: Spatial 3D/360 cinema and video playback
    url: page:vr.spatial-cinema-playback
    badge: VR
    badge_type: video
    description: The screens, the stereo formats and the playlist this panel controls.
  - title: Setting up your headset and OpenXR
    url: page:vr.headset-setup-and-openxr
    badge: VR
    badge_type: docs
    description: Editions, headsets, the master switch and the controls legend.
  - title: Watching videos - controls, gestures and saving frames
    url: page:player.video-playback-controls
    badge: Video
    badge_type: video
    description: The equivalent flat-screen controls, outside the headset.
---

The floating control panel, its head-locked banner, the session settings behind the menu button, hiding and summoning the strip, thumbstick seeking with the grip modifier, controller haptics, hand tracking, and the FPS overlay - every control VR Cinema puts within reach.
