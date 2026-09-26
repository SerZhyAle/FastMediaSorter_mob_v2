---
page_id: images.viewer-and-gestures
title: Viewing Photos, GIFs and Zoom Gestures
nav_title: Viewing photos and GIFs
description: How to open a picture full screen, move between photos with a swipe, zoom in, turn a sideways photo for a moment, and play or pause animated GIF, WebP and APNG images.
category: Images, Audio & Slideshow
category_slug: images
ticket: S2952
flavor: All editions
recipe_number: "01"
canonical_url: documentation/images/viewer-and-gestures.html
why: |
  You come back from a trip with four hundred photos and want to look through them quickly, one after another, on the whole screen. Or a friend sends you a funny animated GIF and you want to stop it on the best frame. The image viewer is made for exactly this: one picture at a time, a swipe to the next one, and nothing in the way.

  Nothing on this page changes your files. Zooming, turning a photo for a moment and pausing an animation only change what you see right now.
ingredients:
  - "FastMediaSorter installed. The image viewer is part of every [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR and FOSS."
  - "At least one [resource](term:resource) with pictures in it, for example the [All Images](term:all-images) collection or a folder you added yourself."
  - "For HEIC and HEIF photos (the format many phones use for camera shots): Android 9 or newer. For AVIF pictures: Android 12 or newer."
steps:
  - number: 1
    id: open-photo
    title: Open a photo full screen
    text: |
      Open a resource on the [main screen](term:main-screen), then tap any picture in the [file browser](page:browsing.grid-and-list-views). The picture opens in the [image viewer](term:image-viewer), filling the screen.

      The viewer shows JPG, PNG, WebP, BMP and GIF pictures, animated WebP and APNG, and HEIC, HEIF and AVIF photos. If your Android is too old for HEIC or AVIF, the viewer does not fail silently: it tells you which Android version the picture needs, for example "requires Android 9 (API 28) or newer".
    image_bookmark:
      shot_id: images.viewer-fullscreen-photo
      device_profile: phone
      screen_state: image-viewer-fullscreen
      alt: A landscape photo opened full screen in the FastMediaSorter image viewer with the command panel hidden
      caption: "A photo opened full screen in the image viewer."
      title: "Screenshot: Photo full screen"
      desc: A local photo open in the player, no panels shown, phone portrait.
  - number: 2
    id: next-previous
    title: Move to the next or previous photo
    text: |
      Swipe from right to left to see the next picture in the folder, and from left to right to go back to the previous one. The order is the same as in the file browser, so if you sorted the folder by date, you walk through your photos by date.

      You can also tap the screen instead of swiping. The screen is divided into invisible [touch zones](term:touch-zones): a tap on the right side of the middle row opens the next file, a tap on the left side opens the previous one.
    callout:
      type: tip
      title: Keyboard and remote control
      text: "On a tablet with a keyboard, or on a TV box with a remote, the left and right arrow keys move between photos too. See [keyboard, D-pad and TV navigation](page:general.keyboard-dpad-tv-navigation)."
  - number: 3
    id: zoom
    title: Zoom in to see the details
    text: |
      Put two fingers on the picture and spread them apart to zoom in; pinch them together to zoom out. While zoomed in, drag with one finger to look around the picture.

      By default the viewer loads a lighter copy of each picture, about 1920 pixels wide, so large photos open quickly. To see every pixel of a 50-megapixel photo when you zoom in - and to zoom while the [command panel](term:command-panel) is open - go to **Settings**, the **Media** tab, the **Images, GIFs and slideshow** section, and turn on **Load images at full resolution**.
    image_bookmark:
      shot_id: images.viewer-pinch-zoom
      device_profile: phone
      screen_state: image-viewer-zoomed
      alt: A photo zoomed in with two fingers in the image viewer, showing a close-up of the details
      caption: "Pinch to zoom in on a photo."
      title: "Screenshot: Zoomed-in photo"
      desc: The same photo as in step 1, zoomed about three times.
  - number: 4
    id: turn-for-a-moment
    title: Turn a sideways photo for a moment
    text: |
      Some photos are shown lying on their side. To look at one upright without changing the file, open the [three-dots menu](term:three-dots-menu) and tap **Rotate 90°** (turn clockwise) or **Rotate -90°** (turn counter-clockwise). Each tap turns the picture by a quarter; four taps bring it back.

      This turn lasts only while you look at the photo. The file itself stays exactly as it was, and the phone's own screen rotation is not touched. To turn the photo for good, use the editing tools described in [Editing photos: rotate, crop, colors and GIF tools](page:images.editing-photos).
    image_bookmark:
      shot_id: images.viewer-rotate-menu
      device_profile: phone
      screen_state: image-viewer-overflow-rotate
      alt: The image viewer three-dots menu open, showing the Rotate 90 degrees and Rotate minus 90 degrees items
      caption: "Rotate 90° and Rotate -90° in the three-dots menu."
      title: "Screenshot: Rotate items in the menu"
      desc: Player overflow menu open over a photo, the two rotate items visible.
  - number: 5
    id: animated-images
    title: Play and pause animated GIF, WebP and APNG pictures
    text: |
      Animated pictures start moving as soon as you open them, just like in a messaging app. This works for GIF files and also for animated WebP and APNG pictures.

      To stop on a frame, tap the play and pause button on the control bar. Tap it again and the animation continues from where it stopped. When you move to another file and come back, the viewer remembers whether you left this animation playing or paused.
    image_bookmark:
      shot_id: images.viewer-gif-paused
      device_profile: phone
      screen_state: image-viewer-gif-paused
      alt: An animated GIF paused in the image viewer with the play button shown on the control bar
      caption: "An animated GIF, paused on one frame."
      title: "Screenshot: Paused GIF"
      desc: A GIF open in the player, paused, control bar visible with the play button.
    callout:
      type: tip
      title: More you can do with an animation
      text: "Speed an animation up or slow it down, save its first frame as a picture, or cut it into separate frames - all of this is in [Editing photos: rotate, crop, colors and GIF tools](page:images.editing-photos)."
  - number: 6
    id: fit-or-fill
    title: Choose between the whole picture and a full screen
    text: |
      A photo rarely has exactly the shape of your screen. By default the viewer shows the whole picture and leaves thin empty bars at the edges, so nothing is cut off.

      If you prefer the picture to cover the entire screen, go to **Settings**, the **Media** tab, the **Images, GIFs and slideshow** section, and turn on **Crop images to fill screen**. The edges of the picture that do not fit are then hidden. The same choice applies to the [slideshow](page:images.slideshow-and-transitions).
    image_bookmark:
      shot_id: images.settings-crop-to-fill
      device_profile: phone
      screen_state: settings-images-crop-fill
      alt: The Images, GIFs and slideshow settings section with the Crop images to fill screen switch turned off
      caption: "The Crop images to fill screen switch in Settings, Media, Images, GIFs and slideshow."
      title: "Screenshot: Crop images to fill screen"
      desc: Settings, Images section, the crop-to-fill row visible and off.
