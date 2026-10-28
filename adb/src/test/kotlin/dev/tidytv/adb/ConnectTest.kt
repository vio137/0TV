package dev.tidytv.adb

import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.ops.DebloatOutcome
import dev.tidytv.core.ops.Debloater
import dev.tidytv.core.ops.HandoverOutcome
import dev.tidytv.core.ops.LauncherHandover
import dev.tidytv.core.ops.MemoryActionLog
import dev.tidytv.core.ops.RestoreOutcome
import dev.tidytv.core.ops.Restorer
import dev.tidytv.core.ops.TvReader
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

/** The real Kadb client against [FakeAdbd] on localhost: everything but the TV is real. */
class ConnectTest {
    companion object {
        private lateinit var keyDir: File

        @BeforeClass @JvmStatic fun identity() {
            keyDir = Files.createTempDirectory("tidytv-key").toFile()
            AdbIdentity.use(File(keyDir, "adbkey.pem"))
        }
    }

    private lateinit var tv: FakeTv
    private lateinit var adbd: FakeAdbd
    private val opened = mutableListOf<KadbTvShell>()

    @Before fun setUp() {
        tv = FakeTv.googleTv(sdk = 34)
        adbd = FakeAdbd(tv)
    }

    @After fun tearDown() {
        opened.forEach { it.close() }
        adbd.close()
    }

    private fun connector(hintMs: Long = 300, timeoutMs: Long = 20_000) =
        TvConnector(allowHintMs = hintMs, allowTimeoutMs = timeoutMs)

    private fun connect(c: TvConnector = connector(), onHint: () -> Unit = {}): ConnectResult = runBlocking {
        c.connect("127.0.0.1", adbd.port, onHint).also { if (it is ConnectResult.Connected) opened += it.shell }
    }

    @Test fun firstConnectShowsPressAllowThenConnects() {
        adbd.promptDelayMs = 1_200
        var hinted = false
        val r = connect { hinted = true }
        assertTrue(r.toString(), r is ConnectResult.Connected)
        assertTrue("UI was told to show Press Allow", hinted)
        assertEquals(1, adbd.prompts.get())
    }

    @Test fun savedKeyConnectsWithoutAskingAgain() {
        assertTrue(connect() is ConnectResult.Connected)
        opened.forEach { it.close() }
        var hinted = false
        val second = connect { hinted = true }
        assertTrue(second is ConnectResult.Connected)
        assertEquals("the TV asked only once", 1, adbd.prompts.get())
        assertFalse(hinted)
    }

    @Test fun denyIsReportedAsDenied() {
        adbd.user = FakeAdbd.User.DENY
        adbd.promptDelayMs = 800
        assertEquals(ConnectResult.Denied, connect())
    }

    @Test fun nobodyPressingAllowTimesOut() {
        adbd.user = FakeAdbd.User.IGNORE
        assertEquals(ConnectResult.AllowTimedOut, connect(connector(hintMs = 200, timeoutMs = 1_500)))
    }

    @Test fun closedPortIsPortClosed() {
        val free = ServerSocket(0).use { it.localPort }
        val r = runBlocking { connector().connect("127.0.0.1", free) }
        assertEquals(ConnectResult.PortClosed, r)
    }

    @Test fun silentHostIsUnreachable() {
        val c = TvConnector(prober = { _, _, _ -> PortProber.Result.NO_ANSWER })
        assertEquals(ConnectResult.Unreachable, runBlocking { c.connect("10.0.0.9") })
    }

    @Test fun pairingCodeMustHaveSixDigits() {
        val r = runBlocking { connector().pairAndConnect("127.0.0.1", 1, "12 34", 2) }
        assertTrue(r is ConnectResult.Failed)
    }

    @Test fun snapshotOverTheWire() {
        val shell = (connect() as ConnectResult.Connected).shell
        val snap = runBlocking { TvReader.snapshot(shell) }
        assertEquals("TCLG05TEST01", snap.info.serial)
        assertTrue("com.tcl.ttvs" in snap.installed)
    }

