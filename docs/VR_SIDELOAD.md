---
layout: default
title: "VR Sideloading Guide"
permalink: /docs/VR_SIDELOAD.html
---

# VR Sideloading Guide

How to install the working immersive VR build (`noLegal`) on Meta Quest without using a store.

The `vr` flavor is the intended Meta Horizon Store / Google Play channel, but its immersive
headset rendering isn't wired up yet (epic S0773) - see [VR Edition Overview](VR_EDITION.md).
Today the only channel with a working immersive experience is `noLegal`, which this guide
installs.

## Prerequisites

1. **Meta Quest headset** - Quest 3, Quest Pro, or Quest 2
2. **USB-C cable** - to connect the headset to your PC
3. **Developer Mode enabled** on your Quest
4. **ADB** - Android Debug Bridge (included in Android SDK Platform Tools)

## Step 1: Enable Developer Mode

1. Open the **Meta app** on your phone
2. Go to **Menu → Devices** and select your headset
3. Tap **Headset Settings → Developer Mode**
4. Toggle **Developer Mode** to ON
5. Restart your headset

> If you don't see the Developer Mode option, register as a Meta developer at [developer.meta.com](https://developer.meta.com/) first.

## Step 2: Install ADB

If you already have Android Studio or Android SDK, ADB is at:
```
C:\Users\<username>\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

Otherwise, download Platform Tools from [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools) and extract to a convenient location.

## Step 3: Connect Quest to PC

1. Connect your Quest to the PC via USB-C
2. Put on the headset - you should see a dialog asking **"Allow USB debugging?"**
3. Check **"Always allow from this computer"** and tap **Allow**
4. Verify connection in terminal:

```powershell
adb devices
```

You should see your device listed as `device` (not `unauthorized`).

## Step 4: Build the noLegal APK

```powershell
.\a.ps1 nd
```

That runs `scripts/builders/build-nolegal-debug.ps1`, which passes the flags `noLegal` actually
needs (`-Pchaquopy.enabled=true --no-configuration-cache`) so the build doesn't depend on a
machine-local `local.properties` setting. The APK lands at:
```
app_v2/build/outputs/apk/noLegal/debug/
```

## Step 5: Install on Quest

```powershell
.\a.ps1 ivn
```

That runs `scripts/builders/install-nolegal-debug-to-device.ps1`, which resolves the just-built
APK for the connected device's ABI and installs it - no need to type the version-stamped file name
by hand. It installs only, by design (see Step 6).

## Step 6: Launch on Quest

The app appears in **Unknown Sources** in the Quest library:

1. Put on your headset
2. Open **App Library**
3. Select **Unknown Sources** from the filter dropdown (top right)
4. Find **FastMediaSorter (noLegal debug)** and launch it

Launch from the Quest Library, not `adb shell am start`: launching over ADB skips the vrshell
`launch_id` the immersive session needs to enter focused XR, so the app opens but the headset
stays on the flat 2D window.

## ADB over Wi-Fi (Wireless)

To avoid the USB cable after initial setup:

```powershell
# While still connected via USB:
adb tcpip 5555

# Disconnect USB, then connect wirelessly:
adb connect <quest-ip-address>:5555
```

Find the Quest IP address in **Settings → Wi-Fi → Connected Network → Details**.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `adb devices` shows `unauthorized` | Put on headset and accept the USB debugging dialog |
| `adb devices` shows nothing | Check USB cable, try a different port, ensure Developer Mode is on |
| App not visible in library | Look in **Unknown Sources** section |
| App crashes on launch | Check logcat: `adb logcat -s FastMediaSorter` |
| XR runtime not available | Ensure Quest firmware is up to date |

## Related Documentation

- [VR Edition Overview](VR_EDITION.md) - what the VR edition does and how it differs from standard
- [Build Scripts](../scripts/builders/README.md) - all available build commands
