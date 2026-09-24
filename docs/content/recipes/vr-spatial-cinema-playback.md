---
page_id: vr.spatial-cinema-playback
title: Spatial 3D/360 Cinema and Video Player
nav_title: Spatial cinema playback
description: How to step from the flat video player into VR Cinema, the 360, 180, cylinder and flat virtual cinema screens, stereo SBS/OU playback and depth, the immersive playlist and PREV/NEXT, subtitles in the headset, and what happens when something goes wrong.
category: VR and OpenXR
category_slug: vr
ticket: S2967
flavor: noLegal and VR, on a compatible headset
recipe_number: "02"
canonical_url: documentation/vr/spatial-cinema-playback.html
why: |
  A flat screen is honest about what a video is - a rectangle. [VR Cinema](term:vr-cinema) is FastMediaSorter's answer to what a video could be instead: a 360° trip wrapped all the way around you, a 3D film with real depth, or simply an ordinary video projected onto a virtual screen the size of a real cinema, floating in a dark room built just for it.

  This page is about what happens once you are actually inside - the screen shapes, the stereo formats, the playlist that keeps going without a return trip to the flat player, and the handful of things that can go wrong along the way and how FastMediaSorter tells you about them instead of just going dark. Headset and edition setup is its own recipe, linked below, and so is the floating control panel you use once you are in.
ingredients:
  - "Everything from [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr): a supported edition, a compatible headset, and the 3D-VR master switch turned on."
  - "A video - local files work everywhere; the immersive player only opens files it can reach directly, not ones still downloading from the network."
  - "For the full stereo and 360° effect: a video actually shot or authored that way. An ordinary flat video still plays, just on the virtual cinema screen instead of wrapped around you."
