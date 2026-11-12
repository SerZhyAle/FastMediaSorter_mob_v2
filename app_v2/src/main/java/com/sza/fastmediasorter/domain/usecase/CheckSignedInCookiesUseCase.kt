package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.link.auth.AccountIdentityExtractor
import java.net.HttpCookie
import javax.inject.Inject

/**
 * S4505: whether a browser login has finished, judged by the credential cookie only a signed-in session
 * of the host carries - the login page itself already sets cookies, so their count proves nothing.
 */
class CheckSignedInCookiesUseCase @Inject constructor() {
    operator fun invoke(host: String, cookies: List<HttpCookie>): Boolean =
        AccountIdentityExtractor.hasSignedInCookies(host, cookies)
}
