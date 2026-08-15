# Phase 04 — Выживание после process death

**Файлы:** `BrowseCameraCaptureManager.kt`, `BrowseActivity.kt`

## Суть изменения

После process death Android пересоздаёт Activity. Поля `pendingTempFile` и `pendingResource` теряются — менеджер не может завершить сохранение. Добавить сохранение/восстановление через Bundle (onSaveInstanceState / onCreate).

## API менеджера

```kotlin
fun saveState(outState: Bundle) {
    pendingTempFile?.absolutePath?.let { outState.putString(KEY_TEMP_FILE, it) }
    pendingResource?.id?.let { outState.putLong(KEY_RESOURCE_ID, it) }
}

fun restoreState(savedState: Bundle, getResourceById: (Long) -> MediaResource?) {
    val path = savedState.getString(KEY_TEMP_FILE) ?: return
    val file = File(path)
    if (!file.exists()) {
        Timber.w("S0022-CAM: restoreState — tempFile not found after process death path=%s", path)
        showSnackbar(R.string.camera_capture_error_session_expired)
        return
    }
    pendingTempFile = file
    val resourceId = savedState.getLong(KEY_RESOURCE_ID, -1L)
    if (resourceId != -1L) {
        pendingResource = getResourceById(resourceId)
    }
    if (pendingResource == null) {
        Timber.w("S0022-CAM: restoreState — resource not found id=%d", resourceId)
        file.delete()
        pendingTempFile = null
        showSnackbar(R.string.camera_capture_error_session_expired)
    }
}
```

## В BrowseActivity

```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (::cameraCaptureManager.isInitialized) {
        cameraCaptureManager.saveState(outState)
    }
}
```

В `onCreate` после инициализации менеджера:

```kotlin
savedInstanceState?.let { state ->
    cameraCaptureManager.restoreState(state) { id ->
        viewModel.state.value.resource?.takeIf { it.id == id }
    }
}
```

## Чеклист

- [x] `saveState` / `restoreState` добавлены в BrowseCameraCaptureManager
- [x] BrowseActivity.onSaveInstanceState вызывает saveState
- [x] BrowseActivity.onCreate вызывает restoreState после инициализации
- [x] При невозможности восстановить контекст → Snackbar "попытка не завершена"
