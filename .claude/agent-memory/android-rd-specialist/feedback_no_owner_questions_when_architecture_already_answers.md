---
name: no-owner-questions-when-architecture-already-answers
description: Don't ask owner scope/business questions whose answer is already mechanically determined by the epic's architecture; treat the architecture as already-given truth and skip the question
metadata:
  type: feedback
---

Не задавай владельцу вопросы про scope или business choices, если ответ уже жёстко вытекает из ранее зафиксированной архитектуры эпика. Если иерархия флэйворов / inheritance / contracts уже механически дают ответ - пропускай вопрос, не подаваи как «выбор».

**Why:** в S0240 (2026-05-18 02:46) при `OWNER_QUESTIONS_DRAFT` я задал Q3 «scope cloud features в VR-сборке: все провайдеры или ограниченный набор?» - но иерархия `standard` ⊂ `vr`, зафиксированная самим владельцем за 30 минут до этого, уже механически отвечала «все». Владелец прочитал вопрос как «значит, плохо разобрался в структуре задачи». Это маркер - я раздуваю интерфейс вопросов искусственно созданными choices там, где архитектура уже закрыла обсуждение.

**How to apply:**

- Перед каждым «вопросом владельцу» - проверь, не отвечает ли архитектура спеки (flavor hierarchy, contracts, inheritance, scope-границы) механически.
- Если отвечает - преврати «вопрос» в «вывод из архитектуры» в самом тексте спеки и убери из owner-вопросов.
- Особенно касается `OWNER_QUESTIONS_DRAFT`-формата и `/ui-clarify`: если фасад спеки уже определил поведение - не переформулировывай его как выбор.
- Допустимо записать «следствие из §X.Y: все провайдеры наследуются автоматически» в нужном месте спеки - без owner-question.
- Если есть **технический caveat** (как Google OAuth на Quest), он идёт в research-задачу, не в owner-question.

Это правило сильнее принципа «лучше спросить чем угадать»: если ответ зафиксирован архитектурой - это не догадка, а чтение спеки.
