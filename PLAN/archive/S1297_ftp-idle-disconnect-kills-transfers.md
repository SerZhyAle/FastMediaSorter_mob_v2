# S1297 - FTP idle-disconnect timer kills the shared FTPClient mid-operation - any transfer longer than 30s fails

**Ticket:** S1297
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-3.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: mirror SMB usageCount/isPendingClose pattern.

## Finding 1: FTP idle-disconnect timer kills the shared FTPClient in the middle of any operation longer than 30 s

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt:344`
- Symptom: Every FTP download/upload/recursive listing that takes longer than 30 s fails mid-transfer: the idle callback logs out and disconnects the one shared FTPClient while retrieveFile/storeFile is still streaming on it, leaving a partial file and a failed copy operation.
- Failure scenario: User copies a 500 MB movie from an FTP source (FtpToLocalStrategy.kt:79 -> ftpClient.downloadFile -> FtpConnectedOperations.downloadFile line 292 client.retrieveFile). connect() armed the 30 s timer; withTrackedConnectedOperation touches it once at op start and nothing touches it during the transfer. At t=+30 s IdleDisconnectPolicyImpl fires the callback, which runs disconnect() concurrently (disconnectInternal takes no mutex, lines 130-162) - logout/disconnect on the control socket kills the in-flight data transfer, retrieveFile fails, the copy reproducibly fails for any file that needs >30 s. Same for long recursive listFilesWithMetadata scans and uploads.
- Fix sketch: Track an in-flight operation counter in FtpClient (increment in withTrackedConnectedOperation around block()); make the idle callback skip disconnect while the counter is non-zero (mark pending-close and disconnect on op completion), mirroring SMB's usageCount/isPendingClose pattern.
- Verifier rationale: Confirmed. withTrackedConnectedOperation (350-359) touches the idle timer only BEFORE block() and re-arms only after success; FtpConnectedOperations holds no policy reference, so nothing refreshes the timer during a transfer. The armed 30 s callback (line 344) is disconnect(), and disconnectInternal (130-162) acquires no mutex - it logs out and disconnects the single shared FTPClient while retrieveFile/storeFile is still streaming on it. Any connected-mode download/upload/recursive listing taking longer than 30 s reproducibly fails mid-transfer with a partial file. The Apache controlKeepAliveTimeout NOOPs do not touch the policy, so nothing prevents the timer firing.

Evidence excerpt:

```
private fun armCurrentTransport() {
    currentTransportKey?.let { transportKey ->
        idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) {   // 30_000L
            disconnect()
        }
    }
}
// withTrackedConnectedOperation: touch only BEFORE block(), re-arm only after
```

