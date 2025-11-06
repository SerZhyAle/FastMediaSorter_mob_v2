package com.sza.fastmediasorter

import com.sza.fastmediasorter.ui.settings.LanguageTest
import com.sza.fastmediasorter.ui.settings.SettingsActivityTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    MainActivityTest::class,
    SettingsActivityTest::class,
    LanguageTest::class,
)
class AndroidTestSuite
