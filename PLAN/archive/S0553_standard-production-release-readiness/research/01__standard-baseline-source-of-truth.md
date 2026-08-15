# Research 01 - Standard baseline source of truth (S0553 §9.1)

**Вопрос:** что является каноническим источником standard feature baseline для release gate - `docs/FEATURES.md`, `docs/ALL_FEATURES.jsonl` или BuildConfig matrix?

## Наблюдения

- `docs/FEATURES.md` (EN/RU/UK) - curated public showcase. По CLAUDE.md §11 он заполняется ТОЛЬКО `/skill-release` из diff `ALL_FEATURES` и никогда не редактируется per-spec. Это витрина, не машинно-проверяемый контракт: формулировки маркетинговые, нет flavor-разметки на уровне записи.
- `docs/ALL_FEATURES.jsonl` - EN-only developer inventory, одна запись на capability. Каждая запись несёт массив `flavors` (например `["standard","lite","photos","legacy","vr","noLegal"]`), поле `status` (`active`) и `spec`. Записи фильтруются по `flavors ∋ "standard"` -> точный machine-readable список того, что standard обязан предъявить.
- BuildConfig standard matrix (`app_v2/build.gradle.kts`, flavor `standard`) - источник истины для capability-гейтов: `SUPPORT_VIDEO/AUDIO/MIC/IMAGES/CLOUD/LOCAL_NETWORK/DOCUMENTS`, `ENABLE_ANIMATIONS/EPUB/TRANSLATION/PERSISTENT_AUDIO_PLAYBACK`, `SUPPORTS_DEFAULT_PLAYER`, `SUPPORT_WEAR_COMPANION`, `SUPPORT_CAST`, `ENABLE_DTS_DECODER`, `SUPPORT_VR_PLAYER=false`, `IS_NO_LEGAL_FLAVOR=false`.

## Решение

- Канонический baseline для gate = пересечение двух машинных источников: записи `ALL_FEATURES.jsonl` с `flavors ∋ "standard"` и `status=active`, сверенные с включёнными флагами BuildConfig standard matrix.
- `docs/FEATURES.md` остаётся витриной и НЕ является источником истины для gate (используется только для парности store-метаданных).
- Gate потребляет генерируемый snapshot (standard-only срез из `ALL_FEATURES.jsonl` + BuildConfig), а не ручной список. Snapshot воспроизводим из тех же двух источников - дрейф витрины не ломает gate.
- Любая запись, у которой `flavors` содержит `standard`, но capability недоступна в собранном `standardRelease`, - кандидат в §5.2 flavor-surface regression.

**Статус:** Resolved
