---
layout: doc
title: "Support for Fast Media Sorter"
description: "How to report a problem with Fast Media Sorter, what to include, the private contact, what answer to expect and how to send the app's diagnostic log."
permalink: /docs/SUPPORT.html
---
<sub class="doc-stamp">26.10.18 20:00</sub>

# Support

Fast Media Sorter is written and maintained by one developer. This page says where to ask, what makes a report useful, which address is private and how to send the app's log when it is needed.

## Where to ask

- **Public questions and bug reports:** the [issue tracker on GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues). Anyone can read it, and a solved issue helps the next person with the same problem.
- **Anything you would not post in public:** email **[sza@ukr.net](mailto:sza@ukr.net)**. This is the one private contact - use it for a security problem, for a log you do not want to publish, or for anything personal.
- **From the app:** the email links in the app open your mail app with the product name and the version already in the subject. Nothing is sent until you press Send there.

Before writing, the [troubleshooting guide](TROUBLESHOOTING.html) and the [FAQ](FAQ.html) answer the most common questions.

## What makes a report useful

- **The exact version.** In Settings, the General tab, the bottom line shows the version and the build, for example `2.60.9240.148 | Build 260924014`. The version tells exactly which build you run, from a store or from an APK file.
- **The device and Android version**, for example a phone model and Android 14.
- **What you did, step by step**, what you expected and what happened instead.
- **Whether it happens every time** or only sometimes.
- **A screenshot or a short screen recording** when the problem is on screen.
- **The diagnostic log** when something fails or the app closes - see below.

## Sending the diagnostic log

The app keeps a log on the device: a record of what it was doing and which errors happened.

1. Repeat the action that fails once more, so the log catches it.
2. Open Settings, the General tab, the group Backups, restore and settings export.
3. Choose **Share Debug Logs** to pack the log into one archive, `fastmediasorter_logs.zip`, and pick your mail app or messenger - or **Save Debug Logs** to save the archive to a folder you choose.

What the log contains and what it does not:

- It records events, errors, file names and the addresses of the servers you connected to.
- Passwords, keys and tokens are replaced by stars before a line is written.
- It never leaves the device on its own: nothing is uploaded, and only you choose where the archive goes.
- File names and server addresses can still be private. Send the archive by email rather than attaching it to a public issue, and do not paste log lines into a public report.

The full walk-through, including a report from the watch, is in [Device diagnostics and logs](../documentation/programs/device-diagnostics-and-logs.html). What the app stores and sends is described in the [privacy policy](PRIVACY_POLICY.html).

## What answer to expect

- Every message and issue is read.
- There is no fixed response time: one person answers, so it can take a day or several weeks.
- A fix arrives in a later release; the [release notes](WHATS_NEW.html) list what changed.
- If a problem cannot be reproduced, you may be asked for the log or for more steps.
