# Задача 8: Полная поддержка мыши и клавиатуры

## Статус: ✅ ВЫПОЛНЕНО

**Дата выполнения**: $(date +%Y-%m-%d)

### Выполненные шаги

- ✅ Prompt 1: KeyboardShortcutHandler.kt - универсальный обработчик shortcuts
- ✅ Prompt 2: MouseEventHandler.kt - обработка right/middle-click
- ✅ Prompt 3: FocusManager.kt - навигация стрелками в RecyclerView
- ✅ Prompt 4: BrowseActivity keyboard integration (Ctrl+A/C/X, F2, F5, Backspace)
- ✅ Prompt 5: PlayerActivity - уже имел PlayerKeyboardHandler
- ✅ Prompt 6: MainActivity - уже имел KeyboardNavigationHandler
- ✅ Prompt 7: MediaFileAdapter mouse/focus support
- ✅ Prompt 8: Hover/focus colors и item_focus_selector drawable
- ✅ Prompt 9: ResourceAdapter mouse/focus support
- ✅ Prompt 10: context_menu_file.xml для right-click меню

### Созданные файлы

- `util/KeyboardShortcutHandler.kt`
- `ui/common/MouseEventHandler.kt`
- `ui/common/FocusManager.kt`
- `res/drawable/item_focus_selector.xml`
- `res/drawable/button_hover_selector.xml`
- `res/menu/context_menu_file.xml`

### Изменённые файлы

- `res/values/colors.xml` - focus/hover colors
- `res/values-night/colors.xml` - dark theme focus colors
- `res/layout/item_media_file.xml` - focus selector
- `res/layout/item_media_file_grid.xml` - focus selector
- `res/layout/item_resource.xml` - focus selector
- `res/layout/item_resource_grid.xml` - focus selector
- `ui/browse/managers/KeyboardNavigationManager.kt` - Ctrl shortcuts
- `ui/browse/BrowseActivity.kt` - keyboard callback implementation
- `ui/browse/MediaFileAdapter.kt` - right-click, focusable
- `ui/main/ResourceAdapter.kt` - right-click, focusable
- `res/values/strings.xml` - open, select, file_info strings

---

## Описание

Реализовать comprehensive поддержку мыши и клавиатуры во всех экранах, списках и activity приложения для улучшения UX на ChromeOS и Desktop mode.

## Приоритет

🔵 Улучшение UX (для ChromeOS/Desktop mode)

## Требования

### Навигация клавиатурой

- Arrow keys для перемещения по спискам
- Tab/Shift+Tab для переключения фокуса между элементами
- Proper focus indicators (подсветка активного элемента)

### Клавиатурные shortcuts

- `Space` / `Enter` - выбор/открытие файла
- `Ctrl+A` - выбрать все
- `Ctrl+C` - копировать
- `Ctrl+X` - вырезать/переместить
- `Delete` - удалить
- `F2` - переименовать
- `F5` - обновить/refresh
- `Backspace` - назад (в BrowseActivity)
- `Escape` - отменить выбор/закрыть диалог

### Мышь

- Click - выбор файла
- Double-click - открыть файл
- Right-click - контекстное меню
- Middle-click - открыть в новом окне (если применимо)
- Scroll wheel - прокрутка списков
- Hover effects для всех кнопок и элементов

### Accessibility

- Proper focus indicators
- Screen reader support (contentDescription)
- Logical tab order

## Затронутые файлы

**Новые:**

- `util/KeyboardShortcutHandler.kt`
- `ui/common/MouseEventHandler.kt`
- `ui/common/FocusManager.kt`

**Изменяемые:**

- `MainActivity.kt`
- `BrowseActivity.kt`
- `PlayerActivity.kt`
- `MediaFileAdapter.kt`
- `ResourceAdapter.kt`
- Layout XML files (добавить focusable attributes)

---

## Промпты для разработки

### Промпт 1: Создание KeyboardShortcutHandler

```
Создай универсальный обработчик клавиатурных shortcuts:

Файл: `util/KeyboardShortcutHandler.kt`

```kotlin
package com.sza.fastmediasorter.util

import android.view.KeyEvent
import timber.log.Timber

/**
 * Universal keyboard shortcut handler
 * Task 8: Full keyboard and mouse support
 */
