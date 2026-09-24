---
page_id: storage.storage-sources-setup
title: Adding Folders from Your Phone, Memory Card and USB Drive
nav_title: Adding folders from this device
description: How to add a folder from the phone, a memory card or a USB drive as a resource, what the ready-made collections such as All Images and Camera Photos are, why some folders show only photos, video and audio, and how Reconnect resource fixes that.
category: Sources, Destinations & File Operations
category_slug: storage
ticket: S2949
flavor: All editions
recipe_number: "01"
canonical_url: documentation/storage/storage-sources-setup.html
why: |
  Your photos from the last holiday sit in the camera folder, the music you copied years ago lives on the memory card, and a USB stick from a friend is plugged into the phone. FastMediaSorter shows none of them until you tell it where to look. Every place you add becomes a [resource](term:resource) - one row or tile on the [main screen](term:main-screen) that opens straight into that folder.

  Adding a [local folder](term:local-folder) takes a minute and changes nothing in your files. Removing the resource later removes only the app's entry; the folder and everything in it stay where they are.
ingredients:
  - "FastMediaSorter in any [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR or FOSS. Adding a folder from the device works in all of them."
  - "The permission to read your media. The app asks for it on first launch; see [Understanding app permissions](page:getting-started.permissions-guide) if you said no."
  - "For a memory card or a USB drive: the card inserted or the drive connected (a USB drive usually needs a small USB-C or OTG adapter)."
steps:
  - number: 1
    id: ready-made
    title: Look at what is already there
    text: |
      You do not have to add anything to get started. On first launch the app creates a few ready-made [virtual resources](term:virtual-resource) that collect files of one kind from all over the device:

      - **Recent Media** - the media files you opened lately.
      - **All Music**, **All Videos** and **All Images** - every song, video or picture on the device, whatever folder it is in.
      - **Camera Photos** - the photos and videos from the camera, newest first, shown as tiles.
      - **All Documents** - PDF, e-book and text files, in the editions that open documents.

      A collection appears only when your edition supports that kind of file. If you deleted one by mistake, it comes back by itself the next time the app starts.
    image_bookmark:
      shot_id: storage.ready-made-resources
      device_profile: phone
      screen_state: main-screen-default-virtual-resources
      alt: The main screen right after first launch, showing Recent Media, All Music, All Videos, Camera Photos, All Images and All Documents
      caption: "The ready-made collections on a fresh install."
      title: "Screenshot: Ready-made resources"
      desc: Main screen, fresh install, list view with the six virtual resources.
  - number: 2
    id: open-add
    title: Open the Add Resource screen
    text: |
      On the main screen tap **Add** on the [command bar](term:command-bar). The **Add Resource** screen opens with the heading **Select Resource Type** and a card for each kind of place: **Local Folder**, **Network Folder**, **SFTP / FTP**, **Cloud Storage** and **Stream**, as far as your edition has them.

      Tap **Local Folder** - "Add folders from device storage". In the editions where no network or cloud places exist, or when you have switched them all off, the app skips this choice and opens the local folder screen straight away.
    image_bookmark:
      shot_id: storage.add-resource-type-picker
      device_profile: phone
      screen_state: add-resource-select-type
      alt: The Add Resource screen with the cards Local Folder, Network Folder, SFTP / FTP, Cloud Storage and Stream, and the Import a ready configuration buttons below
      caption: "Choose what kind of place to add."
      title: "Screenshot: Select Resource Type"
      desc: Add Resource screen, type picker with all cards visible, Standard edition.
  - number: 3
    id: pick-folder
    title: Pick the folder
    text: |
      The **Add Local Folder** screen offers two ways:

      - **Scan Local** - the app searches the device for folders that hold media and lists them. Tick the ones you want.
      - **Add Local Manually** - opens the **Select Folder** window, where you choose one folder yourself.

      In the **Select Folder** window you find:

      - Quick buttons for the ready-made collections, grayed out when they are already added.
      - Quick buttons for well-known folders: the root of the internal storage, **DCIM**, **Pictures**, **Download**, **Documents** and the media folders of WhatsApp, Telegram and Instagram.
      - One button for each memory card or USB drive that is connected right now, with its name and free space.
      - **Browse with SAF** - opens Android's own folder window, where you can walk to any folder and tap **Use this folder**.
    image_bookmark:
      shot_id: storage.select-folder-dialog
      device_profile: phone
      screen_state: add-local-select-folder-dialog
      alt: The Select Folder window with quick buttons for DCIM, Pictures and Download, a button for an SD card with its free space, and the Browse with SAF button
      caption: "The Select Folder window."
      title: "Screenshot: Select Folder window"
      desc: Select Folder dialog open, an SD card inserted so its volume button shows.
    callout:
      type: tip
      title: A memory card or USB drive
      text: "Tap the button with the card's or drive's name. If Android has not yet given the app access to it, the app explains why it needs access and then opens Android's folder window on that card - just tap **Use this folder** there. A USB drive disappears from the list when you unplug it, and its resource shows as unavailable until you plug it in again."
  - number: 4
    id: options
    title: Choose how the folder is read
    text: |
      Before you confirm, look at the options under the folder:

      - **Scan subdirectories** (on by default) - also shows the files inside every folder within this one. Turn it off to see only the files that lie directly in the chosen folder.
      - **Add to Quick Sort** - makes this folder a [destination](term:destination), so you can later copy or move files into it with one tap (see [Sorting files into destinations](page:storage.destination-targets-setup)).

      Confirm, and the new resource appears in the [resource list](term:resource-list). The app counts its files in the background and shows the number on the resource.

      Later you can change everything - the name, the kinds of files shown, a [PIN](term:pin) - by long-pressing the resource and choosing **Edit**. With **All Files** mode on for a resource, the editor also offers **Show Hidden Files** for files whose names start with a dot.
  - number: 5
    id: limited
    title: When a folder shows only photos, video and audio
    text: |
      Since Android 11 an app sees every kind of file in a folder only if it holds the special "access to all files" permission, or if you picked the folder yourself in Android's folder window. Some editions never ask for "access to all files", and in the others you may have said no, so a folder added by its path shows only photos, video and audio. The app tells you so instead of pretending the folder is complete:

      - The file count on the resource reads, for example, "124 - photos, video and audio only". The All Documents collection shows "Documents cannot be read" instead of a zero.
      - The first time you open such a folder, a note at the top explains how to see the documents too.
      - The filter does not offer document types that the folder cannot return.
  - number: 6
    id: reconnect
    title: Reconnect a folder to see every file
    text: |
      To let the app see documents and other files in that folder, give it the folder through Android's folder window:

      1. On the main screen tap the [three-dots menu](term:three-dots-menu) on the folder's row.
      2. Tap **Reconnect resource**.
      3. Android's folder window opens. Walk to the same folder and tap **Use this folder**.

      The resource keeps its name, its [Favorites](term:favorites), its place in Quick Sort and its [scheduled operations](term:scheduled-operation) - only the way the app reaches the folder changes. **Reconnect resource** appears only for folders that need it, and only on Android 11 or newer.
    image_bookmark:
      shot_id: storage.reconnect-resource-menu
      device_profile: phone
      screen_state: main-resource-row-menu-reconnect
      alt: The three-dots menu of a local folder on the main screen with the Reconnect resource item highlighted
      caption: "Reconnect resource in the folder's menu."
      title: "Screenshot: Reconnect resource"
      desc: Main screen, row menu of a path-based local folder open on Android 13, Play Store edition.
