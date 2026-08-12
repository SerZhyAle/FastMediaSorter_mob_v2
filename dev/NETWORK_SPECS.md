# Network Protocol Notes

- **SMB**: Use `SmbConnectionManager` for connection pooling. SMBJ 0.12.1+.
- **FTP**: Apache Commons Net. Use active mode fallback for PASV timeouts.
- **SFTP**: JSch (`com.github.mwiede:jsch`, the maintained fork), Ed25519 support built in - no separate EdDSA artifact. The comment above the dependency in `app_v2/build.gradle.kts` records why this fork beat the alternative on Android KEX support. Check `Job.isActive` for cancellation.
