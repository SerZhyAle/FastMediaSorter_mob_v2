---
page_id: wear.phone-and-network-folders-on-watch
title: Opening the Phone's and Network Folders on the Watch
nav_title: Phone and network folders on the watch
description: How to browse your phone's folders and favorites from the watch, add and manage network resources on the watch itself, walk into subfolders, and copy, move or send files between the phone and the watch.
category: Wear OS Watch
category_slug: wear
ticket: S2966
flavor: Phone side - Standard and noLegal; browsing the phone, adding a network resource and everything else on this page - the full watch version (sideload only). Testing an already-saved connection also works in the Google Play watch app.
recipe_number: "08"
canonical_url: documentation/wear/phone-and-network-folders-on-watch.html
why: |
  Your phone already holds most of what you own - photos in a dozen folders, the NAS in the living room, the FTP share at work. The watch doesn't need its own copy of any of it: it can open your [phone](term:phone)'s [folders](term:folder) directly, add its own [network folders](term:network-folder), and step into subfolders like a small [file browser](term:file-browser) on your wrist.

  It works the other way too. The watch itself can sit in the phone's own [resource](term:resource) list, so a file can travel either direction without ever going near a cloud service.
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app for most of this recipe. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "At least one [network resource](term:network-resource) reaching the watch - sent from the phone as described in [syncing the phone and the watch](page:wear.companion-data-sync), or typed in on the watch itself."
  - "For browsing the phone's own folders: [Wear Companion](term:wear-companion) switched on and the phone nearby."
