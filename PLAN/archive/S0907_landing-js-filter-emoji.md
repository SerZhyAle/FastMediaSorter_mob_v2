# S0907 - Landing: strip emoji from JS scenario-filter labels + variant/tab buttons (text-only)

**Status:** Archived
**Ticket:** S0907
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- discovered by /spec-all S0889 - 2026-07-03 (out-of-scope finding, CLAUDE.md 3.1) -->
<!-- approach chosen by owner 2026-07-04: text-only strip (not icon injection) -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, при S0889 (замена emoji на иконки приложения в docs/site).

S0889 заменил emoji на иконки приложения в статических слотах (карточки лендинга, howto-бюллетени, DOCS_MAP, SETTINGS_REFERENCE) и откатил 16 meta/nav-карточек обратно на text-only (owner reversal). Вне его объёма остались ~28 emoji в JS-строящихся лейблах интерактивного фильтра и на кнопках variant/tab (`index{,-ru,-uk}.html`), которые владелец («I dont need emoj») тоже хочет убрать.

Функциональные глифы - контролы, а не декор - оставить: `▼` (dropdown, U+25BC), `◐` (theme toggle, U+25D0), `✖` (clear filter, U+2716), `🔍` (search, U+1F50D), `→` (link arrow, U+2192).

## 1. Проблема / симптом

Лендинг непоследователен после S0889: статические карточки несут иконки приложения, а параллельный JS-фильтр сценариев и variant/tab-кнопки всё ещё показывают emoji. Владелец просил «no emoji».

## 2. Решение (owner-approved 2026-07-04)

Text-only strip, НЕ инъекция иконок. Обоснование:

- Буквальный запрос владельца «no emoji».
- Прецедент S0889: владелец откатил навигационные карточки на text-only; фильтр-чипы и вкладки - те же навигационные контролы.
- Инъекция inline-SVG в JS-строящиеся лейблы хрупка (SVG внутри JS-строки) и повторяет риск повторного отката.

Объём:

- Глобальный проход по 3 лендинг-страницам: снять любой non-whitelist emoji-глиф (+ опц. пробел) - JS-лейблы (`label: '<emoji> X'` -> `label: 'X'`), кнопки variant/tab/toggle и декоративные emoji вне слотов (заголовки `<summary>`, JS `innerText`-титулы вроде `📋 Other Features`).
- Сохранить функциональные глифы `▼ ◐ ✖ 🔍 →` и буллет `•` (whitelist; класс emoji их не матчит по построению - не astral / не `⚙`/`✨`, а `🔍` исключён negative-lookahead).
- НЕ трогать статические `card-icon`-спаны S0889 и `doc-icon-map.json` (иконки не добавляются).
- Механизм: `scripts/docs/strip-landing-filter-emoji.ps1` (глобальный, idempotent, UTF-8 без BOM, ASCII-исходник, зеркалит write-path `apply-doc-icons.ps1`).
- Гейт: расширить `assert-doc-icons-sync.ps1` - лендинг emoji-free кроме whitelist.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0889 (parent - icon slots), S0815 (icon inventory).
- **UI/UX approach:** text-only strip approved 2026-07-04 (over icon-injection / hybrid).

## Phases

### Phase 1 - Strip + gate

1. Write `scripts/docs/strip-landing-filter-emoji.ps1`. Global pass over `index.html`, `index-ru.html`, `index-uk.html`: strip any non-whitelist emoji grapheme (any astral emoji except `🔍`, plus `⚙`/`✨`, optional VS16) + an optional following space, page-wide - covers JS `label:`, variant/tab/toggle buttons, and decorative emoji outside those slots (`<summary>`, JS `innerText` titles). Whitelist `▼ ◐ ✖ 🔍 → •` preserved by construction. Pattern glyphs built from hex code points -> ASCII-only source. Write UTF-8 without BOM. Idempotent; print per-file counts.
   - Verification: run twice; second run reports 0 removed. `temp/S0907/scan-emoji.ps1` after the run lists only the whitelist (U+2022/2192/25BC/25D0/2716/1F50D).
2. Extend `scripts/quality/assert-doc-icons-sync.ps1` with a section 5 (`-Gate`): strip the whitelist glyphs from each landing page, assert `$emojiRx` finds no remaining emoji anywhere. FAIL per residual. Update the file's scope note (JS filter labels now asserted).
   - Verification: `assert-doc-icons-sync.ps1 -Gate` exits 0 after strip; re-adding an emoji to a label makes it exit 1.

## 10. Связи с другими спеками

- S0889 - родитель; поставил слот-иконки и оставил JS-хвост.

## 11. Критерии приёмки

1. Ноль non-whitelist emoji в 3 лендинг-страницах во всех локалях (whitelist `▼ ◐ ✖ 🔍 → •` сохранён).
2. `assert-doc-icons-sync.ps1 -Gate` PASS и покрывает пункт 1.
3. Статические `card-icon`-спаны S0889 не тронуты (прежнее число SVG-спанов, section 3 гейта по-прежнему PASS).

## Last Audit

**Date:** 2026-07-04
**Mode:** full (Simple path)
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

### Verification

- Strip: `strip-landing-filter-emoji.ps1` (global pass) removed ~55 emoji per locale (JS filter/scenario/category/howto-link labels + 9 variant/tab/toggle buttons + 2 decorative headings `<summary>` / `innerText`) across index{,-ru,-uk}.html; re-run reports 0 (idempotent). `temp/S0907/scan-emoji.ps1` post-strip lists only the whitelist (U+2022/2192/25BC/25D0/2716/1F50D) in all 3 locales.
- Whitelist intact: search 🔍 (`search-icon` span), theme toggle ◐, dropdown ▼, clear ✖, link arrow →, bullet • - preserved and spot-verified in place.
- Gate: `assert-doc-icons-sync.ps1 -Gate` PASS (24 drawables; section 3 card-icons untouched = criterion 3). Negative test: injecting U+1F310 into a JS label -> gate exit 1 with diagnostic; byte-exact restore -> exit 0.
- Self-test: `temp/S0907/test-strip.ps1` 8/8 (labels, buttons, gear+VS16, 🔎 OCR stripped; 🔍/→/✖ kept).

### Manual / on-device

- [ ] Optional: eyeball the live landing filter widget renders clean text labels (no visual gap where an emoji sat). Low risk - CSS lays out text; no on-device gate.
