package com.sza.fastmediasorter.data.browser

/**
 * Thrown by [GoogleDomainBrowserLauncher] when no Chrome Custom Tabs-capable browser is installed
 * on the device. Caller renders the refusal UX (`CctRefusalDialog`) and stays on the current screen.
 */
class CctUnavailableException(message: String) : Exception(message)