class KeyboardShortcutHandler(
    private val callbacks: KeyboardShortcutCallbacks
) {
    
    interface KeyboardShortcutCallbacks {
        fun onSelectAll() {}
        fun onCopy() {}
        fun onCut() {}
        fun onDelete() {}
        fun onRename() {}
        fun onRefresh() {}
        fun onBack() {}
        fun onEscape() {}
        fun onSpace() {}
        fun onEnter() {}
    }
    
    /**
     * Handle key event and trigger appropriate callback
     * @return true if event was handled
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        
        // Check modifiers
        val isCtrlPressed = event.isCtrlPressed
        val isShiftPressed = event.isShiftPressed
        val isAltPressed = event.isAltPressed
        
        Timber.d("KeyboardShortcutHandler: keyCode=$keyCode, ctrl=$isCtrlPressed, shift=$isShiftPressed, alt=$isAltPressed")
        
        return when {
            // Ctrl+A - Select All
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_A -> {
                callbacks.onSelectAll()
                true
            }
            
            // Ctrl+C - Copy
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_C -> {
                callbacks.onCopy()
                true
            }
            
            // Ctrl+X - Cut (Move)
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_X -> {
                callbacks.onCut()
                true
            }
            
            // Delete - Delete files
            keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> {
                callbacks.onDelete()
                true
            }
            
            // F2 - Rename
            keyCode == KeyEvent.KEYCODE_F2 -> {
                callbacks.onRename()
                true
            }
            
            // F5 - Refresh
            keyCode == KeyEvent.KEYCODE_F5 -> {
                callbacks.onRefresh()
                true
            }
            
            // Backspace - Back
            keyCode == KeyEvent.KEYCODE_BACK && !isCtrlPressed -> {
                callbacks.onBack()
                true
            }
            
            // Escape - Cancel/Close
            keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                callbacks.onEscape()
                true
            }
            
            // Space - Toggle selection or play
            keyCode == KeyEvent.KEYCODE_SPACE -> {
                callbacks.onSpace()
                true
            }
            
            // Enter - Open file
            keyCode == KeyEvent.KEYCODE_ENTER -> {
                callbacks.onEnter()
                true
            }
            
            else -> false
        }
    }
}
```

Протестируй на simple activity с логами.

```

---

### Промпт 2: Создание MouseEventHandler

```

Создай обработчик событий мыши для двойного клика и контекстного меню:

Файл: `ui/common/MouseEventHandler.kt`

```kotlin
package com.sza.fastmediasorter.ui.common

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import timber.log.Timber

/**
 * Mouse event handler for double-click and right-click
 * Task 8: Full keyboard and mouse support
 */
class MouseEventHandler(
    private val callbacks: MouseEventCallbacks
) {
    
    interface MouseEventCallbacks {
        fun onSingleClick(view: View) {}
        fun onDoubleClick(view: View) {}
        fun onRightClick(view: View, x: Float, y: Float) {}
        fun onMiddleClick(view: View) {}
    }
    
    private var lastClickTime = 0L
    private val doubleClickThreshold = 300L // 300ms
    
    /**
     * Handle motion event for mouse interactions
     * @return true if event was handled
     */
    fun handleMotionEvent(view: View, event: MotionEvent): Boolean {
        Timber.d("MouseEventHandler: action=${event.actionMasked}, buttonState=${event.buttonState}")
        
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleMouseDown(view, event)
            }
            MotionEvent.ACTION_UP -> {
                handleMouseUp(view, event)
            }
            else -> false
        }
    }
    
    private fun handleMouseDown(view: View, event: MotionEvent): Boolean {
        // Check button state for right-click
        if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
            // Right-click (secondary button)
            callbacks.onRightClick(view, event.x, event.y)
            return true
        }
        
        // Check for middle-click
        if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0) {
            // Middle-click (tertiary button)
            callbacks.onMiddleClick(view)
            return true
        }
        
        return false
    }
    
    private fun handleMouseUp(view: View, event: MotionEvent): Boolean {
        // Primary button (left-click) - check for double-click
        if (event.buttonState == 0 || event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastClick = currentTime - lastClickTime
            
            if (timeSinceLastClick < doubleClickThreshold) {
                // Double-click detected
                callbacks.onDoubleClick(view)
                lastClickTime = 0L // Reset to prevent triple-click
                return true
            } else {
                // Single click
                lastClickTime = currentTime
                callbacks.onSingleClick(view)
                return true
            }
        }
        
        return false
    }
}
```

Интегрируй в ViewHolders для обработки кликов мыши.

```

