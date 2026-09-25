---
page_id: tools.inline-translation
title: Translating Extracted Text
nav_title: Translating text on screen
description: How to translate a picture, a PDF page, an EPUB chapter or recognized text fully offline, choose the source and target language, switch between a translation card and a Google Lens-style overlay painted over the original words, adjust the font, and what happens on a device translation is not licensed for.
category: Распознавание текста, рисование и отправка
category_slug: tools
ticket: S2957
flavor: Standard, noLegal, Legacy and VR
recipe_number: "02"
canonical_url: documentation/tools/inline-translation-ru.html
why: |
  Once words are on the screen - recognized from a photo, printed on a PDF page, or sitting in an EPUB chapter - FastMediaSorter can turn them into your own language right there, using Google's on-device [ML Kit](term:ocr) translation. No internet is needed once the language models are on the phone, and nothing you translate is sent anywhere except to render the model download itself.

  Two different starting points feed the same translator: a picture goes through [OCR](term:ocr) first so there is text to translate, while a PDF page, an EPUB chapter or a text file is already text and skips straight to translation.
ingredients:
  - "FastMediaSorter in an [edition](term:edition) with translation: Standard, noLegal, Legacy or VR. The Lite, Photos and FOSS editions do not translate text."
  - "**Enable Translation** switched on in **Settings** - or on the setup wizard's functionality page. The first time, this downloads the **Translation Module** extension; see [first launch and setup](page:getting-started.welcome-and-setup) and [downloadable extensions](page:flavors.extensions-and-plugins)."
  - "To translate a picture: the **OCR Engines** extension too, since a photo or a scan has to be read before it can be translated - see [recognizing text on pictures](page:tools.ocr-text-recognition)."
  - "Something to translate: a picture open in the [image viewer](term:image-viewer) - see [viewing photos and gestures](page:images.viewer-and-gestures) - a PDF page, an EPUB chapter, or a text file."
  - "A phone, tablet, laptop, desktop or Chromebook. Translation is not offered on a TV, a car head unit or an XR headset - see the last step."
