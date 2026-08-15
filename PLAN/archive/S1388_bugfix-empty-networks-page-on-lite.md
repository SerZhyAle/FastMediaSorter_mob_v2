# Спецификация (compact bugfix): S1388 - Пустая страница сетевых источников на lite

**Ticket:** S1388
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-04
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S1384

**Текст:**

On the `lite` flavor the Welcome "Network sources" page renders its header and nothing else - the page promises remote media and then shows an empty body.

Observed 2026-08-04 on emulator-5554 (Android 15 / SDK 35), `com.sza.fastmediasorter.lite.debug`, build of 2026-08-04, first-run onboarding, page index 2. The full accessibility tree of that page was:

- `tvTitle` = "Network sources"
- `tvDescription` = "Add media from network shares and remote storage."
- `btnPrevious`, `btnNext`

Nothing else. No source rows, no Windows-companion note, no button.

Cause is not a defect in the page itself: `lite` is the only flavor that sets BOTH `SUPPORT_CLOUD = false` and `SUPPORT_LOCAL_NETWORK = false` (`app_v2/build.gradle.kts`, the `lite` block), so `RemoteSourceAvailabilityGate.isNetworkGroupSupported()` and `isCloudGroupSupported()` are both false and every row on the page is hidden by design. What is missing is the next step: when a page has no content left, it should not be shown, or it should say what the build does support instead.

Why it matters: the header text actively promises a capability the build does not have ("Add media from network shares and remote storage"), which is a stronger claim than an empty screen. A first-run user on `lite` is told about remote sources and then given nothing to act on, and the Next button is the only way forward.

Not introduced by S1384 - the rows were already gated before it; S1384 only made the emptiness easier to notice because it verified the page on `lite` explicitly.

---

## 1. Проблема / симптом

На сборке `lite` третья страница мастера онбординга показывает заголовок «Network sources» и подпись «Add media from network shares and remote storage», а под ними - ничего.

Эвиденс 2026-08-04, emulator-5554 (Android 15 / SDK 35), `com.sza.fastmediasorter.lite.debug`: полное дерево доступности этой страницы - `tvTitle`, `tvDescription`, `btnPrevious`, `btnNext`. Ни одной строки-переключателя, ни промо-заметки, ни кнопки. Проба контроллера в том же прогоне сообщила `network=false cloud=false`.

Хуже, чем просто пустой экран: заголовок активно обещает возможность, которой в сборке нет, а единственное доступное действие - пролистнуть дальше.

---

## 2. Корневая причина

`lite` - единственный флейвор, у которого одновременно `SUPPORT_CLOUD = false` и `SUPPORT_LOCAL_NETWORK = false`. Контроллер страницы честно скрывает всё, чем управляет: обе сетевые строки, облачную строку и промо Windows-компаньона. Пустой остаётся сама страница - решение о её включении в мастер принимается безусловно, в отличие от страницы проигрывателя по умолчанию, которая рядом в том же методе уже добавляется по условию.

То есть механизм условного включения страницы в мастере существует и применяется - к этой странице он просто не применён.

---

## 3. Исправление

### Step 1 - Не включать страницу, у которой нечего показать

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Prompt for developer:**

> Добавлять страницу сетевых источников только когда сборка поддерживает хотя бы одну из групп - локальную сеть или облако. Условие брать из уже внедрённых в Activity возможностей медиа, тем же способом, каким рядом вычисляется показ страницы проигрывателя по умолчанию.

**Why:**

Страница существует ради переключателей удалённых источников (§2); когда ни одного из них в сборке нет, её заголовок превращается в обещание невыполнимого (§1). Условное включение - уже действующий в этом же методе приём, поэтому правка не вводит новый механизм, а распространяет существующий.

**Verification:**

- `Grep` - `shouldShowNetworksPage` присутствует в `WelcomeActivity.kt`.
- `.\a.ps1 fk` - exit 0.
- На `lite`: страницы сетевых источников в мастере нет, мастер проходится до конца.
- На `standard`: страница на месте со всеми тремя группами.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1384 - тикет, на котором находка всплыла; страница та же.

---

## 4. Проверка

- `.\a.ps1 fk` - exit 0; `post-change: PASS` (ChangeType Kotlin).
- На `lite` после двух шагов мастера идёт страница «What should the app do?»: строки «Network sources» на ней нет ни одной, мастер доходит до конца и после Finish открывает Настройки.
- На `standard` страница осталась целиком: все три группы, три примера из S1384, промо Windows-компаньона и кнопка.
- Инвентарь возможностей не пополняется: убранная страница - снятие ложного обещания, а не новая возможность.

---

## Last Audit

**Date:** 2026-08-04
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] На `lite` страницы сетевых источников в мастере нет - verified on-device 2026-08-04 (совпадений «Network sources» на следующей странице: 0)
- [x] На `lite` мастер проходится до конца - verified on-device 2026-08-04 (после Finish открылась SettingsActivity)
- [x] На `standard` страница на месте со всеми тремя группами - verified on-device 2026-08-04
- [x] Правка использует уже действующий в этом же методе приём условного включения страницы - `Grep` подтверждает соседний `shouldShowDefaultPlayerPage`
- [x] `.\a.ps1 fk` exit 0
- [x] `post-change: PASS`
