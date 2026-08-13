# S0408 - Краш плеера при destroy: EIO на закрытии текстового файла

**Ticket:** S0408
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-12
**Tier:** 1 - Quick Win (ad-hoc bugfix)

## Problem

`PlayerActivity.onDestroy` падает с `RuntimeException: Unable to destroy activity`, причина - `java.io.IOException: close failed: EIO (I/O error)` при закрытии `RandomAccessFile` в текстовом пейджере во время освобождения ресурсов. EIO означает, что нижележащий дескриптор/носитель уже недоступен (отмонтирован SD/USB, оборвался content-provider fd) - повторное закрытие смысла не имеет, но непойманное исключение роняет всё уничтожение активити. Воспроизведено на Samsung SM-S731B, Android 16.

## Approach

- `TextFilePager.close()`: обернуть закрытие `RandomAccessFile` в try/catch, гарантированно обнулить ссылку в finally, залогировать сбой закрытия на уровне warn (ресурс при teardown всё равно отбрасывается, ОС освободит дескриптор). Закрытие не должно пробрасывать исключение наружу при освобождении.

## Done criteria

- Закрытие текстового пейджера с недоступным fd не пробрасывает исключение; `PlayerActivity.onDestroy` завершается без краша.
