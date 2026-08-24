---
name: device-text-entry-and-adb-path-traps
description: Typing a path with adb input text gets mangled by Gboard autocorrect, and raw adb from bash rewrites /sdcard paths - two traps that fail silently on the emulator
metadata:
  type: feedback
---

Two device-driving traps that produce a plausible-looking wrong result instead of an error.

## `input text` is not a literal paste - Gboard rewrites it

Typing `ScanInbox` into a text field lands as `Scan inbox`: the IME treats it as two words and
autocorrects. Worse, the mangling can happen **later** - the field read back correctly right after
typing, and the word was only replaced when the next button was tapped and the composing text
committed.

**Why:** measured 2026-08-24 on emulator-5554 while filling the app's manual folder-path field. Only
Gboard is installed on the project AVDs (`ime list -a -s` shows it plus a voice IME), so there is no
autocorrect-free keyboard to switch to.

**How to apply:**
- Prefer single dictionary words in any fixture name you will have to type (`Inbox`, `Archive`,
  `Books`). camelCase and glued words are what triggers the split.
- Tapping into a field puts the caret where you tapped, not at the end. Clear with
  `input keyevent 123` (MOVE_END) then `input keyevent 67 67 67 ..` - `input keyevent` accepts many
  codes in one call, so build the list with Python rather than looping.
- Dismiss the keyboard with `input keyevent 4` before tapping the confirm button, then re-read the
  field with `uidump` and only then tap. Verifying after the tap is too late.

## Raw `adb` from the Bash tool rewrites device paths

`adb push file /sdcard/Inbox/` from bash fails with
`remote secure_mkdirs() failed` on `C:/Program Files/Git/sdcard/Inbox/` - MSYS rewrote the POSIX-looking
device path into a Windows one. Same for `adb shell ls /sdcard/...`. This is the Rule 27 family, but
Rule 27 names slash-command arguments, not device paths, and the canon guard does not refuse it.

**How to apply:** `export MSYS2_ARG_CONV_EXCL='*'` in the same call before any raw `adb` that carries a
device path. Better, use `scripts/devtest/adb.ps1`, which handles it - fall back to raw `adb` only for
what the wrapper has no verb for, such as backgrounding `screenrecord`.

Related: [[emulator-acceptance-ceiling]], [[setup-test-media]].
