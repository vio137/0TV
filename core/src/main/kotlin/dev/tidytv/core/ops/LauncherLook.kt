package dev.tidytv.core.ops

import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.PackageName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A look for the Apple TV style home screen (cleanyourtv's launcher): its themes/ folders, or the
 * owner's own photo. [config] is the theme's config.json; [wallpapers] is a rotating collection.
 */
class LookFiles(
    val id: String,
    val config: String,
    val wallpaper: ByteArray? = null,
    val wallpapers: List<Pair<String, ByteArray>> = emptyList(),
    val captions: ByteArray? = null,
) {
    init {
        require(NAME.matches(id))
        wallpapers.forEach { require(NAME.matches(it.first)) { "unsupported wallpaper name" } }
    }

    companion object {
        val NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$")
    }
}

/**
 * Puts a look on the Apple TV style home screen, the way upstream tools/theme.sh does: every file is
 * staged in /data/local/tmp, then written through the launcher's ThemeProvider so the launcher owns
 * it (on Android 14 a file the shell creates in Android/data can't be read by the app), with cp as
 * the fallback for launchers or Android versions without `content write`. The owner's dock is kept.
 * Nothing here changes which apps are on or off, so it isn't logged for Restore; uninstalling the
 * home screen removes its files.
 */
class LauncherLook(
    private val tv: TvShell,
    /** The dock launcher whose theme folder is written: STYLED or PREMIUM. */
    home: HomeApp = HomeApp.STYLED,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pkg = home.pkg

    /** True when the look is on the TV and Home was restarted with it. */
    suspend fun apply(look: LookFiles): Boolean {
        if (!Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok")) return false
        val dock = currentDock()
        put("config.json", merged(look.config, dock).toByteArray())
        if (look.wallpaper != null) put("wallpaper.jpg", look.wallpaper) else tv.run(Commands.lookDelete(pkg, "wallpaper.jpg"))
        tv.run(Commands.lookClearWallpapers(pkg))
        for ((name, bytes) in look.wallpapers) put("wallpapers/$name", bytes)
        if (look.captions != null) put("wallpapers/captions.txt", look.captions) else tv.run(Commands.lookDelete(pkg, "wallpapers/captions.txt"))
        put("theme_revision", clock().toString().toByteArray())
        return Parse.lines(tv.run(Commands.restartLook(pkg))).contains("restarted")
    }

    /** Writes the dock alone, as the handover does after installing the home screen. */
    suspend fun writeDock(dockApps: List<String>) {
        if (!Parse.lines(tv.run(Commands.makeConfigDir(pkg))).contains("ok")) return
        put("config.json", Commands.dockConfig(dockApps).toByteArray())
        put("theme_revision", clock().toString().toByteArray())
    }

    private suspend fun put(name: String, bytes: ByteArray) {
        tv.push(bytes, Commands.lookStage(name))
        tv.run(Commands.lookPut(pkg, name))
    }

    /** The dock apps in the config.json on the TV now, so a new look keeps the owner's choice. */
    private suspend fun currentDock(): List<String> = runCatching {
        val text = tv.run(Commands.lookReadConfig(pkg))
        Json.parseToJsonElement(text.substring(text.indexOf('{'))).jsonObject["dock_apps"]!!.jsonArray
            .map { it.jsonPrimitive.content }.filter(PackageName::isValid)
    }.getOrDefault(emptyList())

    companion object {
        /** The theme's settings with the owner's dock in place of the theme's sample dock. */
        fun merged(config: String, dock: List<String>): String {
            val theme = runCatching { Json.parseToJsonElement(config).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            val out = theme.toMutableMap()
            if (dock.isNotEmpty()) out["dock_apps"] = JsonArray(dock.map { JsonPrimitive(it) })
            else out.remove("dock_apps")
            // Editing stays on unless the theme itself says otherwise.
            for (k in Commands.EDITABLE_HOME) out.putIfAbsent(k, JsonPrimitive(true))
            return JsonObject(out).toString() + "\n"
        }
    }
}
