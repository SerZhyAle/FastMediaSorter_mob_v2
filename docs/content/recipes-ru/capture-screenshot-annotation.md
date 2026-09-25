---
page_id: capture.screenshot-annotation
title: Taking Screenshots and What Happens Next
nav_title: Taking screenshots
description: How to capture a screenshot with an edge gesture, what Android asks you to confirm, and what the app can do with the shot right away - open it for editing, translate the text on it, send it, or just save it quietly.
category: Камера и запись экрана
category_slug: capture
ticket: S2956
flavor: Standard and noLegal editions
recipe_number: "3"
canonical_url: documentation/capture/screenshot-annotation-ru.html
why: |
  A confirmation code, a boarding pass, a joke in a group chat - some things on your screen are worth keeping exactly as they look right now. FastMediaSorter turns a single swipe at the screen edge into a screenshot, and can immediately do something useful with it: open it for editing, translate the text on it, or send it on - without you opening the app first.

  Screen capture is not a separate program you launch. It rides on the same [edge gesture](term:edge-gesture) system used for quick photos and app shortcuts, so once it is set up, it works from inside any app.
ingredients:
  - "FastMediaSorter in the Standard or noLegal [edition](term:edition). Screen capture is not part of the Lite, Photos, Legacy, VR or FOSS editions."
  - "The **Display over other apps** permission, so the gesture strip can sit on top of whatever you are looking at."
  - "Android's own on-screen confirmation before a capture - on Standard, before every one; on noLegal, only until you turn on the accessibility shortcut described below."
steps:
  - number: 1
    id: turn-on-and-assign
    title: Turn on the gesture and give it a job
    text: |
      Open **Settings**, the **Management** tab, and turn on **Gesture overlay** - "Show up to 4 thin gesture strips at the screen edges, over other apps." Tap **Configure gestures** to open the edge-gesture window, pick a band and a direction, and set its **Gesture action** to one of the [screenshot](term:screenshot) options below. The bands, the directions, and everything else you can assign to them are covered in full in [Screen-edge gestures and the quick-access panel](page:capture.edge-gestures-and-quick-access-panel).
    image_bookmark:
      shot_id: capture.settings-gesture-overlay-toggle
      device_profile: phone
      screen_state: settings-operations-gesture-overlay
      alt: The Management tab in Settings with the Gesture overlay switch turned on and the Configure gestures button below it
      caption: "Gesture overlay in the Management tab."
      title: "Screenshot: Gesture overlay setting"
      desc: Settings, Management tab, Gesture overlay section expanded.
  - number: 2
    id: swipe-to-capture
    title: Swipe to capture
    text: |
      Perform the swipe on the band you assigned - a short drag from a corner band that FastMediaSorter reads as **Up**, **Right** or **Down**, wherever exactly it started. The first time, a one-time notice explains what is about to happen - **"Screen capture needs your permission"**: "The app can capture the current device screen, including other apps, only after you confirm the Android system prompt.." Tap **Continue**, then confirm Android's own screen-capture prompt. This is the same Play-safe capture flow behind every screenshot action on this page; on Standard, that Android prompt appears before every single shot, since there is no way around it in a Play-published app.
  - number: 3
    id: choose-what-happens-next
    title: Choose what happens to the shot
    text: |
      The action you assigned to that gesture decides what happens the moment the screenshot is saved:

      - **Silent screenshot** - just saves it, nothing else.
      - **Screenshot - view** - opens it in the [player](term:player).
      - **Screenshot for editing** - opens it in the [drawing editor](term:drawing-editor), ready to circle or write on.
      - **Screenshot - OCR translation** - reads the text on the screen and translates it; see [Extracting text with offline OCR](page:tools.ocr-text-recognition) and [Translating extracted text](page:tools.inline-translation).
      - **Screenshot - send to..** - opens the Send To sheet with the shot already attached; see [Sharing files to nearby devices and apps](page:tools.fast-sharing-and-export).
      - **Screenshot - share** - hands it to the Android system share sheet.
      - **Crop screenshot and share** - reopens the shot with a draggable crop frame, overwrites it with the cropped result, then opens Send To for the cropped picture.

      Each of the four edge bands has its own **Up**, **Right** and **Down** slot, so different corners can run different actions - a quiet save on one, an editable copy on another.
    image_bookmark:
      shot_id: capture.screenshot-action-picker
      device_profile: phone
      screen_state: edge-gesture-screenshot-action-picker
      alt: The Gesture action picker listing the screenshot options - silent screenshot, view, editing, OCR translation, send to, share and crop and share
      caption: "Choosing what a screenshot gesture does next."
      title: "Screenshot: Gesture action picker"
      desc: Edge-gesture window, Gesture action dialog open over a direction row, screenshot options visible.
  - number: 4
    id: skip-the-dialog-every-time
    title: Skip the dialog every time (noLegal)
    text: |
      On Standard, Android asks you to confirm every single shot - a Play-published app cannot remove that step. The [noLegal edition](term:nolegal-edition) offers a second path on Android 11 and newer: an accessibility service that saves screenshots silently, with nothing to confirm each time. The first time you turn on **Gesture overlay**, a dialog titled **"Enable screen gestures"** offers **Open settings** to turn it on. If you would rather not, or your phone will not allow it, tap **Old method** to fall back to the same per-shot confirmation Standard uses - which is also what noLegal itself falls back to automatically on Android 8, 9 and 10, since the accessibility route needs Android 11 or newer.
    image_bookmark:
      shot_id: capture.screenshot-accessibility-prompt
      device_profile: phone
      screen_state: screenshot-accessibility-permission-dialog
      alt: The Enable screen gestures dialog offering Open settings to turn on the accessibility service, and Old method as a fallback
      caption: "Turning on silent capture, or falling back to the per-shot confirmation."
      title: "Screenshot: Enable screen gestures dialog"
      desc: Enable screen gestures dialog, Open settings and Old method buttons visible.
  - number: 5
    id: choose-where-it-is-saved
    title: Choose where screenshots are saved
    text: |
      By default a screenshot lands in the phone's screenshots folder, or Downloads if that is not available. Open **Configure gestures**, expand **General gesture settings**, and use **Save screenshots to..** to pick a [resource](term:resource) instead - a network folder or a cloud folder works too. If that place cannot be reached at the moment a shot is taken, it falls back through Pictures/Screenshots, then DCIM/Screenshots, then Downloads, so a screenshot is never lost, only redirected.

      Turn on **Save screenshots to clipboard** in the same group and every screenshot is also copied to the clipboard the instant it is saved, ready to paste into a chat - on top of whatever the gesture action already does with it.
    image_bookmark:
      shot_id: capture.screenshot-destination-picker
      device_profile: phone
      screen_state: edge-gesture-general-group-destination
      alt: The General gesture settings group with Save screenshots to.. and Save screenshots to clipboard
      caption: "Choosing a save destination and clipboard copy in General gesture settings."
      title: "Screenshot: Screenshot destination and clipboard"
      desc: Edge-gesture window, General gesture settings group expanded.
