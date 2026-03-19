# FastMediaSorter v2: Tech Stack

Ref: `gradle/libs.versions.toml`

## Platform
- `minSdk 26` (Android 8+) for standard/lite/photos; `minSdk 23` (Android 6+) for legacy
- `coreLibraryDesugaring` enabled — provides `java.time.*` on API 23-25 (legacy only)

## Dependencies
- **Core**: Hilt, Room
- **Media**: ExoPlayer
- **Image**: Glide (App), Coil (Wear)
- **Network**: SMBJ, SSHJ, Commons Net, OkHttp
- **Cloud**: Drive, OneDrive, Dropbox
- **OCR/AI**: ML Kit, Tesseract4Android

## Network Protocol Notes
- **SMB**: `SmbConnectionManager`.
- **FTP**: Apache Commons Net. Active mode fallback.
- **SFTP**: SSHJ + EdDSA. Check `Job.isActive`.