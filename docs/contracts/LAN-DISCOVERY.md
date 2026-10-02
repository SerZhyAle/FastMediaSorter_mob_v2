# Pointer - `LAN-DISCOVERY`

| | |
| --- | --- |
| **Id** | `LAN-DISCOVERY` |
| **Version** | 0.10, draft. Owner: shared (candidate owner FMS Companion) |
| **Home** | `lan-discovery/README.md` in the shared contracts catalog |
| **Role here** | consumer (discovers companion SFTP shares over mDNS/DNS-SD) |

## What this repository must do to stay conformant

- Discover companion SFTP servers announced on the local network as `_sftp-fms._tcp`; the service name is never changed (rule 1).
- Match a discovered service by TXT `fp` (the canonical host-key fingerprint) and by nothing else; ignore unknown TXT keys and read an absent optional key as v1 behaviour (rule 3).
- Scope discovery to app foreground using `ProcessLifecycleOwner` and hold Wi-Fi multicast lock only while active.
- Graceful degradation: if mDNS is blocked or local-network permission is denied, fall back to configured addresses without crashing.

## Where it lives here

- `app_v2/.../data/remote/sftp/CompanionMdnsDiscovery.kt`.
- `app_v2/.../data/remote/sftp/SftpEndpointResolver.kt`.
- `app_v2/.../data/remote/sftp/SftpHostKeyPinRegistry.kt`.
