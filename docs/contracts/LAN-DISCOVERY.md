# Pointer - `LAN-DISCOVERY`

| | |
| --- | --- |
| **Id** | `LAN-DISCOVERY` |
| **Version** | 0.9, draft. Owner: shared (candidate owner FMS Companion) |
| **Home** | `lan-discovery/README.md` in the shared contracts catalog |
| **Role here** | consumer (discovers companion SFTP shares over mDNS/DNS-SD) |

## What this repository must do to stay conformant

- Discover companion SFTP servers announced on the local network as `_sftp-fms._tcp` (`_fms-sftp._tcp`).
- Extract canonical host-key fingerprint from TXT record `fp=` (or `id=`).
- Scope discovery to app foreground using `ProcessLifecycleOwner` and hold Wi-Fi multicast lock only while active.
- Graceful degradation: if mDNS is blocked or local-network permission is denied, fall back to configured addresses without crashing.

## Where it lives here

- `app_v2/.../data/remote/sftp/CompanionMdnsDiscovery.kt`.
- `app_v2/.../data/remote/sftp/SftpEndpointResolver.kt`.
- `app_v2/.../data/remote/sftp/SftpHostKeyPinRegistry.kt`.