---

### Промпт 3: Создание FocusManager для навигации

```

Создай менеджер фокуса для навигации arrow keys:

Файл: `ui/common/FocusManager.kt`

```kotlin
package com.sza.fastmediasorter.ui.common

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * Focus manager for arrow key navigation in RecyclerView
 * Task 8: Full keyboard and mouse support
 */
class FocusManager(
    private val recyclerView: RecyclerView,
    private val callbacks: FocusCallbacks
) {
    
    interface FocusCallbacks {
        fun onItemFocused(position: Int)
        fun onItemSelected(position: Int)
        fun getItemCount(): Int
    }
    
    private var currentFocusPosition = -1
    
    /**
     * Handle arrow key navigation
     * @return true if event was handled
     */
    fun handleArrowKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        
        val itemCount = callbacks.getItemCount()
        if (itemCount == 0) return false
        
        // Initialize focus if not set
        if (currentFocusPosition == -1) {
            currentFocusPosition = 0
        }
        
        val layoutManager = recyclerView.layoutManager ?: return false
        val isGridLayout = layoutManager is androidx.recyclerview.widget.GridLayoutManager
        val spanCount = if (isGridLayout) {
            (layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount
        } else {
            1
        }
        
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ARROW_UP -> {
                val newPosition = if (isGridLayout) {
                    (currentFocusPosition - spanCount).coerceAtLeast(0)
                } else {
                    (currentFocusPosition - 1).coerceAtLeast(0)
                }
                moveFocus(newPosition)
                true
            }
            
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_ARROW_DOWN -> {
                val newPosition = if (isGridLayout) {
                    (currentFocusPosition + spanCount).coerceAtMost(itemCount - 1)
                } else {
                    (currentFocusPosition + 1).coerceAtMost(itemCount - 1)
                }
                moveFocus(newPosition)
                true
            }
            
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ARROW_LEFT -> {
                if (isGridLayout) {
                    val newPosition = (currentFocusPosition - 1).coerceAtLeast(0)
                    moveFocus(newPosition)
                }
                true
            }
            
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ARROW_RIGHT -> {
                if (isGridLayout) {
                    val newPosition = (currentFocusPosition + 1).coerceAtMost(itemCount - 1)
                    moveFocus(newPosition)
                }
                true
            }
            
            else -> false
        }
    }
    
    private fun moveFocus(newPosition: Int) {
        if (newPosition == currentFocusPosition) return
        
        Timber.d("FocusManager: Moving focus from $currentFocusPosition to $newPosition")
        
        currentFocusPosition = newPosition
        
        // Scroll to position if needed
        recyclerView.smoothScrollToPosition(newPosition)
        
        // Notify callback
        callbacks.onItemFocused(newPosition)
        
        // Request focus on the view
        recyclerView.post {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
            viewHolder?.itemView?.requestFocus()
        }
    }
    
    fun getCurrentPosition(): Int = currentFocusPosition
    
    fun setPosition(position: Int) {
        currentFocusPosition = position
    }
    
    fun reset() {
        currentFocusPosition = -1
    }
}
```

Тестируй навигацию arrow keys в списке.

```

---

### Промпт 4: Интеграция в BrowseActivity

```

Интегрируй keyboard shortcuts и mouse events в BrowseActivity:

В `BrowseActivity.kt`:

```kotlin
class BrowseActivity : AppCompatActivity() {
    
    private lateinit var keyboardShortcutHandler: KeyboardShortcutHandler
    private lateinit var focusManager: FocusManager
    
    // ... existing code ...
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... existing code ...
        
