---
page_id: network.cloud-dropbox-onedrive
title: Dropbox and OneDrive
nav_title: Dropbox and OneDrive
description: Signing in to Dropbox and OneDrive and adding their folders as resources, what the app tells you if a sign-in is canceled or a connection breaks, and where to look when a Microsoft account needs signing in again after a new phone.
category: Сетевые папки и облака
category_slug: network
ticket: S2950
flavor: Standard, noLegal, Photos, Legacy and VR - not Lite, not FOSS. Needs a Dropbox and/or Microsoft account.
recipe_number: "06"
canonical_url: documentation/network/cloud-dropbox-onedrive-ru.html
why: |
  Work files live in OneDrive, personal photos are backed up to Dropbox, and you would rather browse both from inside FastMediaSorter than juggle two more apps. Sign in to either one, or both, and their folders sit on the main screen next to everything else.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Photos, Legacy or VR [edition](term:edition) - not Lite, not FOSS. See [The seven editions](page:flavors.overview-and-comparison)."
  - "A [Dropbox](term:dropbox) account, a Microsoft account for [OneDrive](term:onedrive), or both."
steps:
  - number: 1
    id: dropbox
    title: Add a Dropbox folder
    text: |
      On the main screen tap **Add**, then **Cloud Storage**, and choose **Dropbox** on the **Select Cloud Provider** screen. Dropbox's own sign-in window opens; approve it there, and the app shows "Signed in: " with your account. A folder picker - Dropbox's own - opens next so you can choose the folder to add.

      Each folder becomes its own [resource](term:resource) on the main screen. Tap **Dropbox** again later, once you are already signed in, and a dialog offers **Select Folder** to add another one or **Sign out** to end the session; **Cancel** just backs out.
  - number: 2
    id: onedrive
    title: Add a OneDrive folder
    text: |
      Tap **Add**, then **Cloud Storage**, and choose **OneDrive**. Microsoft's sign-in window opens; approve it, and the app shows "Signed in: " with your account. Tap **Select Folder** and pick the OneDrive folder you want - it opens as a resource of its own, exactly like a Dropbox or Google Drive folder does.

      The same already-signed-in dialog appears if you tap **OneDrive** again: **Select Folder** for another one, **Sign out** to end the session.
    image_bookmark:
      shot_id: network.cloud-onedrive-select-folder
      device_profile: phone
      screen_state: onedrive-select-folder-picker
      alt: The Select Folder screen for OneDrive, showing the signed-in account's folder tree with one folder highlighted
      caption: "Pick the OneDrive folder you want to see in the app."
      title: "Screenshot: OneDrive Select Folder"
      desc: Add Resource, Cloud Storage, OneDrive chosen and signed in, Select Folder screen open with a folder tree, one subfolder highlighted.
  - number: 3
    id: sign-in-problems
    title: What the app says when a sign-in does not go through
    text: |
      Close the sign-in window before finishing and the app says "Dropbox authentication canceled" or "OneDrive authentication canceled" - nothing is added, and you can just try again. A sign-in that fails outright is reported as "Dropbox authentication failed" or "OneDrive authentication failed" rather than a bare error, and if a folder you already added stops answering, the app names it: "Connection failed: " with the reason.
outcome: |
  Dropbox and OneDrive folders sit on the main screen next to your local and Google Drive ones, sign-in and sign-out work the same simple way for both, and a canceled or failed sign-in tells you plainly instead of leaving you guessing.
tips:
  - "**Moved to a new phone?** OneDrive keeps its sign-in in a place the app cannot carry over automatically, so you sign in to OneDrive once more there - see the new-phone notes in [Adding network folders and cloud storage as sources](page:storage.network-and-cloud-sources)."
  - "**Looking for Google Drive?** It gets its own page, with account binding and the cross-device transfer queue that rides on it - see [Google Drive Integration](page:network.cloud-google-drive)."
  - "**Adding network folders and cloud storage in general** is covered from the top in [Adding network folders and cloud storage as sources](page:storage.network-and-cloud-sources)."
next_recipes:
  - title: Google Drive Integration
    url: page:network.cloud-google-drive
    badge: Network
    badge_type: docs
    description: Sign in to Google Drive, plus the settings backup and cross-device queue it unlocks.
  - title: Adding network folders and cloud storage as sources
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: The overview of every kind of place you can add as a resource.
  - title: Backing up your settings and keeping devices in sync
    url: page:settings.backup-and-device-sync
    badge: Settings
    badge_type: docs
    description: The full backup and restore picture for your settings and resources.
---

Sign in to Dropbox and OneDrive to add their folders as resources next to your local and Google Drive ones, with the same simple sign-in, sign-out and plain-spoken error messages for both [cloud providers](term:cloud-provider).
