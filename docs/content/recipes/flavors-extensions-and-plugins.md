---
page_id: flavors.extensions-and-plugins
title: Downloadable Extensions - Add Features Only When You Need Them
nav_title: Downloadable extensions
description: How to open the Downloadable Extensions screen, download text recognition, extra audio formats, background videos and stream catalogs, follow a download, take a newer copy when one is ready, and delete what you no longer use.
category: Editions, Extensions & Languages
category_slug: flavors
ticket: S2947
flavor: Standard, noLegal, Legacy and VR
recipe_number: "02"
canonical_url: documentation/flavors/extensions-and-plugins.html
why: |
  Some parts of FastMediaSorter are big and only a few people need them: the engine that reads text in pictures, the models for Russian and Ukrainian letters, a decoder for rare audio formats, looping videos behind your music. Packing all of them into the app would make it heavier for everybody.

  So these parts are [extensions](term:extension): you download each one only when you want it, and delete it again when you need the space. Until you download an extension, the rest of the app works exactly as before.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Legacy or VR [edition](term:edition). The Lite, Photos and FOSS editions have no extensions screen, because the features the extensions add are not part of those editions. See [The seven editions](page:flavors.overview-and-comparison)."
  - "An internet connection. Wi-Fi is best: the largest extension is about 44 MB."
  - "Some free space on the phone. Each row of the screen shows how much the extension needs."
