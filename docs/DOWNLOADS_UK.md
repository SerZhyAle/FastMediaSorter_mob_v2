# Завантаження APK FastMediaSorter

Скомпільовані APK публікуються як **ассети GitHub Release** - у репозиторій вони не комітяться.

## Завантажити (рекомендовано)

🔗 **[Останній реліз](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest)**

Кожен реліз містить по одному ассету на публічну збірку, ім'я файлу - `FastMediaSorter-<редакція>-<версія>.apk`:

- **standard** - повнофункціональний додаток (телефон / TV / авто)
- **vr** - редакція для Meta Quest / XR
- **lite** - полегшена збірка (зображення та відео, без хмари)
- **photos** - збірка для фотографій
- **legacy** - старі пристрої Android (API 23+)
- **wear** - APK-компаньйон для Wear OS (ручне встановлення через adb)

Кнопки завантаження на сайті ведуть прямо на ці ассети та завжди показують актуальну версію.

## Дзеркало (Google Drive)

🔗 **[Тека на Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)** - захищені паролем ZIP-архіви, **пароль від ZIP: `1`**. Використовуйте, якщо мережа блокує пряме завантаження `.apk`.

## Як це працює

1. `scripts/release/build-release-spectrum.ps1` збирає всі релізні редакції на одній спільній версії.
2. `scripts/release/publish-github-release.ps1` завантажує їх усі в один тег GitHub Release.
3. ZIP-дзеркало на Google Drive оновлюється скриптами збірки паралельно.
