---
page_id: wear.watch-file-manager
title: Browsing and Managing Files on the Watch - Lists, Grids, File Actions and Locked Containers
nav_title: Browsing files on the watch
description: How to browse the phone's media by type from the watch, switch file lists between a list and a grid with real thumbnails, select files and send, move, delete or rename them, see where a file sent to the phone landed, hand a file back to the phone to open it there, and open FileDO encrypted containers on the watch.
category: Wear OS Watch
category_slug: wear
ticket: S2965
flavor: The full version of the watch app (sideload only), with the phone app in the Standard or noLegal edition. Opening FileDO containers stored on a network resource - noLegal only.
recipe_number: "10"
canonical_url: documentation/wear/watch-file-manager.html
why: |
  A watch screen is about the size of a postage stamp, so a [file browser](term:file-browser) on it has to earn every pixel. On the watch, FastMediaSorter shows the same kinds of files in the same way wherever they live - on the watch itself, on your [phone](term:phone) or on a [network resource](term:network-resource) - and lets you do the everyday chores right there: pick a few files, send them to the phone, tidy up a name, delete what you no longer need.

  And when a file is simply too much for a small round screen - a PDF, a long text, an archive - one tap hands it to the phone, which opens it in the right viewer.
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "For anything that talks to the phone: the phone app in the Standard or noLegal edition with the [Wear Companion](term:wear-companion) switched on, and the phone nearby."
  - "For files on a server: a [network resource](term:network-resource) on the watch - see [phone and network folders on the watch](page:wear.phone-and-network-folders-on-watch)."
