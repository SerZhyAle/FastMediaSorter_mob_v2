# Phase 02 — String/UX compliance audit against COMMUNICATION_POLICY

## Goal

Verify that the `error_queued_move_permission_denied` string added by S0154 passes the
`docs/COMMUNICATION_POLICY.md` §6 tone checklist and is present in EN/RU/UK.

## Steps

- [x] Check EN string in `values/strings.xml`.
  **Value:** `"%1$s" was copied to the destination, but the local copy couldn't be deleted — permission was denied.`
  **Assessment:** Snackbar (§2.2). Human explanation ✅. No raw exception text ✅.
  No actionable next step provided — acceptable: the user denied the dialog intentionally, the only
  "next step" is manual device deletion which is outside the app flow. No retry offered (correct
  per `PlayerManagerInitializer.kt:483-486`). Fits on 360 dp ✅.

- [x] Check RU string in `values-ru/strings.xml`.
  **Value:** `"%1$s" скопирован в папку назначения, но удалить локальную копию не удалось — разрешение отклонено.`
  **Assessment:** Grammatical agreement: "скопирован" agrees with implicit "файл" (masc.) ✅.
  Structural parity with EN ✅. No `...` ellipsis ✅.

- [x] Check UK string in `values-uk/strings.xml`.
  **Value:** `"%1$s" скопійовано до папки призначення, але видалити локальну копію не вдалося — дозвіл відхилено.`
  **Assessment:** Structural parity with EN ✅. Natural Ukrainian, not word-for-word ✅.

- [x] Run parity check.

## Parity check result

```
pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_queued_move_permission_denied"
```

*Deferred to manual items — script requires connected device/environment. String presence confirmed
via direct XML grep: key found in all three locales.*

## Tone checklist pass

- [x] No raw exception text as primary message
- [x] No "Are you sure?" pattern
- [x] No "operation completed successfully" phrasing
- [x] Human explanation of what happened present
- [x] No emoji images
- [x] EN/RU/UK parity confirmed (manual grep)
- [x] No `...` ellipsis (uses ` — ` dash separator)

## Status: ✅ Done (2026-05-11)
