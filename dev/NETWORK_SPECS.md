# Network Protocol Notes

- **SMB**: Use `SmbConnectionManager` for connection pooling. SMBJ 0.12.1+.
- **FTP**: Apache Commons Net. Use active mode fallback for PASV timeouts.
- **SFTP**: SSHJ + EdDSA. Check `Job.isActive` for cancellation.
