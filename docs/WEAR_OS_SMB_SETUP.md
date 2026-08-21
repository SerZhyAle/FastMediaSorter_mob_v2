---
layout: default
title: "Wear OS - How to Set Up SMB Network Storage"
permalink: /docs/WEAR_OS_SMB_SETUP.html
---
# Wear OS - How to Set Up SMB Network Storage

> **Step-by-step guides:** See our visual walk-throughs: [Connect Smartwatch to NAS & PC Shares](howto/scenario-watch-network.md) and [Listen to Music on Your Watch](howto/scenario-watch-music.md).

## What is SMB in Wear OS?

SMB allows your Wear OS watch to access files from:

- Windows computers (shared folders)
- NAS devices (Synology, QNAP, etc.)
- Linux servers (Samba)

## How to add a connection: use your phone

**Add network connections in the phone app. They sync to your watch automatically.** This is the normal
way, and on a watch installed from Google Play it is the only way.

1. **On your phone**, open FastMediaSorter.
2. Go to **Resources**.
3. Tap **+ Add Network Source**.
4. Fill in the server, share, user and password with the phone keyboard.
5. Tap **Save**.

That is it. Within a few seconds the connection appears on the watch under **Browse -> Resources**, ready
to browse. Nothing needs to be typed on the watch, and your password is never entered on the small screen.

**If the connection has not appeared yet**, open **Browse -> Resources** on the watch and tap
**Sync from Phone**. That pulls the current list from the phone straight away instead of waiting.

> **Why the watch does not ask for a password.** Entering a username and password on a watch means a masked
> field on a screen the size of a stamp, in public, with voice input as the realistic alternative. We chose
> not to offer it: the credential stays on the phone, and the watch receives an already-working connection.

Once a connection is on the watch you can do everything else there - browse it, play from it, check its
status and remove it. Only *creating* one belongs to the phone.

---

## Adding a connection on the watch (development builds only)

> **This section does not apply to the app from Google Play.** The **+ Add SMB Connection** button is
> present only in development builds. If you are looking for that button and cannot find it, that is
> expected - use the phone instead, as described above.

### UI steps, development builds

### Step 1: Open Resources

**On your Wear watch:**

1. Open **FastMediaSorter** app
2. Navigate to **Browse** tab
3. Scroll down to **Resources** section
4. Tap on **Resources** or **Add Connection**

> **What you see:** A list showing:
>
> - "📡 Resources" (title)
> - Any previously saved connections (e.g., "Home NAS", "PC Shared")
> - **+ Add SMB Connection** button at the bottom

### Step 2: Tap "Add SMB Connection"

Click the **"+ Add SMB Connection"** button

> **What appears:** The "Add SMB Connection" screen with these fields:
>
> - Server
> - Share
> - User  
> - Base path
> - Status message
> - Test Connection button
> - Save button

### Step 3: Enter Server Details

Since Wear OS has limited text input, you'll need to enter details using:

#### **Option A: Voice Input (Recommended)**

1. Long-press the **"Server:"** chip
2. Say your server IP or hostname
   - Example: "192 dot 168 dot 1 dot 50"
   - Example: "nas dot local"
3. Watch will display what it heard

#### **Option B: Character Picker**

1. Tap the **"Server:"** chip
2. A character picker appears (tap letters/numbers)
3. Enter your server address

#### **Option C: Phone App** (see the phone route at the top of this page)

1. On your **phone**, use the main app to add the network source
2. Both devices share the same account
3. The connection syncs automatically to your watch

### Step 4: Fill in Fields

**Server**: IP address or hostname

- Example: `192.168.1.50` (NAS device on your network)
- Example: `nas.local` (if using hostname)
- Example: `192.168.1.100` (Windows PC)

**Share**: Folder name to access

- Example: `media` (common NAS share name)
- Example: `SharedMedia` (Windows shared folder)
- Example: `backup` (backup folder)

**User**: Your username (if required)

- Leave empty for guest access
- Example: `admin` (NAS default)
- Example: `user` (your login)

**Password**: Your password (if required)

- Swipe to reveal password field after entering username
- Leave empty for guest access

### Step 5: Test Connection

