package dev.tidytv.core.ops

/** The buttons on TidyTV's remote, as Android key codes. */
enum class RemoteKey(val code: String) {
    UP("KEYCODE_DPAD_UP"), DOWN("KEYCODE_DPAD_DOWN"), LEFT("KEYCODE_DPAD_LEFT"), RIGHT("KEYCODE_DPAD_RIGHT"),
    OK("KEYCODE_DPAD_CENTER"), BACK("KEYCODE_BACK"), HOME("KEYCODE_HOME"), MENU("KEYCODE_MENU"),
    VOLUME_UP("KEYCODE_VOLUME_UP"), VOLUME_DOWN("KEYCODE_VOLUME_DOWN"), MUTE("KEYCODE_VOLUME_MUTE"),
    POWER("KEYCODE_POWER"), PLAY_PAUSE("KEYCODE_MEDIA_PLAY_PAUSE"),
    REWIND("KEYCODE_MEDIA_REWIND"), FAST_FORWARD("KEYCODE_MEDIA_FAST_FORWARD"),
    DELETE("KEYCODE_DEL"), ENTER("KEYCODE_ENTER"),
}

/** An app the TV can open from its home screen. [component] is "pkg/activity". */
data class TvApp(val pkg: String, val component: String)

object RemoteCommands {
    /** Launchable TV apps (the ones a TV home screen shows). */
    const val TV_APPS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"
    /** Older TVs and some boxes register ordinary launcher activities only. */
    const val PHONE_APPS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"

    fun key(k: RemoteKey) = "input keyevent ${k.code}"
    fun launch(app: TvApp) = "am start -n ${Commands.component(app.component)}"
}

object RemoteParse {
    private val COMPONENT = Regex("^\\s*([a-zA-Z][\\w]*(?:\\.[\\w]+)+)/([\\w.$]+)\\s*$")

    /** `query-activities --brief` prints one "pkg/activity" per match among other lines. */
    fun apps(out: String): List<TvApp> = out.lineSequence()
        .mapNotNull { COMPONENT.find(it) }
        .map { m ->
            val pkg = m.groupValues[1]
            val act = m.groupValues[2]
            TvApp(pkg, "$pkg/" + if (act.startsWith(".")) act else act)
        }
        .distinctBy { it.pkg }
        .toList()
}

/**
 * A phone remote over the same connection TidyTV already has: works on any Android TV and on
 * Fire TV. Keys go through a kept-open shell when the connection allows it (a write per press),
 * else one command each. [TvConnectionLost] means the caller should reconnect.
 */
class Remote(private val tv: TvShell) : AutoCloseable {
    private var session: ShellSession? = null
    private var triedSession = false

    suspend fun press(key: RemoteKey) = send(RemoteCommands.key(key))

    /** Types [text] into whatever field has focus on the TV. False when nothing typable was left. */
    suspend fun type(text: String): Boolean {
        val cmd = TextInput.command(text) ?: return false
        send(cmd)
        return true
    }

    suspend fun apps(): List<TvApp> {
        val tvApps = RemoteParse.apps(tv.run(RemoteCommands.TV_APPS))
        return tvApps.ifEmpty { RemoteParse.apps(tv.run(RemoteCommands.PHONE_APPS)) }
    }

    suspend fun launch(app: TvApp) {
        tv.run(RemoteCommands.launch(app))
    }

    private suspend fun send(command: String) {
        if (!triedSession) {
            triedSession = true
            session = runCatching { tv.openSession() }.getOrNull()
        }
        val s = session
        if (s == null) {
            tv.run(command)
            return
        }
        try {
            s.send(command)
        } catch (e: TvConnectionLost) {
            runCatching { s.close() }
            session = null
            triedSession = false
            throw e
        }
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
    }
}
