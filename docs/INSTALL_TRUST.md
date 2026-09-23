---
layout: default
title: "Why Android warns about this APK"
permalink: /docs/INSTALL_TRUST_EN.html
---

# Why Android warns about this APK - and what to tap

FastMediaSorter is a free app from an independent developer, and the builds on the downloads page are
installed directly from a file instead of from an app store. Android treats a package it has not seen
before with extra caution, so you will meet one or two warnings on the way. Here is what each one says,
why it appears, and exactly what to do.

## What you will see

Two different screens, in this order.

**"For security, your phone currently isn't allowed to install unknown apps from this source."** This is
the browser or file manager asking for a one-time permission to hand the file to the installer. It is
about the app you downloaded *with*, not about FastMediaSorter.

**"Unsafe app blocked" / "Play Protect doesn't recognize this app's developer"** or, on some versions,
**"Scan app?"**. This is Google Play Protect, the scanner built into Android. It reports that the
package is new to it, not that it found anything in it.

## Why it appears

The APK is signed with our release key - the same key as the builds on Google Play - but a key earns
recognition from the number of installs Google has already seen. A build handed out directly, from a
GitHub release or a Drive mirror, does not pass through that count, so Play Protect meets a developer
it cannot vouch for and says so. That is the warning working correctly, not a mistake and not something
you should have been spared.

Two things follow from it. The warning has nothing to say about the contents of this particular file -
it is a statement about how many times Android has seen the signature. And it fades: as installs
accumulate, the recognition does too, which is why the same app from the Play Store is silent.

If you would rather not meet any of this, the Play Store edition is the same app without the direct
downloads - it simply carries fewer features, because some of them cannot be published to a store.

## What to tap

1. Open the downloaded `.apk` file. When the phone says the source is not allowed to install unknown
   apps, tap **Settings**, turn on **Allow from this source** for that one app, and go **back**.
2. Tap **Install**.
3. If Play Protect says it does not recognize the developer, tap **More details**, then **Install
   anyway**. If it offers to scan the app first, let it - the scan takes a moment and changes nothing
   else.

That is all. Two settings worth knowing about afterwards:

- The **Allow from this source** switch is per app and reversible. Turn it back off in
  **Settings > Apps > Special app access > Install unknown apps** once you are done, and Android will
  ask again next time.
- **Play Protect stays on.** Nothing on this page asks you to switch it off, to disable an antivirus, or
  to lower any device setting - none of that is needed to install this app, and we will never ask for
  it.

## What the app never does

These are the same facts as the [privacy policy](PRIVACY_POLICY.md) and the permission list in it.

- It has **no servers**. There is nowhere for your files or your settings to be sent, because we do not
  operate a backend of any kind.
- It contains **no analytics, no advertising and no tracking**. Nothing about how you use the app is
  measured or reported.
- Your files stay where they are - on the device, on your own network servers, or in the cloud account
  you connected. File operations run directly between your device and that storage.
- Credentials you enter for SMB, SFTP, FTP or a cloud account are encrypted on the device and are never
  transmitted anywhere except to the server they belong to.
- Every permission is asked for at the moment the feature behind it is used, is optional, and is
  explained in the privacy policy. Denying one disables that feature and nothing else.

## When the warning goes away

It fades on its own as more devices install the app. This page stays either way: people installing an
older build still meet the warning, and the page is the answer they are looking for.

Questions: [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) or <sza@ukr.net>.