        // Task 8: Initialize keyboard shortcut handler
        keyboardShortcutHandler = KeyboardShortcutHandler(object : KeyboardShortcutHandler.KeyboardShortcutCallbacks {
            override fun onSelectAll() {
                UserActionLogger.logKeyboardShortcut("Ctrl+A", "Select All")
                viewModel.selectAllFiles()
            }
            
            override fun onCopy() {
                val selectedCount = viewModel.state.value.selectedFiles.size
                if (selectedCount > 0) {
                    UserActionLogger.logKeyboardShortcut("Ctrl+C", "Copy $selectedCount files")
                    showCopyDialog()
                }
            }
            
            override fun onCut() {
                val selectedCount = viewModel.state.value.selectedFiles.size
                if (selectedCount > 0) {
                    UserActionLogger.logKeyboardShortcut("Ctrl+X", "Move $selectedCount files")
                    showMoveDialog()
                }
            }
            
            override fun onDelete() {
                val selectedCount = viewModel.state.value.selectedFiles.size
                if (selectedCount > 0) {
                    UserActionLogger.logKeyboardShortcut("Delete", "Delete $selectedCount files")
                    showDeleteConfirmation()
                }
            }
            
            override fun onRename() {
                val selectedCount = viewModel.state.value.selectedFiles.size
                if (selectedCount > 0) {
                    UserActionLogger.logKeyboardShortcut("F2", "Rename")
                    showRenameDialog()
                }
            }
            
            override fun onRefresh() {
                UserActionLogger.logKeyboardShortcut("F5", "Refresh")
                viewModel.reloadFiles()
            }
            
            override fun onBack() {
                UserActionLogger.logKeyboardShortcut("Backspace", "Navigate back")
                viewModel.navigateUp()
            }
            
            override fun onEscape() {
                val selectedCount = viewModel.state.value.selectedFiles.size
                if (selectedCount > 0) {
                    UserActionLogger.logKeyboardShortcut("Escape", "Clear selection")
                    viewModel.clearSelection()
                } else {
                    UserActionLogger.logKeyboardShortcut("Escape", "Close")
                    finish()
                }
            }
            
            override fun onSpace() {
                val focusedPosition = focusManager.getCurrentPosition()
                if (focusedPosition >= 0) {
                    val file = viewModel.state.value.mediaFiles.getOrNull(focusedPosition)
                    if (file != null) {
                        UserActionLogger.logKeyboardShortcut("Space", "Toggle selection: ${file.name}")
                        viewModel.selectFile(file.path)
                    }
                }
            }
            
            override fun onEnter() {
                val focusedPosition = focusManager.getCurrentPosition()
                if (focusedPosition >= 0) {
                    val file = viewModel.state.value.mediaFiles.getOrNull(focusedPosition)
                    if (file != null) {
                        UserActionLogger.logKeyboardShortcut("Enter", "Open file: ${file.name}")
                        if (file.isDirectory) {
                            viewModel.navigateToFolder(file.path)
                        } else {
                            viewModel.openFile(file)
                        }
                    }
                }
            }
        })
        
        // Task 8: Initialize focus manager
        focusManager = FocusManager(
            recyclerView = binding.rvMediaFiles,
            callbacks = object : FocusManager.FocusCallbacks {
                override fun onItemFocused(position: Int) {
                    // Update focus highlight
                    binding.rvMediaFiles.adapter?.notifyItemChanged(position, "FOCUS_CHANGED")
                }
                
                override fun onItemSelected(position: Int) {
                    val file = viewModel.state.value.mediaFiles.getOrNull(position)
                    if (file != null) {
                        viewModel.selectFile(file.path)
                    }
                }
                
                override fun getItemCount(): Int {
                    return viewModel.state.value.mediaFiles.size
                }
            }
        )
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Task 8: Handle keyboard shortcuts
        if (keyboardShortcutHandler.handleKeyEvent(keyCode, event)) {
            return true
        }
        
        // Task 8: Handle arrow key navigation
        if (focusManager.handleArrowKey(keyCode, event)) {
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
}
```

Добавь в ViewModel метод `selectAllFiles()`.

```

---

### Промпт 5: Интеграция mouse events в MediaFileAdapter

```

Добавь mouse event handling в MediaFileAdapter:

В `MediaFileAdapter.kt` (ListViewHolder и GridViewHolder):

```kotlin
inner class ListViewHolder(
    private val binding: ItemMediaFileBinding
) : RecyclerView.ViewHolder(binding.root) {
    
    private val mouseEventHandler = MouseEventHandler(object : MouseEventHandler.MouseEventCallbacks {
        override fun onSingleClick(view: View) {
            // Handled by existing click listener
        }
        
        override fun onDoubleClick(view: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val file = getItem(position)
                UserActionLogger.logMouseEvent("Double-click", file.name)
                
                if (file.isDirectory) {
                    onFolderClick(file)
                } else if (file.type.isBinaryFile()) {
                    onBinaryFileClick(file)
                } else {
                    onFileClick(file)
                }
            }
        }
        
        override fun onRightClick(view: View, x: Float, y: Float) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val file = getItem(position)
                UserActionLogger.logMouseEvent("Right-click", file.name)
                
                // Show context menu
                showContextMenu(view, file, x, y)
            }
        }
        
