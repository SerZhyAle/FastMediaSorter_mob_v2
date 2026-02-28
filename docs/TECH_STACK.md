# FastMediaSorter v2: Tech Stack

Ref: `gradle/libs.versions.toml`

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