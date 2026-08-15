# Tactical Plan — S0163: Двойная загрузка на SMB при Move после batch-delete диалога

**Ticket:** S0163
**Status:** Tactical
**Strategic spec:** `PLAN/S0163_bugfix-smb-move-double-upload-after-batchdelete.md`
**Created:** 2026-05-11

## Summary

The double-upload regression is fully covered by the S0154 `PermissionGate` fix already merged to
`PlayerFileOperationQueue.kt`. No new code is required. These phases constitute a verification sweep
and formal sign-off against the §9 criteria in the strategic spec.

## Phases

| # | File | Title | Status |
|---|------|--------|--------|
| 01 | `PHASE_01__gate-mechanism-verification.md` | Verify S0154 PermissionGate covers all S0163 criteria | ✅ Done |
| 02 | `PHASE_02__string-ux-compliance.md` | String/UX compliance audit against COMMUNICATION_POLICY | ✅ Done |

## Verification Summary (F3 execution)

All S0163 §9 criteria verified via code review (2026-05-11):

- **§9.1** Upload SUCCESS → confirm dialog → no error: ✅ `gate.await()` suspends worker;
  after `gate.complete(true)`, emits `Succeeded` directly — no use-case re-invocation.
- **§9.2** File absent on device, present on SMB: ✅ System auto-deletes on `createDeleteRequest`
  confirmation; upload already done before dialog is shown.
- **§9.3** Cancel dialog → clear message: ✅ `error_queued_move_permission_denied` EN/RU/UK
  shows "copied to destination, local copy not deleted — permission denied"; no retry offered.
- **§9.4** No `uploadToSmb: Local file does not exist` after SUCCESS: ✅ Gate prevents use-case
  restart; upload is never called twice for the same operation.
- **§9.5** FTP/SFTP same pattern absent or fixed: ✅ Both `FtpFileOperationHandler` and
  `SftpFileOperationHandler` call `requestBatchDeletePermission` → same `PermissionRequired` result
  → same queue gate handles them; code confirmed at `FtpFileOperationHandler.kt:125` and
  `SftpFileOperationHandler.kt:148`.

No code changes required. Status advances directly to Implemented.
