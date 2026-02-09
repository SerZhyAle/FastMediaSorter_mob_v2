---
layout: default
title: "SMB Network Storage Setup Guide"
permalink: /docs/SMB_SETUP_GUIDE.html
---
# SMB Network Storage Setup Guide

## What is SMB?

**SMB (Server Message Block)** is a protocol for sharing files and printers over a network. It's used by:

- Windows file sharing (network shares)
- NAS devices (Synology, QNAP, etc.)
- Linux servers (Samba)

## Prerequisites

Before connecting, you need:

1. **A device on your network** that shares files via SMB:
   - Windows PC with shared folder
   - NAS device (Synology, QNAP, etc.)
   - Linux server with Samba enabled

2. **Connection details:**
   - Server IP address or hostname
   - Share name (folder name)
   - Username (if required)
   - Password (if required)

## Setup Steps in FastMediaSorter

### Step 1: Open Network Storage Tab

1. Launch the app
2. Go to **Browse** tab
3. Look for **Network Sources** or **Network Storage** section
4. Tap **Add Network Source** or **+** button

### Step 2: Select SMB Protocol

1. Choose **SMB** from the protocol list
2. You'll see a form with fields:
   - **Server Address**: IP or hostname
   - **Share Name**: Folder to access
   - **Username**: (optional)
   - **Password**: (optional)

### Step 3: Enter Server Details

**Example 1: Windows PC with shared folder**

```
Server Address: 192.168.1.100
Share Name: SharedMedia
Username: (leave empty if no auth required)
Password: (leave empty if no auth required)
```

**Example 2: NAS Device**

```
Server Address: 192.168.1.50 (or nas.local)
Share Name: media
Username: admin
Password: your_password
```

**Example 3: Linux Server (Samba)**

```
Server Address: 192.168.1.200
Share Name: shared
Username: user
Password: password
```

### Step 4: Test Connection

1. Tap **Test Connection** button
2. Wait for result (usually 2-5 seconds)
3. If successful: ✓ "Connected"
4. If failed: Check error message and verify details

### Step 5: Save Configuration

1. Tap **Save** or **Add Source**
2. The source now appears in your browse list
3. Tap it to browse files

## Finding Your Server Details

### On Windows (Share a Folder)

**Create shared folder:**

1. Right-click folder → **Properties** → **Sharing** → **Advanced Sharing**
2. Check **Share this folder**
3. Note the share name
4. Get your IP: Run `ipconfig` in Command Prompt, find "IPv4 Address"

**Find another Windows PC sharing:**

1. Press `Win + E` → **Network**
2. See available computers
3. Double-click computer → see shares
4. Right-click share → **Properties** → note the path

### On macOS (Share a Folder)

1. **System Preferences** → **Sharing** → **File Sharing**
2. Click **Options** to see SMB settings
3. Your IP is shown (like `smb://192.168.1.X`)
4. Share name is the folder name

### On Linux (Samba)

Check `/etc/samba/smb.conf` for shared folders, or ask your admin for:

- Server IP
- Share name
- Credentials

### Find NAS Details

**Synology:**

1. Open Synology DiskStation Manager (DSM)
2. **File Services** → **SMB/AFP/NFS** → **SMB** → verify enabled
3. Find your IP in browser address bar
4. Share name is in **File Station** (usually `media`, `backup`, etc.)

**QNAP:**

1. Access QNAP Web UI
2. Go to **Network** → verify SMB enabled
3. Check **Shared Folders** for available shares
4. Find your IP in browser or run `find QNAP devices` on your network

## Common Issues

### "Cannot Connect to Server"

**Causes & Solutions:**

1. **Wrong IP/hostname**
   - Verify with `ping server_address` from your phone's WiFi
   - Check device is on same network as your phone

2. **Server not sharing**
   - Verify SMB/CIFS is enabled on the device
   - Windows: Check **File Sharing** is enabled
   - NAS: Check **SMB** is enabled in settings

3. **Firewall blocking**
   - Check device firewall allows port 445 (SMB port)
   - Windows Firewall: Allow SMB traffic
   - Router: May need port forwarding if accessing outside home

### "Access Denied" / "Invalid Credentials"

1. **Wrong username/password**
   - Try without credentials first (leave blank)
   - Verify correct spelling and case

2. **Guest access disabled**
   - Enable guest access on the server
   - Or provide valid credentials

### "Share Not Found"

1. **Wrong share name**
   - Double-check exact share name (case-sensitive on some systems)
   - See examples above for finding share names

2. **Share is offline**
   - Verify device is powered on and connected to network
   - Check with `ping server_address`

## Performance Tips

1. **Local Network Only** - SMB works best on your home/office WiFi
2. **Use 5GHz WiFi** - Faster than 2.4GHz for large file transfers
3. **Wired Connection** - Server should ideally be wired for best speed
4. **Close Other Apps** - Free up bandwidth on your phone

## Security Notes

⚠️ **Important:**

- Avoid using SMB over untrusted networks (public WiFi, internet)
- Use strong passwords for share authentication
- Enable encryption on your server if available
- Consider VPN if accessing from outside your home network

## For Developers

**SMB Connection Details (in app code):**

- Protocol: `smb://`
- Default port: 445
- Auth: NTLM or Kerberos
- Library: SMBJ 0.12.1
- Read/write operations: Async with connection pooling

**Wear OS Network Browsing:**
See `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/` for network source UI.

---

**Need Help?** Check your device documentation or contact your network admin for server details.

