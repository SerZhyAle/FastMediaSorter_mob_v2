---
id: S0280
name: bugfix-local-copy-mediastore-pre-q-regression
status: Tactical
priority: 85
tier: 2
file: PLAN/S0280_bugfix-local-copy-mediastore-pre-q-regression.md
created: 2026-05-20
---

# S0280 — Local copy: MediaStore pre‑Q regression

Follow‑up to S0231. SFTP/SMB/FTP/Cloud → Local copy is broken on API < 29 because `MediaStoreLocalDestinationWriter` unconditionally uses scoped‑storage semantics (`RELATIVE_PATH` + `IS_PENDING`), which the legacy MediaProvider rejects.

## 1. Symptom

- User copies any source file into `/storage/emulated/0/Download/...` on a pre‑Q device.
- `findExistingItem` throws `SQLiteException: no such column: relative_path` (swallowed → "treating as not‑exists").
- `contentResolver.insert(Files, values)` throws `IllegalArgumentException: no path was provided when inserting new file`.
- UI: "Не получилось скопировать выбранные файлы".

## 2. Root cause

`MediaStoreLocalDestinationWriter.openMediaStoreSink` builds `ContentValues` with `RELATIVE_PATH` + `IS_PENDING` — both introduced in API 29 (Q). On pre‑Q the underlying MediaProvider `files` table has neither column, and the legacy insert requires `MediaStore.MediaColumns.DATA` (absolute path) instead.

Before S0231 the write was a plain `FileOutputStream(File(destination))`; S0231 routed every `PublicCollection` through MediaStore without a legacy branch.

## 3. Fix

- On `Build.VERSION.SDK_INT < Q`, `LocalDestinationWriter.open` for `PublicCollection` delegates to the existing `openFileSystemSink` with the absolute path reconstructed as `Environment.getExternalStorageDirectory()/{relativePath}{displayName}`.
- On `Q+` keep current MediaStore flow unchanged.
- Defensive hardening: `findExistingItem` guards the `RELATIVE_PATH` selector behind `SDK_INT >= Q`; the existing catch‑all `Timber.w(...)` stays as last‑line defence.

## 4. Scope

Single file edit: `data/transfer/local/MediaStoreLocalDestinationWriter.kt`. No interface or DI change. No classifier change.

## 5. Acceptance

- Build `assembleStandardDebug` passes.
- Manual: SFTP → `/storage/emulated/0/Download/<name>` on pre‑Q emulator copies the file successfully (no SQLite, no IAE).
- Q+ path unchanged: SFTP/SMB/FTP/Cloud → Pictures/Music/Movies/Download still works via MediaStore + `IS_PENDING`.
- `NonPublic` destinations unchanged.

## 6. Related

- S0231 (Verified) — original scoped‑storage fix that introduced the regression.
