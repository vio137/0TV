package dev.tidytv.core.ops

import dev.tidytv.core.lists.Launchers

sealed interface WraithCheck {
    data object Ok : WraithCheck
    /** The saved Wi-Fi belongs to setupwraith: disabling it deletes that network and drops the TV off Wi-Fi. */
    data object OwnsWifi : WraithCheck
    /** First setup isn't finished; setupwraith is still needed. */
    data object SetupIncomplete : WraithCheck
    data object Persistent : WraithCheck
    /** Couldn't tell. Fail closed. */
    data class Unknown(val why: String) : WraithCheck
}

/**
 * Before Google TV's setup app may be turned off (upstream tools/disable.sh guard + tv-detox
 * install.sh checks). Unlike upstream, an unreadable answer blocks instead of passing.
 */
object SetupWraithGuard {
    suspend fun check(tv: TvShell, onEthernet: Boolean): WraithCheck = try {
        val wifi = tv.run(Commands.WIFI_OWNERS)
        val flags = Parse.flags(tv.run(Commands.flags(listOf(Launchers.SETUPWRAITH))))[Launchers.SETUPWRAITH].orEmpty()
        when {
            !onEthernet && wifi.contains("setupwraith") -> WraithCheck.OwnsWifi
            tv.run(Commands.PROVISIONED).trim() != "1" || tv.run(Commands.SETUP_COMPLETE).trim() != "1" -> WraithCheck.SetupIncomplete
            flags.isEmpty() -> WraithCheck.Unknown("couldn't read its flags")
            flags.contains("PERSISTENT") -> WraithCheck.Persistent
            else -> WraithCheck.Ok
        }
    } catch (e: TvConnectionLost) {
        WraithCheck.Unknown("the TV stopped answering")
    }
}
