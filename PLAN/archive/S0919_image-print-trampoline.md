# S0919 - Image print via PrintDispatchActivity trampoline

**Status:** Archived

## 0. Evidence

- Device: samsung SM-S731B, One UI, Android 16 (from `logs/fastmediasorter_20260703_184432.log`).
- Symptom: "Send to.. -> Print" for an image in the standalone photo/video player does nothing (no system print preview).
- Root cause: `PhotoVideoStandaloneActivity.printMediaFile()` calls `androidx.print.PrintHelper(this).printBitmap(..)` where `this` is a `BaseActivity` whose `attachBaseContext` is wrapped by `createConfigurationContext` (locale). One UI rejects `PrintManager.print()` on that wrapped context with "Can print only from an activity". No `try/catch`, no fallback -> silent no-op.
- This is the exact defect `PrintDispatchActivity` (S0613) already solves for PDF/TEXT by dispatching from a plain `AppCompatActivity` with a clean context. The IMAGE path never routed through it.
- Secondary: `DocumentPrintManager.printImage()` hits the same wrapped context but survives only via `try/catch` -> share-fallback (no real print on One UI).

## 1. Goal

Route every image-print job through `PrintDispatchActivity` (clean Activity context), so One UI reaches the system print UI instead of the "Can print only from an activity" rejection.

## 2. Scope

- Add `PrintMode.IMAGE` + `startImage(..)` + `dispatchImage()` to `PrintDispatchActivity`.
- `PhotoVideoStandaloneActivity.printMediaFile()`: stage the displayed bitmap to a private temp PNG, dispatch through the trampoline.
- `DocumentPrintManager.printImage()`: dispatch the already-materialised image file through the trampoline; drop the inline `PrintHelper` block.

## 3. Design

- Trampoline decodes the passed image file with `BitmapFactory.decodeFile`, then `PrintHelper(this).printBitmap(jobLabel, bitmap)` on its clean context; on decode/dispatch failure it reuses the existing share-for-print fallback (mime `image/*`).
- Standalone host owns its temp staging dir `cacheDir/print_tmp/`: cleared before each write (bounds to one file), OS-reclaimable. The trampoline deletes nothing (`DocumentPrintManager`'s file is cache/LRU-managed elsewhere).
- `printMediaFile()` keeps its `Boolean` gate contract: `true` once an on-screen bitmap exists and async staging+dispatch is launched; a hard failure surfaces a toast.

## 4. Non-goals

- The `onDestroy` Glide crash (`StandaloneViewManager.release`) is already fixed in tree (`Glide.with(activity.applicationContext)`); only a rebuild is needed. Not part of this ticket.

## 5. Verification

- On the Samsung device: open an image in the standalone photo player -> Share -> Send to.. -> Print -> system print preview must open.
- Repeat via the in-app/document player image path (S0613 host) -> same result.
