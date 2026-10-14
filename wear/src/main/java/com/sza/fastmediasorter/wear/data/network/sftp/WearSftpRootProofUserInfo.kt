package com.sza.fastmediasorter.wear.data.network.sftp

import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo

/**
 * S4228: answers the one `keyboard-interactive` exchange a protected root offers (contract DEVICE-EXCHANGE 17 item
 * AH): three hidden fields, in the exact order root, password, PIN. Any other shape is not answered, because guessing
 * which field wants which secret would hand the PIN to a prompt that never asked for it.
 */
internal class WearSftpRootProofUserInfo(
    private val root: String,
    private val password: String,
    private val pin: String,
) : UserInfo, UIKeyboardInteractive {

    override fun getPassphrase(): String? = null

    override fun getPassword(): String? = null

    override fun promptPassword(message: String?): Boolean = false

    override fun promptPassphrase(message: String?): Boolean = false

    // The pinned host-key policy already decided; this only lets a pinned key through.
    override fun promptYesNo(message: String?): Boolean = true

    override fun showMessage(message: String?) = Unit

    override fun promptKeyboardInteractive(
        destination: String?,
        name: String?,
        instruction: String?,
        prompt: Array<String>?,
        echo: BooleanArray?,
    ): Array<String>? = answers(root, password, pin, prompt?.size ?: 0, echo)

    override fun toString(): String = "WearSftpRootProofUserInfo(***)"

    companion object {
        private const val FIELDS = 3

        /** Root, password, PIN - or null when the server asks anything else. */
        fun answers(root: String, password: String, pin: String, prompts: Int, echo: BooleanArray?): Array<String>? {
            val hidden = echo != null && echo.size == FIELDS && echo.none { it }
            return if (prompts == FIELDS && hidden) arrayOf(root, password, pin) else null
        }
    }
}
