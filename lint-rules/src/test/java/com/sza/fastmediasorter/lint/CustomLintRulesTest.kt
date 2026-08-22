package com.sza.fastmediasorter.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CustomLintRulesTest {

    // S1195: the detectors match media types by fully-qualified name, so the test sources must
    // declare those exact FQNs. A locally-declared `class ExoPlayer` proves nothing about a rule
    // that resolves androidx.media3.exoplayer.ExoPlayer.
    private val media3Stub = kotlin(
        """
        package androidx.media3.exoplayer

        open class ExoPlayer {
            fun release() {}
            class Builder(context: Any) {
                fun build(): ExoPlayer = ExoPlayer()
            }
        }
        """.trimIndent()
    )

    private val mediaControllerStub = kotlin(
        """
        package androidx.media3.session

        class MediaController {
            fun release() {}
        }
        """.trimIndent()
    )

    private val futureStub = kotlin(
        """
        package com.google.common.util.concurrent

        class ListenableFuture<T>
        """.trimIndent()
    )

    // S1195: UiContextLeakDetector resolves types through their supertypes, so the stubs must
    // model the real hierarchy - TextView IS-A View, AppCompatActivity IS-A Activity IS-A Context.
    private val androidUiStub = kotlin(
        """
        package android.content

        open class Context
        open class ContextWrapper : Context()
        """.trimIndent()
    )

    private val androidAppStub = kotlin(
        """
        package android.app

        import android.content.ContextWrapper

        open class Application : ContextWrapper()
        open class Activity : ContextWrapper()
        """.trimIndent()
    )

    private val androidViewStub = kotlin(
        """
        package android.view

        open class View
        open class ViewGroup : View()
        """.trimIndent()
    )

    private val androidWidgetStub = kotlin(
        """
        package android.widget

        import android.view.View
        import android.view.ViewGroup

        open class TextView : View()
        open class FrameLayout : ViewGroup()
        """.trimIndent()
    )

    private val androidxStub = kotlin(
        """
        package androidx.appcompat.app

        import android.app.Activity

        open class AppCompatActivity : Activity()
        """.trimIndent()
    )

    private val recyclerViewStub = kotlin(
        """
        package androidx.recyclerview.widget

        import android.view.ViewGroup

        open class RecyclerView : ViewGroup()
        """.trimIndent()
    )

    private val hiltStub = kotlin(
        """
        package dagger.hilt.android.qualifiers

        annotation class ApplicationContext
        annotation class ActivityContext
        """.trimIndent()
    )

    private val singletonStub = kotlin(
        """
        package javax.inject

        annotation class Singleton
        """.trimIndent()
    )

    private val viewModelStub = kotlin(
        """
        package androidx.lifecycle

        open class ViewModel
        """.trimIndent()
    )

    // S1195: MainThreadIoDetector resolves the dispatcher argument to its declaring class, so the
    // stub must expose Dispatchers as a real object with IO / Default / Main members.
    private val coroutinesStub = kotlin(
        """
        package kotlinx.coroutines

        class CoroutineDispatcher

        object Dispatchers {
            val IO = CoroutineDispatcher()
            val Default = CoroutineDispatcher()
            val Main = CoroutineDispatcher()
        }

        class CoroutineScope {
            fun launch(dispatcher: CoroutineDispatcher, block: () -> Unit) {}
        }

        fun <T> withContext(dispatcher: CoroutineDispatcher, block: () -> T): T = block()
        """.trimIndent()
    )

    private val workerThreadStub = kotlin(
        """
        package androidx.annotation

        annotation class WorkerThread
        """.trimIndent()
    )

    private val smbjStub = kotlin(
        """
        package com.hierynomus.smbj

        class SMBClient {
            fun connect(hostname: String): Any = Any()
        }
        """.trimIndent()
    )

    private val commonsNetStub = kotlin(
        """
        package org.apache.commons.net.ftp

        class FTPClient {
            fun connect(hostname: String) {}
            fun listFiles(): Array<Any> = emptyArray()
        }
        """.trimIndent()
    )

    private val jschStub = kotlin(
        """
        package com.jcraft.jsch

        interface JSch {
            fun getSession(user: String, host: String, port: Int): Session
        }

        interface Session {
            fun connect()
            fun openChannel(type: String): Channel
        }

        interface Channel
        interface ChannelSftp : Channel {
            fun connect()
            fun ls(path: String): List<Any>
        }
        """.trimIndent()
    )

    private val uiStubs = arrayOf(
        androidUiStub,
        androidAppStub,
        androidViewStub,
        androidWidgetStub,
        androidxStub,
        recyclerViewStub,
        hiltStub,
        singletonStub,
        viewModelStub
    )

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

    /** S1195: the core true positive - an unqualified Context in a @Singleton and in a ViewModel. */
    @Test
    fun testUiContextLeakDetectorFlagsRawContext() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import android.content.Context
                    import androidx.lifecycle.ViewModel
                    import javax.inject.Singleton

                    @Singleton
                    class MyRegistry(private val context: Context)

                    class MyViewModel : ViewModel() {
                        lateinit var activityContext: Context
                    }
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectErrorCount(2)
    }

    /**
     * S1195 false positive, 33 of the 44: an Activity-scoped helper under `ui/<feature>/helpers`
     * holding a View is exactly what CLAUDE.md Rule 3 mandates. The old `endsWith("Manager")`
     * heuristic read that mandated name as evidence of a long lifetime.
     */
    @Test
    fun testUiContextLeakDetectorIgnoresActivityScopedManager() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import android.app.Activity
                    import android.view.ViewGroup

                    class LauncherEditModeManager(
                        private val activity: Activity,
                        private val root: ViewGroup
                    )
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive, the other 11: every one carries a Hilt @ApplicationContext on the
     * CONSTRUCTOR PARAMETER, which `field.annotations` alone does not see. The old escape compared
     * the field NAME against "appContext", and all 11 fields are named `context`.
     */
    @Test
    fun testUiContextLeakDetectorHonoursApplicationContextQualifier() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data

                    import android.content.Context
                    import dagger.hilt.android.qualifiers.ApplicationContext
                    import javax.inject.Singleton

                    @Singleton
                    class SettingsRepositoryImpl(
                        @ApplicationContext private val context: Context
                    )

                    @Singleton
                    class ParamSiteRepository(
                        @param:ApplicationContext private val context: Context
                    )
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: @ActivityContext is a deliberate, annotated opt-in - not a leak to report. */
    @Test
    fun testUiContextLeakDetectorHonoursActivityContextQualifier() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import android.content.Context
                    import dagger.hilt.android.qualifiers.ActivityContext
                    import javax.inject.Singleton

                    @Singleton
                    class BrowseVrCinemaLaunchManager(
                        @ActivityContext private val context: Context
                    )
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: `typeName.contains("android.view.View")` swept in nested types, so a
     * `View.OnDragListener` property was reported as a retained View.
     */
    @Test
    fun testUiContextLeakDetectorIgnoresNestedViewInterface() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import javax.inject.Singleton

                    class OnDragListener

                    @Singleton
                    class DragRegistry {
                        val dragListener = OnDragListener()
                    }
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: an Application is process-lifetime, so retaining one in a singleton is correct. */
    @Test
    fun testUiContextLeakDetectorIgnoresApplicationContext() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data

                    import android.app.Application
                    import javax.inject.Singleton

                    @Singleton
                    class AppHolder(private val app: Application)
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 THE FALSE NEGATIVE - the reason the rule is worth keeping at all.
     *
     * The old detector matched type names literally: `.endsWith(".View")` never matched TextView or
     * RecyclerView, and `.endsWith(".Activity")` never matched AppCompatActivity. A real View
     * retained by a ViewModel was never reported. All three of these fail against the pre-Phase-03
     * detector and pass after it.
     */
    @Test
    fun testUiContextLeakDetectorCatchesViewSubclassesInLongLivedHolders() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import android.widget.TextView
                    import androidx.appcompat.app.AppCompatActivity
                    import androidx.lifecycle.ViewModel
                    import androidx.recyclerview.widget.RecyclerView
                    import javax.inject.Singleton

                    class LabelViewModel : ViewModel() {
                        lateinit var label: TextView
                    }

                    class HostViewModel : ViewModel() {
                        lateinit var host: AppCompatActivity
                    }

                    @Singleton
                    class ListRegistry {
                        lateinit var list: RecyclerView
                    }
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectErrorCount(3)
    }

    /** S1195: the paired clean siblings for the three subclass positives above. */
    @Test
    fun testUiContextLeakDetectorAcceptsGuardedViewSubclasses() {
        lint()
            .allowMissingSdk()
            .files(
                *uiStubs,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import android.content.Context
                    import android.widget.TextView
                    import androidx.appcompat.app.AppCompatActivity
                    import androidx.lifecycle.ViewModel
                    import androidx.recyclerview.widget.RecyclerView
                    import dagger.hilt.android.qualifiers.ApplicationContext
                    import java.lang.ref.WeakReference
                    import javax.inject.Singleton

                    class LabelViewModel : ViewModel() {
                        var label: WeakReference<TextView>? = null
                    }

                    class HostViewModel : ViewModel() {
                        var host: WeakReference<AppCompatActivity>? = null
                    }

                    @Singleton
                    class ListRegistry(
                        @ApplicationContext private val context: Context
                    ) {
                        var list: WeakReference<RecyclerView>? = null
                    }
                    """.trimIndent()
                )
            )
            .issues(UiContextLeakDetector.ISSUE)
            .run()
            .expectClean()
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

    /** S1195: an owner - builds an ExoPlayer, never releases it. The case the rule exists for. */
    @Test
    fun testPlayerReleaseDetectorFlagsUnreleasedOwner() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer

                    class UnreleasedPlayerHolder {
                        private val player = ExoPlayer.Builder(this).build()

                        fun play() {
                            player.toString()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    /** S1195: same owner, but it releases what it built - must be clean. */
    @Test
    fun testPlayerReleaseDetectorAcceptsReleasedOwner() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer

                    class ReleasedPlayerHolder {
                        private val player = ExoPlayer.Builder(this).build()

                        fun onDestroy() {
                            player.release()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 defect 2: the old `hasReleaseCall` walk accepted ANY method named release() anywhere
     * in the class, so a wakeLock silenced the rule about a player.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresUnrelatedReleaseCall() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer

                    class WakeLock {
                        fun release() {}
                    }

                    class DecoyReleaseHolder {
                        private val wakeLock = WakeLock()
                        private val player = ExoPlayer.Builder(this).build()

                        fun onDestroy() {
                            wakeLock.release()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    /**
     * S1195, found by measuring the rewritten rule against the codebase: a factory function whose
     * return type IS the player hands ownership to its caller. Models `PlayerSetupHelper`'s
     * `createPlayer(): ExoPlayer`, which the first draft of the ownership rule reported.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresFactoryThatReturnsPlayer() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import androidx.media3.exoplayer.ExoPlayer

                    class PlayerSetupHelper {
                        fun createPlayer(): ExoPlayer {
                            val player = ExoPlayer.Builder(this).build()
                            return player
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195, same measurement: the class releases the player through a different local than the one
     * it constructed into, so name matching alone reported it. Models `BackgroundMusicManager`,
     * which assigns `musicPlayer` and frees it via a `player` alias.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresReleaseThroughAlias() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import androidx.media3.exoplayer.ExoPlayer

                    class BackgroundMusicManager {
                        private var musicPlayer: ExoPlayer? = null

                        fun start() {
                            musicPlayer = ExoPlayer.Builder(this).build()
                        }

                        fun release() {
                            val player = musicPlayer ?: return
                            player.release()
                            musicPlayer = null
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: an `object` with zero properties whose own name contains "Player".
     * Its synthetic INSTANCE field is typed with the enclosing class's FQN, which the old
     * substring match read as a player property. Models PlayerTextureFrameCapture.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresObjectNamedPlayer() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    internal object PlayerTextureFrameCapture {
                        fun capture(): Int = 0
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: a private companion object yields a synthetic Companion field typed
     * with the enclosing class's FQN. Models S0981OpenInPlayerDefaultOff.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresCompanionOfPlayerNamedClass() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.migration

                    class S0981OpenInPlayerDefaultOff {
                        fun migrate(): Boolean = true

                        private companion object {
                            const val KEY = "open_in_player"
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: a DTO carrying a player-typed property it never constructs is a
     * borrower, not an owner. Models VideoPlayerHostDependencies.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresBorrowingDto() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer

                    data class VideoPlayerHostDependencies(
                        val player: ExoPlayer,
                        val playerCallback: () -> Unit
                    )
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: a Lazy<T> of a type merely named "..Player.." is not a player.
     * Models FastMediaSorterApp.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresLazyOfPlayerNamedType() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package com.sza.fastmediasorter

                    class Lazy<T>

                    class OpenInPlayerDefaultOff

                    class FastMediaSorterApp {
                        val migration: Lazy<OpenInPlayerDefaultOff> = Lazy()
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive: a nested DTO holding a ListenableFuture<MediaController> was evaluated
     * in isolation from the outer class that actually owns and releases the player. Both the
     * nested DTO and the releasing outer class must be clean. Models AudioServiceController.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresNestedFutureDto() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                mediaControllerStub,
                futureStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer
                    import androidx.media3.session.MediaController
                    import com.google.common.util.concurrent.ListenableFuture

                    class AudioServiceController {
                        private val player = ExoPlayer.Builder(this).build()

                        fun shutdown() {
                            player.release()
                        }

                        data class FutureRequest(val future: ListenableFuture<MediaController>)
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195 false positive, found by measuring the rewritten rule against the codebase: a file of
     * top-level extension functions compiles to a synthetic `<File>Kt` facade. The facade holds no
     * state, so the player its function builds lands on the extension receiver, which owns and
     * releases it. Models SmbPlaybackHelper.kt and its four siblings.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresTopLevelExtensionFacade() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.exoplayer.ExoPlayer

                    class VideoPlayerManager {
                        var exoPlayer: ExoPlayer? = null

                        fun teardown() {
                            exoPlayer?.release()
                        }
                    }
                    """.trimIndent()
                ),
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import androidx.media3.exoplayer.ExoPlayer
                    import com.sza.fastmediasorter.ui.VideoPlayerManager

                    internal fun VideoPlayerManager.playSmbVideo() {
                        exoPlayer = ExoPlayer.Builder(this).build()
                    }

                    private const val RETRY_LIMIT = 3
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195: the facade exemption must not become a blanket one. A top-level property IS state, so
     * a file that parks a player in one and never frees it is still an owner and still reported.
     */
    @Test
    fun testPlayerReleaseDetectorFlagsTopLevelPropertyOwner() {
        lint()
            .allowMissingSdk()
            .files(
                media3Stub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.helpers

                    import androidx.media3.exoplayer.ExoPlayer

                    private val leakedPlayer = ExoPlayer.Builder("ctx").build()

                    internal fun peek(): String = leakedPlayer.toString()
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    /**
     * S1195 false positive: an asynchronously built MediaController is freed with
     * `MediaController.releaseFuture(future)`, which takes the FUTURE, so the owned type sits in
     * the type argument. Matching only the argument's own type read this as a leak.
     * Models NowPlayingViewModel.
     */
    @Test
    fun testPlayerReleaseDetectorIgnoresReleaseThroughFuture() {
        lint()
            .allowMissingSdk()
            .files(
                futureStub,
                kotlin(
                    """
                    package androidx.media3.session

                    import com.google.common.util.concurrent.ListenableFuture

                    class SessionToken(context: Any, name: Any)

                    class MediaController {
                        fun release() {}

                        class Builder(context: Any, token: SessionToken) {
                            fun buildAsync(): ListenableFuture<MediaController> = ListenableFuture()
                        }

                        companion object {
                            fun releaseFuture(future: ListenableFuture<MediaController>) {}
                        }
                    }
                    """.trimIndent()
                ),
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.media3.session.MediaController
                    import androidx.media3.session.SessionToken
                    import com.google.common.util.concurrent.ListenableFuture

                    class NowPlayingViewModel {
                        private var controllerFuture: ListenableFuture<MediaController>? = null

                        fun connect() {
                            val token = SessionToken(this, this)
                            val future = MediaController.Builder(this, token).buildAsync()
                            controllerFuture = future
                        }

                        fun disconnect() {
                            controllerFuture?.let { MediaController.releaseFuture(it) }
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(PlayerReleaseDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: the original true positive - unconfined blocking read in a ViewModel. */
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
            .expectErrorCount(1)
    }

    /**
     * S1195 true positive: a blocking read reached from onPostResume() with no coroutine anywhere
     * in the chain. Models PrintDispatchActivity, the one genuine finding of the two.
     */
    @Test
    fun testMainThreadIoDetectorFlagsUnconfinedActivityRead() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.player.print

                    import java.io.File

                    class PrintDispatchActivity {
                        fun onPostResume() {
                            dispatchText(File("note.txt"))
                        }

                        private fun dispatchText(file: File) {
                            file.readText()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    /**
     * S1195 false positive: a private helper whose every call site in the file is already inside
     * withContext(Dispatchers.IO). The detector stopped at the enclosing function body and never
     * saw the caller's confinement. Models PhotoVideoStandaloneActivity:400.
     */
    @Test
    fun testMainThreadIoDetectorHonoursCallerConfinement() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui.player.standalone

                    import java.io.File
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext

                    class PhotoVideoStandaloneActivity {
                        suspend fun printMediaFile() {
                            withContext(Dispatchers.IO) { stageBitmapForPrint() }
                        }

                        private fun stageBitmapForPrint() {
                            File("staging").delete()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: a coroutine builder with an IO dispatcher confines just as withContext does. */
    @Test
    fun testMainThreadIoDetectorHonoursLaunchWithIoDispatcher() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import java.io.File
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.Dispatchers

                    class GalleryActivity {
                        val lifecycleScope = CoroutineScope()

                        fun load(file: File) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                file.readText()
                            }
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: a suspend function is not assumed to run on the main thread. */
    @Test
    fun testMainThreadIoDetectorHonoursSuspendFunction() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import java.io.File

                    class NotesActivity {
                        suspend fun load(file: File): String = file.readText()
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectClean()
    }

    /** S1195: an explicit @WorkerThread contract is a confinement the rule must honour. */
    @Test
    fun testMainThreadIoDetectorHonoursWorkerThreadAnnotation() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                workerThreadStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import androidx.annotation.WorkerThread
                    import java.io.File

                    class ExportActivity {
                        @WorkerThread
                        fun export(file: File): String = file.readText()
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectClean()
    }

    /**
     * S1195: Dispatchers.Main must NOT confine. The old check asked whether the argument's rendered
     * text contained "IO", so any identifier carrying those letters silenced the rule.
     *
     * The enclosing function is deliberately NOT suspend - a suspend enclosing function is itself a
     * confinement, and would mask exactly what this case exists to prove.
     */
    @Test
    fun testMainThreadIoDetectorRejectsMainDispatcher() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.ui

                    import java.io.File
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.Dispatchers

                    class ImportActivity {
                        val lifecycleScope = CoroutineScope()

                        fun load(file: File) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                file.readText()
                            }
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(MainThreadIoDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testNetworkDataSourceDispatcherFlagsUnconfinedSmbCall() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                smbjStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import com.hierynomus.smbj.SMBClient

                    class SmbDataSource(private val client: SMBClient) {
                        suspend fun listShares(): Any {
                            return client.connect("192.168.1.1")
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testNetworkDataSourceDispatcherFlagsUnconfinedFtpCall() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                commonsNetStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import org.apache.commons.net.ftp.FTPClient

                    class FtpDataSource(private val client: FTPClient) {
                        suspend fun listFiles(): Array<Any> {
                            return client.listFiles()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testNetworkDataSourceDispatcherFlagsUnconfinedSftpCall() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                jschStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import com.jcraft.jsch.ChannelSftp

                    class SftpDataSource(private val channel: ChannelSftp) {
                        suspend fun listFiles(): List<Any> {
                            return channel.ls("/path")
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testNetworkDataSourceDispatcherAcceptsWithContextIo() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                smbjStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import com.hierynomus.smbj.SMBClient
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext

                    class SmbDataSource(private val client: SMBClient) {
                        suspend fun listShares(): Any = withContext(Dispatchers.IO) {
                            client.connect("192.168.1.1")
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testNetworkDataSourceDispatcherAcceptsConfinedPrivateHelper() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                commonsNetStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import org.apache.commons.net.ftp.FTPClient
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext

                    class FtpDataSource(private val client: FTPClient) {
                        suspend fun fetch(): Array<Any> = withContext(Dispatchers.IO) {
                            doList()
                        }

                        private fun doList(): Array<Any> {
                            return client.listFiles()
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testNetworkDataSourceDispatcherAcceptsFieldStoredDispatcher() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                jschStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import com.jcraft.jsch.ChannelSftp
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext

                    class SftpDataSource(private val channel: ChannelSftp) {
                        private val ioDispatcher = Dispatchers.IO

                        suspend fun list(): List<Any> = withContext(ioDispatcher) {
                            channel.ls("/path")
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testNetworkDataSourceDispatcherRejectsMainDispatcher() {
        lint()
            .allowMissingSdk()
            .files(
                coroutinesStub,
                smbjStub,
                kotlin(
                    """
                    package com.sza.fastmediasorter.data.network

                    import com.hierynomus.smbj.SMBClient
                    import kotlinx.coroutines.Dispatchers
                    import kotlinx.coroutines.withContext

                    class SmbDataSource(private val client: SMBClient) {
                        suspend fun list(): Any = withContext(Dispatchers.Main) {
                            client.connect("192.168.1.1")
                        }
                    }
                    """.trimIndent()
                )
            )
            .issues(NetworkDataSourceDispatcherDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }
}
