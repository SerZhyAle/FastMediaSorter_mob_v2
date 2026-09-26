---
page_id: settings.privacy-and-network-security
title: Privacy, Saved Sign-ins and Error Reports - Staying in Control and Reporting Problems Well
nav_title: Privacy and error reports
description: Reviewing and clearing saved web sign-ins and unused network credentials, keeping password screens off the Recents preview, reading a masked system diagnostic report, sending a crash report automatically or on your own terms, and finding a watch's log report in the phone's own log export.
category: "Settings & Navigation"
category_slug: settings
ticket: S2962
flavor: All editions - saved authorizations, unused credentials, secure sensitive screens, show detailed errors and the diagnostic report are in Standard, Lite, Photos and Legacy; watch log reports need Standard or noLegal with Wear Companion
recipe_number: "04"
canonical_url: documentation/settings/privacy-and-network-security.html
why: |
  A phone remembers more than you'd think on your own behalf - the sites it kept you signed into, the network passwords a deleted [resource](term:resource) left behind, the exact reason the last screen crashed. This page is about staying on top of that: seeing who's still signed in and clearing what's gone stale, keeping a password field off the Recents preview, and sending a report that actually helps - without digging through logs by hand.
ingredients:
  - "FastMediaSorter, any edition, with **Settings** open. See [Finding your way around Settings](page:settings.settings-overview-and-search) if this is your first visit."
steps:
  - number: 1
    id: saved-authorizations
    title: Review your saved sign-ins, and clear credentials nothing uses any more
    text: |
      In **Settings**, **General**, expand **Authorization and accounts**. Tap **Saved authorizations** to open the list of sign-in sessions the in-app web browser keeps when it auto-downloads from a site that needs you signed in - review them, and remove the ones you no longer need.

      **Unused credentials** sits right beside it, but only once it has something to show: a saved network password or key that belongs to no resource any more, once it has sat unused past the grace period, is counted here - the row reads, for example, "Unused credentials - 12." Tap it and you get a full list by address, port and user name, never the password, before anything is removed.
    image_bookmark:
      shot_id: settings.saved-authorizations-and-unused-credentials
      device_profile: phone
      screen_state: settings-general-authorization-accounts-section
      alt: The Authorization and accounts section in General settings with the Saved authorizations row and the Unused credentials button showing its count
      caption: "Saved sign-ins and unused credentials, in one section."
      title: "Screenshot: Authorization and accounts"
      desc: Settings, General tab, Authorization and accounts section expanded, Saved authorizations row and Unused credentials button visible.
    callout:
      type: warning
      title: Deleting unused credentials can't be undone
      text: "You'd have to enter that password or key again to reconnect. The confirmation lists every entry by address, port and user name first, so you can check before you commit."
  - number: 2
    id: secure-screens
    title: Keep passwords off the Recents preview
    text: |
      **Secure sensitive screens**, in **Settings**, **General**, is on by default. It blocks screenshots and the Recents app-switcher preview, but only on the screens that could actually show a password - adding or editing an SMB or SFTP/FTP resource, the credentials editor in Settings, the sign-in web view and the credential QR code. Everywhere else - picking a resource type, browsing a local or cloud folder - stays screenshotable as usual.
    image_bookmark:
      shot_id: settings.secure-sensitive-screens-toggle
      device_profile: phone
      screen_state: settings-general-secure-sensitive-screens
      alt: The Secure sensitive screens toggle in General settings, switched on, with its description text
      caption: "Secure sensitive screens, on by default."
      title: "Screenshot: Secure sensitive screens"
      desc: Settings, General tab, Secure sensitive screens row with its summary text, toggle on.
  - number: 3
    id: detailed-errors
    title: Ask for the technical detail behind an error
    text: |
      Turn on **Show detailed errors**, in **Settings**, **General**, and an error dialog stops summarizing - it shows the technical codes and context behind the message instead of the plain-language version. Leave it off for the friendlier message day to day, and switch it on when you need to describe a problem precisely, for example right before reporting it.
    image_bookmark:
      shot_id: settings.show-detailed-errors-toggle
      device_profile: phone
      screen_state: settings-general-show-detailed-errors
      alt: The Show detailed errors toggle in General settings with its description text
      caption: "Show detailed errors, for the full picture."
      title: "Screenshot: Show detailed errors"
      desc: Settings, General tab, Show detailed errors row, toggle highlighted.
  - number: 4
    id: diagnostic-report
    title: Pull a diagnostic report before you ask for help
    text: |
      **About system**, in **Settings**, **General**, under **Debug logs and test tools**, gathers a masked system report you can read right there in the dialog - version and device details, without the values that could identify or locate you. Tap **Copy full report** to copy that masked version; if whoever is helping you genuinely needs the sensitive values too - the signature hash, local addresses, mount paths - confirming the follow-up prompt copies the unmasked report instead.
    image_bookmark:
      shot_id: settings.diagnostic-report-dialog
      device_profile: phone
      screen_state: settings-about-system-report-dialog
      alt: The About system dialog showing the masked system report with the Copy full report button and its reveal confirmation
      caption: "A masked report, one tap from Copy full report."
      title: "Screenshot: System diagnostic report"
      desc: About system dialog open with the masked report visible and the reveal confirmation dialog layered on top.
  - number: 5
    id: crash-reports
    title: Send a crash report, automatically or on your own terms
    text: |
      If the app closes unexpectedly, the next launch offers to send one: "**Send crash report?** The app closed unexpectedly last time. Send a crash report with the app log to the author?" Say yes and your mail app opens with the details and the log already attached - if nothing on the device can send mail, the report simply stays saved locally instead of the offer going nowhere.

      You don't have to wait for a crash to do the same thing: whenever an error dialog is showing a real exception rather than just a message, its **Email crash report to author** button sends the same package on demand.
    image_bookmark:
      shot_id: settings.crash-report-restart-prompt
      device_profile: phone
      screen_state: crash-report-prompt-after-restart
      alt: The Send crash report prompt shown on the next launch after the app closed unexpectedly
      caption: "Offered once, right after a crash."
      title: "Screenshot: Crash report prompt"
      desc: Send crash report dialog shown on app launch following a previous crash.
  - number: 6
    id: watch-logs
    title: A watch's log report travels with the phone's own logs
    text: |
      *Standard and noLegal editions, with [Wear Companion](term:wear-companion).* A log report your paired [watch](term:watch) sends over lands beside the phone's own logs, and it stays reachable even after you dismiss its arrival notification: both **Share Debug Logs** and **Save Debug Logs**, in **Settings**, **General**, **Debug logs and test tools**, fold it into the archive they produce.
    image_bookmark:
      shot_id: settings.watch-log-in-export
      device_profile: phone
      screen_state: settings-debug-logs-watch-report-included
      alt: The Debug logs and test tools section with Share Debug Logs and Save Debug Logs, a watch log report included among the phone's own logs
      caption: "A watch's log report, folded into the phone's own export."
      title: "Screenshot: Watch logs in export"
      desc: Settings, General tab, Debug logs and test tools section, a watch-originated log report included alongside the phone's own logs.