steps:
  - number: 1
    id: translate-image
    title: Translate the text in a picture
    text: |
      Open the three-dots menu and tap **Translate** (in landscape the button may sit right on the command panel bar). After "Translation started.." the recognized lines come back translated, in one of two looks depending on the **Translation result in blocks** setting:

      - **Off (default)** - the translation appears in a card of its own, below or over the picture, kept separate from the original.
      - **On** - each translated line is painted directly over the original words on an opaque plate, Google Lens style, so the picture reads in your language in place. A line whose words sit far apart - text on both sides of a photo, separate speech bubbles - is split and translated as separate pieces instead of one strip across the picture. A plate that reaches the bottom of the screen grows upward instead of being cut off, and a translation taller than the whole screen shrinks its type first, so it is always fully drawn.
    image_bookmark:
      shot_id: tools.image-translation-overlay
      device_profile: phone
      screen_state: image-translate-lens-overlay
      alt: Translated text painted directly over the original words on a photographed sign, each line on its own solid plate
      caption: "Translated text in place of the original, Lens style."
      title: "Screenshot: Lens-style image translation"
      desc: A photographed shop sign with the Lens-style translation overlay on, each recognized line replaced by an opaque plate with the translated words.
    callout:
      type: warning
      title: No overlay on a guessed language
      text: "If the source language was only guessed and most of what came back could not be trusted, no plate is drawn at all: "The text is in a script the recognizer was not set up for, so no translation is drawn over the picture. Choose the Original Language in the translation settings." Pick the language explicitly (next step) and try again."
  - number: 2
    id: translate-page-chapter
    title: Translate a PDF page or an EPUB chapter
    text: |
      A PDF page - including a scanned one with no text layer, read through the same offline OCR pass - and an EPUB chapter both translate the same way: open the three-dots menu and tap **Translate**. A PDF page follows the card-or-overlay look from the step above; an EPUB chapter always opens in a resizable panel over the text, with its own font size separate from the book's. See [reading PDF documents](page:documents.pdf-epub-viewing) and [reading EPUB books](page:documents.office-docs-support) for turning pages and finding the button.
  - number: 3
    id: text-settings
    title: Open Text Settings and choose your languages
    text: |
      Open the three-dots menu and tap **Text Settings** to open the language and look dialog shared by images, PDFs, EPUB chapters and text files. Tap **Original Language and OCR** or **Translate To** to open a searchable language list ordered by your interface language, with **Auto** available as the source. A swap button next to the two rows exchanges the source and target language when the swap makes sense - handy when you translate back and forth between the same two languages.

      The same dialog holds **Translation result in blocks** (the card-or-overlay switch from the first step), **Font Size** and **Font Family** for the translated text. Confirming the dialog also turns translation on, if it was off.
    image_bookmark:
      shot_id: tools.text-settings-dialog
      device_profile: phone
      screen_state: text-settings-dialog-open
      alt: The Text Settings dialog with Original Language and OCR, Translate To, a swap button, the Translation result in blocks switch, and Font Size and Font Family dropdowns
      caption: "Text Settings: languages, look and font in one place."
      title: "Screenshot: Text Settings dialog"
      desc: Text Settings dialog open over the reader, source language Russian, target language English, Translation result in blocks switched on.
  - number: 4
    id: fast-second-time
    title: Why the second translation is instant
    text: |
      The first time you pick a new target language, FastMediaSorter downloads its translation model in the background as soon as you choose it, not while you are waiting for a result - you may briefly see "Downloading translation model.." and then "Translation model ready.". A failed download shows "Couldn't download the translation model. Try again." with a **Retry** button.

      A page or a picture you translate once stays translated in memory for the rest of the session: reopen the same page and the translation is already there, with no repeat recognition and no repeat download.
  - number: 5
    id: device-support
    title: Where translation is not offered
    text: |
      On-device translation is licensed for phones, tablets, laptops, desktops and Chromebooks. On a TV, a car head unit or an XR headset, the **Translate** button stays visible but disabled, with "Translation is not licensed for this type of device" as the explanation, and the translator [widget](term:widget) and launcher gadget are not offered on those devices at all.

      A translated result carries a small, screen-reader-readable Google credit, since the translation itself comes from Google's on-device model.
outcome: |
  Recognized text, a PDF page, an EPUB chapter or a text file reads in your language without the internet - as a card of its own or painted right over the original words, in the font and size you like. Switching languages downloads what it needs quietly in the background, and translating the same page twice costs nothing the second time.
tips:
  - "**Haven't recognized any text yet?** Start with [recognizing text on pictures](page:tools.ocr-text-recognition)."
  - "**Want to send the translated text to someone?** See [sharing and exporting files](page:tools.fast-sharing-and-export)."
  - "**Reading Cyrillic text that keeps coming out wrong?** Pick the source language explicitly instead of Auto - see the language step above, and [recognizing text on pictures](page:tools.ocr-text-recognition) for why Auto can misread Cyrillic letters."
  - "**Only need the language pack, not a tour of the extensions screen?** Jump straight to [downloadable extensions and language models](page:flavors.extensions-and-plugins)."
next_recipes:
  - title: Extracting text with offline OCR
    url: page:tools.ocr-text-recognition
    badge: Tools
    badge_type: docs
    description: Recognize text from a photo, a screenshot or a scanned page before translating it.
  - title: Reading PDF documents
    url: page:documents.pdf-epub-viewing
    badge: Documents
    badge_type: docs
    description: Turn pages, read at night, search, copy and export PDF pages.
  - title: Downloadable extensions and language models
    url: page:flavors.extensions-and-plugins
    badge: Editions
    badge_type: docs
    description: Install the Translation Module and OCR language models, or remove them again.
---

Recognized text, a PDF page, an EPUB chapter or a text file reads in your language with one tap, entirely on the phone - as a card of its own, or painted right over the original words like Google Lens.
