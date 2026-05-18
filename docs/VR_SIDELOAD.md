# VR Sideloading Guide

How to install the FastMediaSorter VR APK on Meta Quest without using a store.

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

## Step 4: Build the VR APK

```powershell
# Option A: Build script (auto-versions + copies to DOWNLOADS/)
.\scripts\builders\build-vr-debug.ps1

# Option B: Gradle direct
.\gradlew.bat assembleVrDebug
```

The APK will be at:
```
app_v2/build/outputs/apk/vr/debug/app_v2-vr-debug.apk
```

Or, if using the build script, also copied to:
```
DOWNLOADS/FastMediaSorter_vr_debug.apk
```

## Step 5: Install on Quest

```powershell
# Option A: Build + install in one step
.\scripts\builders\build-vr-device.ps1

# Option B: Manual install
adb install -r -d DOWNLOADS\FastMediaSorter_vr_debug.apk
```

## Step 6: Launch on Quest

The app appears in **Unknown Sources** in the Quest library:

1. Put on your headset
2. Open **App Library**
3. Select **Unknown Sources** from the filter dropdown (top right)
4. Find **FastMediaSorter VR** and launch it

You can also launch via ADB:
```powershell
adb shell am start -n com.sza.fastmediasorter.vr.debug/com.sza.fastmediasorter.ui.main.MainActivity
```

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