    @Test fun cleanUpHandoverAndRestoreOverTheWire() = runBlocking {
        val shell = (connect() as ConnectResult.Connected).shell
        val log = MemoryActionLog()
        val bloat = listOf("com.tcl.tv.plus", "com.tcl.ttvs", "com.tcl.guard", "com.google.android.feedback")

        val clean = Debloater(shell, log, sleep = {}).run(bloat)
        assertTrue(clean is DebloatOutcome.Finished)
        assertTrue(tv.disabled.containsAll(bloat))

        val apk = File("../app/src/main/assets/launcher/tvhome.apk").takeIf { it.exists() }?.readBytes() ?: ByteArray(37_287) { it.toByte() }
        val handover = LauncherHandover(shell, log, sleep = {}).run(apk, listOf("com.netflix.ninja"), sdk = 34)
        assertTrue(handover.toString(), handover is HandoverOutcome.Done)
        assertTrue("APK arrived intact over sync", apk.contentEquals(tv.pushed.values.single()))
        assertEquals(Launchers.TIDY_HOME_COMPONENT, tv.home())

        val restore = Restorer(shell, log).run()
        assertTrue(restore is RestoreOutcome.Done)
        assertTrue(tv.disabled.isEmpty())
        assertEquals(Launchers.LAUNCHERX + "/.MainActivity", tv.home())
    }

    @Test fun wifiDropMidCleanUpThenReconnectAndResume() = runBlocking {
        val shell = (connect() as ConnectResult.Connected).shell
        val log = MemoryActionLog()
        val bloat = listOf("com.tcl.tv.plus", "com.tcl.ttvs", "com.tcl.guard", "com.google.android.feedback", "com.tcl.messagebox")
        adbd.dropAfterCommands = adbd.shellCommands.size + 6

        val first = Debloater(shell, log, sleep = {}).run(bloat)
        assertTrue(first.toString(), first is DebloatOutcome.Disconnected)

        val again = (connect() as ConnectResult.Connected).shell
        val second = Debloater(again, log, sleep = {}).run(bloat)
        assertTrue(second is DebloatOutcome.Finished)
        assertTrue(tv.disabled.containsAll(bloat))
        assertEquals(1, adbd.prompts.get())
    }

    @Test fun scannerFindsOpenPortsOnly() = runBlocking {
        val open = setOf("192.168.1.5", "192.168.1.77")
        val s = SubnetScanner(prober = { h, _, _ -> if (h in open) PortProber.Result.OPEN else PortProber.Result.NO_ANSWER })
        assertEquals(open, s.scan("192.168.1.20").toList().toSet())
    }

    @Test fun scannerOnlyScansPrivateSlash24() {
        assertEquals(253, SubnetScanner.hostsOf("192.168.1.20").size)
        assertFalse("192.168.1.20" in SubnetScanner.hostsOf("192.168.1.20"))
        assertEquals(253, SubnetScanner.hostsOf("10.0.0.2").size)
        assertEquals(253, SubnetScanner.hostsOf("172.20.5.9").size)
        assertTrue(SubnetScanner.hostsOf("8.8.8.8").isEmpty())
        assertTrue(SubnetScanner.hostsOf("192.168.1.300").isEmpty())
        assertTrue(SubnetScanner.hostsOf("fe80::1").isEmpty())
        assertTrue(SubnetScanner.isValidHost(" 192.168.0.10 "))
        assertFalse(SubnetScanner.isValidHost("192.168.0"))
    }

    @Test fun remoteKeysGoThroughOneKeptOpenShell() = runBlocking {
        val shell = (connect() as ConnectResult.Connected).shell
        val remote = dev.tidytv.core.ops.Remote(shell)
        remote.press(dev.tidytv.core.ops.RemoteKey.UP)
        remote.press(dev.tidytv.core.ops.RemoteKey.OK)
        remote.type("hello world")
        // Lines are written to the kept-open stream; give the fake a moment to run them.
        repeat(50) { if (tv.typed.isNotEmpty()) return@repeat; Thread.sleep(20) }
        assertEquals(1, adbd.sessionsOpened.get())
        assertEquals(listOf("KEYCODE_DPAD_UP", "KEYCODE_DPAD_CENTER"), tv.keys)
        assertEquals(listOf("hello world"), tv.typed)
        remote.close()
    }

    @Test fun bigFilesStreamWithProgress() = runBlocking {
        val shell = (connect() as ConnectResult.Connected).shell
        val data = ByteArray(3 * 1024 * 1024 + 7) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        shell.pushStream(data.inputStream(), data.size.toLong(), "/sdcard/Download/TidyTV/movie.mp4") { progress += it }
        assertTrue("file arrived intact", data.contentEquals(tv.pushed["/sdcard/Download/TidyTV/movie.mp4"]))
        assertTrue("several progress steps", progress.size > 3)
        assertEquals(data.size.toLong(), progress.last())
    }
}
