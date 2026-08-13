# S1433 research: Android capabilities and existing network foundations

**Date:** 2026-08-06  
**Scope:** Network Monitor first release

## Conclusions

- The app already has a process-wide connectivity observer built on `ConnectivityManager`, plus safe abstractions for host reachability, local-resource discovery and per-resource speed tests.
- The monitor can accurately show every network Android exposes to the app: transport, validation/captive-portal state, metered state, estimated bandwidth, interface, local IPv4/IPv6 addresses, DNS servers, routes, default proxy and the default gateway inferred from the default route.
- The monitor must collect live state while its screen is visible and unregister every callback when the screen stops. Android limits registered network and diagnostics callbacks per UID; a permanent polling service is not justified for a diagnostic screen.
- Wi-Fi SSID/BSSID and scan results are permission-sensitive. Android 13+ uses `NEARBY_WIFI_DEVICES` for relevant Wi-Fi operations; scan results still require location permission. First release should show only the currently connected Wi-Fi when permission allows, never scan nearby access points.
- Bluetooth state and paired-device details require the Android 12+ Nearby devices permissions. First release should show adapter availability/state after opt-in and must not scan for nearby devices.
- `getActiveModemCount()` exposes configured modem count without identifying the subscriber. Active SIM labels and per-SIM state require `READ_PHONE_STATE`; device identifiers, phone numbers, ICCID, detailed radio configuration and privileged carrier state must never be requested or displayed.
- An external IP cannot be derived locally. It requires an HTTPS request to a chosen external reflector, which reveals the public IP to that provider. It must be on-demand, disclosed and excluded from background refresh and persistent history.
- The existing resource speed test writes a temporary file to some resources. It cannot become an automatic health check. Keep it as an explicit per-resource action with the current cleanup/error contract. A neutral Internet speed test needs a maintained download/upload endpoint and is not recommended until an endpoint owner, limits and privacy terms exist.
- Android cannot provide a complete router/LAN topology to an ordinary app. The truthful first-release visual is a path diagram: device interface -> default gateway -> DNS -> selected Internet check. LAN host probing remains an explicit, cancellable tool, not a background map.

## Recommended first-release functional set

1. Overview: every currently known Android network, default route, transport, validation, metered/VPN/captive-portal flags and last refresh time.
2. Detail per network: interface, local IPv4/IPv6, DNS, gateway/default route, proxy, estimated upstream/downstream and Wi-Fi details only when access is granted.
3. Cellular: active-modem count and, after a separate `READ_PHONE_STATE` grant, up to two visible SIM rows with user-facing carrier label and active/default-data indication. Missing hardware or permission is an explicit unavailable state.
4. Bluetooth: availability and enabled/disabled state; paired-device detail only after Nearby devices access; no discovery scan.
5. Reachability: a manual, cancellable check of the app's saved remote resources and a small fixed Internet health set, with a clear protocol-level result and no credentials in UI/logs.
6. Network path: an honest diagram of device, gateway, DNS and health endpoint; an explicit action may run the existing bounded LAN service discovery.
7. Measurements: show passive system estimates; retain the existing per-resource speed test as an explicit action. Defer general Internet throughput tests.
8. Integration: model enablement, icon, internal route, Programs menu, launch panel, launcher actions and eligible widget/shortcut catalogs on the Calculator pattern. Do not copy Calculator's selected-text commands because their input/result contract has no Network Monitor equivalent.

## Sources

- Android Developers: ConnectivityManager, NetworkCapabilities and LinkProperties documentation.
- Android Developers: Read network state guidance.
- Android Developers: Wi-Fi nearby-device permission guidance.
- Android Developers: Bluetooth permission guidance.
- Android Developers: TelephonyManager and SubscriptionManager API references.

## Existing project foundations

- `core/network/NetworkStateMonitor` observes connectivity and link-property changes.
- `domain/usecase/HostReachabilityChecker` provides a bounded TCP probe contract.
- `domain/usecase/DiscoverNetworkResourcesUseCase` provides cancellable, bounded LAN service probing.
- `domain/usecase/NetworkSpeedTestUseCase` measures a selected saved resource and persists its result.
- Calculator integration has an enablement setting, Programs-menu entry, panel route and launcher starter-set route.