steps:
  - number: 1
    id: phone-by-type
    title: Open the phone's media by kind
    text: |
      On the watch's home screen, open **Phone**. Pick **Video**, **Audio**, **Images**, **Documents** or **All**, and the watch lists what the phone holds of that kind - no digging through folders first. Tap a file and the watch fetches it from the phone and opens it in its own player.

      The same categories carry the same names and colors on the watch's own storage and on your network resources, so once you know one list you know them all. Walking the phone's real folder tree, searching and sorting are covered in [phone and network folders on the watch](page:wear.phone-and-network-folders-on-watch).
    image_bookmark:
      shot_id: wear.phone-media-by-type
      device_profile: watch
      screen_state: wear-phone-section-audio-category-list
      alt: The Phone section on the watch opened on the Audio category, listing the phone's music files
      caption: "The phone's music, one tap from the watch's home screen."
      title: "Screenshot: Phone media by kind"
      desc: Round watch, Phone section, Audio category open, a list of the phone's audio files.
  - number: 2
    id: list-or-grid
    title: Choose a list or a grid of pictures
    text: |
      Every file list - the watch's own files and those of a network resource - can be shown as a **List**, a **Grid 2** or a **Grid 3**. Pick the one you like under **Files view** in the watch's settings; it is kept separately from **Screens view**, the look of the navigation screens, so a roomy list of sections and a dense grid of photos can live side by side.

      In a grid, each cell shows a real picture. For a photo on a server the watch reads only the first part of the file, where the camera tucked a small preview, so thumbnails appear without downloading whole photos over the air. A file with no preview shows a large icon of its kind - a note for music, a film frame for video - filling the cell, and the name underneath runs over two lines instead of being cut off after a few letters.

      In a list, each row carries a small colored icon of its kind beside the name. On a small watch where three columns would make every cell too small to hit reliably, **Grid 3** quietly falls back to two.
    image_bookmark:
      shot_id: wear.file-grid-thumbnails
      device_profile: watch
      screen_state: wear-network-file-list-grid3-thumbnails
      alt: A watch file list shown as a three-column grid with photo thumbnails, type icons for files without a preview and two-line file names
      caption: "A grid with real thumbnails, and two lines for every name."
      title: "Screenshot: File grid"
      desc: Round watch, network resource file list in Grid 3 mode, photo thumbnails, a music icon cell, two-line captions.
  - number: 3
    id: select-and-act
    title: Pick several files and act on them at once
    text: |
      Press and hold a file to start selecting. Tap more files to add them, or tap **Select all**. The top line counts them - **Selected: 3** - and the menu offers what these files allow:

      - **Send to phone** - a copy goes to the phone, the watch keeps its own.
      - **Move to phone** - the watch removes its copy only after the phone confirms the file arrived whole.
      - **Delete** - asks first, because **Deleting from the watch cannot be undone**.
      - **Rename** - type a **New name**; a name already taken is changed a little, and the watch shows the final one: **Saved as ..**.

      Delete and rename work on files the app keeps on the watch. Files from a network resource ([SMB](term:smb), [FTP](term:ftp) or [SFTP](term:sftp)) are read-only here - you can open and send them, but not change them on the server.
    image_bookmark:
      shot_id: wear.file-selection-actions
      device_profile: watch
      screen_state: wear-file-selection-menu-send-move-delete-rename
      alt: The watch file list with three files selected and a menu offering Send to phone, Move to phone, Delete and Rename
      caption: "Hold one file, tap the rest, then choose what to do."
      title: "Screenshot: Selected files"
      desc: Round watch, file list, three files marked Selected, action menu with Send to phone, Move to phone, Delete and Rename.
  - number: 4
    id: where-it-landed
    title: See where a sent file ended up
    text: |
      After **Send to phone** or **Move to phone**, the watch tells you for each file where it went:

      - **Saved to ..** with the folder name - the file is on the phone.
      - **Queued for upload to ..** - the phone is set to pass files from the watch on to a network or cloud folder, and the upload is under way in the background.
      - **No destination configured** - the phone has nowhere set up to put files from the watch yet.

      If a background upload fails later on, the watch gets an **Upload failed** notification naming the file and the place it was going to, so nothing disappears silently. Where files from the watch land is chosen on the phone - see [the watch as a resource on the phone](page:wear.phone-and-network-folders-on-watch).
    image_bookmark:
      shot_id: wear.send-outcome-destination
      device_profile: watch
      screen_state: wear-send-to-phone-outcome-queued-for-upload
      alt: The watch after sending a file to the phone, reporting Queued for upload to a named network folder
      caption: "The watch names the place each file went."
      title: "Screenshot: Where it landed"
      desc: Round watch, per-file result after Send to phone, one row reading Queued for upload to a network folder.
  - number: 5
    id: watch-own-files
    title: Tidy up photos and voice notes made on the watch
    text: |
      A photo you took with the watch or a voice note you recorded there lives in the watch's shared storage, and its press-and-hold menu offers **Delete**, **Rename** and **Move** as well as sending it to the phone. Because those files belong to the watch rather than to the app, the watch first shows its own system prompt asking you to allow the change, and the action finishes as soon as you answer.

      If your watch has no such prompt at all, the action simply isn't listed, so the menu never offers something that would only be refused. The menu for one file and the menu for a selection always show the same set.
    image_bookmark:
      shot_id: wear.watch-storage-file-menu
      device_profile: watch
      screen_state: wear-local-photo-menu-delete-rename-move
      alt: The press-and-hold menu of a photo taken on the watch, offering Send to phone, Delete, Rename and Move
      caption: "Your own watch photos can be renamed, moved and deleted too."
      title: "Screenshot: Watch file menu"
      desc: Round watch, Local section, press-and-hold menu of a photo shot on the watch with Delete, Rename and Move.
  - number: 6
    id: open-on-phone
    title: Open a file on the phone instead
    text: |
      Some files just don't belong on a wrist. In the watch's **Phone** section, tap a file the watch can't show - a PDF, a text file, an archive - or press and hold any file and pick **Open on phone**. Nothing is copied to the watch first, so the offer is there straight away.

      - If the phone app is already on screen, the file opens there at once and the watch says **Showing on your phone**.
      - If it isn't, the phone shows a notification naming the file, and the watch says **Tap the notification on your phone**. One tap on the phone opens the file in the right viewer.
      - If the phone's notifications for the app are switched off, the watch tells you exactly that - **Phone cannot show it - turn on its notifications for the app** - so you know the fix is on the phone, not the distance between you.
    image_bookmark:
      shot_id: wear.open-on-phone-result
      device_profile: watch
      screen_state: wear-open-on-phone-tap-notification-message
      alt: The watch after choosing Open on phone, showing the message Tap the notification on your phone
      caption: "Too big for the wrist? The phone takes over."
      title: "Screenshot: Open on phone"
      desc: Round watch, Phone section, message Tap the notification on your phone after Open on phone on a PDF.
  - number: 7
    id: filedo-containers
    title: Open a locked FileDO container
    text: |
      A FileDO container is a file sealed with a password - its name ends in `.fd-sec`. Tap one in a watch file list and the watch asks for the password, masked as you type. Enter it and the file inside opens in the usual viewer or player. The unlocked copy lives only in the app's private space on the watch and is wiped the moment you go back to the list.

      Tick **Remember password** before you open it, and from then on the watch tries that password first - a container sealed with it opens straight into the viewer. If the remembered password doesn't fit a container, the watch forgets it and says **The saved password did not fit and was forgotten. Enter the password.** The password is kept only on the watch, in its protected storage; it is never shared with the phone and never written to any log.

      To seal or unseal files on the watch itself, switch on **FileDO encryption** in the watch settings (it is off by default). A single file's menu then gains **Encrypt FileDO** and **Decrypt FileDO**. Encrypting asks for the password twice, keeps your original and reports **Encrypted. The original is still here.** An empty password is allowed, but the watch warns that it protects nothing.

      *noLegal version only:* a container on a network resource opens the same way, from both the flat list and a folder walk. The watch fetches it over [SMB](term:smb), [FTP](term:ftp) or [SFTP](term:sftp), asks for the password, opens what's inside and deletes the fetched copy right afterwards.
    image_bookmark:
      shot_id: wear.filedo-password-prompt
      device_profile: watch
      screen_state: wear-filedo-open-container-password-remember
      alt: The Open the container screen on the watch with a masked Password field and a Remember password option
      caption: "One password, and the container opens like any other file."
      title: "Screenshot: Open the container"
      desc: Round watch, Open the container screen, masked password field, Remember password checkbox.