        override fun onMiddleClick(view: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val file = getItem(position)
                UserActionLogger.logMouseEvent("Middle-click", file.name)
                // Could open in new window or perform special action
            }
        }
    })
    
    fun bind(file: MediaFile, selectedPaths: Set<String>) {
        // ... existing bind code ...
        
        // Task 8: Add mouse event handling
        binding.root.setOnTouchListener { v, event ->
            if (mouseEventHandler.handleMotionEvent(v, event)) {
                true
            } else {
                v.performClick()
                false
            }
        }
        
        // Task 8: Make view focusable for keyboard navigation
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = false
        
        // Task 8: Add focus change listener for highlight
        binding.root.setOnFocusChangeListener { v, hasFocus ->
            binding.root.setBackgroundColor(
                when {
                    file.path in selectedPaths -> v.context.getColor(R.color.item_selected)
                    hasFocus -> v.context.getColor(R.color.item_focused)
                    else -> v.context.getColor(R.color.item_normal)
                }
            )
        }
    }
    
    private fun showContextMenu(view: View, file: MediaFile, x: Float, y: Float) {
        // Create PopupMenu at position
        val popup = android.widget.PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.context_menu_file, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open -> {
                    onFileClick(file)
                    true
                }
                R.id.action_copy -> {
                    onCopyClick(file)
                    true
                }
                R.id.action_move -> {
                    onMoveClick(file)
                    true
                }
                R.id.action_rename -> {
                    onRenameClick(file)
                    true
                }
                R.id.action_delete -> {
                    onDeleteClick(file)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
}
```

Сделай то же для GridViewHolder.

```

---

### Промпт 6: Добавление hover effects в layouts

```

Добавь hover effects для кнопок и списков:

Создай drawable селекторы с hover states:

Файл: `res/drawable/button_hover_selector.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_hovered="true" android:state_enabled="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/button_hover" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/button_pressed" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/button_focused" />
            <corners android:radius="8dp" />
            <stroke android:width="2dp" android:color="@color/focus_indicator" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/button_normal" />
            <corners android:radius="8dp" />
        </shape>
    </item>
</selector>
```

Файл: `res/drawable/list_item_hover_selector.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_hovered="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/item_hovered" />
        </shape>
    </item>
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/item_focused" />
            <stroke android:width="2dp" android:color="@color/focus_indicator" />
        </shape>
    </item>
    <item android:state_selected="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/item_selected" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/item_normal" />
        </shape>
    </item>
</selector>
```

Добавь цвета в `colors.xml`:

```xml
<color name="button_hover">#E0E0E0</color>
<color name="button_pressed">#BDBDBD</color>
<color name="button_focused">#EEEEEE</color>
<color name="button_normal">@android:color/transparent</color>
<color name="item_hovered">#F5F5F5</color>
<color name="item_focused">#E3F2FD</color>
<color name="focus_indicator">#2196F3</color>
```

Примени селекторы к кнопкам и list items в layouts.

```

---

### Промпт 7: Context Menu XML

```

Создай context menu для файлов:

Файл: `res/menu/context_menu_file.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_open"
        android:title="@string/open"
        android:icon="@drawable/ic_open_in_new" />
    
    <item
        android:id="@+id/action_copy"
        android:title="@string/copy"
        android:icon="@drawable/ic_copy" />
    
    <item
        android:id="@+id/action_move"
        android:title="@string/move"
        android:icon="@drawable/ic_drive_file_move" />
    
    <item
        android:id="@+id/action_rename"
        android:title="@string/rename"
        android:icon="@drawable/ic_edit" />
    
    <item
        android:id="@+id/action_delete"
        android:title="@string/delete"
        android:icon="@drawable/ic_delete" />
</menu>
```

Используй это меню в right-click handler.

```

---

### Промпт 8: Интеграция в MainActivity

```

Добавь keyboard shortcuts в MainActivity для навигации:

В `MainActivity.kt`:

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var keyboardShortcutHandler: KeyboardShortcutHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... existing code ...
        
        // Task 8: Initialize keyboard shortcut handler
        keyboardShortcutHandler = KeyboardShortcutHandler(object : KeyboardShortcutHandler.KeyboardShortcutCallbacks {
            override fun onRefresh() {
                UserActionLogger.logKeyboardShortcut("F5", "Refresh resources")
                viewModel.refreshResources()
            }
            
            override fun onEscape() {
                UserActionLogger.logKeyboardShortcut("Escape", "Close")
                finish()
            }
            
            override fun onEnter() {
                // Open selected resource
                val focusedPosition = getCurrentFocusedResourcePosition()
                if (focusedPosition >= 0) {
                    val resource = viewModel.state.value.resources.getOrNull(focusedPosition)
                    if (resource != null) {
                        UserActionLogger.logKeyboardShortcut("Enter", "Open resource: ${resource.name}")
                        openResource(resource)
                    }
                }
            }
        })
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Task 8: Handle keyboard shortcuts
        if (keyboardShortcutHandler.handleKeyEvent(keyCode, event)) {
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    private fun getCurrentFocusedResourcePosition(): Int {
        // Get current focused position from RecyclerView
        val focusedView = binding.rvResources.focusedChild
        return if (focusedView != null) {
            binding.rvResources.getChildAdapterPosition(focusedView)
        } else {
            -1
        }
    }
}
```

Аналогично добавь в ResourceAdapter mouse events и focus handling.

```

---

### Промпт 9: PlayerActivity keyboard shortcuts

```

Добавь keyboard shortcuts в PlayerActivity для управления воспроизведением:

В `PlayerActivity.kt`:

```kotlin
class PlayerActivity : AppCompatActivity() {
    
    private lateinit var keyboardShortcutHandler: KeyboardShortcutHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... existing code ...
        
        // Task 8: Initialize keyboard shortcut handler
        keyboardShortcutHandler = KeyboardShortcutHandler(object : KeyboardShortcutHandler.KeyboardShortcutCallbacks {
            override fun onSpace() {
                UserActionLogger.logKeyboardShortcut("Space", "Play/Pause")
                togglePlayPause()
            }
            
            override fun onEscape() {
                UserActionLogger.logKeyboardShortcut("Escape", "Close player")
                finish()
            }
            
            override fun onBack() {
                UserActionLogger.logKeyboardShortcut("Backspace", "Previous file")
                navigateToPrevious()
            }
            
            override fun onEnter() {
                UserActionLogger.logKeyboardShortcut("Enter", "Next file")
                navigateToNext()
            }
        })
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Task 8: Handle keyboard shortcuts
        if (keyboardShortcutHandler.handleKeyEvent(keyCode, event)) {
            return true
        }
        
        // Arrow keys for seek
        when (keyCode) {
            KeyEvent.KEYCODE_ARROW_LEFT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekBackward()
                return true
            }
            KeyEvent.KEYCODE_ARROW_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekForward()
                return true
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    private fun togglePlayPause() {
        // Toggle play/pause
    }
    
    private fun seekBackward() {
        // Seek -10 seconds
    }
    
    private fun seekForward() {
        // Seek +10 seconds
    }
    
    private fun navigateToPrevious() {
        // Go to previous file
    }
    
    private fun navigateToNext() {
        // Go to next file
    }
}
```

Добавь визуальную индикацию keyboard shortcuts на UI.

```

---

### Промпт 10: Accessibility improvements

```

Улучши accessibility для screen readers:

1. Добавь contentDescription ко всем важным элементам:

```kotlin
// In adapters
binding.ivThumbnail.contentDescription = when (file.type) {
    MediaType.IMAGE -> "Image: ${file.name}"
    MediaType.VIDEO -> "Video: ${file.name}"
    MediaType.AUDIO -> "Audio: ${file.name}"
    else -> "File: ${file.name}"
}

binding.cbSelect.contentDescription = if (isSelected) {
    "Deselect ${file.name}"
} else {
    "Select ${file.name}"
}

binding.btnFavorite.contentDescription = if (file.isFavorite) {
    "Remove ${file.name} from favorites"
} else {
    "Add ${file.name} to favorites"
}
```

1. Установи правильный traversal order для Tab navigation:

```xml
<!-- In layout XMLs -->
<Button
    android:id="@+id/btnAction1"
    android:nextFocusDown="@id/btnAction2"
    android:nextFocusRight="@id/btnAction2" />

<Button
    android:id="@+id/btnAction2"
    android:nextFocusUp="@id/btnAction1"
    android:nextFocusLeft="@id/btnAction1" />
```

1. Добавь announce для important actions:

```kotlin
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityEventCompat

fun announceForAccessibility(view: View, message: String) {
    ViewCompat.announceForAccessibility(view, message)
}

// Usage
announceForAccessibility(binding.root, "5 files selected")
```

Протестируй с TalkBack screen reader.

```

---

### Промпт 11: Тестирование

```

Протестируй keyboard и mouse support:

**Тест 1: Keyboard navigation**

- Открой BrowseActivity
- Нажми Arrow Down несколько раз:
  - ✅ Фокус перемещается по списку
  - ✅ Видна подсветка focused item
- Нажми Space на focused item:
  - ✅ Файл выбран (checkbox checked)
- Нажми Enter на focused item:
  - ✅ Файл открывается

**Тест 2: Keyboard shortcuts**

- Выбери несколько файлов
- Нажми Ctrl+A:
  - ✅ Все файлы выбраны
- Нажми Ctrl+C:
  - ✅ Открылся диалог копирования
- Нажми Escape:
  - ✅ Диалог закрылся
- Нажми F5:
  - ✅ Список обновился
- Выбери файл, нажми F2:
  - ✅ Открылся диалог переименования
- Выбери файл, нажми Delete:
  - ✅ Открылся диалог подтверждения удаления

**Тест 3: Mouse events**

- Double-click на файл:
  - ✅ Файл открылся
- Right-click на файл:
  - ✅ Появилось context menu
  - ✅ Все опции работают (Open, Copy, Move, Rename, Delete)
- Hover над кнопками:
  - ✅ Видны hover effects

**Тест 4: Grid mode**

- Переключи в Grid mode
- Нажми Arrow keys:
  - ✅ Навигация работает по сетке (Up/Down/Left/Right)
- Double-click на item:
  - ✅ Открывается

**Тест 5: PlayerActivity**

- Открой видео/аудио
- Нажми Space:
  - ✅ Play/Pause переключается
- Нажми Arrow Left/Right:
  - ✅ Перемотка работает
- Нажми Escape:
  - ✅ Player закрывается

**Тест 6: Accessibility**

- Включи TalkBack
- Навигация Tab/Arrow:
  - ✅ Screen reader читает элементы
  - ✅ Content descriptions корректны

Запиши результаты в лог файл.

```

---

## Критерии готовности

- ✅ KeyboardShortcutHandler создан и работает
- ✅ MouseEventHandler обрабатывает double-click и right-click
- ✅ FocusManager управляет arrow key navigation
- ✅ BrowseActivity поддерживает все keyboard shortcuts
- ✅ MainActivity поддерживает keyboard navigation
- ✅ PlayerActivity поддерживает keyboard control
- ✅ MediaFileAdapter обрабатывает mouse events
- ✅ ResourceAdapter обрабатывает mouse events
- ✅ Hover effects добавлены во все layouts
- ✅ Context menu работает на right-click
- ✅ Focus indicators видны
- ✅ Tab order логичный
- ✅ Accessibility: contentDescription и screen reader support
- ✅ Все тесты пройдены

---

## Примечания

- **ChromeOS/Desktop mode**: Эта функциональность критична для ChromeOS и Android desktop mode
- **Backwards compatibility**: Не должна ломать touch navigation
- **Performance**: Mouse/keyboard events не должны влиять на производительность
- **Testing**: Тестировать на физическом устройстве с мышью и клавиатурой

---

## Зависимости

- Требует минимальный API 21 (для hover state support)
- Рекомендуется тестировать на Android 11+ для лучшего desktop mode

---

## Будущие улучшения

- **Configurable shortcuts**: Позволить пользователям настраивать shortcuts
- **Chord shortcuts**: Поддержка сложных комбинаций (Ctrl+Shift+X)
- **Mouse gestures**: Drag & drop для перемещения файлов
- **Multi-window support**: Drag файлов между окнами