1. After entering all required fields, tap **"Test Connection"** button
2. Watch shows status:
   - ✓ **"Connected"** - Success! Green indicator
   - ✗ **"Failed: Access Denied"** - Check credentials
   - ✗ **"Failed: Cannot reach server"** - Check IP/hostname

### Step 6: Save Connection

1. Once test succeeds, tap **"Save"** button
2. Watch shows: **"Connection saved"**
3. You're returned to Resources list
4. Your new connection now appears in the list!

---

## Example: Adding a NAS Device

**Typical settings:**

```
Server: 192.168.1.50
Share: media
User: admin
Password: your_password
```

**Steps:**

1. On the phone, open FastMediaSorter -> **Resources** -> **+ Add Network Source**
2. Server: `192.168.1.50`
3. Share: `media`
4. User: `admin`, Password: your password
5. Tap **Save**
6. On the watch: **Browse -> Resources** - the NAS is there, ready to browse

> In a development build you can also enter these on the watch itself; see the development-builds
> section above.

---

## Example: Adding Windows Shared Folder

**Typical settings:**

```
Server: 192.168.1.100
Share: MyVideos
User: (leave empty)
Password: (leave empty)
```

**Steps:**

1. On the phone, open FastMediaSorter -> **Resources** -> **+ Add Network Source**
2. Server: `192.168.1.100`
3. Share: `MyVideos`
4. Leave user and password empty
5. Tap **Save**
6. On the watch: **Browse -> Resources** - the shared folder is there

---

## What Happens After Save?

After saving, your connection:

1. Appears in the Resources list with icon 📡
2. Shows: **"[Name]"** and **"[Server IP]"**
3. Tap it to **browse files** on that share
4. Navigate folders like you would on your phone

---

## Checking a Connection You Already Saved

When a saved connection stops answering, you can test it without retyping anything:

1. Go to Browse -> Resources
2. **Press and hold** the connection
3. Choose **Test**
4. The watch reports the same result the setup screen gives: success, or the reason it failed

**Delete** sits in the same menu and still asks for confirmation before removing anything.

This works for connections that arrived from your phone as well as ones typed on the watch.

---

## Troubleshooting

### "Failed: Cannot reach server"

- **Problem:** Network connection failed
- **Solutions:**
  - Verify server IP is correct
  - Check server is powered on and connected to network
  - Ensure watch is on same WiFi as server
  - Try pinging: On PC, open Command Prompt and type: `ping [server_ip]`

### "Failed: Access Denied"

- **Problem:** Wrong username/password
- **Solutions:**
  - Try without credentials (leave user/password empty)
  - Verify username spelling (case-sensitive)
  - Check server allows guest access
  - Ask your network admin for correct credentials

### "Cannot find share"

- **Problem:** Wrong share name
- **Solutions:**
  - Double-check exact share name (case matters sometimes)
  - On PC: `File Explorer → Network → Computer → see share names`
  - On NAS: Admin panel → File Services → Shared Folders
  - Try different share names

### "Text input not working"

- **Solutions:**
  1. Use **voice input** (easiest on watch)
  2. Use **phone app** to add connection, it syncs to watch
  3. Try **character picker** popup

---

## Using the phone app

This is the primary route, described in full at the top of this page. In short:

1. **On your phone:**
   - Open main FastMediaSorter app
   - Go to Resources
   - Tap "+ Add Network Source"
   - Fill in details easily with phone keyboard
   - Save

2. **On your watch:**
   - Connection automatically syncs
   - Just browse and use!

On a Google Play build this is the only way to create a connection; on the watch you browse and use it.

---

## Resources Files Browser

Once connected, you can:

- **Tap files** to preview/play
- **Long-press** for options (copy, move, delete)
- **Scroll** through folders
- **Go back** to previous folder
- **Exit** network browse to return to local files

---

## Performance Tips

1. **Use 5GHz WiFi** - Faster than 2.4GHz
2. **Stay close to router** - Better signal on watch
3. **Server should be wired** - Faster access
4. **Don't run other apps** - Frees up resources

---

## Removing a Network Connection

1. Go to Resources list
2. Long-press the connection you want to remove
3. Tap "Delete" or "Remove"
4. Confirm deletion

---

## Need Help?

- Check [SMB_SETUP_GUIDE.md](SMB_SETUP_GUIDE.md) for detailed network setup
- See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for more issues
- Contact your network administrator for server details

