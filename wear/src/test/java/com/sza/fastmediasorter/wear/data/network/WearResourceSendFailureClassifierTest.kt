package com.sza.fastmediasorter.wear.data.network

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeTunnelUnavailableException
import com.sza.fastmediasorter.wear.data.network.ftp.FtpStoreRefusedException
import com.sza.fastmediasorter.wear.data.network.sftp.WearResourcePinMissingException
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/** S4230: one reason per refused or broken write, whichever of the three libraries raised it. */
class WearResourceSendFailureClassifierTest {

    private val classifier = WearResourceSendFailureClassifier(WearNetworkFailureClassifier())

    @Test
    fun `an SFTP permission denial is a refusal`() {
        val error = SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "Permission denied")

        assertEquals(WearResourceSendFailure.REFUSED, classifier.classify(error))
    }

    @Test
    fun `an SFTP failure naming a full disk is no space`() {
        val error = SftpException(ChannelSftp.SSH_FX_FAILURE, "No space left on device")

        assertEquals(WearResourceSendFailure.NO_SPACE, classifier.classify(error))
    }

    @Test
    fun `an SFTP missing directory is the resource being unavailable`() {
        val error = SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such file")

        assertEquals(WearResourceSendFailure.UNREACHABLE, classifier.classify(error))
    }

    @Test
    fun `an SFTP plain failure is a broken transfer`() {
        val error = SftpException(ChannelSftp.SSH_FX_FAILURE, "Failure")

        assertEquals(WearResourceSendFailure.BROKEN, classifier.classify(error))
    }

    @Test
    fun `FTP replies map by what they mean`() {
        assertEquals(WearResourceSendFailure.NO_SPACE, classifier.classify(FtpStoreRefusedException(REPLY_NO_SPACE)))
        assertEquals(WearResourceSendFailure.REFUSED, classifier.classify(FtpStoreRefusedException(REPLY_DENIED)))
        assertEquals(WearResourceSendFailure.UNREACHABLE, classifier.classify(FtpStoreRefusedException(REPLY_DOWN)))
        assertEquals(WearResourceSendFailure.BROKEN, classifier.classify(FtpStoreRefusedException(REPLY_OTHER)))
    }

    @Test
    fun `SMB statuses map by what they mean`() {
        assertEquals(WearResourceSendFailure.NO_SPACE, classifier.fromSmbStatus("STATUS_DISK_FULL"))
        assertEquals(WearResourceSendFailure.REFUSED, classifier.fromSmbStatus("STATUS_ACCESS_DENIED"))
        assertEquals(WearResourceSendFailure.UNREACHABLE, classifier.fromSmbStatus("STATUS_BAD_NETWORK_NAME"))
        assertEquals(WearResourceSendFailure.BROKEN, classifier.fromSmbStatus("STATUS_SOMETHING_ELSE"))
        assertEquals(WearResourceSendFailure.BROKEN, classifier.fromSmbStatus(null))
    }

    @Test
    fun `an unreadable source is named apart from the resource`() {
        assertEquals(WearResourceSendFailure.SOURCE_UNREADABLE, classifier.classify(FileNotFoundException("gone")))
        assertEquals(WearResourceSendFailure.SOURCE_UNREADABLE, classifier.classify(SecurityException("denied")))
    }

    @Test
    fun `an unknown host and a refused connection are unavailable`() {
        assertEquals(WearResourceSendFailure.UNREACHABLE, classifier.classify(UnknownHostException("nas")))
        assertEquals(WearResourceSendFailure.UNREACHABLE, classifier.classify(ConnectException("refused")))
    }

    @Test
    fun `a rejected login is a refusal`() {
        assertEquals(WearResourceSendFailure.REFUSED, classifier.classify(JSchException("Auth fail")))
        assertEquals(
            WearResourceSendFailure.REFUSED,
            classifier.classify(IllegalStateException("FTP login failed for user@host"))
        )
    }

    @Test
    fun `a typed failure is found down the cause chain`() {
        val wrapped = IOException("send failed", SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied"))

        assertEquals(WearResourceSendFailure.REFUSED, classifier.classify(wrapped))
    }

    @Test
    fun `an exchange folder reached only over the phone's Bluetooth is no network`() {
        assertEquals(
            WearResourceSendFailure.NO_NETWORK,
            classifier.classify(WearExchangeNeedsOwnNetworkException())
        )
    }

    @Test
    fun `a protected folder without a held PIN needs the PIN`() {
        assertEquals(
            WearResourceSendFailure.PIN_NEEDED,
            classifier.classify(WearResourcePinMissingException())
        )
    }

    @Test
    fun `an exchange folder of an account the watch holds no entry of is not connected`() {
        assertEquals(
            WearResourceSendFailure.NOT_CONNECTED,
            classifier.classify(WearExchangeEntryMissingException())
        )
    }

    @Test
    fun `a tunnel the server will not open is unreachable`() {
        assertEquals(
            WearResourceSendFailure.UNREACHABLE,
            classifier.classify(WearExchangeTunnelUnavailableException("offline"))
        )
    }

    @Test
    fun `an exchange refusal wrapped by the session is found down the cause chain`() {
        val wrapped = IOException("send failed", WearExchangeEntryMissingException())

        assertEquals(WearResourceSendFailure.NOT_CONNECTED, classifier.classify(wrapped))
    }

    @Test
    fun `anything unnamed is a broken transfer`() {
        assertEquals(WearResourceSendFailure.BROKEN, classifier.classify(IOException("Connection reset")))
    }

    private companion object {
        const val REPLY_NO_SPACE = 552
        const val REPLY_DENIED = 550
        const val REPLY_DOWN = 421
        const val REPLY_OTHER = 599
    }
}