outcome: |
  One swipe at the edge of the screen captures whatever is in front of you, on Standard with Android's own quick confirmation and on noLegal silently once you turn that on - and lands exactly where you told it to: open for editing, translated, on its way to someone, or just quietly saved.
tips:
  - "**Different corners, different jobs.** Assign a quiet **Silent screenshot** to one band and **Screenshot for editing** to another, so a plain capture and an annotated one are two different swipes."
  - "**A copy on the clipboard is not a backup.** It holds only the last screenshot; the saved file in your chosen place is the one that stays. The same idea applies to photos in [Taking photos, videos and voice notes straight into a folder](page:storage.capture-to-destination)."
  - "**Nothing happens when you swipe?** Check that direction is not set to **Do not use**, and that **Display over other apps** is still granted - Android sometimes revokes it after a while."
next_recipes:
  - title: Screen-edge gestures and the quick-access panel
    url: page:capture.edge-gestures-and-quick-access-panel
    badge: Gestures
    badge_type: other
    description: Every band, every direction, and everything else you can assign to a swipe.
  - title: Drawing and image annotations
    url: page:tools.drawing-and-image-markup
    badge: Tools
    badge_type: other
    description: Circle, write and mark up a screenshot after you capture it.
  - title: Sharing files to nearby devices and apps
    url: page:tools.fast-sharing-and-export
    badge: Tools
    badge_type: other
    description: Where Send To and the system share sheet take a captured screenshot.
---

Turn on Gesture overlay, give a swipe a job, and a screenshot is one drag away from wherever you are - saved quietly, opened for editing, translated or sent on, exactly the way you set it up.
