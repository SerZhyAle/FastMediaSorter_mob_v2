package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.TranslatorGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1177: the cells that work on text, supplied as one binding rather than one constructor parameter each.
 *
 * A collection for a single gadget looks like ceremony and is not: `LauncherGadgetRegistry` sits at the
 * constructor threshold detekt enforces, and its own comment states that the next gadget joins a qualified
 * list instead of the parameter list. The technical module next door is scoped by its KDoc to the four
 * metric tiles sharing one class, so a translator does not belong there.
 *
 * Qualified because the payload is `List<LauncherGadget>`, the exact type the registry deals in; an
 * unqualified list binding would otherwise satisfy that injection point by accident.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TextToolGadgets

@Module
@InstallIn(SingletonComponent::class)
object TextToolGadgetModule {

    @Provides
    @Singleton
    @TextToolGadgets
    fun provideTextToolGadgets(
        translator: TranslatorGadget,
    ): List<LauncherGadget> = listOf(translator)
}
