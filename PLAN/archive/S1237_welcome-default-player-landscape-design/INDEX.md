# S1237 - Тактический план

**Стратегическая спека:** [`../S1237_welcome-default-player-landscape-design.md`](../S1237_welcome-default-player-landscape-design.md)
**Статус:** 🚧 In Progress
**Создан:** 2026-07-29

---

## Обзор

Изменение целиком в разметке: страница «проигрыватель по умолчанию» переводится на общий для мастера
приём ограничения ширины контента, а в ландшафте у блока кнопок снимается прижатие к верху. Слой
Kotlin не трогается - набор идентификаторов сохраняется полностью.

---

## Фазы

| Фаза | Название | Статус | Зависит от |
|:----:|----------|:------:|:----------:|
| 01 | [Ограничение ширины и раскладка](PHASE_01__width-cap-and-layout.md) | 🚧 In Progress | - |

---

## Ключевые факты исследования

Собраны при написании стратегической спеки 2026-07-29, чтобы фаза не переоткрывала их заново.

- Общий приём мастера - `app:layout_constraintWidth_max="@dimen/welcome_content_max_width"` на вложенном `ConstraintLayout` с `android:layout_width="0dp"`, растянутом между `parent` слева и справа. Используется в шести портретных и шести ландшафтных страницах мастера.
- `page_welcome_default_player.xml` - единственная страница семейства без этого приёма: в `layout/` стоит `android:maxWidth` на `LinearLayout`, в `layout-land/` ограничения нет вовсе.
- `android:maxWidth` платформой объявлен только для `ImageView`, `ProgressBar`, `ResolverDrawerLayout`, `SearchView`, `TextView` - проверено по `platforms/android-36/data/res/values/attrs.xml`. На `LinearLayout` атрибут молча игнорируется.
- `welcome_content_max_width` определён в бакетах `values` (600dp), `sw320dp` (400dp), `sw480dp` (500dp), `sw600dp` (800dp), `sw720dp` (960dp).
- Ландшафтный вариант отдельно оговаривает двухколоночную подачу в собственном комментарии - её надо сохранить.
- Идентификаторы, которые читает `WelcomePagerAdapter.DefaultPlayerViewHolder`: `layoutContent`, `ivIcon`, `tvTitle`, `tvDescription`, `tvHint`, `layoutTypeButtons`, `btnSetDefaultAudio`, `btnSetDefaultVideo`, `btnSetDefaultImages`, `btnSetDefaultDocs`.
- `layoutContent` не может стоять на корне: привязка требует совпадения корневого идентификатора между конфигурациями, а корни у вариантов разных типов.
- Копий разметки ровно две - других исходных наборов с этим файлом нет.
- Число видимых кнопок задаётся возможностями сборки, поэтому раскладка проверяется и при одной кнопке.

---

## Критерии готовности тикета

1. Обе разметки используют `layout_constraintWidth_max` с `welcome_content_max_width`.
2. `android:maxWidth` на контейнере-не-`TextView`/`ImageView` в этих файлах отсутствует.
3. Набор идентификаторов не изменился - модуль собирается.
4. Блок кнопок в ландшафте не прижат к верху.
5. Обе ориентации правятся в одном изменении.
