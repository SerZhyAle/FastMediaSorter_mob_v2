package com.sza.fastmediasorter.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class CustomIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            ActivityLogicDetector.ISSUE,
            UiContextLeakDetector.ISSUE,
            UnsafeFlowCollectDetector.ISSUE,
            PlayerReleaseDetector.ISSUE,
            MainThreadIoDetector.ISSUE,
            NetworkDataSourceDispatcherDetector.ISSUE
        )

    override val api: Int
        get() = CURRENT_API

    override val minApi: Int
        get() = 12

    override val vendor: Vendor
        get() = Vendor(
            vendorName = "FastMediaSorter Team",
            feedbackUrl = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues",
            contact = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2"
        )
}
