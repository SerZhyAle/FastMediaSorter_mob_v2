# Research 03 - Reading Bluetooth state without BLUETOOTH_CONNECT

**Strategic item:** S1415 §6.3 ("Bluetooth: разрешение не установлено")
**Date:** 2026-08-06
**Verdict:** Resolved - a permission-free, subscription-based read exists.

---

## Question

Does reading the Bluetooth adapter state on API 31+ require `BLUETOOTH_CONNECT`, and can the tray show
the indicator without declaring a new permission?

## Evidence

Inspected the platform stub directly (`android.jar`, compileSdk 36) with `javap`:

- `android.bluetooth.BluetoothAdapter` - `public int getState()` and `public boolean isEnabled()` exist and
  are public. The stub carries no permission annotation, so the stub alone cannot answer the question; the
  documented contract for API 31+ is `@RequiresPermission(BLUETOOTH_CONNECT)`.
- `android.provider.Settings$Global` - `public static final String BLUETOOTH_ON` exists, is **not**
  deprecated, and its constant value is the string `bluetooth_on`.

Confirmed with `javap -v`:

```text
public static final java.lang.String BLUETOOTH_ON;
  flags: (0x0019) ACC_PUBLIC, ACC_STATIC, ACC_FINAL
  ConstantValue: String bluetooth_on
```

## Consequence for the plan

`Settings.Global.getInt(contentResolver, Settings.Global.BLUETOOTH_ON, 0)` is a plain settings read: it needs
no permission, and `Settings.Global.getUriFor(BLUETOOTH_ON)` gives a `ContentObserver` target, so the source
stays subscription-based as strategic §3.2 requires instead of polling on a timer.

The adapter call stays as a fallback for a device whose settings row is unreadable: it is attempted only when
the settings read yields nothing, and a `SecurityException` from it is treated as "unreadable" - the indicator
is then absent per ADR-1 rather than drawn empty.

## What this does not settle

Whether any shipped device actually hides `bluetooth_on` from a non-system reader. That is an on-device
observation, not a stub question, and it is folded into the ticket's device test rather than blocking the
plan: both outcomes lead to the same code path, and the fallback policy for a required permission is already
fixed by strategic §6.4.

## Method note

The stub is the authority on whether a symbol exists; it is not the authority on what a permission-guarded
call does at run time. Both claims above are existence claims, which is why `javap` settles them.