steps:
  - number: 1
    id: entering-from-the-player
    title: Step in from the flat player
    text: |
      Open any video in the ordinary flat [video player](term:video-player) and, on a headset with immersive mode on, a **VR** badge sits right in the controls row alongside play, seek and volume - tap it and VR Cinema opens on the spot. The same jump is in the overflow menu as **Open in VR**, for when you would rather reach for a menu than a badge.

      For a file that is already stereo or panoramic, FastMediaSorter can skip the tap entirely: turn on auto-immersive entry and that kind of file launches straight into VR Cinema the moment you open it, no extra step. Either way, the flat player's state - where you were, what you had set - is captured before the handoff and handed back to you exactly as it was if you return to it, and a brief loading screen covers the transition while the first immersive frame decodes, so you are never staring at a half-built scene.
    image_bookmark:
      shot_id: vr.player-vr-badge
      device_profile: phone
      screen_state: video-player-vr-badge
      alt: The flat video player controls row with a VR badge next to the playback buttons
      caption: "The VR badge, right in the controls row."
      title: "Screenshot: VR badge in the player"
      desc: Flat video player, controls row visible, VR badge highlighted among play/seek/volume controls.
  - number: 2
    id: cinema-screen-shapes
    title: Four shapes for four kinds of file
    text: |
      VR Cinema chooses the screen to match what you are watching:

      - **360°** - a full sphere around you, for an equirectangular photo or video.
      - **180°** - a hemisphere in front of you, for VR180 and other front-facing fisheye captures.
      - **Cylinder** - a wide, gently curved band for 180° content authored as a cylinder rather than a sphere, without the pole distortion a sphere would introduce.
      - **Flat** - an ordinary photo or video projected onto a virtual cinema screen in a dark room, styled as **Cinema**, **Full SBS** or **Full OU** in the VR settings block.

      Each one is a live [OpenXR](https://www.khronos.org/openxr/) composition layer, not a flat texture stretched over a shape, so the picture stays sharp as you turn your head to look around it.
    image_bookmark:
      shot_id: vr.spatial-cinema-3d
      device_profile: phone
      screen_state: vr-cinema-flat-screen
      alt: A mirrored capture of the VR Cinema virtual screen showing a flat video framed like a curved cinema screen
      caption: "An ordinary video, projected onto the virtual cinema screen."
      title: "Screenshot: Virtual cinema screen"
      desc: Mirrored headset view, flat video playing on a curved virtual cinema screen inside a dark environment.
  - number: 3
    id: video-playback
    title: Video, playing for real
    text: |
      Local videos, stereo 3D and 360° alike, play through the same immersive pipeline with the seek position picked up exactly where the flat player's snapshot left off, so switching between flat and immersive mid-film never costs you your place.
  - number: 4
    id: stereo-and-depth
    title: Stereo 3D - detected, or set by hand
    text: |
      Side-by-side and over-under are the two stereo layouts FastMediaSorter understands, rendered per eye so each one actually sees its own half of the frame. It works out which one a file uses, and whether the file is stereo at all, from three places in order: a marker in the filename, the file's own embedded spatial metadata, and - optionally - an aspect-ratio guess for files that carry neither. A video whose name was changed after downloading no longer has to fall back to flat and mono just because the filename lost its marker - the metadata still carries the truth.

      Got it wrong, or just prefer a different layout for one particular file? Override it by hand in that file's own settings, and FastMediaSorter remembers your choice and uses it again next time, ahead of anything auto-detection would have picked.

      Once the picture is stereo, a slider on the floating panel - see [the controls in the headset](page:vr.passthrough-and-controllers) - adjusts how much depth the 3D effect has in real time, no need to leave the film to retune it.
  - number: 5
    id: playlist-and-navigation
    title: A playlist that keeps going
    text: |
      VR Cinema does not play one file in isolation - it carries the whole ordered list of images and videos you came from, so **PREV** and **NEXT** on the panel walk the same folder or resource you were already browsing, in the same order, without a return trip to the flat screen. When you reach either end, FastMediaSorter tells you plainly - **First file** or **Last file** - instead of looping silently or doing nothing.
  - number: 6
    id: subtitles
    title: Subtitles, right in front of you
    text: |
      **Editions:** noLegal.

      A video's subtitle track follows you into the headset: cues render right in the immersive scene, timed against the same playback clock as the picture and the sound, so you are not choosing between reading captions and staying immersed.
  - number: 7
    id: when-things-go-wrong
    title: When something goes wrong
    text: |
      A file VR Cinema cannot open at all says so honestly - **This file type is not ready for VR yet** - and a stereo or panoramic file that briefly fails to decode gets the same courtesy rather than a silent freeze. If immersive mode itself cannot start, FastMediaSorter stops playback outright instead of leaving audio running behind a black screen - **Immersive mode failed to start. Playback was stopped to avoid background audio.**

      A playback error mid-session tears down cleanly: the video surface switches off, the panel stops mirroring a player that is no longer running, and the next file you try does not trip over anything left behind by the last one. And if immersive mode is unavailable for a file that would otherwise qualify, you land in flat mode instead of nowhere - **Immersive mode unavailable - playing in flat mode. You can change the 3D format in player settings.**
outcome: |
  From a tap on the VR badge to a full 360° film with the depth tuned to your taste, VR Cinema plays what you already have - stereo, panoramic or perfectly ordinary - on the screen shape that suits it, keeps walking your playlist with PREV and NEXT, carries your subtitles with you, and tells you plainly on the rare occasion something does not work.
tips:
  - "**No VR badge in the player?** Turn on the 3D-VR master switch in Settings, the Media tab - see [setting up your headset and OpenXR](page:vr.headset-setup-and-openxr)."
  - "**Prefer VR Cinema to open by itself?** Turn on auto-immersive entry so stereo and panoramic files skip the badge entirely."
  - "**Not sure the layout is right?** Override the stereo format for that one file instead of waiting on auto-detection - see the controls panel's session settings in [controls in the headset](page:vr.passthrough-and-controllers)."
  - "**Want the panel and its buttons explained end to end?** See [controls in the headset](page:vr.passthrough-and-controllers)."
next_recipes:
  - title: Controls in the headset
    url: page:vr.passthrough-and-controllers
    badge: VR
    badge_type: docs
    description: The floating panel, the controllers, hand tracking and haptics.
  - title: Setting up your headset and OpenXR
    url: page:vr.headset-setup-and-openxr
    badge: VR
    badge_type: docs
    description: Editions, headsets, the master switch and VR Cinema's entry points.
  - title: Subtitles, audio tracks and 3D
    url: page:player.subtitles-and-audio-tracks
    badge: Video
    badge_type: video
    description: How subtitles and stereo formats work in the flat player.
---

Step from the flat player into VR Cinema, meet the 360, 180, cylinder and flat virtual cinema screens, watch stereo 3D detected or set by hand, keep walking your playlist with PREV and NEXT, and see how FastMediaSorter handles it when something along the way does not work.