steps:
  - number: 1
    id: open-extensions
    title: Open the Downloadable Extensions screen
    text: |
      Open **Settings**, stay on the **General** tab, and tap **Downloadable Extensions**. The screen opens with a short note at the top and a list of extensions below it, grouped under **Translation & OCR recognition**, **Media playback** and **Streams**.

      There are two more ways to reach the same screen:

      * **Settings**, the **Media** tab, the **Translation, digitization (OCR)** section, the row **OCR & translation downloads**.
      * The page about features in the [first-launch wizard](page:getting-started.welcome-and-setup).

    image_bookmark:
      shot_id: flavors.extensions-manager-list
      device_profile: phone
      screen_state: extensions-manager-mixed-states
      alt: The Downloadable Extensions screen with the OCR Engines row installed and the Russian and Ukrainian OCR Model rows available for download, and the Install all and Delete all buttons at the bottom
      caption: "The Downloadable Extensions screen."
      title: "Screenshot: Downloadable Extensions"
      desc: Extensions screen in English, OCR Engines installed, the two OCR language models available, bottom buttons visible.
  - number: 2
    id: know-the-rows
    title: Know what each extension adds
    text: |
      Each row has a name, a short description, the **Estimated size** and a status on the right: **Available**, **Downloading**, **Installed**, **Failed** or **Update ready**.

      * **OCR Engines** - the text-recognition engine that reads text from pictures and documents without the internet. You need it for [text recognition](page:tools.ocr-text-recognition).
      * **Russian OCR Model** and **Ukrainian OCR Model** - better recognition of Russian and Ukrainian text. Download the one for the language of your documents.
      * **Translation Module** - translates recognized text between languages on the phone itself. See [Translating extracted text](page:tools.inline-translation).
      * **FFmpeg DTS Decoder** - plays audio in the DTS, APE, WMA and WavPack formats, including the DTS sound track of many movies.
      * **Audio Visualizations** - looping background videos that the [audio player](term:audio-player) shows behind a track that has no cover picture.
      * **Stream sources catalog**, **Channel preview atlas** and **Station logos** - the list of [Streams](term:stream) to choose from, and the pictures shown for them in the grid. See [Browsing the channel catalog](page:streams.channel-catalog-browsing).

      The screen shows only the extensions that your edition and your device can use. If a row you read about here is missing, your edition does not have that feature.
  - number: 3
    id: download
    title: Download an extension
    text: |
      Tap **Download** on the row you want. The status changes to **Downloading** and a progress bar with the percentage appears in the row, for example "Downloading: 40%". You can leave the screen: the download goes on, and the row shows the right status when you come back.

      Before an extension is marked **Installed**, the app checks that the downloaded file is complete and exactly the one it expects. A broken or interrupted download ends as **Failed**; tap **Download** again to retry.

      To get everything at once, tap **Install all** at the bottom of the screen. The app downloads every extension that is not installed yet.
    image_bookmark:
      shot_id: flavors.extensions-downloading
      device_profile: phone
      screen_state: extensions-manager-downloading
      alt: A row of the Downloadable Extensions screen showing the Downloading status with a progress bar at forty percent
      caption: "An extension being downloaded, with its progress in the row."
      title: "Screenshot: Download in progress"
      desc: Extensions screen, the Russian OCR Model row downloading, progress bar about half full.
    callout:
      type: tip
      title: Nothing breaks if you say no
      text: "You never have to download an extension. Without it, only the feature it adds is missing; everything else works as before. When you later try to use that feature, the app offers the download again."
  - number: 4
    id: keep-and-update
    title: Keep your extensions up to date
    text: |
      A downloaded extension stays until you delete it. It survives app updates, and it is not removed when you or the phone clear the app's cache.

      Sometimes a newer copy of an extension is published - a better model, a longer catalog, fresh station logos. Then the status of its row changes to **Update ready**. The old copy keeps working until you tap **Download** to take the new one.

      For the Streams rows, the size is measured on the file that is actually published, so the number you see is what you will download. Without an internet connection the row shows an estimate instead.
  - number: 5
    id: delete
    title: Delete extensions to free space
    text: |
      Tap **Delete** on an installed row. The app asks "Delete extension?" and explains that you can download it again later. Confirm, and the space is free again.

      To remove all of them at once, tap **Delete all** at the bottom of the screen and confirm "Delete all extensions?".
    image_bookmark:
      shot_id: flavors.extensions-delete-confirm
      device_profile: phone
      screen_state: extensions-manager-delete-dialog
      alt: The Delete extension? confirmation dialog over the Downloadable Extensions screen, with its cancel and confirm buttons
      caption: "Deleting an extension asks for confirmation first."
      title: "Screenshot: Delete extension"
      desc: Extensions screen, Delete tapped on the installed OCR Engines row, the confirmation dialog open.
  - number: 6
    id: extended-video
    title: Play video files the built-in player cannot open (noLegal edition only)
    text: |
      *Only in the [noLegal edition](term:nolegal-edition), the sideload version installed from an APK file.*

      The noLegal edition has one more row under **Media playback**: **Extended Video Playback**. It adds a second video engine that plays video files and disc images the built-in player cannot open. It is a one-time download of about 44 MB.

      You can download it from this screen in advance, or simply open a video that does not play: the app then offers the same download on the spot.
outcome: |
  The app stays light, and you add exactly the extras you use - text recognition in your languages, rare audio formats, background videos, stream catalogs - and remove them again whenever you need the space.
tips:
  - "**On mobile data?** Download the large extensions later over Wi-Fi. Their size is shown in each row before you tap anything."
  - "**Moving to another edition?** Extensions are not part of a settings backup. Download them again in the new edition from the same screen. See [The seven editions](page:flavors.overview-and-comparison)."
  - "**Text recognition reads Cyrillic badly?** Download the **Russian OCR Model** or the **Ukrainian OCR Model** - the basic engine alone recognizes those letters less accurately."
next_recipes:
  - title: Extracting text with offline OCR
    url: page:tools.ocr-text-recognition
    badge: Tools
    badge_type: docs
    description: Turn the text in a photo or scanned page into text you can copy.
  - title: The seven editions
    url: page:flavors.overview-and-comparison
    badge: Editions
    badge_type: docs
    description: Which edition has which features, and how to move between them.
  - title: Choosing the app language and units
    url: page:flavors.multilingual-support
    badge: Editions
    badge_type: docs
    description: Pick one of thirteen languages and switch between metric and US units.
---

[Extensions](term:extension) are optional parts of FastMediaSorter that you download only when you need them: text recognition, extra audio formats, background videos and stream catalogs. This page shows how to download, update and delete them.
