# S1433 research: live signal and GNSS charts

**Date:** 2026-08-06  
**Scope:** Real-time charts for Network Monitor

## Conclusions

- Wi-Fi exposes current RSSI in dBm and current Rx/Tx link speeds. The monitor can chart RSSI only for the connected Wi-Fi network; it must not present an AP scan as a passive signal monitor.
- Cellular signal strength is delivered by the modern `TelephonyCallback.SignalStrengthsListener`. Register a separate callback for each visible subscription and render separate series; absent SIM, permission or radio support is an unavailable state, not zero signal.
- Bluetooth RSSI is not a passive adapter property. For a connected GATT device it can be read through the connection with `BLUETOOTH_CONNECT`; advertising-device RSSI requires a Bluetooth scan. First release should chart only an explicitly selected, connected device, otherwise omit the series.
- `LocationManager.registerGnssStatusCallback` provides satellite status only while the GPS provider is enabled and the app is foreground, and requires `ACCESS_FINE_LOCATION`. It supports a truthful GNSS chart: satellite C/N0, number visible, number used in fix, constellation and session trend.
- The charts must use bounded in-memory samples with a fixed visible time window. They begin when the section/screen is visible, stop and clear when it closes, and never create a background location or radio monitor.

## Recommended scope

1. One compact live chart per available signal source, all using dBm/C/N0 labels rather than colour alone.
2. Wi-Fi: RSSI, frequency, standard and link speed, sampled while connected.
3. Cellular: per-SIM signal-strength series and current radio display where Android exposes it; no cell identity/location.
4. Bluetooth: disabled by default; only an explicitly selected, connected device can provide a series.
5. GNSS: satellite sky/list view plus timeline of visible/used satellites and average C/N0. A fresh location coordinate is not required for this diagnostic.
6. Sampling and drawing pause with screen lifecycle; chart surfaces have an accessible text summary containing the last value, minimum, maximum and trend.
