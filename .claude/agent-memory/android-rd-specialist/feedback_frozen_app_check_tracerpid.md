---
name: frozen-app-check-tracerpid-lldb
description: App frozen on emulator with no crash/ANR trace? Check /proc/<pid>/status TracerPid - lldb-server (native debugger) attached freezes the VM, not a code bug
metadata:
  type: feedback
---

When the app appears frozen on the emulator (UI unresponsive, nothing clickable) and logcat just stops after `Waiting for a blocking GC ProfileSaver` with no crash and no fresh `/data/anr/` trace, the cause is often **the native LLDB debugger holding the process under ptrace**, not application code.

**Diagnostic sequence (adb at `/c/Users/serzh/AppData/Local/Android/Sdk/platform-tools/adb.exe`, use `MSYS_NO_PATHCONV=1` in Git Bash so `/data/...` paths aren't mangled):**
1. `adb shell cat /proc/<pid>/status | grep -E 'TracerPid|State'` - if `TracerPid` is non-zero, a debugger is attached.
2. `adb shell cat /proc/<tracerpid>/cmdline` - if it is `lldb-server gdbserver ...`, native debugging is on.
3. `adb shell cat /proc/<pid>/status | grep ShdPnd` - a stuck `ShdPnd` bit (e.g. `0000000000000004` = SIGQUIT pending) confirms signals are intercepted by lldb and not delivered; this is why a `kill -3` thread dump prints only the SignalCatcher header and never flushes per-thread stacks.

**Why:** Android Studio launched in Debug with native debugging (Debugger type = Dual/Native) attaches `lldb-server` via ptrace. ART's blocking GC and SIGQUIT then deadlock because lldb holds signal control; input times out and the system reports `Channel is unrecoverably broken` (ANR) for the foreground Activity. Browsing documents makes it worse because the PDF thumbnail path calls native `libpdfclient.so`.

**How to apply:** Before hunting for a Kotlin bug behind a freeze, rule out the debugger. Fix is environmental: run via Run (not Debug), or set Debugger type to **Java Only**. Unfreeze now with `adb shell am force-stop com.sza.fastmediasorter.debug` (kills the Studio debug session). Note: `kill -3` from adb shell is `Operation not permitted`; use `adb shell run-as <pkg> kill -3 <pid>` for debuggable builds, and `adb root` is denied on Google production emulator images.
