# S1304 - Wear SMB getFileStream drops the smbj File handle - one leaked open SMB2 handle per viewed media file

**Ticket:** S1304
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-6.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: S0725/S0902 (same module, different defect).

## Finding 1: Wear SMB getFileStream drops the smbj File handle - one leaked open SMB2 handle per viewed media file

- Severity: P2, effort: trivial.
- File: `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt:190`
- Symptom: On the watch, every SMB photo/video/audio opened leaks an open SMB2 file handle on the long-lived session (client- and server-side); after browsing many files over a long session the NAS starts refusing opens (handle/resource limits) until the app reconnects or exits.
- Failure scenario: User browses an SMB photo folder on the watch for an hour. Each ImageViewer/VideoPlayer/AudioPlayer load calls getFileStream, copies the stream with .use (ImageViewerViewModel.kt:115) - but smbj's FileInputStream.close() does not close the underlying DiskEntry, and the File object is unreachable, so the SMB2 handle stays open on the session until disconnect(). The phone app knows this: app_v2 SmbClient.kt:622-634 wraps the identical stream in a FilterInputStream whose close() explicitly closes the file ('Return wrapper that closes the file when stream is closed'). The wear module lacks that wrapper, so handles accumulate for the lifetime of the connection.
- Fix sketch: Mirror the phone-app pattern: return object : FilterInputStream(file.inputStream) { override fun close() { try { super.close() } finally { runCatching { file.close() } } } } from getFileStream (and close the File in getFileSize's error paths, which already use file.use).
- Verifier rationale: Confirmed. getFileStream (lines 181-192) opens the smbj File, returns file.inputStream, and drops the File reference - smbj's FileInputStream.close() does not close the underlying DiskEntry, so the SMB2 handle stays open on the long-lived session until disconnect(). The phone app documents exactly this smbj behavior and fixes it with a FilterInputStream wrapper whose close() explicitly closes the file handle (app_v2 SmbClient.kt:622-645, 'Return wrapper that closes the file when stream is closed'); the wear module lacks that wrapper. Sibling methods getFileSize/getFileInfo correctly use file.use, underlining getFileStream as the one leaking path. One leaked handle per viewed media file, accumulating client- and server-side for the connection lifetime - resource growth without crash, P2; fix is a copy of the existing phone-side wrapper, trivial.

Evidence excerpt:

```
val file = currentShare.openFile(
    cleanPath, EnumSet.of(AccessMask.GENERIC_READ), null,
    SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null
)
val inputStream = file.inputStream
Result.success(inputStream)   // `file` reference dropped, never closed
```

