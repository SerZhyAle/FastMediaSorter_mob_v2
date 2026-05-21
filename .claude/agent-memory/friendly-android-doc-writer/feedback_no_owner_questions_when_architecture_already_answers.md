---
name: no-owner-questions-when-architecture-already-answers
description: Don't ask owner scope/business questions whose answer is already mechanically determined by the epic's architecture; treat the architecture as already-given truth and skip the question
metadata:
  type: feedback
---

Не задавай владельцу вопросы про scope или business choices, если ответ уже жёстко вытекает из ранее зафиксированной архитектуры эпика. Если иерархия флэйворов / inheritance / contracts уже механически дают ответ - пропускай вопрос, не подавай как «выбор».

**Why:** в S0240 (2026-05-18 02:46) при `OWNER_QUESTIONS_DRAFT` я задал Q3 «scope cloud features в VR-сборке: все провайдеры или ограниченный набор?» - но иерархия `standard` ⊂ `vr`, зафиксированная самим владельцем за 30 минут до этого, уже механически отвечала «все». Владелец прочитал вопрос как «значит, плохо разобрался в структуре задачи». Это маркер - я раздуваю интерфейс вопросов искусственно созданными choices там, где архитектура уже закрыла обсуждение.

**How to apply:** Не выноси на владельца «выбор между формулировкой A и формулировкой B», если `docs/COMMUNICATION_POLICY.md` уже мандатирует один из вариантов. Процитируй пункт политики и выбери. Политика - тот же «архитектурный слой» для текстов, что flavor hierarchy для кода: один раз зафиксированное правило не превращается обратно в open question при каждой следующей строке. Если правда есть **новый** caveat (новая аудитория, новый канал, legal-исключение) - это уходит в research-задачу, а не в owner-question. И только структурный вопрос (создавать новый раздел / удалить существующий / поменять тон целиком) уместен как DISCUSS.
