package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo

/**
 * Answers the one `keyboard-interactive` exchange a protected root offers: three hidden fields, in the exact
 * order `Resource`, `Password`, `PIN`. Any other shape is not answered, because guessing which field wants which
 * secret would hand the PIN to a prompt that never asked for it.
 */
internal class SftpRootProofUserInfo(
    private val proof: SftpRootProof,
    private val password: String,
) : UserInfo, UIKeyboardInteractive {

    override fun getPassphrase(): String? = null

    override fun getPassword(): String? = null

    override fun promptPassword(message: String?): Boolean = false

    override fun promptPassphrase(message: String?): Boolean = false

    // The pinned host-key repository already decided; this only lets a pinned key through.
    override fun promptYesNo(message: String?): Boolean = true

    override fun showMessage(message: String?) = Unit

    override fun promptKeyboardInteractive(
        destination: String?,
        name: String?,
        instruction: String?,
        prompt: Array<String>?,
        echo: BooleanArray?,
    ): Array<String>? = answers(proof, password, prompt?.size ?: 0, echo)

    companion object {
        private const val FIELDS = 3

        /** Root, password, PIN - or null when the server asks anything else. */
        fun answers(proof: SftpRootProof, password: String, prompts: Int, echo: BooleanArray?): Array<String>? {
            val hidden = echo != null && echo.size == FIELDS && echo.none { it }
            return if (prompts == FIELDS && hidden) arrayOf(proof.root, password, proof.pin) else null
        }
    }
}