steps:
  - number: 1
    id: phone-section
    title: Browse what's on your phone, right from your wrist
    text: |
      Open **Phone** on the watch's home screen. It offers the same shape everywhere on the watch: **Recents**, **Video**, **Audio**, **Images**, **Documents**, **All** and **Browse** - the exact same names and colors your watch's own storage and your network folders use, so a category means the same thing wherever you meet it. **All** is a flat list of everything of that kind, newest first; **Browse** walks the phone's actual folders one level at a time, titled with whichever folder you're standing in - the section name only shows at the root. The same screen also lists your local [favorites](term:favorites), so a file you starred earlier is never more than a scroll away.

      Folders in **Browse** get a cover picture too, and the whole screen follows your chosen list or grid view. Thumbnails load reliably - a stalled first attempt quietly retries instead of leaving a blank square, and the top row stays clear of the search and sort icons so nothing overlaps them.

      Tap a photo under [Camera Photos](term:camera-photos) or [All Images](term:all-images) on the phone and it opens right there on the watch - no detour through a path the phone never gave the watch in the first place.

      If a category opens empty, the watch tells you why: a folder with nothing in it right now reads **Nothing here yet**, while a category no resource on your phone is even set up to hold says **No phone resource is set up to hold this type** and offers no pointless retry button.
    image_bookmark:
      shot_id: wear.phone-section-grid
      device_profile: watch
      screen_state: wear-phone-section-categories-grid
      alt: The Phone section of the watch app with Recents, Video, Audio, Images, Documents, All and Browse categories
      caption: "The Phone section - the same category names everywhere on the watch."
      title: "Screenshot: Phone section"
      desc: Round watch, Phone section grid with seven categories and thumbnail folders.
  - number: 2
    id: refine-list
    title: Search, filter and sort what you're looking at
    text: |
      Every screen that lists files - your phone's, a network resource's, or the watch's own - carries small search and refine icons above the list. They tuck themselves out of the way while you scroll and come back the moment you stop, so they never crowd the files underneath.

      Tap the search icon to type or speak a name; tap the refine icon to open **Refine list**, one screen with **Sort** and **Show only** as plain, full-label lists instead of a cramped row of icons. If a list holds only one kind of file, it says so - "This list holds one kind of file, so there is nothing to filter by" - instead of offering a filter with nothing to filter by.

      Your type filter and sort order are remembered for next time, even after restarting the watch; a typed search is not, since a hidden query would silently leave you staring at an empty screen. The phone's own folders offer five sort orders instead of seven, because the phone never sends the watch a date for them.
    image_bookmark:
      shot_id: wear.refine-menu-sheet
      device_profile: watch
      screen_state: wear-refine-menu-sort-and-filter
      alt: The Refine list screen on the watch with Sort and Show only groups listed as full-label rows
      caption: "One screen for sorting and filtering, in full words."
      title: "Screenshot: Refine list"
      desc: Round watch, Refine list menu, Sort group and Show only group expanded.
  - number: 3
    id: add-network-source
    title: Add a network folder without touching the phone
    text: |
      *Sideload version only.* On **Resources**, tap **Add resource**. Pick a **Protocol** - **SMB**, **FTP** or **SFTP** - and fill in **Name**, **Server Address**, **Port**, **Username** and **Password**, plus **Share Name**, **Domain** or **Base path** where the protocol needs them. Every field is its own full-width row showing its name and what's typed into it, one column whatever view mode you've chosen, so there's nothing to pinch or scroll sideways for.

      Tap **Test** before **Save** to make sure the watch can actually reach it - the same check the saved-source menu uses later.
    image_bookmark:
      shot_id: wear.add-network-source-column
      device_profile: watch
      screen_state: wear-add-network-source-smb-form
      alt: The Add resource screen on the watch with Protocol, Name, Server Address, Port, Username and Password rows and Test and Save buttons
      caption: "Typing a network folder in, one field per row."
      title: "Screenshot: Add resource"
      desc: Round watch, Add resource form, SMB protocol selected, full-width fields.
  - number: 4
    id: manage-connections
    title: Keep a saved connection healthy - and in step with the phone
    text: |
      Press and hold a saved source and the watch offers **Test** next to **Delete** instead of heading straight for deletion - so a share that stopped answering can be checked without retyping it. **Test** runs in the free Google Play version too, even though the form that adds a source in the first place does not.

      When a connection genuinely fails, the watch names the reason instead of guessing: refused, timed out, wrong user name or password, or the computer isn't found on this network. Behind the scenes it tries every address the phone knows for that resource in turn and settles on whichever one answers - the same trick that keeps a moved home server working.

      A network resource that only holds file kinds the watch cannot show says exactly that - "This resource holds only file kinds the watch cannot show. Open it on the phone." - rather than blaming a media-type switch you never touched.

      Resources stay in step with the phone on their own: delete one on either device and it disappears from the other at the next sync; clear its tick on the phone's **Resources for the watch** screen and the watch drops it too, with no re-adding needed if you tick it again. That screen only ever lists SMB, FTP and SFTP sources, since those are the only kinds the watch channel can carry.
    image_bookmark:
      shot_id: wear.saved-source-actions-dialog
      device_profile: watch
      screen_state: wear-network-source-test-or-delete-dialog
      alt: A saved network source on the watch with a dialog offering Test and Delete after a press and hold
      caption: "Press and hold a saved source to test or delete it."
      title: "Screenshot: Test or delete a source"
      desc: Round watch, saved SMB source, action dialog with Test chip and Delete as the destructive action.
  - number: 5
    id: walk-folders
    title: Walk into subfolders, on the watch or on a share
    text: |
      Both the watch's own storage and a network resource offer **Browse** beside their flat category lists. On the watch itself, **Browse** covers the app's own files plus anything copied over from the phone that the system's own media index never sees, and rebuilds the folder structure of your shared storage on the fly. On a network resource, **Browse** opens the same way over SMB, FTP or SFTP: subfolders are their own rows, tapping one shows its contents, the back gesture takes you a level up, and tapping a file opens the player. Thumbnails over FTP and SFTP load properly here too, instead of sitting on "Unavailable".
    image_bookmark:
      shot_id: wear.local-storage-browse-entry
      device_profile: watch
      screen_state: wear-local-storage-browse-folder-walk
      alt: The watch's own storage list with a Browse entry above six flat categories, opened one level into a folder
      caption: "Browse walks folders one level at a time."
      title: "Screenshot: Browse"
      desc: Round watch, local storage Browse entry, one folder opened showing subfolders and files.
  - number: 6
    id: open-and-send
    title: Let the phone open something on the watch, and send it onward from there
    text: |
      Pick your watch in the phone's **Send to..** menu and whatever you have open there - a photo, a GIF, a video or a track - opens on the watch. If the watch app was closed and its screen dark, you'd once have found nothing until you opened the app by hand; now the watch raises a notification - **The phone sent something to open** - and one tap plays it straight away. With the watch app already open, the player just starts, no notification needed.

      Once a file is in front of you on the watch, its own menu opens with **Send to..** - the very same list your phone offers: email, messengers, printing, the clipboard, the system share. It's built once on the phone, so switching a receiver off there removes it from the watch too, and a receiver the watch can handle by itself works with no phone nearby; one it can't is marked "via phone" and says so before it starts, never as a failure afterward.
    image_bookmark:
      shot_id: wear.open-on-watch-notification
      device_profile: watch
      screen_state: wear-notification-phone-sent-something-to-open
      alt: A watch notification reading The phone sent something to open, with a tap target that opens the player
      caption: "A notification instead of a silent arrival."
      title: "Screenshot: Something arrived"
      desc: Round watch, notification shade, The phone sent something to open notification.
  - number: 7
    id: copy-to-watch
    title: Keep a copy on the watch itself
    text: |
      A photo, track or video you've already opened from the phone, or one browsed over SMB, FTP or SFTP, can move in properly: open its menu and pick **Copy to watch** or **Move to watch**. The copy lands in the watch's own storage next to your other pictures, music and videos, so it shows up in the local lists afterward and survives closing the app. A name already taken gets a new one, and the watch tells you which.

      A move only drops the original once the watch has confirmed the copy is whole and correctly sized. If the original can't be removed - the phone needs a confirmation dialog it can't show remotely, the file changed since it was copied, the share is read-only, or the phone stays silent - nothing is lost: the copy stays and the watch says **Copied to the watch - the original stayed where it was**. Not enough room on the watch refuses before anything is written, and documents never offer this, since the watch's own picture, music and video lists have nowhere to put them.

      A file that came from the phone this way no longer offers **Send to phone** or **Move to phone**, since the phone still has the original; a file the watch recorded or shot itself keeps both.
    image_bookmark:
      shot_id: wear.copy-to-watch-menu
      device_profile: watch
      screen_state: wear-file-menu-copy-to-watch-move-to-watch
      alt: The watch file menu with Copy to watch and Move to watch entries on a phone file
      caption: "Copy to watch and Move to watch, in the file menu."
      title: "Screenshot: Copy to watch"
      desc: Round watch, file menu open on a phone photo, Copy to watch and Move to watch rows visible.
  - number: 8
    id: watch-as-resource
    title: The watch is one more resource on the phone
    text: |
      It runs the other way too: add your paired watch as its own resource on the phone's main screen, right beside your local and cloud ones. Browse whatever the watch holds, copy or move files onto it from any phone screen, and anything the watch sends back lands wherever you choose. Each transfer is capped at 32 MB and reports its outcome through the usual transfer notification, the same one every other copy or move uses.
    image_bookmark:
      shot_id: wear.watch-resource-card
      device_profile: phone
      screen_state: phone-main-screen-paired-watch-resource
      alt: The phone's main screen resource list with a Paired watch card among the local and cloud resources
      caption: "The paired watch, listed as a resource."
      title: "Screenshot: Watch as a resource"
      desc: Phone, main screen resource list, Paired watch card visible among local and network resources.
outcome: |
  Your phone's photos, your NAS, the FTP share at work and anything you copy over live on the watch too - browsable, searchable, and just as reachable when the phone is out of the room.
tips:
  - "**Adding a network folder needs the full watch app.** The free Google Play version can still test a source that's already there - just not add one or browse the phone."
  - "**A folder full of odd files?** \"This resource holds only file kinds the watch cannot show\" means exactly that - open it on the phone instead, nothing was switched off."
  - "**Want the whole library, not one folder?** Send more resources over from [syncing the phone and the watch](page:wear.companion-data-sync)."
next_recipes:
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: Push network resources to the watch, or pull them back from your wrist.
  - title: Browsing files on the watch
    url: page:wear.watch-file-manager
    badge: Watch
    badge_type: docs
    description: The watch's own storage, side by side with the phone's and the network's.
  - title: Tiles and complications
    url: page:wear.tiles-and-complications
    badge: Watch
    badge_type: docs
    description: Pin your favorite network folder one swipe from the watch face.
---

Browse your [phone](term:phone)'s folders and your [network resources](term:network-resource) right from the watch, add a new one by hand, and copy, move or send a file between the two devices - or add the watch itself as a resource on the phone.
