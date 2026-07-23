---
name: emulator-mouse-wheel-injection
description: Emulator CAN inject real mouse-wheel ACTION_SCROLL via adb input mouse scroll --axis VSCROLL (API 35+)
metadata:
  type: reference
---

The AVD/emulator CAN inject a genuine `SOURCE_MOUSE` `MotionEvent.ACTION_SCROLL` (mouse wheel), contrary to older assumptions.

On the Pixel 9 / Android 15 (API 35) system image, `adb shell input` supports a `mouse` source and a `scroll` command:

```
adb -s emulator-5554 shell input mouse scroll <x> <y> --axis VSCROLL,<N>
```

Negative VSCROLL = scroll down, positive = scroll up. The event arrives at the pointer position (x,y) as a real wheel `ACTION_SCROLL`, reaching `BaseActivity.dispatchGenericMotionEvent` - not a touch swipe, not a trackball `roll`.

**Why:** S0996 (mouse-wheel scroll) and S0289 (mouse dispatch) status notes claim "emulator cannot inject ACTION_SCROLL -> needs a human with a real mouse". That was true on older images but is FALSE on this API-35 image. `input roll` (trackball) and mobile-mcp `swipe` (touchscreen) do NOT exercise the wheel path; `input mouse scroll --axis VSCROLL` does.

**How to apply:** For device-testing any mouse-wheel / pointer-scroll ticket, inject with `input mouse scroll x y --axis VSCROLL,N` and confirm via the spec's `Timber.d` probe + before/after element-Y shift in the a11y tree. `superConsumed=true` in the S0996 probe means native-first dispatch let the view under the cursor consume the wheel (the fix working); `superConsumed=false` means the fallback (`ActivityMouseDispatchHelper`) path ran. Verify `input --help` lists a `mouse` source before relying on this - older images lack it.
