package com.sza.fastmediasorter.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CustomLintRulesTest {

    @Test
    fun testActivityLogicDetector() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui
                    
                    annotation class Inject
                    open class Activity
                    
                    class TestActivity : Activity() {
                        @Inject
                        lateinit var repository: MyRepository
                    }
                    
                    class MyRepository
                    """.trimIndent()
                )
            )
            .issues(ActivityLogicDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/sza/fastmediasorter/ui/Inject.kt:7: Error: Activities must not contain business logic or direct references to repositories/usecases/data sources/databases. Delegate to a ViewModel or Manager. [ActivityLogicViolation]
                    @Inject
                    ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testUiContextLeakDetector() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui
                    
                    annotation class Singleton
                    open class Context
                    open class ViewModel
                    
                    @Singleton
                    class MyManager {
                        lateinit var context: Context
                    }
                    
                    class MyViewModel : ViewModel() {
                        lateinit var activityContext: Context
                    }
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/sza/fastmediasorter/ui/Singleton.kt:9: Error: Do not store UI Context (Activity, Fragment, View) in long-lived ViewModels or Singletons. This leads to severe memory leaks. [UiContextLeak]
                    lateinit var context: Context
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/com/sza/fastmediasorter/ui/Singleton.kt:13: Error: Do not store UI Context (Activity, Fragment, View) in long-lived ViewModels or Singletons. This leads to severe memory leaks. [UiContextLeak]
                    lateinit var activityContext: Context
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testUnsafeFlowCollectDetector() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui
                    
                    class Flow<T> {
                        fun collect(action: (T) -> Unit) {}
                    }
                    
                    class LifecycleScope {
                        fun launch(block: suspend () -> Unit) {}
                    }
                    
                    class TestClass {
                        val lifecycleScope = LifecycleScope()
                        val flow = Flow<String>()
                        
                        fun test() {
                            lifecycleScope.launch {
                                flow.collect { value ->
                                    println(value)
                                }
                            }
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(UnsafeFlowCollectDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/sza/fastmediasorter/ui/Flow.kt:17: Error: Collecting Flow directly in lifecycleScope.launch is unsafe. Use repeatOnLifecycle or flowWithLifecycle to prevent background resource usage and leaks. [UnsafeFlowCollect]
                            flow.collect { value ->
                            ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testPlayerReleaseDetector() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui
                    
                    class ExoPlayer
                    
                    class UnreleasedPlayerHolder {
                        val player = ExoPlayer()
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/sza/fastmediasorter/ui/ExoPlayer.kt:5: Error: Class declares Player/ExoPlayer/MediaController property but does not call release() to clean up resources. [PlayerNotReleased]
                class UnreleasedPlayerHolder {
                ^
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testMainThreadIoDetector() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui
                    
                    import java.io.File
                    
                    open class ViewModel
                    
                    class MyViewModel : ViewModel() {
                        fun loadData(file: File) {
                            file.readText()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/sza/fastmediasorter/ui/ViewModel.kt:9: Error: Blocking I/O operation called on the main thread in a UI or ViewModel class. Wrap it in withContext(Dispatchers.IO). [MainThreadIo]
                        file.readText()
                        ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }
}
