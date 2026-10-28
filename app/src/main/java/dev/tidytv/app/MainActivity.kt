package dev.tidytv.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import dev.tidytv.app.data.ShareCard
import dev.tidytv.app.data.Store
import dev.tidytv.app.data.caption
import dev.tidytv.app.ui.Host
import dev.tidytv.app.data.Trace
import dev.tidytv.app.net.AdbBackend
import dev.tidytv.app.net.DemoBackend
import dev.tidytv.app.net.Discovery
import dev.tidytv.app.ui.TidyApp
import dev.tidytv.app.ui.TidyTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: WizardViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application
                val store = Store(app.filesDir)
                val trace = Trace(store.logsDir)
                val real by lazy { AdbBackend(Discovery(app, trace::line), store.keyFile, trace::line) }
                WizardViewModel(
                    store = store,
                    trace = trace,
                    real = { real },
                    demo = if (BuildConfig.DEBUG) DemoBackend(phoneApk = { pkg ->
                        runCatching { java.io.File(packageManager.getApplicationInfo(pkg, 0).sourceDir) }.getOrNull()
                    }) else null,
                    lists = { Store.loadLists(app.assets) },
                    launcherApk = { home -> runCatching { app.assets.open("launcher/${home.asset}").use { it.readBytes() } }.getOrNull() },
                    iconFromApk = dev.tidytv.app.data.ApkIcons(app, store)::iconFromApk,
                    labelFromApk = dev.tidytv.app.data.ApkIcons(app, store)::labelFromApk,
                    looks = { id -> dev.tidytv.app.data.Looks.load(app.assets, id) },
                    photoWallpaper = { f -> dev.tidytv.app.data.Wallpaper.fromPhoto(f.size) { f.open() } },
                    apkIdentity = { f -> dev.tidytv.app.data.CobaltApk.identity(app, f) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!vm.back()) finish() }
        })
        setContent {
            TidyTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                TidyApp(state, vm, ::shareLogs, host)
            }
        }
    }

    /** While the remote is open, the phone's volume buttons turn the TV's volume. */
    @android.annotation.SuppressLint("RestrictedApi") // lint false positive: javap of androidx.core 1.15.0 and 1.16.0 shows ComponentActivity.dispatchKeyEvent is public with no RestrictTo (only putExtraData/getExtraData/superDispatchKeyEvent/dispatchKeyShortcutEvent are restricted)
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val up = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) && vm.ui.value.wizard.step == dev.tidytv.core.flow.Step.REMOTE) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) vm.press(if (up) dev.tidytv.core.ops.RemoteKey.VOLUME_UP else dev.tidytv.core.ops.RemoteKey.VOLUME_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Lazy: filesDir is null until the activity's context is attached, so it can't be read in an initializer.
    private val host by lazy { Host(
        // Feedback never leaves the phone by itself: it is written to a file and handed to the system share sheet.
        sendFeedback = { draft, includeLog ->
            val log = if (includeLog) runCatching { vm.exportLogs(File(cacheDir, "share"), BuildConfig.VERSION_NAME).readText() }.getOrNull() else null
            runCatching {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "feedback.txt")
                file.writeText(buildString {
                    append(draft.message.trim())
                    if (!log.isNullOrBlank()) append("\n\n--- log ---\n").append(log)
                })
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share feedback"))
                true
            }.getOrDefault(false)
        },
        renderCard = { ShareCard(this, vm.ui.value.icons).render(it) },
        shareCard = { stats ->
            val file = ShareCard(this, vm.ui.value.icons).save(stats, File(cacheDir, "share"))
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, stats.caption())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share your 0TV card"))
        },
        openUrl = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
    ) }

    private fun shareLogs() {
        val file = vm.exportLogs(File(cacheDir, "share"), BuildConfig.VERSION_NAME)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "0TV log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share logs"))
    }
}
