# Стратегическая спецификация: S0515 - btnStopScan отсутствует в landscape диалога network discovery

**Ticket:** S0515
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18

> **Scope:** Compact spec (Simple path). Landscape parity fix for the network-discovery dialog.

---

## Goal

Восстановить паритет portrait/landscape (Rule 11): добавить `btnStopScan` в `layout-land/dialog_network_discovery.xml`, чтобы в альбомной ориентации пользователь мог остановить запущенное сканирование сети. До фикса поле `binding.btnStopScan` было nullable (вью только в portrait), и код использовал safe-call - в landscape кнопки не было, остановить скан было нельзя.

## Acceptance criteria

1. `btnStopScan` присутствует в `layout-land/dialog_network_discovery.xml` (стиль/текст/placement как в portrait).
2. После паритета поле `binding.btnStopScan` non-null; лишние safe-call `?.` и stale-комментарии «may be null in landscape» сняты в `NetworkDiscoveryDialog.kt`.
3. `compileStandardDebugKotlin` + `processStandardDebugResources` проходят (non-null доступ компилируется = паритет подтверждён).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0500 (button unification - finding origin).

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-18 (обнаружено при миграции кнопок S0500)

**Симптом:**

В `dialog_network_discovery.xml` (portrait) есть кнопка `@+id/btnStopScan` (остановить сканирование сети), но в landscape-двойнике `layout-land/dialog_network_discovery.xml` её нет - там только `btnCancel`. На альбомной ориентации пользователь не может остановить запущенное сканирование.

**Доказательство:**

- `app_v2/src/main/res/layout/dialog_network_discovery.xml` - содержит `btnStopScan`.
- `app_v2/src/main/res/layout-land/dialog_network_discovery.xml` - `btnStopScan` отсутствует (только `btnCancel`).
- Pre-existing рассинхрон portrait/landscape (Rule 11), не связан со стилями кнопок.

**Что выяснить при проработке:**

- Намеренно ли убрана кнопка в landscape, или это пропущенный паритет.
- Поведение биндинга: если `ViewBinding` ссылается на `btnStopScan`, в landscape поле nullable - проверить на NPE/skip в коде диалога.

---

## 10. Связи с другими спеками

- Обнаружено при S0500 (унификация кнопок).

---

## Phase 01 - Restore landscape stop-scan button

**Files:**
- `app_v2/src/main/res/layout-land/dialog_network_discovery.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt`

**Done:**

- Added `btnStopScan` (Outlined, `@string/stop`) before `btnCancel` in the landscape button row, mirroring portrait.
- `binding.btnStopScan` is now non-null (present in both orientations) - replaced the two `binding.btnStopScan?` safe-calls with direct access and removed the stale "may be null in landscape" comments.
- Resolved §0 questions: not an intentional removal - a missed parity; the binding was nullable and the code already used safe-calls (no NPE), but the button was simply absent in landscape.

**Status:** `[x]` done

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 3 - WARN 0 - FAIL 0 - MANUAL 1 - EXEMPT 0

- [PASS §1] `btnStopScan` present in `layout-land/dialog_network_discovery.xml`.
- [PASS §2] `grep` shows zero `binding.btnStopScan?` safe-calls remain; stale comments removed.
- [PASS §3] `.\a.ps1 fc` BUILD SUCCESSFUL - the non-null `binding.btnStopScan` access compiled, which only holds when the view is present in both layout variants (parity proven by the compiler).
- Behaviour shares the portrait code path (already working); restores a FIX, no ALL_FEATURES capability change. Zero `Timber.d("S0515:` tags.

### Manual / on-device

- [ ] Open the network-discovery dialog in landscape and confirm the Stop button renders and stops a running scan - MANUAL (low-risk: layout mirrors portrait, behaviour is the shared `viewModel.stopScan()` path; build proved binding parity).
