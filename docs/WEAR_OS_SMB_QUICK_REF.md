---
layout: default
title: "Wear OS SMB Quick Reference"
permalink: /docs/WEAR_OS_SMB_QUICK_REF.html
---
# Wear OS SMB Quick Reference

> **Step-by-step guide:** See [Connect Smartwatch to NAS & PC Shares](howto/scenario-watch-network.md).

## Add a Connection (on your phone)

```
Phone app → Resources → "+ Add Network Source" → fill in → Save
```

It syncs to the watch automatically. **On a Google Play build this is the only way to add one** - the
watch does not ask for a username or password. Not there yet? On the watch: `Browse → Resources → Sync
from Phone`.

| Field | Example | Notes |
|-------|---------|-------|
| **Server** | 192.168.1.50 | IP or hostname |
| **Share** | media | Folder to access |
| **User** | admin | Leave empty for guest |
| **Password** | secret | Leave empty if not needed |

## On Your Watch

What the watch itself can do, on every build:

```
Browse → Resources → tap a connection   (browse and play)
Browse → Resources → Sync from Phone    (pull the phone list now)
Browse → Resources → tap ⋯ → Delete     (remove a connection)
```

### Adding on the watch - development builds only

A `+ Add SMB Connection` chip appears only in development builds. If you do not see it, that is expected;
add the connection on the phone. In those builds the fields are the same as the table above, filled by
voice input or the character picker, then `Test Connection` and `Save`.

### Check a Saved Connection

```
Press and hold a connection -> Test
```

Reports the same result as the setup screen. Delete lives in the same menu and still confirms.

---

## Network Examples

### Home NAS (Synology/QNAP)

```
Server: 192.168.1.50 (or nas.local)
Share: media
User: admin
Password: [your password]
```

### Windows PC

```
Server: 192.168.1.100
Share: SharedMedia
User: (empty)
Password: (empty)
```

### Linux Server

```
Server: 192.168.1.200
Share: shared
User: user
Password: [your password]
```

---

## Troubleshooting

| Error | Solution |
|-------|----------|
| Cannot reach server | Check IP is correct, server is on |
| Access Denied | Verify username/password, try empty |
| Cannot find share | Double-check exact share name |

---

## See Also

- [WEAR_OS_SMB_SETUP.md](WEAR_OS_SMB_SETUP.md) - Full guide
- [SMB_SETUP_GUIDE.md](SMB_SETUP_GUIDE.md) - Network setup details