outcome: |
  Your camera roll, your music folder, the memory card and the USB stick each have their own resource on the main screen. One tap opens any of them in the [file browser](term:file-browser), and the ready-made collections keep gathering new files by themselves.
tips:
  - "**One Resource Profile dialog.** Choosing a [resource profile](term:resource-profile) opens the same **Resource Profile** dialog when you add a resource and when you edit it; the File Manager profile keeps the switches for the kinds of files visible, and each preset sets the same kinds of files wherever you pick it."
  - "**Removing a resource never deletes files.** Long-press it and remove it: only the app's entry goes away."
  - "**Too many folders from one scan?** Untick the ones you do not need before you confirm - you can always add a folder later."
  - "**Folders on another computer or in the cloud** are added from the same Add Resource screen - see [Adding network folders and cloud storage](page:storage.network-and-cloud-sources)."
  - "**Keep a folder private** with a PIN - see [Protecting files with a PIN and encryption](page:storage.file-encryption-and-security)."
next_recipes:
  - title: Adding network folders and cloud storage
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: Add a shared folder on your home computer, a server or Google Drive.
  - title: Sorting files into destinations
    url: page:storage.destination-targets-setup
    badge: Storage
    badge_type: other
    description: Turn your folders into one-tap targets for sorting photos.
  - title: Browsing media collections
    url: page:browsing.grid-and-list-views
    badge: Browsing
    badge_type: image
    description: Look through a resource as tiles or as a list.
---

Add a folder from the phone, a memory card or a USB drive as a resource, meet the ready-made collections the app creates for you, and let a folder show every kind of file with Reconnect resource.
