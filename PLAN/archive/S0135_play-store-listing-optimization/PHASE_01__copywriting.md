# Phase 01 — Copywriting

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Replace all `store_assets/` placeholder listing texts with ASO-optimized Title / Short description / Long description for EN, RU, UK. Create a What's New template for all three locales.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Git history is accessible for `git log` (needed in Step 01.4 to extract recent changes).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `store_assets/play_store_description_en.txt` | Modified | ≤ 80 |
| `store_assets/play_store_description_ru.txt` | Modified | ≤ 80 |
| `store_assets/play_store_description_uk.txt` | Modified | ≤ 80 |
| `store_assets/whats_new.txt` | Modified | ≤ 20 |
| `store_assets/whats_new_ru.txt` | Modified | ≤ 20 |
| `store_assets/whats_new_uk.txt` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 — Write EN ASO listing

**Files:** `store_assets/play_store_description_en.txt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the entire content of `store_assets/play_store_description_en.txt` with the text below.
> Title is 29 chars (limit 30). Short description is 80 chars (limit 80). Long description is within 4000 chars.
> Do not alter section header labels (`TITLE (30 chars):` etc.) — Play Console operators use them as copy guides.

```
TITLE (30 chars):
Fast Media Sorter & Organizer

SHORT DESCRIPTION (80 chars):
Sort photos & videos fast. Swipe to organize files from storage, NAS, and cloud.

FULL DESCRIPTION (4000 chars):
Stop hunting for photos in cluttered galleries. Fast Media Sorter moves files exactly where you want them — with one tap.

★ SORT WITH ONE TAP
Set up target folders once. From then on, a single tap while viewing moves or copies any file to its destination. No drag. No menu. Done.

📂 ALL STORAGE, ONE SCREEN
Browse files on your phone, SD card, NAS, and cloud — without switching apps.
• SMB — home NAS, Windows shares, Samba servers
• SFTP and FTP — Linux servers, seedboxes, NAS
• Google Drive, OneDrive, Dropbox

🖼️ VIEWER AND PLAYER BUILT IN
View JPEG, PNG, HEIC, WEBP, GIF, animated WebP. Play MP4, MKV, AVI, MOV, M2TS, audio files. Full-screen slideshow.

🗂️ ORGANIZE YOUR WAY
• Batch select — move, copy, delete, rename in one action
• Filter by date, size, and file type
• Soft-delete (trash) with undo — no accidental permanent loss
• Configurable sort order inside folders

🌐 DIRECT NETWORK STORAGE
Connect to your NAS or home server without cloud intermediaries. SMB, SFTP, and FTP — as fast as local storage.

☁️ CROSS-CLOUD MOVES
Move files between Google Drive, OneDrive, Dropbox, and local storage in a single operation.

📹 WIDE FORMAT SUPPORT
Video: MP4, MKV, AVI, MOV, M2TS, WebM and more.
Photo: JPEG, PNG, HEIC, WEBP, TIFF, BMP and more.
Audio: MP3, FLAC, AAC, OGG, OPUS and more.
Documents: PDF and EPUB preview.

✅ WORKS OFFLINE
Local and NAS operations work without internet.

Fast Media Sorter — sort once, find forever.
```

**Verification:**

- `Read` — `store_assets/play_store_description_en.txt` first line contains `Fast Media Sorter & Organizer` (no `Sorter&`).
- `Grep` — pattern `Sorter&` returns zero hits in that file (typo absent).
- `Grep` — `Fast Media Sorter — sort once, find forever.` present in that file.

**Status:** `[x] done`

---

### Step 01.2 — Write RU ASO listing

**Files:** `store_assets/play_store_description_ru.txt`
**Depends on:** — can run in parallel with 01.1

**Prompt for developer:**

> Replace the entire content of `store_assets/play_store_description_ru.txt` with the text below.
> Title is 28 chars (limit 30). Short description is 73 chars (limit 80).
> Keywords targeted: сортировка фото, сортировщик файлов, организация медиа, файловый менеджер NAS.
> Author style: `..` (two dots), `ё`/`Ё` where grammatically correct.

```
TITLE (30 chars):
FastMedia: сортировка файлов

