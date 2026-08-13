# Спецификация (compact bugfix): S1389 - Плитки первого экрана не смотрят на флейвор

**Ticket:** S1389
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-04
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S1386

**Текст:**

The six feature cards on the first Welcome page are a hardcoded list with no flavor gate, so a build advertises capabilities it does not ship.

Evidence, 2026-08-04, emulator-5554 (Android 15 / SDK 35). The `lite` build (`com.sza.fastmediasorter.lite.debug`) sets `SUPPORT_LOCAL_NETWORK = false` and `SUPPORT_CLOUD = false` (`app_v2/build.gradle.kts`, the `lite` block), and its network onboarding page is correspondingly empty. Its FIRST page nonetheless renders all six cards, including:

- "Network Sources" / "SMB, FTP and SFTP shares browsed like local ones"
- "Cloud Storage" / "Google Drive, OneDrive and Dropbox in one list"

Neither exists in that build. The same run's page-1 accessibility dump lists both tiles under `gridFeatures`.

Source of the defect: `WelcomeActivity.setupViewPager()` builds `featureCards = listOf(..)` as a fixed six-element list. Every other capability-bearing surface in the welcome flow consults a gate - the network rows use `RemoteSourceAvailabilityGate`, the profile grid uses `DeviceProfileAvailability`, the default-player page is gated by `SUPPORTS_DEFAULT_PLAYER` - but this list consults nothing.

Why it matters: this is the first screen a user sees, and it is the one place in the flow that makes promises rather than offering toggles. On `lite` the promise is false, and the user only finds out later by not finding the feature.

Distinct from S1388 (`bugfix-empty-networks-page-on-lite`): that one is about a page whose body correctly hides everything and is left empty; this one is about a list that fails to hide anything.

---

## 1. Проблема / симптом

Первый экран мастера обещает возможности, которых в текущей сборке нет. Затронуты четыре карточки из шести, на двух разных флейворах.

На `lite` (`SUPPORT_LOCAL_NETWORK = false`, `SUPPORT_CLOUD = false`), эвиденс 2026-08-04 с emulator-5554 - дамп первой страницы содержит:

- «Network Sources» / «SMB, FTP and SFTP shares browsed like local ones»
- «Cloud Storage» / «Google Drive, OneDrive and Dropbox in one list»

На `photos` (`SUPPORT_VIDEO = false`, `SUPPORT_AUDIO = false`) те же карточки, что и везде, утверждают:

- «Photos & Video» / «Every photo, video and GIF on the device» - видео в этой сборке нет.
- «Slideshow» / «Hands-free playback, with music if you like» - звука в этой сборке нет.

Это единственное место мастера, которое обещает, а не предлагает переключатель, и оно же первое, что видит пользователь.

---

## 2. Корневая причина

Набор карточек задан литеральным списком из шести элементов при построении страниц мастера. Он не консультируется ни с одним признаком возможностей сборки - в отличие от соседних поверхностей того же метода: страница сетевых источников и страница проигрывателя по умолчанию обе добавляются по условию.

Два симптома при этом разной природы: сетевая и облачная карточки лишние целиком, а карточки фото и слайдшоу нужны везде - неверна только их формулировка, упоминающая видео и музыку.

---

## 3. Исправление

### Step 1 - Завести формулировки для сборок без видео и без звука

**Файлы:** `app_v2/src/main/res/values*/strings_setup.xml`

**Prompt for developer:**

> Добавить в EN/RU/UK ключи `welcome_feature_photos_images_only`, `welcome_feature_photos_detail_images_only`, `welcome_feature_slideshow_detail_no_audio`. Тексты повторяют исходные, но без упоминания видео и музыки соответственно.

**Why:**

Карточки фото и слайдшоу нужны на всякой сборке (§2), поэтому скрывать их нельзя - неверна только часть формулировки, и её нужно чем-то заменить.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_feature_"` - exit 0.

**Status:** `[x]` done

---

### Step 2 - Собирать набор карточек по возможностям сборки

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Prompt for developer:**

> Заменить литеральный список карточек на собираемый: сетевую карточку добавлять при поддержке локальной сети, облачную - при поддержке облака, для карточки фото и подписи слайдшоу выбирать строку в зависимости от поддержки видео и звука. Признаки брать из уже внедрённых в Activity возможностей медиа - тех же, по которым рядом решается показ страницы сетевых источников.

**Why:**

Ложное обещание на первом экране (§1) снимается только там, где набор формируется, - в самом списке (§2). Источник признаков берётся тот же, что у соседних решений в этом методе, чтобы две части экрана не могли разойтись во мнении о том, что умеет сборка.

**Verification:**

- `Grep` - `supportsLocalNetworkSources` и `supportsVideo` присутствуют в блоке построения карточек `WelcomeActivity.kt`.
- `.\a.ps1 fk` - exit 0.
- На `photos`: карточка фото без слова video, подпись слайдшоу без музыки.
- На `standard`: все шесть карточек в прежнем виде.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1386 - переоформление того же экрана, может поглотить этот список целиком; S1388 - соседний симптом того же гейта на странице сетевых источников.

---

## 4. Проверка

Проверка построена так, чтобы две сборки закрыли все четыре ветки условий - третья сборка ничего нового бы не доказала.

- `photos` (видео нет, звука нет, сеть есть, облако есть), emulator-5554: «Photos & GIFs» / «Every photo and GIF on the device», «Hands-free playback of your photos»; карточки сети и облака на месте; всего шесть карточек.
- `lite` (видео есть, звук есть, сети нет, облака нет), emulator-5554: «Photos & Video» / «Every photo, video and GIF on the device», «Hands-free playback, with music if you like» - исходные формулировки сохранены; совпадений «Network Sources» и «Cloud Storage» на странице: 0 и 0; осталось четыре карточки в прежнем порядке.
- `.\a.ps1 fc` и `.\a.ps1 fk` - exit 0; `post-change: PASS`.
- Инвентарь возможностей не пополняется: снятие ложного обещания - не новая возможность.

---

## Last Audit

**Date:** 2026-08-04
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] Ветка «нет видео»: заголовок и подпись без слова video - verified on-device 2026-08-04 (`photos`)
- [x] Ветка «нет звука»: подпись слайдшоу без музыки - verified on-device 2026-08-04 (`photos`)
- [x] Ветка «есть видео и звук»: исходные формулировки не тронуты - verified on-device 2026-08-04 (`lite`)
- [x] Ветка «нет сети и облака»: обе карточки исчезли, порядок остальных сохранён - verified on-device 2026-08-04 (`lite`, совпадений 0 и 0)
- [x] Логика вынесена из Activity в хелпер мастера - требование Rule 3, поймано гейтом detekt (LargeClass + TooManyFunctions) и исправлено переносом, а не подгонкой
- [x] Витрина и страницы мастера спрашивают один и тот же источник возможностей, поэтому разойтись не могут
- [x] Новый класс зарегистрирован в каталоге (`role`, `status=new`)
- [x] `post-change: PASS`
