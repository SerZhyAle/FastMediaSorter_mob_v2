# Fast Media Sorter v2 Testing

This guide is for testing the main application features related to local and SMB resources.

## 1. Test Preparation

### 1.1. Application Installation
1.  Install the application's APK file on a test Android device.
2.  Launch the application.
3.  Grant all requested permissions (file access, etc.).

### 1.2. Test Environment Setup

#### Local Folders:
1.  On the Android device, ensure the `Download` and `Photos` folders exist. Create them if necessary.
2.  Copy several media files (e.g., from your project's `test_media` folder) into the `Download` and `Photos` folders on the device. Make sure both folders contain files.

#### SMB Resource:
1.  Ensure the SMB server is accessible at `192.168.1.100`.
2.  Ensure there is a shared folder named `test2` on the server.
3.  Copy all files from the local project folder `test_media` to the `test2` shared folder on the SMB server.
4.  Ensure you have the credentials (username and password) to access this folder if required.

## 2. Test Scenarios

### 2.1. Working with Local Storage

#### Test 2.1.1: Add and View a Local Resource
1.  On the main screen, tap "Add Resource".
2.  Select "Local Storage".
3.  Navigate to the `Download` folder and select it.
4.  Verify that the `Download` resource appears in the list.
5.  Tap on the `Download` resource.
6.  **Expected Result:** A gallery with media files from the `Download` folder is displayed. Check that all files are visible.

#### Test 2.1.2: Copying Files Between Local Folders
1.  Open the `Download` resource.
2.  Select one or more files.
3.  Tap the "Copy" button.
4.  In the destination folder selection dialog, choose the `Photos` folder.
5.  **Expected Result:** The files are successfully copied to the `Photos` folder. Verify the files' presence in the `Photos` folder using the device's file manager. The original files should remain in the `Download` folder.

#### Test 2.1.3: Moving Files Between Local Folders
1.  Open the `Download` resource.
2.  Select different files (not the ones you copied).
3.  Tap the "Move" button.
4.  In the destination folder selection dialog, choose the `Photos` folder.
5.  **Expected Result:** The files are successfully moved to the `Photos` folder. Verify the files' presence in the `Photos` folder and their absence from the `Download` folder.

#### Test 2.1.4: Deleting Files
1.  Open the `Photos` resource.
2.  Select one or more files.
3.  Tap the "Delete" button.
4.  Confirm the deletion.
5.  **Expected Result:** The files are deleted from the `Photos` folder. Verify their absence.

### 2.2. Working with an SMB Resource

#### Test 2.2.1: Add and View an SMB Resource
1.  On the main screen, tap "Add Resource".
2.  Select "SMB Server".
3.  Enter the following details:
    *   Server Address: `192.168.1.100`
    *   Folder Path: `test2`
    *   Username and password (if required).
4.  Tap "Connect".
5.  **Expected Result:** The resource `smb://192.168.1.100/test2` appears in the list. Tapping it opens a gallery with files from this network folder.

#### Test 2.2.2: Copying Files from SMB to Local Device
1.  Open the SMB resource.
2.  Select one or more files.
3.  Tap "Copy".
4.  In the destination folder selection dialog, choose the local `Download` folder.
5.  **Expected Result:** The files are successfully copied to the `Download` folder. Verify their presence on the device.

#### Test 2.2.3: Moving Files from SMB to Local Device
1.  Open the SMB resource.
2.  Select different files.
3.  Tap "Move".
4.  In the destination folder selection dialog, choose the local `Download` folder.
5.  **Expected Result:** The files are successfully moved to the `Download` folder. Verify their presence on the device and their absence from the SMB server.

#### Test 2.2.4: Copying Files from Local Device to SMB
1.  Open the local `Photos` resource.
2.  Select one or more files.
3.  Tap "Copy".
4.  In the destination folder selection dialog, choose the SMB resource (`test2`).
5.  **Expected Result:** The files are successfully copied to the `test2` folder on the SMB server.

#### Test 2.2.5: Moving Files from Local Device to SMB
1.  Open the local `Photos` resource.
2.  Select different files.
3.  Tap "Move".
4.  In the destination folder selection dialog, choose the SMB resource (`test2`).
5.  **Expected Result:** The files are successfully moved to the `test2` folder on the SMB server and deleted from the local `Photos` folder.

#### Test 2.2.6: Deleting Files from an SMB Resource
1.  Open the SMB resource.
2.  Select one or more files.
3.  Tap "Delete".
4.  Confirm the deletion.
5.  **Expected Result:** The files are deleted from the SMB server.

### 2.3. Error Testing

#### Test 2.3.1: Incorrect SMB Folder Path
1.  Try to add an SMB resource with an incorrect path, e.g., `test` instead of `test2`.
2.  **Expected Result:** The application should show a meaningful error (e.g., "Folder not found" or "Invalid path"), not just "Copy failed" or another generic error.

#### Test 2.3.2: Network Loss During Operation
1.  Start copying a large file from the SMB resource.
2.  During the copy, turn off Wi-Fi on the device.
3.  **Expected Result:** The application should handle the error correctly, cancel the operation, and show the user a message about the lost connection. The partially copied file should be deleted from the destination folder.