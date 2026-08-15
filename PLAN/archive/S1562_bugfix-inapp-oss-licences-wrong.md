# Спецификация (compact bugfix): S1562 - экран лицензий в приложении называет неверные лицензии

**Ticket:** S1562
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-10
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-10

**Захвачено во время:** S1495

**Текст:**

In-app Open Source Licenses screen states wrong licences and lists 2 libraries. S1495 research (2026-08-10) verified against upstream LICENSE files and POMs that SMBJ 0.12.1 is Apache-2.0 and epub4j-core 4.2 is Apache-2.0, NOT LGPL-2.1. The wrong LGPL-2.1 claim ships inside the app in three places: app_v2/src/main/res/values/strings.xml lines 931 (lib_smbj_license) and 933 (lib_epub4j_license) plus the ru/uk mirrors (values-ru 146/148, values-uk 145/147), and OpenSourceLicensesFragment.kt lines 48 and 56, whose "View License" buttons both open https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html - the wrong licence text. The screen also lists only SMBJ and epub4j (plus a NewPipe card gated on capability) against 97 shipping coordinates. S1495 fixes the published documents and explicitly scopes itself out of app code (its §1: "юридические документы проекта и публикуемая страница, не код приложения"), so this needs its own ticket. Likely shape: render the in-app screen from the same scripts/docs/oss-licenses.psd1 manifest S1495 introduces, so the two surfaces cannot diverge again.

---

## 1. Проблема / симптом

Экран «Open Source Licenses» в приложении утверждает лицензию, которой у библиотеки нет.

Установлено проверкой первоисточников 2026-08-10 (LICENSE в репозитории плюс блок `<licenses>` в POM закреплённой версии):

- `com.hierynomus:smbj` 0.12.1 - **Apache-2.0**, а не LGPL-2.1. Вероятный источник путаницы: LGPL заявляет `eu.agno3.jcifs:jcifs-ng`, который в проекте не используется.
- `io.documentnode:epub4j-core` 4.2 - **Apache-2.0**, а не LGPL-2.1. Апстрим `psiegman/epublib` заявляет LGPL в родительском POM, форк epub4j отгружает полный текст Apache-2.0.

Где это лежит:

- `app_v2/src/main/res/values/strings.xml:931` (`lib_smbj_license`), `:933` (`lib_epub4j_license`).
- Зеркала: `values-ru` строки 146/148, `values-uk` строки 145/147.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt:48` и `:56` - обе кнопки «View License» открывают `https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html`, то есть текст чужой лицензии.

Отдельно от неточности - полнота: экран перечисляет две библиотеки (плюс карточка NewPipe, показываемая по capability), тогда как в поставляемый артефакт входит 97 уникальных координат.

**Почему отдельным тикетом:** S1495 чинит публикуемые документы и генератор, но его §1 прямо ограничивает область - «юридические документы проекта и публикуемая страница, не код приложения». Здесь правка Kotlin плюс три локали плюс сборка и проверка на устройстве.

---

## 2. Корневая причина

<расследовать>

Гипотеза при захвате: экран собран вручную под две библиотеки в момент, когда их и было две, и с тех пор не связан ни с чем, что знает состав зависимостей. Тот же класс дефекта, что и на публикуемой странице.

---

## 3. Исправление

<реализовать>

Направление, предложенное при захвате: рендерить экран из того же манифеста `scripts/docs/oss-licenses.psd1`, который заводит S1495, чтобы две поверхности не могли разойтись снова. Минимальный вариант - исправить две лицензии и две ссылки, но он оставляет расхождение по полноте.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1495 - завёл манифест лицензий и генератор публикуемой страницы; здесь та же ошибка внутри приложения.

---

## 4. Проверка

<определить>