SHORT DESCRIPTION (80 chars):
Сортируйте фото и видео за минуты. Телефон, NAS, облако — в одном экране.

FULL DESCRIPTION (4000 chars):
Перестаньте искать фото в переполненной галерее. Fast Media Sorter перемещает файлы туда, куда нужно, — одним касанием.

★ СОРТИРОВКА ОДНИМ НАЖАТИЕМ
Настройте папки назначения один раз. После этого одно нажатие во время просмотра перемещает или копирует файл в нужное место. Без перетаскивания. Без меню. Готово.

📂 ВСЁ ХРАНИЛИЩЕ — ОДИН ЭКРАН
Файлы с телефона, SD-карты, NAS и облака — без переключения приложений.
• SMB — домашний NAS, общие папки Windows, серверы Samba
• SFTP и FTP — Linux-серверы, сидбоксы, NAS
• Google Drive, OneDrive, Dropbox

🖼️ ПРОСМОТР И ПЛЕЕР ВСТРОЕНЫ
Просматривайте JPEG, PNG, HEIC, WEBP, GIF, анимированный WebP. Воспроизводите MP4, MKV, AVI, MOV, M2TS и аудио. Полноэкранное слайд-шоу.

🗂️ ОРГАНИЗУЙТЕ ТАК, КАК НУЖНО ВАМ
• Групповое выделение — переместить, скопировать, удалить, переименовать за одно действие
• Фильтры по дате, размеру и типу файла
• Корзина с отменой — случайное удаление исключено
• Настраиваемый порядок сортировки внутри папок

🌐 ПРЯМАЯ РАБОТА С СЕТЕВЫМ ХРАНИЛИЩЕМ
Подключайтесь к NAS или домашнему серверу без промежуточного облака. SMB, SFTP, FTP — как локальное хранилище.

☁️ ПЕРЕМЕЩЕНИЕ МЕЖДУ ОБЛАКАМИ
Перемещайте файлы между Google Drive, OneDrive, Dropbox и локальным хранилищем за одно действие.

📹 ШИРОКИЙ СПЕКТР ФОРМАТОВ
Видео: MP4, MKV, AVI, MOV, M2TS, WebM и другие.
Фото: JPEG, PNG, HEIC, WEBP, TIFF, BMP и другие.
Аудио: MP3, FLAC, AAC, OGG, OPUS и другие.
Документы: предпросмотр PDF и EPUB.

✅ РАБОТАЕТ БЕЗ ИНТЕРНЕТА
Операции с локальным хранилищем и NAS работают без сети.

Fast Media Sorter — сортируй один раз, находи всегда.
```

**Verification:**

- `Read` — `store_assets/play_store_description_ru.txt` first line contains `FastMedia: сортировка файлов`.
- `Grep` — `сортируй один раз, находи всегда` present.
- `Grep` — `ё` used at least once (author style check — `всё` or `ещё`).

**Status:** `[x] done`

---

### Step 01.3 — Write UK ASO listing

**Files:** `store_assets/play_store_description_uk.txt`
**Depends on:** — can run in parallel with 01.1, 01.2

**Prompt for developer:**

> Replace the entire content of `store_assets/play_store_description_uk.txt` with the text below.
> Title is 28 chars (limit 30). Short description is 74 chars (limit 80).
> Keywords targeted: сортування фото, сортувальник файлів, організація медіа, файловий менеджер NAS.
> Ukrainian text — not transliterated Russian. Author style: `..` (two dots).

```
TITLE (30 chars):
FastMedia: сортування файлів

SHORT DESCRIPTION (80 chars):
Сортуйте фото та відео за хвилини. Телефон, NAS, хмара — в одному екрані.

FULL DESCRIPTION (4000 chars):
Перестаньте шукати фото в переповненій галереї. Fast Media Sorter переміщує файли туди, куди потрібно, — одним дотиком.

★ СОРТУВАННЯ ОДНИМ НАТИСКАННЯМ
Налаштуйте теки призначення один раз. Далі одне натискання під час перегляду переміщує або копіює файл у потрібне місце. Без перетягування. Без меню. Готово.

