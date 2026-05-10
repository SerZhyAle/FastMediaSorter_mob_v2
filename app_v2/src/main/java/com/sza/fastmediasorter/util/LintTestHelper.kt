package com.sza.fastmediasorter.util

import android.util.Log

class LintTestHelper {
    fun doSomething() {
        Log.d("LintTest", "This is a forbidden log")
        println("This is a forbidden println")
    }
}
