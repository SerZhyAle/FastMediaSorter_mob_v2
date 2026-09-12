package com.sza.fastmediasorter.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency injection module for Android 16+ system assistant AppFunctions surface (S2920).
 * Provides bindings and dependencies used by assistant action services and handlers.
 */
@Module
@InstallIn(SingletonComponent::class)
object AssistantModule
