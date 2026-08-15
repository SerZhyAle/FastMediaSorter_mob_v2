# Research 03 - Network-change signal for cache invalidation

**§6 item:** 3 - which existing mechanism resets the endpoint-selection cache
**Status:** Resolved
**Method:** reading `core/network/NetworkStateMonitor.kt` (2026-07-12).

## Finding: reuse NetworkStateMonitor (same source SMB already uses)

`core/network/NetworkStateMonitor.kt` (256 LOC) wraps `ConnectivityManager.NetworkCallback` and exposes:

- `registerCallback(callback: NetworkChangeCallback)` / `unregisterCallback(..)` - thread-safe.
- `NetworkChangeCallback { onNetworkChanged(); onNetworkLost() }`.
- Internal network identity = `"${network.networkHandle}_${linkProperties.interfaceName}"` (e.g. `961183404045_wlan0`, `1012723011597_rmnet0`), compared to fire `onNetworkChanged()` only on a real transition (Wi-Fi ⇄ cellular, reconnect, IP change).

This is the exact mechanism `SmbConnectionManager` uses ("Network reconnected - invalidating all SMB connections" in the field log), so the SFTP resolver follows the established pattern.

## Design implication

- The resolver implements `NetworkStateMonitor.NetworkChangeCallback` and registers itself. On `onNetworkChanged()` / `onNetworkLost()` it clears its endpoint-selection cache, so the next cold connection re-probes candidates for the new network. This is what makes a home↔cellular switch transparent (strategic §11 criterion 2).
- A network **epoch** for cache keying is not strictly required: invalidating the whole cache on `onNetworkChanged` is sufficient and simplest. The monitor's own network id can be adopted later if finer keying is wanted (§5.3 extensibility), without a contract change.
- Registration lifecycle: the resolver is an application-scoped singleton (Hilt `@Singleton`), so it registers once and never unregisters - matching NetworkStateMonitor's app-lifetime scope.
