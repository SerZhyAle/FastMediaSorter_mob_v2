---
name: reset-appsettings-without-onboarding
description: Reset AppSettings to defaults on device by deleting only the DataStore file - `pm clear` is not needed and drags in the whole onboarding walk
metadata:
  type: feedback
---

To put the app back into "fresh settings" state on a device, delete only the DataStore file - never `pm clear`:

```
adb.ps1 stop
adb.ps1 shell -Cmd "run-as com.sza.fastmediasorter.debug cp files/datastore/settings.preferences_pb files/datastore/settings.preferences_pb.bak"
adb.ps1 shell -Cmd "run-as com.sza.fastmediasorter.debug rm files/datastore/settings.preferences_pb"
```

**Why:** `AppSettings` lives in `files/datastore/settings.preferences_pb`, but the first-run gate (`welcome_completed`) lives in a separate SharedPreferences file, `welcome_prefs`. Deleting the DataStore therefore resets every setting to its constructor default while the app still believes onboarding is done. `pm clear` wipes both plus every runtime permission grant, which forces the whole WelcomeActivity walk and its permission-dialog chain - see [[onboarding-device-test-gotchas]] for how expensive and derail-prone that is. Verified 2026-08-08 on emulator-5554 (Android 15) while proving S1535.

**How to apply:** reach for this whenever a test needs default-valued settings (auto-suggest paths, first-run branches keyed off a setting's value, migration checks). Always copy the file aside first and restore it afterwards - it holds the owner's real settings, and the emulator is a working device, not a scratch VM.
