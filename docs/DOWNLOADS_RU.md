---
layout: default
title: "Загрузки APK FastMediaSorter"
permalink: /docs/DOWNLOADS_RU.html
---

# Загрузки APK FastMediaSorter

Скомпилированные APK публикуются как **ассеты GitHub Release** - в репозиторий они не коммитятся.

## Скачать (рекомендуется)

🔗 **[Последний релиз](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest)**

Каждый релиз содержит по одному ассету на публичную сборку, имя файла - `FastMediaSorter-<редакция>-<версия>.apk`:

- **standard** - полнофункциональное приложение (телефон / TV / авто)
- **vr** - редакция для Meta Quest / XR
- **lite** - облегчённая сборка (изображения и видео, без облака)
- **photos** - сборка для фотографий
- **legacy** - старые устройства Android (API 23+)
- **wear** - APK-компаньон для Wear OS (ручная установка через adb)

Кнопки скачивания на сайте ведут прямо на эти ассеты и всегда показывают актуальную версию.

## Зеркало (Google Drive)

🔗 **[Папка на Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)** - защищённые паролем ZIP-архивы, **пароль от ZIP: `1`**. Используйте, если сеть блокирует прямую загрузку `.apk`.

## Как это работает

1. `scripts/release/build-release-spectrum.ps1` собирает все релизные редакции на одной общей версии.
2. `scripts/release/publish-github-release.ps1` загружает их все в один тег GitHub Release.
3. ZIP-зеркало на Google Drive обновляется скриптами сборки параллельно.
