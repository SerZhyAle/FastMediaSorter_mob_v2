# OneDrive Support Setup Guide

This guide describes how to configure the FastMediaSorter application to support Microsoft OneDrive authentication and file operations.

## 1. Register Application in Azure Portal

To allow the application to access OneDrive, you must register it in the Microsoft Azure Portal.

1.  Go to the [Azure Portal](https://portal.azure.com/).
2.  Navigate to **Microsoft Entra ID** (formerly Azure Active Directory).
3.  Select **App registrations** > **New registration**.
4.  Fill in the details:
    *   **Name**: `FastMediaSorter` (or your preferred name).
    *   **Supported account types**: Select **Accounts in any organizational directory (Any Microsoft Entra ID tenant - Multitenant) and personal Microsoft accounts (e.g. Skype, Xbox)**.
        *   *Note: This is required to support personal OneDrive accounts.*
5.  Click **Register**.

## 2. Configure Authentication Platform (Android)

1.  In your new app registration, go to **Authentication** in the left menu.
2.  Click **Add a platform** > **Android**.
3.  Enter the Package Name:
    *   `com.sza.fastmediasorter.debug` (for debug builds)
    *   `com.sza.fastmediasorter` (for release builds)
    *   *Note: You may need to add both if you use both.*
4.  Generate the **Signature Hash**:
    *   You need the SHA-1 hash of your signing certificate, Base64 encoded.
    *   For the debug keystore (default), run this command in a terminal (Git Bash or PowerShell):
        ```bash
        keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
        ```
        *   *Default password is `android`.*
    *   *Note: On Windows, the path is usually `C:\Users\YourUser\.android\debug.keystore`.*
5.  Enter the hash into the **Signature hash** field in Azure Portal.
6.  Click **Configure**.

## 3. Configure Client ID and Redirect URI in Code

1.  In Azure Portal, go to **Overview** and copy the **Application (client) ID**.
2.  Open the project file: `app_v2/src/main/res/raw/msal_config.json`.
3.  Update the fields:
    *   Replace `YOUR_AZURE_AD_CLIENT_ID` with the **Application (client) ID**.
    *   Replace `YOUR_SIGNATURE_HASH` in the `redirect_uri` with the **Signature hash** you generated (ensure it is URL encoded if it contains special characters, though usually it's just the hash).
    *   *Example Redirect URI format*: `msauth://com.sza.fastmediasorter.debug/<Your-Signature-Hash>`

## 4. Update AndroidManifest.xml

The application manifest must handle the redirect URI response.

1.  Open `app_v2/src/main/AndroidManifest.xml`.
2.  Locate the `BrowserTabActivity` entry.
3.  Ensure the `android:path` in the `<data>` tag matches the **Signature hash** part of your Redirect URI (prefixed with `/`).
    *   Example: If your hash is `AbCdEf123...`, the path should be `/AbCdEf123...`.
    *   **Currently, it is set to `/YOUR_SIGNATURE_HASH`**. You MUST update this to match your real hash.

## 5. Verify Permissions

Ensure in Azure Portal > **API permissions** that `User.Read` and `Files.ReadWrite` (or `Files.ReadWrite.All`) are granted.
*   The generic `User.Read` is usually default.
*   You may need to add `Files.ReadWrite` from **Microsoft Graph** API permissions to allow file access.

## 6. Build and Test

1.  Build the app.
2.  Try to add a OneDrive repository.
3.  The Microsoft login page should appear.
4.  After login, the app should receive the token and allow file browsing.

## Troubleshooting

*   **App Crashes on Login**: Check logcat. If you see `MsalClientException`, verify that the `redirect_uri` in `msal_config.json` EXACTLY matches the constructed URI from the `AndroidManifest.xml` intent filter.
*   **"Source not found"**: If you generated the hash for a different keystore (e.g. release vs debug), authentication will fail. Ensure you are using the correct keystore for the build variant.
