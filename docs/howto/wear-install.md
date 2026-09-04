---
layout: default
title: "Put FastMedia on Your Watch - FastMediaSorter v2"
permalink: /docs/howto/wear-install.html
---
# <img src="../icons/doc/ic_display.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Put FastMedia on Your Watch

> **Level:** Beginner &bull; **Time:** ~5 minutes &bull; **Device:** Wear OS smartwatch paired with an Android phone

[Русский](wear-install-ru.md) | [Українська](wear-install-uk.md)

FastMedia Wear is the watch half of FastMediaSorter. Once it is on your wrist you can play music and view photos straight from the watch, reach folders shared by your paired phone, and open network shares the watch connects to on its own. This page gets it installed and paired.

---

## What You Will Need

- A smartwatch running **Wear OS 3.0** or newer
- An Android phone with FastMediaSorter installed and the watch already paired to it in the system settings
- A Wi-Fi or mobile connection on the watch, or on the phone it is paired with, for the download

---

## Step 1 - Install FastMedia Wear on the Watch

1. On the watch, open the apps list and tap **Play Store**.
2. Search for **FastMedia Wear**.
3. Tap **Install** and wait for the download to finish. The watch will show the app in its apps list when it is done.

> Watches vary in how much they let you type. If searching on the wrist is awkward, open the Play Store on your phone, find FastMedia Wear, and choose your watch as the install target - the watch downloads it by itself.

### No Play Store? Install an APK through ADB

Use this route when your watch has no Play Store access. You need a computer with the Android
SDK Platform-Tools (`adb`) and a local Wi-Fi network shared by the computer and watch. It does not
work through the internet alone.

1. Download one APK from the [Direct APK Release](../DOWNLOADS_EN.md) page:
   - `FastMediaSorter_wear_debug.apk` is the debug build for testing. It installs as
     `com.sza.fastmediasorter.debug`.
   - `FastMediaSorter_wear_release.apk` is the signed non-debug build. It installs as
     `com.sza.fastmediasorter`.
   - The two builds have different package names, so they can stay installed side by side. Do not
     try to install a Play Store `.aab` file with ADB.
2. On the watch, enable developer mode: **Settings** → **About watch** → tap **Build number** seven
   times. In **Developer options**, enable **ADB debugging** and **Wireless debugging**.
3. In **Wireless debugging**, choose **Pair new device**. On the computer, enter the pairing address
   and code shown by the watch, then connect with the separate connection port from the main
   Wireless debugging screen:

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Accept the debugging prompt on the watch. The pairing and connection ports are different.
4. Install or update the APK. Use the command matching the file you downloaded:

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` updates the same package while keeping its app data. It does not convert a debug build into
   a release build, because these are separate apps.
5. Open **FastMedia Wear** from the watch app list. If needed, start it from ADB:

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> This method requires a Wear OS watch. Galaxy Watch 3, Galaxy Watch Active and Active 2 run Tizen
> and cannot install Wear OS APKs. When finished, turn off Wireless debugging unless you need it for
> another update.

---

## Step 2 - Turn On the Wear Companion on the Phone

The phone side is switched off until you say you own a watch.

1. Open FastMediaSorter on the phone.
2. Go to **Settings** and open the **Management** tab.
3. Find the **Wear OS** group and expand it.
4. Turn the **Wear companion** checkbox on.

The checkbox switches the whole companion on: the button that opens its window appears right below, an entry for it joins the programs list, and it becomes available as a panel tile and a launcher shortcut.

> Builds without the watch bridge do not show this group at all. If you cannot find it, you are running a flavor that ships without Wear support.

---

## Step 3 - Choose What Travels to the Watch

1. In the same group, tap **Wear companion**. Its window opens over the app.
2. Pick the resources you want the watch to see. Nothing is sent until you choose - an empty selection sends nothing rather than pushing your whole library.
3. Adjust the watch's own preferences here as well: view mode, keep-awake behaviour and the sections shown on the watch home screen.

---

## Step 4 - Check That Both Halves See Each Other

1. Open **FastMedia Wear** on the watch.
2. The home screen lists its sections - **Phone**, **Local**, **Resources**, **Streams** and **Apps**.
3. Tap **Phone**. The folders you selected in Step 3 appear.

If the Phone section is empty, return to the companion window on the phone and confirm that at least one resource is selected.

> **Tip:** You can navigate back from any screen on your watch using the visible universal back affordance button on the left edge, swiping from the left edge, or pressing your watch's hardware back button. On the main home screen, tapping the back affordance shows a close icon (×) to exit or a double-chevron («) to minimize background playback.

---

## If Something Does Not Work

- **The watch app does not appear in the Play Store.** Confirm the watch runs Wear OS 3.0 or newer. Older watches use a different app model and are not supported.
- **The Wear OS group is missing from the phone settings.** The build you are running does not carry the watch bridge.
- **The Phone section on the watch is empty.** Nothing is selected in the companion window, or the watch and the phone have lost their pairing - check the pairing in the system settings first.
- **Playback stutters over the phone connection.** Bluetooth between watch and phone is narrow. For long listening, transfer the files to the watch or connect the watch to a network share directly.

---

## Where to Go Next

- [Music on Smartwatch](scenario-watch-music.md) - play your collection on the watch, with cover art, shuffle and bezel volume.
- [Connect Watch to Network Shares](scenario-watch-network.md) - reach a NAS or a PC share from the watch over Wi-Fi, without the phone.
