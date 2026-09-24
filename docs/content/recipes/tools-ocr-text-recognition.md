---
page_id: tools.ocr-text-recognition
title: Extracting Text with Offline OCR
nav_title: Text recognition (OCR)
description: How to pull the words out of a photo, a screenshot or a scanned document page with the on-device OCR engine, snap a picture and get instant text with Camera OCR, pick the right recognition language for tricky or Cyrillic text, and save or copy what was found.
category: OCR, Drawing & Sharing
category_slug: tools
ticket: S2957
flavor: Standard, noLegal, Legacy and VR
recipe_number: "01"
canonical_url: documentation/tools/ocr-text-recognition.html
why: |
  A shop sign in another language, a recipe photographed from a magazine, a scanned contract page, a screenshot of an error message you want to search for - printed words trapped in a picture are everywhere. FastMediaSorter reads them for you with an on-device [OCR](term:ocr) engine, so the words become text you can select, copy, search or save. Nothing is uploaded anywhere: the recognition runs on the phone itself.

  The engine is [Tesseract](term:ocr), tuned here for strong Cyrillic support - so a Russian or Ukrainian sign is read as Russian or Ukrainian letters, not as the nearest-looking Latin ones.
ingredients:
  - "FastMediaSorter in an [edition](term:edition) with OCR: Standard, noLegal, Legacy or VR. The Lite, Photos and FOSS editions do not read text from pictures."
  - "The **OCR Engines** extension, downloaded once from **Settings**, the **General** tab, **Downloadable Extensions** - or turned on right on the setup wizard's functionality page. See [first launch and setup](page:getting-started.welcome-and-setup) and [downloadable extensions](page:flavors.extensions-and-plugins)."
  - "For better recognition of Cyrillic text: the **Russian OCR Model** or the **Ukrainian OCR Model**, downloaded the same way."
  - "A device with Android 8.0 (API 26) or newer and at least 3 GB of RAM. On an older or smaller device you will see "OCR is unavailable on this device.""
  - "A picture with legible text: a photo already open in the app, a fresh photo from the [camera](term:camera), a screenshot, or a scanned PDF page."
