# Research 02 - Post-capture processing & storage under Google Play policy (2025-2026)

**Spec:** S0671
**Verdict:** allowed_with_conditions
**Method:** Web research against official Android + Play policy sources.

## Conclusion

Assuming the capture itself is compliant, the five post-capture pieces are mostly low-risk:
- (a) clipboard copy, (b) in-app markup/drawing, (c) on-device OCR + translation - operate on an image the app itself owns, touch no restricted permission, raise no scoped-storage or Play-policy problem.
- (e) saving to Pictures/Screenshots, DCIM/Screenshots, Downloads via MediaStore needs NO WRITE_EXTERNAL_STORAGE on Android 10+ (app owns the file). `WRITE_EXTERNAL_STORAGE` only matters for legacy API <=28 (`android:maxSdkVersion="28"`).
- (d) curated "send to recipients" list is the only item that can independently create a Play-policy problem: fine with `ACTION_SEND`/`ACTION_SENDTO` intents or the Photo Picker, but restricted if it uses SMS (`SEND_SMS`) or reads Contacts/Call Log - Play lists "content sharing or invites" as a non-permitted use unless the app is the default SMS/Phone handler.

Read-media permissions (`READ_MEDIA_IMAGES/VIDEO`) are NOT needed to write app-created output; they only apply if the feature also browses the user's existing gallery.

## Key clauses

- Android 10+ needs no storage permission to write/modify app-owned media (incl. MediaStore.Downloads). [https://developer.android.com/training/data-storage/shared/media]
- On Android 11 `WRITE_EXTERNAL_STORAGE`/`WRITE_MEDIA_STORAGE` grant no extra access (no-op). [https://developer.android.com/about/versions/11/privacy/storage]
- Recommended write path: `ContentResolver.insert()` into MediaStore, optional `IS_PENDING`. [https://developer.android.com/training/data-storage/use-cases]
- Broad `READ_MEDIA_*` reserved for gallery-class apps; transactional access must use the Photo Picker; writing output needs neither. [https://support.google.com/googleplay/android-developer/answer/14115180]
- SMS/Call Log policy lists "content sharing or invites" as NOT permitted unless default handler. [https://support.google.com/googleplay/android-developer/answer/10208820]
- Google steers to system pickers/Sharesheet; a bespoke share list that avoids sensitive permissions is policy-clean. [https://support.google.com/googleplay/android-developer/answer/16935362]
- Clipboard restricted to foreground apps (Android 10+); Android 12+ shows a paste toast; runtime behavior, not a Play gate. [https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling]

## Conditions for S0671

- Send-to-recipients: deliver via Sharesheet/`ACTION_SEND`/`ACTION_SENDTO` or Photo Picker; do NOT request SMS/Contacts/Call Log for it. If contacts are needed, use the contact picker intent (no `READ_CONTACTS`).
- Storage: write via MediaStore on API 30+; do not declare `WRITE_EXTERNAL_STORAGE` for Android 11+. For legacy <=28 path, scope it with `android:maxSdkVersion="28"`.
- OCR + translation: keep on-device (ML Kit on standard) so no image leaves the device; if anything is sent off-device, disclose in Data safety.
- Do not request `READ_MEDIA_*` solely to save app-created screenshots.