outcome: |
  Your files on the watch, the phone and your servers behave the same way: you can see them as a list or a grid of real pictures, act on several at once, know where each one ended up, and pass anything too big for the wrist to the phone with one tap.
tips:
  - "**Can't rename a file from a server?** That's on purpose - files from SMB, FTP and SFTP are read-only on the watch. Send the file to the phone and rename it there."
  - "**Want more of a photo per cell?** Choose **Grid 2** instead of **Grid 3** under **Files view** in the watch's settings."
  - "**Sealing something private?** FileDO containers made on the watch open on the phone too - the phone uses the very same format."
  - "**Looking for the players themselves?** They have their own recipe: [players and viewers on the watch](page:wear.watch-players-and-viewers)."
next_recipes:
  - title: Phone and network folders on the watch
    url: page:wear.phone-and-network-folders-on-watch
    badge: Watch
    badge_type: docs
    description: Walk the phone's folders, add a server on the watch, copy files to the watch.
  - title: Playing music on the watch
    url: page:wear.standalone-music-playback
    badge: Watch
    badge_type: docs
    description: Put music on the watch and play it with the phone left at home.
  - title: Streaming radio on your wrist
    url: page:wear.wrist-stream-player
    badge: Watch
    badge_type: docs
    description: Find a live channel, pin it and listen right from the watch.
---

Browse the watch's own files, your [phone](term:phone)'s media and your servers as a list or a grid, act on several files at once, see where each one landed on the phone, hand a file to the phone to open it there, and unlock FileDO containers on the watch.
