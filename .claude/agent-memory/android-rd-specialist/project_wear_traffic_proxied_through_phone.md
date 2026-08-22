---
name: wear-traffic-proxied-through-phone
description: Wear OS routes the watch's network traffic through the paired phone by default over Bluetooth, at a bandwidth that can be as low as 4 KB/s - the watch's "own network" is not what runs when the phone is nearby.
metadata:
  type: project
---

Wear OS proxies the watch's network traffic through the paired phone whenever they are
Bluetooth-connected. The watch's own Wi-Fi or LTE is used only when the phone is unavailable. The
app does not choose this and cannot see it - and on a Bluetooth LE link the app may get only about
**4 KB/s** (32 kbit/s), which is below a 64-128 kbit/s radio stream and nowhere near video.

Sources, both quoted verbatim in `PLAN/S1728_wear-streaming-via-phone-network/research/01__wear-network-transports.md`:
developer.android.com/training/wearables/data/network-communication (the proxy statement) and
.../wearables/apps/standalone-apps (the 4 KB/s figure).

The documented remedy runs the other way: `ConnectivityManager.requestNetwork` with
`TRANSPORT_WIFI` plus `bindProcessToNetwork` for the duration of playback, released after. That
needs `CHANGE_NETWORK_STATE`, which the wear manifest does not declare (`ACCESS_NETWORK_STATE`
arrives via a library and is already present in the merged manifest).

**Why:** established 2026-08-19 while writing S1728, whose entire premise ("optionally make it work
through the phone") turned out to describe the default. S1708's §8 was promising users watch
streaming "over the watch's own network, with no phone nearby" on the same refuted premise.

**How to apply:** any claim that the watch works "on its own network" or "without the phone" is
false whenever the phone is in range - challenge it in specs, FEATURES text and acceptance
criteria. Any watch feature that moves real bytes (streaming, large sync, image fetch) must be
sized against the Bluetooth link, not against Wi-Fi. And an app-level relay over the Data Layer
buys no bandwidth at all: it rides the same Bluetooth. See [[index-wear]].
