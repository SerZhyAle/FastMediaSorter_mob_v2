---
page_id: network.network-sync-and-cache
title: Keeping Network Folders Up to Date
nav_title: Keeping network folders up to date
description: Turning on periodic background sync for network folders, choosing how often it runs and preloading thumbnails while it does, and how a playing network video gets priority over preview loading so it never has to compete for bandwidth.
category: Network & Cloud
category_slug: network
ticket: S2950
flavor: Background sync - Standard, noLegal, Photos, Legacy and VR, not Lite, not FOSS. Video playback priority for thumbnails - Standard only.
recipe_number: "07"
canonical_url: documentation/network/network-sync-and-cache.html
why: |
  Someone else keeps adding photos to the shared folder on the home NAS, and you would rather the file list was already current when you open it than pull down to refresh every single time. FastMediaSorter can check your network and cloud [resources](term:resource) for changes by itself, on a schedule you set, and get their thumbnails ready in the background too - so opening one feels instant.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Photos, Legacy or VR [edition](term:edition) for background sync - not Lite, not FOSS. See [The seven editions](page:flavors.overview-and-comparison)."
  - "At least one [network folder](term:network-folder) or [cloud storage](term:cloud-storage) resource already added - see [Adding network folders and cloud storage as sources](page:storage.network-and-cloud-sources)."
steps:
  - number: 1
    id: enable-background-sync
    title: Let the app check for changes by itself
    text: |
      Open **Settings**, the **General** tab, and find **Background sync, network and cache**. Turn on **Enable background sync** - "Automatically check network resources for file changes in the background" - and set **Sync interval (min)** to how often it should run (four hours by default).

      Want it done right now instead of waiting? Tap **Sync Now**. The row shows "Syncing.." while it works, then something like "Synced 5 resources successfully" once it finishes, or "Couldn't finish the sync. Try again." if it did not. Underneath, the app always shows when it last managed it - "Last sync: " or "Never synced" the first time.
    image:
      src: assets/images/network/network-sync-background-sync-settings.png
      alt: The Background sync, network and cache section with the Enable background sync toggle, Sync interval field and Sync Now button
      caption: "Set how often the app checks your network folders by itself."
  - number: 2
    id: preload-thumbnails
    title: Have thumbnails ready before you open the folder
    text: |
      In the same section, **Preload thumbnails** - "Pre-generate thumbnails for network video and PDF files in the background after sync" - gets previews ready right after each sync instead of only when you scroll to a file. If you would rather this only happens on Wi-Fi, turn on **Wi-Fi only preload** too, so it never spends mobile data quietly generating previews you have not asked to see yet.
  - number: 3
    id: video-priority
    title: A playing network video always gets the bandwidth
    text: |
      *Standard edition.* While a video from a network folder is playing, the app pauses loading previews for the files around it in the background, so the video gets the connection to itself instead of sharing it with a dozen thumbnails at once. The moment playback ends, preview loading picks back up by itself, and those paused previews load correctly rather than getting stuck as if they had failed.
outcome: |
  Network and cloud folders stay current on their own, on the schedule you chose, their thumbnails are often ready before you even open them, and a playing network video never has to fight background preview loading for bandwidth.
tips:
  - "**Wondering how the app keeps network folders fast in the first place?** [Adding network folders and cloud storage as sources](page:storage.network-and-cloud-sources) explains the file-list and thumbnail caches this sync feeds."
  - "**Streaming cache is a separate setting.** How long a played video is kept ready for offline resume lives in the same overview page, not here."
  - "**Connecting the folders themselves?** See [Connecting SMB/Windows Shares](page:network.smb-samba-shares) and [Connecting SFTP and FTP Servers](page:network.sftp-ftp-servers)."
next_recipes:
  - title: Adding network folders and cloud storage as sources
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: What kinds of places you can add, and how the app's caches keep them quick.
  - title: Connecting SMB/Windows Shares
    url: page:network.smb-samba-shares
    badge: Network
    badge_type: docs
    description: The full walk-through for shared folders on Windows and NAS.
  - title: Checking Your Connection with Network Monitor
    url: page:network.network-monitor
    badge: Network
    badge_type: docs
    description: See the connection itself when a sync or a stream is not behaving.
---

Turn on a background schedule so network and cloud folders stay current on their own, preload their thumbnails ahead of time, and let a playing network video keep the full connection to itself while previews wait their turn.