steps:
  - number: 1
    id: extract-in-viewer
    title: Pull text out of a picture you are viewing
    text: |
      Open the picture in the [image viewer](term:image-viewer) or the [reader](term:reader), show the [command panel](term:command-panel), and tap the **OCR** button (its accessibility label is **Extract Text**). The recognized words open on a **Recognized Text** screen as plain, selectable text - copy all of it, copy a piece, or share it like any other text.

      If the picture has no legible words - a blurry photo, a plain landscape - you will see "No text found in image".
    image_bookmark:
      shot_id: tools.ocr-recognized-text
      device_profile: phone
      screen_state: ocr-result-recognized-text
      alt: Recognized text shown as an editable panel over a photographed shop sign, with Copy and Save actions in the bar
      caption: "Recognized text, ready to copy or save."
      title: "Screenshot: recognized text panel"
      desc: A photographed sign open in the image viewer with the OCR result panel showing the recognized words as selectable text.
  - number: 2
    id: camera-ocr
    title: Snap a photo and get instant text
    text: |
      For a fresh subject - a menu, a poster, a business card - skip the gallery: open the **Camera OCR** program from the programs menu, drop a **Camera OCR** [widget](term:widget) on your home screen, or use the **Capture & OCR** panel item, then point the camera and shoot.

      After "Processing photo.." a crop frame appears over the photo. Drag its corners around the exact block of text, tap **Retry** to shoot again, or tap **OK** to continue. While "Extracting text and translating.." runs, the result screen opens with an **Original text** tab and a **Translation** tab, plus **Save as .txt** to write the text to a file and **Next photo** to shoot the next one without leaving the flow.
    image_bookmark:
      shot_id: tools.camera-ocr-crop-frame
      device_profile: phone
      screen_state: camera-ocr-crop-frame
      alt: A crop frame drawn over a freshly taken photo of a shop sign, with Retry and OK buttons at the bottom
      caption: "Crop the photo down to the text you want."
      title: "Screenshot: Camera OCR crop frame"
      desc: A just-taken photo of a shop sign with the Camera OCR crop frame open, corner handles visible, Retry and OK buttons at the bottom.
  - number: 3
    id: screenshot-ocr
    title: Turn a screenshot into text
    text: |
      Already looking at something with text on screen - a chat, a web page, a form - and would rather not leave the app? In the **Standard** and **noLegal** (sideload) editions, open the capture strip and tap **Screenshot - OCR translation**: the screenshot opens straight in the same crop-and-recognize screen as a photo, and only the cropped piece is kept in your gallery. The same strip's **Take a photo and OCR-translate** action does the same thing starting from the camera instead of the screen.

      On Legacy and VR, where the capture strip is not available, the **Camera OCR** program and its widgets cover the same ground - recognition itself works the same wherever OCR is available.
  - number: 4
    id: choose-language
    title: Choose the right recognition language
    text: |
      Recognition quality depends on picking the right alphabet. Tap the **OCR language** or **Translation language** row - in the Camera OCR crop screen, or in the reader's **Text Settings** dialog - to open a searchable list of every language the engine currently supports, ordered by your interface language, with **Auto** available for the source language.

      For the best result, pick the source language explicitly rather than leaving it on Auto, especially for Cyrillic text: guessing reads the picture with the English alphabet first, and a look-alike Latin letter can slip in where a Cyrillic one belongs. Choosing **Russian** or **Ukrainian** up front reads those letters correctly from the start.
    image_bookmark:
      shot_id: tools.ocr-language-picker
      device_profile: phone
      screen_state: ocr-language-picker-search
      alt: A searchable language list with a search field, languages ordered by interface language and Russian marked as selected
      caption: "Picking the exact recognition language."
      title: "Screenshot: OCR language picker"
      desc: The searchable language picker open over the Camera OCR crop screen, search field focused, Russian highlighted as the current selection.
  - number: 5
    id: pdf-scan
    title: Recognize text on a scanned PDF page
    text: |
      A PDF made from a scanner or a photocopy has no text layer at all - the words are just part of the picture. Open the page in the reader and tap **Translate**; a picture-only page goes through the same offline OCR pass before its recognized words are shown. See [reading PDF documents](page:documents.pdf-epub-viewing) for finding the Translate button, and [translating extracted text](page:tools.inline-translation) for what happens to the recognized words next.
  - number: 6
    id: save-text
    title: Copy or save what was found
    text: |
      From the **Recognized Text** screen or the Camera OCR result, select all or part of the text and copy it like any other selection, or tap **Save as .txt** to write it to a timestamped text file in your chosen destination folder - you will see "Saved text to.." with the path when it lands.
outcome: |
  Text trapped in a photo, a screenshot or a scanned page becomes real, selectable words - copied, searched, or saved as a text file, entirely on the phone. Camera OCR turns a fresh photo into text in a couple of taps, the crop screen works the same way for a screenshot, and choosing the right language keeps Cyrillic text from turning into Latin look-alikes.
tips:
  - "**Text recognition works in the Google Play version too.** Its engine now ships inside the app, so a Play install no longer shows a misleading message that it is not available."
  - "**The crop frame is easy to grab.** The bottom handle of the crop frame stays clear of the buttons at the bottom of the screen, and the frame's border is drawn in full."
  - "**Want the translated text side by side, not just the original?** See [translating extracted text](page:tools.inline-translation)."
  - "**Blurry photo or a loose crop?** OCR only reads what is actually printed - a steadier photo and a tighter crop around the text read better than a wide, blurry one."
  - "**Only want the language packs, not a tour of the whole extensions screen?** Jump straight to [downloadable extensions and language models](page:flavors.extensions-and-plugins)."
  - "**Setting up the app for the first time?** Turn OCR on right on the functionality page - see [first launch and setup](page:getting-started.welcome-and-setup)."
next_recipes:
  - title: Translating extracted text
    url: page:tools.inline-translation
    badge: Tools
    badge_type: docs
    description: Turn recognized words into your language, on the page or in a card.
  - title: Drawing and image annotations
    url: page:tools.drawing-and-image-markup
    badge: Tools
    badge_type: docs
    description: Circle, underline or write over the picture you just recognized text from.
  - title: Downloadable extensions and language models
    url: page:flavors.extensions-and-plugins
    badge: Editions
    badge_type: docs
    description: Install the OCR engine and language-specific models, or remove them again.
---

One tap turns a photo, a screenshot or a scanned page into text you can copy, search or save - read on the phone itself, with no picture ever leaving the device.
