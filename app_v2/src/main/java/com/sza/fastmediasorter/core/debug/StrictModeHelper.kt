package com.sza.fastmediasorter.core.debug

import android.os.Build
import android.os.StrictMode
import com.sza.fastmediasorter.BuildConfig

/**
 * Helper for managing StrictMode during known-safe operations.
 * 
 * Used to temporarily disable StrictMode penalties for operations that are:
 * - Required during early app initialization (attachBaseContext, onCreate)
 * - Cannot be made async (e.g., SharedPreferences in attachBaseContext)
 * - Safe and expected by Android framework
 * 
 * Example use cases:
 * - Reading SharedPreferences in attachBaseContext() for locale
 * - Early logging initialization
 * - Critical app configuration loading
 */
object StrictModeHelper {
    
    /**
     * Execute a block with temporarily relaxed StrictMode policy.
     * After the block completes, original policy is restored.
     * 
     * Only applies in DEBUG builds - in release builds, no StrictMode is active.
     * 
     * @param block Code to execute with relaxed StrictMode
     * @return Result of the block
     */
    inline fun <T> allowDiskReads(block: () -> T): T {
        if (!BuildConfig.DEBUG) {
            // No StrictMode in release - just execute
            return block()
        }
        
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(oldPolicy)
                    .permitDiskReads()
                    .build()
            )
            return block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }
    
    /**
     * Execute a block with temporarily relaxed StrictMode policy for disk writes.
     * After the block completes, original policy is restored.
     * 
     * @param block Code to execute with relaxed StrictMode
     * @return Result of the block
     */
    inline fun <T> allowDiskWrites(block: () -> T): T {
        if (!BuildConfig.DEBUG) {
            return block()
        }
        
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(oldPolicy)
                    .permitDiskWrites()
                    .build()
            )
            return block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }
    
    /**
     * Execute a block with both disk reads and writes allowed.
     * Use for initialization code that must run synchronously.
     * 
     * @param block Code to execute with relaxed StrictMode
     * @return Result of the block
     */
    inline fun <T> allowDiskIO(block: () -> T): T {
        if (!BuildConfig.DEBUG) {
            return block()
        }
        
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(oldPolicy)
                    .permitDiskReads()
                    .permitDiskWrites()
                    .build()
            )
            return block()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }
}
