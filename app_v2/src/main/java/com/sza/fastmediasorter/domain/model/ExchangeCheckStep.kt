package com.sza.fastmediasorter.domain.model

/**
 * The fifteen steps of the connection check ladder of contract DEVICE-EXCHANGE 25 item CY, in the contract's
 * order: it runs from the machine outward - whether the other end is there, then the path to it, then the
 * protected link, then the account. [id] is the contract's step id and is never shown to the person.
 *
 * @property windowsOnly the step is about a Windows computer and applies only when the target is the exchange
 *   server, which FMS_W runs; a phone talking to a phone has no such step.
 */
enum class ExchangeCheckStep(
    val id: String,
    val applies: Applies,
    val windowsOnly: Boolean = false,
) {
    PC_ASLEEP("pc-asleep", Applies.ANY),
    ADDRESS_LAN("address-lan", Applies.NETWORK),
    GUEST_NETWORK("guest-network", Applies.NETWORK),
    VPN("vpn", Applies.ANY),
    WINDOWS_PUBLIC_NETWORK("windows-public-network", Applies.NETWORK, windowsOnly = true),
    IP_FAMILY("ip-family", Applies.ANY),
    SERVER_INTERNET("server-internet", Applies.INTERNET),
    PORT_FORWARD("port-forward", Applies.INTERNET),
    DEVICE_CLOCK("device-clock", Applies.ANY),
    CERTIFICATE("certificate", Applies.ANY),
    CREDENTIALS("credentials", Applies.ANY),
    SIGN_IN_RATE("sign-in-rate", Applies.ANY),
    INVITATION("invitation", Applies.ANY),
    DEVICE_REMOVED("device-removed", Applies.ANY),
    OPERATOR("operator", Applies.ANY);

    /** Where a step makes sense: the target is on the person's own network, reached from outside, or either. */
    enum class Applies { NETWORK, INTERNET, ANY }

    companion object {
        fun of(id: String): ExchangeCheckStep? = entries.firstOrNull { it.id == id }
    }
}
