package com.sza.fastmediasorter

import com.sza.fastmediasorter.utils.MediaUtilsTest
import com.sza.fastmediasorter.utils.PreferenceManagerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    PreferenceManagerTest::class,
    MediaUtilsTest::class,
)
class UnitTestSuite