📂 УСЕ СХОВИЩЕ — ОДИН ЕКРАН
Файли з телефону, SD-карти, NAS і хмари — без перемикання програм.
• SMB — домашній NAS, спільні папки Windows, сервери Samba
• SFTP і FTP — Linux-сервери, сідбокси, NAS
• Google Drive, OneDrive, Dropbox

🖼️ ПЕРЕГЛЯДАЧ І ПРОГРАВАЧ ВБУДОВАНІ
Переглядайте JPEG, PNG, HEIC, WEBP, GIF, анімований WebP. Відтворюйте MP4, MKV, AVI, MOV, M2TS і аудіо. Повноекранне слайд-шоу.

🗂️ ОРГАНІЗУЙТЕ ТАК, ЯК ЗРУЧНО ВАМ
• Груповий вибір — перемістити, скопіювати, видалити, перейменувати за одну дію
• Фільтри за датою, розміром і типом файлу
• Кошик зі скасуванням — випадкове видалення виключено
• Власний порядок сортування у теках

🌐 ПРЯМА РОБОТА З МЕРЕЖЕВИМ СХОВИЩЕМ
Підключайтесь до NAS або домашнього сервера без проміжного хмарного сервісу. SMB, SFTP, FTP — як локальне сховище.

☁️ ПЕРЕМІЩЕННЯ МІЖ ХМАРАМИ
Переміщуйте файли між Google Drive, OneDrive, Dropbox і локальним сховищем за одну дію.

📹 ШИРОКИЙ СПЕКТР ФОРМАТІВ
Відео: MP4, MKV, AVI, MOV, M2TS, WebM та інші.
Фото: JPEG, PNG, HEIC, WEBP, TIFF, BMP та інші.
Аудіо: MP3, FLAC, AAC, OGG, OPUS та інші.
Документи: попередній перегляд PDF і EPUB.

✅ ПРАЦЮЄ БЕЗ ІНТЕРНЕТУ
Операції з локальним сховищем і NAS працюють без мережі.

Fast Media Sorter — сортуй один раз, знаходь завжди.
```

**Verification:**

- `Read` — `store_assets/play_store_description_uk.txt` first line contains `FastMedia: сортування файлів`.
- `Grep` — `сортуй один раз, знаходь завжди` present.
- `Grep` — `відео` (Ukrainian, not Russian `видео`) present.

**Status:** `[x] done`

---

### Step 01.4 — Write What's New for EN / RU / UK

**Files:** `store_assets/whats_new.txt`, `store_assets/whats_new_ru.txt`, `store_assets/whats_new_uk.txt`
**Depends on:** — can run in parallel with 01.1–01.3

**Prompt for developer:**

> Run `git log --oneline --since="2026-01-31" --no-merges` to obtain changes since the last published release.
> Extract the 3–5 most user-visible changes (features, fixes, improvements — not internal refactors).
> Rewrite all three `whats_new*.txt` files using the template below, substituting actual changes in each language.
> Max length per file: 500 chars (Play Console limit).
> Do NOT use the placeholder `fix and improve`.
> Author style for RU/UK: `..` (two dots), `ё`/`Ё` where grammatically correct (RU).

Template structure (EN):
```
🎉 [Month YYYY] Update

✅ New:
• [Feature / improvement 1]
• [Feature / improvement 2]

🐛 Fixed:
• [Bug fix 1]
• [Bug fix 2]
```

Adapt to RU/UK with translated language — do not transliterate.
If fewer than 3 items exist, merge New + Fixed into a single `✅ Changes:` section.

**Verification:**

- `Grep` — pattern `fix and improve` returns zero hits in `store_assets/whats_new.txt`.
- `Grep` — pattern `fix and improve` returns zero hits in `store_assets/whats_new_ru.txt`.
- `Grep` — pattern `fix and improve` returns zero hits in `store_assets/whats_new_uk.txt`.
- `Read` — each file is non-empty and contains at least one bullet `•`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` — `Sorter&` returns zero hits in all `store_assets/play_store_description_*.txt`.
- [ ] All three description files have distinct locale content (not copies of each other).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`store_assets/` now contains ASO-ready listing texts for all three locales. Phase 05 operator copies these verbatim into Play Console.

---

## Rollback Plan

Revert phase commit — no build impact, no data migration, no user-facing UI changed.