outcome: |
  You know who's still signed in and what's gone stale, password screens stay out of the Recents preview, a diagnostic report is one tap away before you ask for help, and a crash report reaches the author - automatically after a restart or by hand from any error dialog, watch logs included.
tips:
  - "**Not sure which group a setting lives in?** The search overlay in [Finding your way around Settings](page:settings.settings-overview-and-search) finds any of these by keyword."
  - "**Backing up more than one report?** [Backing up and syncing your device](page:settings.backup-and-device-sync) covers exporting the whole settings picture, not just a single diagnostic."
  - "**Navigating this page with a keyboard, D-pad or remote?** See [Controls and key remapping](page:settings.controls-and-key-remapping)."
next_recipes:
  - title: Finding your way around Settings
    url: page:settings.settings-overview-and-search
    badge: Settings
    badge_type: docs
    description: Collapsible groups, keyword search and the consistent row pattern behind every setting.
  - title: Backing up and syncing your device
    url: page:settings.backup-and-device-sync
    badge: Settings
    badge_type: docs
    description: Exporting and restoring settings, resources and favorites, and keeping devices in step.
  - title: Controls and key remapping
    url: page:settings.controls-and-key-remapping
    badge: Settings
    badge_type: docs
    description: Remapping keys, D-pad and gamepad navigation, and the travelling focus frame.
---

Review and clear saved sign-ins and unused network credentials, keep password screens out of the Recents preview, read a masked diagnostic report before you ask for help, and send a crash report - automatically after a restart, by hand from any error dialog, or one that arrived from your [watch](term:watch). This page covers all of it, from [Settings](term:settings), **General**.
