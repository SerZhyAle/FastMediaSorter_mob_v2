# Спецификация (compact bugfix): S1586 - Одиночный обратный слэш исчезает из строковых ресурсов

**Ticket:** S1586
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Захвачено во время:** S1567 (ремедиация кавычек, проверка §6)

**Текст:**

A lone backslash inside an Android string resource is an escape introducer, so AAPT2 consumes it and the character never reaches the user. Measured 2026-08-11 with aapt2 compile + aapt2 dump apc (build-tools 36.0.0): `a\b` compiles to `ab`, `use / \ : * ?` compiles to `use /  : * ?`, and only the doubled form `a\\b` renders `a\b`. ConvertTo-XmlText in scripts/utils/seed-locale-tranche.ps1 and scripts/utils/set-android-string.ps1 escapes apostrophes and (after S1567) double quotes, but does not double a literal backslash, so any map value carrying one loses it silently.

Confirmed instance: `error_invalid_text_note_name` in the ten seeded locales (ar, bn, de, es, fr, hi, it, pt, ur, zh-Hans). The string lists the characters a note name may not contain - `/ \ : * ? " < > |` - and the backslash is precisely the character that disappears, so the message omits one of the very characters it is warning about. English is already correct: it carries the doubled `\\`.

Read from the built resources after S1567, values-ar: `استخدم أحرفًا وأرقامًا وشرطات؛ تجنّب /  : * ? " < > |` - note the two spaces where the backslash should be.

Why this is not S1567: the fix is not mechanical in the same way. A map value legitimately carries `\n` for a line break, and the seeded corpus is full of them, so a blanket "double every backslash" pass would turn every intended newline into a literal `\n` on screen. Distinguishing an intended escape (`\n`, `\t`, `\uXXXX`, `\'`, `\"`) from a literal backslash needs a ruling on the map contract, which is its own decision rather than a continuation of the quote fix.

---

## 1. Проблема / симптом

Инструмент, которым сеются и правятся строковые ресурсы, молча теряет литеральный обратный слэш: он доходит до AAPT2 одиночным, AAPT2 читает его как вводитель escape-последовательности и не доводит символ до пользователя.

Эвиденс снят 2026-08-11, замеры в разделе 0.

**Уточнение состояния на 2026-08-12.** Названный в захвате экземпляр в дереве больше не существует: `error_invalid_text_note_name` был удалён как неиспользуемый ресурс и сегодня встречается только в `app_v2/lint-baseline.xml` как запись про уже отсутствующий ключ. Скан всех `app_v2/src/*/res/values*/` не находит ни одного одиночного обратного слэша. То есть отгруженных данных дефект сейчас не портит - он остался в оснастке и сработает на первом же значении, которое такой слэш принесёт.

---

## 2. Корневая причина

`ConvertTo-XmlText` - две копии, обязанные совпадать дословно (`scripts/utils/set-android-string.ps1`, `scripts/utils/seed-locale-tranche.ps1`) - делает ровно три вещи: XML-экранирует значение, затем возвращает `&apos;` и `&quot;` в форму `\'` и `\"`. Обратный слэш не обрабатывается вообще, поэтому доходит до ресурса как есть.

Для AAPT2 одиночный `\` - не символ, а вводитель. Он снимает слэш и оставляет следующий символ, если пара не образует известной последовательности, и раскрывает последовательность, если образует. В обоих исходах литеральный слэш до пользователя не доходит, а сборка не жалуется.

Удвоить всё нельзя - и это единственная причина, почему дефект не является продолжением S1567. Корпус переводов полон намеренных `\n`, и слепой проход превратил бы каждый перевод строки в видимые на экране два символа.

**Решение по контракту карты** (тот самый вопрос, который захват оставил открытым): значение карты несёт escape-последовательности AAPT2 буквально. Слэш, который вводит известную последовательность, - escape и остаётся как есть; слэш, который не вводит ничего известного, - литерал и удваивается. Известные: `\n`, `\t`, `\uXXXX`, `\'`, `\"`, `\\`, плюс `\@` и `\?` в первой позиции значения.

Правило не выводит ровно один случай - литеральный слэш прямо перед `n`, `t` или `u`. Он пишется в карте удвоенным (`\\n`); это цена контракта, и другой цены здесь не существует, потому что обе трактовки видны в тексте одинаково.

Пробел в список известных не входит намеренно: именно на паре «слэш + пробел» построен исходный дефект из захвата.

---

## 3. Исправление

**Правка 1 - экранирование понимает обратный слэш.** В обеих копиях `ConvertTo-XmlText` перед XML-экранированием пройти по значению одним регулярным матчем, который забирает слэш вместе с возможной известной последовательностью, и удваивать только те слэши, у которых известной последовательности нет. Матч забирает `\\` целиком, поэтому проход идемпотентен: уже удвоенное значение не растит третий слэш.

**Правка 2 - механический гейт.** Завести правило `string-lone-backslash` в `scripts/quality/lib/source-matchers.ps1` рядом с `string-quote-escaping` (тот же обход дерева, та же храповая база) плюс тонкую обёртку `scripts/quality/assert-string-lone-backslash.ps1`. База - 0: дерево сегодня чистое, поэтому храповик заводится на честном нуле, а не на долге.

Данные не правятся: править нечего.

Границы: трогается только экранирование в двух скриптах и набор правил гейта. Ни формат карты, ни порядок элементов в сеялке, ни проверка плейсхолдеров не меняются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1567 (нашёл дефект, починил кавычки в том же экранировании), S1190 (завёл оснастку), S1420 (засеял затронутые локали)

---

## 4. Проверка

1. Замер на AAPT2, тем же способом, что дал эвиденс захвата: скомпилировать временный ресурс и прочитать его `aapt2 dump apc`. Значение с литеральным слэшем, пропущенное через новый экранировщик, должно дойти до ресурса целым; оно же без правки - потерять слэш.
2. Намеренный перевод строки должен уцелеть: `\n` в значении карты остаётся переводом строки, а не превращается в два видимых символа.
3. Идемпотентность: повторный прогон экранировщика по уже экранированному значению даёт то же самое.
4. Гейт: `assert-string-lone-backslash.ps1 -Gate` зелёный на текущем дереве (база 0) и красный на заведомо битой строке.
5. `.\a.ps1 fg` - вся быстрая батарея зелёная.