outcome: |
  You can walk through a whole folder of photos with a swipe, zoom into any detail, straighten a sideways shot for a moment and freeze an animation on the frame you like - without changing a single file.
tips:
  - "**Zoom looks blurry?** Turn on **Load images at full resolution** in Settings, Media, Images, GIFs and slideshow. The viewer then loads the original picture instead of a lighter copy."
  - "**A HEIC or AVIF photo shows a notice instead of a picture?** Your Android version cannot draw that format. The notice names the Android version the picture needs. You can still copy, move and share the file."
  - "**Sort while you look.** The same full-screen player can copy, move or delete the photo you are looking at. See [Copy, move and delete files](page:storage.file-copy-move-delete)."
  - "**Let the photos change by themselves.** Start a [slideshow](page:images.slideshow-and-transitions) and the viewer moves to the next picture on its own."
next_recipes:
  - title: Editing photos, rotate, crop, colors and GIF tools
    url: page:images.editing-photos
    badge: Photos
    badge_type: image
    description: Turn a photo for good, crop it, change its brightness and colors, or change the speed of an animated GIF.
  - title: Creating photo slideshows
    url: page:images.slideshow-and-transitions
    badge: Photos
    badge_type: image
    description: Let your photos change by themselves, with a countdown and background music.
  - title: Grid and list views
    url: page:browsing.grid-and-list-views
    badge: Browsing
    badge_type: docs
    description: Find the photo you want faster with thumbnails, sorting and filters.
---

The [image viewer](term:image-viewer) shows one picture at a time on the whole screen. Swipe to move between photos, pinch to zoom, turn a sideways photo for a moment and pause animated pictures - all without changing your files.
