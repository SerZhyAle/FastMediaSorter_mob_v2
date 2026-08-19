---
layout: default
title: "Connect Smartwatch to NAS & PC Shares - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network.html
---
# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Connect Smartwatch to NAS & PC Shares

> **Level:** Intermediate &bull; **Time:** ~10 minutes &bull; **Device:** Wear OS smartwatch

[Русский](scenario-watch-network-ru.md) | [Українська](scenario-watch-network-uk.md)

FastMediaSorter on Wear OS connects directly to your home network storage (NAS, PC shared folders, FTP, or SFTP servers) over Wi-Fi. You can browse remote files, stream music to Bluetooth headphones, and sync your favorite folders without needing your phone.

> **New to network shares?** If you have not set up a shared folder on your PC or NAS yet, start with our [Connect to NAS / Windows Share (SMB)](scenario-smb-setup.md) guide first.

---

## What You Will Need

- A smartwatch running **Wear OS 3.0** or newer connected to your home Wi-Fi network
- A shared network folder (SMB / Windows share, FTP server, or SFTP server)
- Network credentials: IP address or hostname, share name, username, and password
- FastMedia Wear installed on your watch

---

## Step 1 - Open Resources on Your Watch

1. Open **FastMedia Wear** on your smartwatch.
2. On the main screen, tap **Resources** (Wi-Fi icon).
3. The Resources screen displays your configured network connections.

![Resources screen on Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Sync from Phone shortcut:** If you already added your SMB or SFTP shares in FastMediaSorter on your Android phone, tap **Sync from Phone** to import all connection settings to your watch with one tap.

---

## Step 2 - Add a Network Source

1. On the Resources screen, tap **Add Source**.
2. Select your network protocol:
   - **SMB**: standard Windows shares, Synology, QNAP, or TrueNAS
   - **FTP**: standard FTP file servers
   - **SFTP**: secure SSH file transfer servers (supports password or private SSH key)
3. Tap each field to enter connection details using the watch on-screen keyboard:
   - **Name**: optional label (e.g. "Home NAS" or "Music Share")
   - **Server Address**: your computer or NAS IP (e.g. `192.168.1.50`)
   - **Port**: network port (default: 445 for SMB, 21 for FTP, 22 for SFTP)
   - **Share Name** (SMB only): the shared folder name on your NAS/PC
   - **Username** and **Password**: your login credentials

![Add Network Source screen on watch](screenshots/screenshot-wear-network-step2.png)

---

## Step 3 - Test and Save the Connection

1. Scroll down to the bottom of the form and tap **Test**.
2. FastMedia Wear verifies the network route and credentials:
   - On success, the screen displays **Connection successful!**.
   - If there is an issue, a friendly status message indicates what to adjust (e.g. server address or password).
3. Tap **Save** to store the network source on your watch.

![Test network connection and save source](screenshots/screenshot-wear-network-step3.png)

---

## Step 4 - Browse and Play Network Media

1. On the Resources screen, tap your newly saved network share.
2. FastMedia Wear connects to the remote share and lists its contents.
3. Browse folders and files in list or grid view.
4. Tap any audio track to begin playback in the full-screen player. For detailed player features and battery saving, see [Listen to Music on Your Watch](scenario-watch-music.md).

![Browse files and folders on network share](screenshots/screenshot-wear-network-step4.png)

---

## Done! Network Features on Wear OS

- **Independent Wi-Fi Streaming**: Streams directly from your NAS or PC over Wi-Fi without phone relay.
- **Multi-Protocol Support**: Full support for SMB, FTP, and SFTP with password or SSH private key auth.
- **Bi-directional Sync**: Sync connections from your phone companion or export watch sources back to the phone.

---

## Troubleshooting

| Problem | What to try |
|---------|------------|
| Connection test reports "Connection failed" | Verify your watch is connected to the same Wi-Fi network as the server, and check the IP address |
| Share name error on SMB | Make sure to enter only the share name (e.g. `Music`), not the full path with slashes |
| Authentication failed | Check your username and password. On Windows shares, ensure network sharing permissions allow your user account |
| Slow loading over Wi-Fi | Ensure the watch Wi-Fi signal is strong and 5 GHz / 2.4 GHz network routing to the local server is unblocked |

