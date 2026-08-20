---
layout: default
title: "Wear OS SMB Quick Reference"
permalink: /docs/WEAR_OS_SMB_QUICK_REF.html
---
# Wear OS SMB Quick Reference

> **Step-by-step guide:** See [Connect Smartwatch to NAS & PC Shares](howto/scenario-watch-network.md).

## On Your Watch

### Add Connection

```
Browse → Resources → "+ Add SMB Connection"
```

### Fill in Details

| Field | Example | Notes |
|-------|---------|-------|
| **Server** | 192.168.1.50 | IP or hostname |
| **Share** | media | Folder to access |
| **User** | admin | Leave empty for guest |
| **Password** | secret | Leave empty if not needed |

### Options

1. **Voice Input** ← *Easiest for watch*
   - Long-press field → Speak value

2. **Character Picker**
   - Tap field → select letters/numbers

3. **Use Phone App** ← *Recommended*
   - Add on phone (easier typing)
   - Syncs automatically to watch

### Test & Save

```
Tap: Test Connection → Wait for ✓
Tap: Save → Done!
```

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

