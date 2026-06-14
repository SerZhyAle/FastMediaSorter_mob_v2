# UI Clarification: S0046 - SFTP key-auth setup UX

**Date:** 2026-06-14
**Status:** READY
**Ticket:** S0046
**Scope:** Add/Edit SFTP resource screen, SSH-key auth path, host-key pinning UX

---

## UI Clarification Status
Status: READY

### Approved Decisions

- **Portrait placement:** keep the current `Add S/FTP folder` screen and section order, but split it into a simple first block and advanced collapsible blocks below it.
- **Landscape placement:** same structure and order as portrait; no toolbar action, overflow item, dialog, or bottom sheet for the core setup fields.
- **Primary first-screen block:** show only `Host`, `Port`, `Username`, auth-method switcher, password-or-key input, `Test connection`.
- **Advanced security placement:** move host-key pinning into a collapsed block named `Server verification` / `Проверка сервера` / `Перевірка сервера`, below the auth block and above remote-path/resource-name.
- **Remote path placement:** keep `Remote path` on the main screen, but below `Test connection`; it is not required before the first test.
- **Media/scanning/additional options:** stay collapsed and below the connection block, unchanged in general placement.

- **Visibility rules:** the host-key verification block is visible only for `SFTP`, never for `FTP`.
- **Auth method visibility:** when `Password` is selected, show only username + password fields; when `SSH key` is selected, hide the password field and show only key field + optional passphrase.
- **Hidden vs disabled:** unused auth fields must be hidden, not disabled.
- **Space priority:** `Host`, `Port`, auth switcher, credentials, and `Test connection` outrank every other field. On small screens, advanced blocks remain collapsed by default.
- **Overflow behavior:** no overflow migration for any SFTP setup action in this task.

- **Auth method control:** keep a two-option inline switcher on the form, but label it as a human choice:
  - RU: `Способ входа`
  - EN: `Sign-in method`
  - UK: `Спосіб входу`
- **SSH key field label:** replace `Private key (PEM format)` with human wording that matches real supported inputs:
  - RU: `Приватный SSH-ключ`
  - EN: `Private SSH key`
  - UK: `Приватний SSH-ключ`
- **SSH key helper text:** use plain guidance:
  - RU: `Вставьте ключ или выберите файл.`
  - EN: `Paste the key or choose a file.`
  - UK: `Вставте ключ або виберіть файл.`
- **Passphrase helper text:** keep optional wording and make the empty-state intent explicit:
  - RU: `Оставьте пустым, если ключ не зашифрован.`
  - EN: `Leave empty if the key is not encrypted.`
  - UK: `Залиште порожнім, якщо ключ не зашифрований.`
- **Host-key field label:** do not expose fingerprint jargon as the main label. Use:
  - RU: `Проверка сервера (опционально)`
  - EN: `Server verification (optional)`
  - UK: `Перевірка сервера (необов'язково)`
- **Host-key helper text:** explain purpose without jargon first, technical value second:
  - RU: `Нужно только если хотите закрепить сервер и защититься от подмены. Формат: SHA256:...`
  - EN: `Use this only if you want to pin the server and protect against server impersonation. Format: SHA256:...`
  - UK: `Потрібно лише якщо ви хочете закріпити сервер і захиститися від підміни. Формат: SHA256:...`
- **Button behavior:** `Test connection` remains in the main flow and is the primary action before saving.
- **Long-click behavior:** none.

- **Default values:** for `SFTP`, prefill `Port = 22`; keep it editable.
- **Remote path requirement:** allow empty `Remote path` before the first connection test. If the backend requires a path for save, validate only at save time.
- **Resource name behavior:** keep auto-generation if empty.

- **Success feedback:** after successful test, show a short confirmation and keep the user on the same screen.
  - RU example: `Подключение работает.`
- **Pinned mismatch error:** show a distinct, user-readable message separate from auth failure.
  - RU: `Не удалось подтвердить сервер - отпечаток не совпадает. Проверьте адрес сервера или обновите сохранённый отпечаток.`
  - EN: `Couldn't verify the server - the fingerprint doesn't match. Check the server address or update the saved fingerprint.`
  - UK: `Не вдалося підтвердити сервер - відбиток не збігається. Перевірте адресу сервера або оновіть збережений відбиток.`
- **Generic auth failure:** keep separate from host-key mismatch.
  - RU example: `Не удалось войти на сервер. Проверьте имя пользователя, пароль или ключ.`
- **Upload/key read failure:** inline or snackbar message with one next step.
  - RU example: `Не удалось прочитать ключ. Выберите другой файл или вставьте ключ вручную.`
- **Empty state/help behavior:** no extra help CTA on the main form; helper text is enough.
- **Confirmation dialogs:** none for normal connect-test flow.

- **Accessibility:** every toggle, upload button, and collapsed security block must have explicit `contentDescription`.
- **Touch targets:** keep upload and visibility buttons at standard Material minimum targets.
- **Discoverability:** collapsed `Server verification` must still show a one-line subtitle/hint that it is optional security, not required setup.

### Delegated Assumptions

- The agent may keep the current single-screen architecture and implement the simplification without introducing a multi-step wizard.
- The agent may keep scanning/media/additional sections as collapsible cards and focus only on reducing the cognitive load of the connection/auth/security block.
- The agent may preserve existing save/test flows and only change presentation, labels, visibility rules, and validation timing described above.

---

## Summary For Implementation

1. Reduce the first visible block to connection essentials.
2. Hide unused auth fields completely.
3. Move host-key pinning under an optional security block with human wording.
4. Keep `Test connection` as the first main action.
5. Defer non-essential choices until after the basic connection details are understandable.
