# S1801 Wear OS Network Share Walkthrough

Recorded from Wear OS implementation and UI review (`FastMedia Wear`, `NetworkSourcesScreen`, `AddNetworkSourceScreen`, `BrowseScreen`).

---

## 1. Network Resources List Screen (`NetworkSourcesScreen`)

- **Screen Title**: `Resources` (`R.string.network_storage`)
- **Empty State**:
  - Description text: `wear_resources_empty_hint` ("Nothing has arrived from the phone yet. On the phone open Wear-companion and pick the resources to send here." / "С телефона сюда пока ничего не передали...")
  - Action button: `Sync from Phone` (`wear_sync_from_phone`)
  - Action button: `Add Source` (`add_network_source`, in debug mode or manual setup)
- **Populated State**:
  - List / grid of saved network sources showing Source Name (`name`) and Server Address (`server`)
  - Tapping a source opens file browser for that source (`BrowseScreen`)
  - Bottom action: `Sync from Phone` (`wear_sync_from_phone`)
  - Bottom action: `Send to phone` (`wear_export_to_phone`)
  - Long-press / delete action: `Delete Source` confirmation dialog ("Delete “%s”?")

---

## 2. Add Network Source Screen (`AddNetworkSourceScreen`)

- **Screen Title**: `Add Source` (`add_network_source`)
- **Subtitle**: `Tap to edit` (`tap_to_edit`)
- **Protocol Selection Chips**:
  - `SMB` (`smb_connection`)
  - `FTP` (`ftp_connection`)
  - `SFTP` (`sftp_connection`)
- **Editable Fields** (tapping opens overlay with on-screen keyboard):
  - **Name** (`name_label`): Optional friendly name for the source
  - **Server Address** (`server_address`): Hostname or IP address (e.g. `192.168.1.100`) - required
  - **Port** (`port`): Network port (defaults: SMB 445, FTP 21, SFTP 22) - required
  - **Share Name** (`share_name`): Shared folder name (SMB only) - required
  - **Domain** (`domain`): Windows domain or workgroup (SMB only) - optional
  - **Username** (`username`): Login username - optional for guest / required for auth
  - **Password** (`password`): Masked password field with `Show password` / `Hide password` toggle
  - **Use SSH Key** (`use_ssh_key`): Toggle enabled/disabled (SFTP only)
  - **Private Key** (`ssh_private_key`): Configured key text (SFTP with key auth)
- **Action Buttons**:
  - **Test** (`test_connection`): Tests connection with live server
  - **Save** (`save`): Saves the source configuration and returns to Resources list

---

## 3. Connection Test & Verification Messages

- **Testing in progress**: `Testing..` (`testing_connection`)
- **Success message**: `Connection successful!` (`connection_successful`)
- **Failure messages**:
  - `Connection failed: %s` (`connection_failed_with_reason`)
  - `Server required` (`server_required`)
  - `Server and share name required` (`server_and_share_required`)
  - `SSH private key required` (`ssh_key_required`)
  - `Test not available for this protocol` (`connection_test_not_supported`)
- **Saving status**: `Saving..` (`saving_connection`) / `Connection saved!` (`connection_saved`)

---

## 4. Browsing Network Media (`BrowseScreen`)

- **Root / Base Path Navigation**:
  - Source opens at configured base path / share root.
  - Subfolders and files are listed in scrollable list or grid view.
- **Media-type Navigation**:
  - Filter and browse by media type: `Music`, `Videos`, `Photos`.
  - Tap any file to launch appropriate player (`AudioPlayerScreen`, `VideoPlayerScreen`, `ImageViewerScreen`).
