package dev.tidytv.app

import dev.tidytv.app.data.Store
import dev.tidytv.app.data.Trace
import dev.tidytv.app.net.Backend
import dev.tidytv.app.net.DemoBackend
import dev.tidytv.app.net.Link
import dev.tidytv.core.flow.Attempt
import dev.tidytv.core.flow.ConnectEvent
import dev.tidytv.core.flow.ConnectState
import dev.tidytv.core.flow.FoundTv
import dev.tidytv.core.flow.Step
import dev.tidytv.core.flow.TvKind
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.lists.ListCatalog
import dev.tidytv.core.ops.PkgResult
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import dev.tidytv.core.ops.disabledByUs
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The whole flow through the view model, against the simulated TV. No Android, no network. */
@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dir: File

    private object NoNetwork : Backend {
        override fun scan() = emptyFlow<FoundTv>()
        override suspend fun connect(tv: FoundTv, onAwaitingAllow: () -> Unit) = Link(Attempt.UNREACHABLE)
        override suspend fun pair(tv: FoundTv, code: String) = Link(Attempt.FAILED)
        override suspend fun portOpen(host: String) = false
        override val saveWaitMs = 0L
    }

    private val catalog by lazy {
        val examples = File("../upstream/cleanyourtv/examples")
        ListCatalog(examples.listFiles()!!.filter { it.isDirectory }.associate { it.name to File(it, "debloat.txt").readText() })
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dir = Files.createTempDirectory("tidytv-vm").toFile()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val glassLook = dev.tidytv.core.ops.LookFiles("glass", """{"dock_apps": ["com.sample.app"], "wallpaper": true, "dock_glass": true}""", wallpaper = byteArrayOf(7, 7))
    private val photo = dev.tidytv.app.data.WallpaperImage(ByteArray(310_000), 1920, 1080, originalBytes = 4_200_000)

    private fun vm(demo: DemoBackend, lists: ListCatalog = catalog) =
        WizardViewModel(Store(dir), Trace(File(dir, "logs")), { NoNetwork }, demo, { lists }, { home -> home.pkg.toByteArray() },
            looks = { id -> if (id == "glass") glassLook else null }, photoWallpaper = { photo }, imageWork = dispatcher)

    /** Connects to the Demo TV, which lands on the My TV hub; [tidy] then opens Tidy up. */
    private fun TestScope.connectToDemo(vm: WizardViewModel, tidy: Boolean = true) {
        vm.ready()
        advanceTimeBy(4_000)
        val scan = vm.ui.value.connect as ConnectState.Scanning
        assertTrue(scan.found.contains(DemoBackend.DEMO))
        vm.connect(ConnectEvent.Pick(DemoBackend.DEMO))
        advanceUntilIdle()
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
        if (tidy) { vm.openTidy(); advanceUntilIdle() }
    }

    @Test fun cleanUpWithWifiDropThenHomeScreenThenRestore() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)

        var s = vm.ui.value
        assertEquals(Step.REVIEW, s.wizard.step)
        assertEquals(BackupStatus.Saved, s.review.backup)
        assertTrue(File(dir, "tvs").walk().any { it.name.startsWith("backup-") })
        val picked = s.review.candidates.filter { it.pkg in s.review.selected }.map { it.pkg }
        assertTrue("expected a few apps picked, got $picked", picked.size >= 3)

        // Wi-Fi drops right before the third app is turned off.
        tv.dropOnPrefix = "pm disable-user --user 0 ${picked[2]}"
        vm.startTidy()
        advanceUntilIdle()

        s = vm.ui.value
        assertEquals(Step.HOME_SCREEN, s.wizard.step)
        for (p in picked) {
            assertTrue(p, p in tv.disabled)
            assertEquals("$p shows as turned off", PkgResult.Disabled(p), s.tidy.results[p])
            assertEquals("$p disabled exactly once", 1, tv.commands.count { it == "pm disable-user --user 0 $p" })
        }

        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome()
        advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.SIMPLE), vm.ui.value.home)
        assertEquals(Launchers.TIDY_HOME_COMPONENT, tv.home())

        vm.homeContinue()
        advanceUntilIdle()
        assertEquals(Step.DONE, vm.ui.value.wizard.step)
        assertTrue((vm.ui.value.done.freedMb ?: 0) > 0)

        vm.restoreEverything()
        advanceUntilIdle()
        val r = vm.ui.value.done.restore as RestoreUi.Finished
        assertTrue(r.message, r.ok)
        assertTrue(picked.none { it in tv.disabled })
        assertEquals(Launchers.LAUNCHERX, tv.home()?.substringBefore('/'))
        assertTrue(Launchers.TIDY_HOME_PACKAGE !in tv.installed)
    }

    /** Every list the demo TV matches cut down to [keep], as for a TV whose list only finds one or two apps. */
    private suspend fun catalogWithShortList(keep: Set<String>): ListCatalog {
        val info = dev.tidytv.core.ops.TvReader.snapshot(DemoBackend().tv).info
        val ids = dev.tidytv.core.lists.ModelMatcher.match(info, catalog.lists, limit = Int.MAX_VALUE).map { it.list.id }.toSet()
        val examples = File("../upstream/cleanyourtv/examples")
        val trimmed = examples.listFiles()!!.filter { it.isDirectory }.associate { d ->
            val text = File(d, "debloat.txt").readText()
            d.name to if (d.name !in ids) text else text.lines().filter { l ->
                val t = l.trim()
                t.isEmpty() || t.startsWith("#") || t.substringBefore(' ').substringBefore('#') in keep
            }.joinToString("\n")
        }
        // The real lists now keep nearly every TCL app somewhere, so one other TV's list brings the extra apps.
        return ListCatalog(trimmed + ("zz-other-tv" to "# Other TV (device zzother)\ncom.zzother.bloat\ncom.zzother.junk\ncom.zzother.extra\n"))
    }

    @Test fun aFullListOffersNoMoreApps() = runTest(dispatcher) {
        val vm = vm(DemoBackend())
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        val r = vm.ui.value.review
        assertTrue(r.candidates.size >= ReviewState.FEW)
        assertFalse(r.fewFound)
        assertTrue(r.moreAvailable.isEmpty())
    }

    @Test fun aShortListOffersMoreAppsAllUnselected() = runTest(dispatcher) {
        // First the normal run, to learn which list the demo TV matches and what it offers.
        val first = vm(DemoBackend())
        first.choose(TvKind.ANDROID_TV)
        connectToDemo(first)
        val full = first.ui.value.review
        val keep = full.candidates.take(1).map { it.pkg }.toSet()

        val demo = DemoBackend()
        demo.tv.installed += listOf("com.zzother.bloat", "com.zzother.junk", "com.zzother.extra")
        val vm = vm(demo, catalogWithShortList(keep))
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        var r = vm.ui.value.review
        val own = r.candidates.map { it.pkg }.toSet()
        assertTrue("$own", own.size < ReviewState.FEW && own.containsAll(keep))
        assertTrue("a short list says so: noList=${r.noList} own=${r.candidates.size} more=${r.moreAvailable.size} extra=${r.extra.size} list=${r.listTitle} keep=$keep", r.fewFound)
        assertTrue(r.moreAvailable.isNotEmpty())
        assertTrue(r.extra.isEmpty())

        vm.showMoreApps()
        r = vm.ui.value.review
        assertFalse(r.fewFound)
        assertEquals(r.moreAvailable, r.extra)
        assertTrue("every extra app starts unselected", r.extra.none { it.pkg in r.selected })
        assertTrue(r.extra.none { it.pkg in own })
        assertTrue(r.extra.none { Launchers.isHomeApp(it.pkg) })
        assertTrue(r.extra.none { dev.tidytv.core.lists.Protected.isProtected(it.pkg) })

        // Picking one of them turns it off along with the list's own app.
        val extra = r.extra.first().pkg
        vm.toggleApp(extra)
        vm.startTidy()
        advanceUntilIdle()
        assertTrue(extra in demo.tv.disabled)
        assertTrue(own.filter { it in r.selected }.all { it in demo.tv.disabled })
        assertTrue("unpicked extras stay on", r.extra.drop(1).none { it.pkg in demo.tv.disabled })
    }

    @Test fun styledHomeScreenCanBeChosenAndRestored() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        val turnedOff = vm.ui.value.review.selected.size
        vm.startTidy()
        advanceUntilIdle()
        assertEquals(Step.HOME_SCREEN, vm.ui.value.wizard.step)
        val offer = vm.ui.value.home as HomeState.Offer
        assertEquals(dev.tidytv.core.lists.HomeApp.entries.toSet(), offer.available.toSet())
        assertEquals("the tested home screen is listed first", dev.tidytv.core.lists.HomeApp.PREMIUM, offer.available.first())
        assertEquals("a fresh offer preselects the navigation-checked 0TV Home", dev.tidytv.core.lists.HomeApp.PREMIUM, offer.app)

        vm.chooseHome(dev.tidytv.core.lists.HomeApp.STYLED)
        vm.installHome()
        advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.STYLED), vm.ui.value.home)
        assertEquals(dev.tidytv.core.lists.HomeApp.STYLED.component, tv.home())
        assertTrue(tv.pushed.keys.any { it.endsWith("com.gathin.tvhome/files/config.json") })

        vm.homeContinue()
        advanceUntilIdle()
        vm.restoreEverything()
        advanceUntilIdle()
        val first = vm.ui.value.done.restore as RestoreUi.Finished
        assertTrue(first.ok)
        assertEquals("the old launcher is not counted as an app",
            "Everything is back the way it was: $turnedOff apps on, old home screen back.", first.message)
        assertTrue(dev.tidytv.core.lists.HomeApp.STYLED.pkg !in tv.installed)
        vm.restoreEverything()
        advanceUntilIdle()
        assertEquals("Nothing to put back: every app is already on and your old home screen is set.", (vm.ui.value.done.restore as RestoreUi.Finished).message)
    }

    @Test fun appsTileTurnsOneAppOffAndRestoreBringsItBack() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openApps(); advanceUntilIdle()
        assertEquals(Step.APPS, vm.ui.value.wizard.step)
        val rows = vm.ui.value.apps.rows
        assertTrue("apps listed", rows.size > 5)
        val target = rows.first { !it.locked && it.tier != dev.tidytv.core.lists.Tier.YOURS && !it.keptByList }.pkg
        vm.changeApp(target, uninstall = false); advanceUntilIdle()
        assertTrue("$target is off", target in tv.disabled)
        assertTrue(vm.ui.value.apps.message!!.ok)
        assertTrue(File(dir, "tvs").walk().any { it.name.startsWith("backup-") })
        assertTrue("it left the list", vm.ui.value.apps.rows.none { it.pkg == target })
        vm.toHub(); advanceUntilIdle()
        vm.openRestore(); advanceUntilIdle()
        vm.restoreEverything(); advanceUntilIdle()
        assertTrue(target !in tv.disabled)
    }

    @Test fun appsTileUninstallsForTheTvAndRefusesLockedApps() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openApps(); advanceUntilIdle()
        val rows = vm.ui.value.apps.rows
        val locked = rows.first { it.locked }.pkg
        vm.changeApp(locked, uninstall = true); advanceUntilIdle()
        assertTrue("a locked app is never touched", locked in tv.installed && locked !in tv.disabled)
        val target = rows.first { !it.locked && it.tier != dev.tidytv.core.lists.Tier.YOURS && !it.keptByList }.pkg
        vm.changeApp(target, uninstall = true); advanceUntilIdle()
        assertTrue("$target is uninstalled for the TV's user", target !in tv.installed)
    }

    @Test fun connectNewSkipsGetReadyOnceTheTvHasBeenConnectedBefore() = runTest(dispatcher) {
        val demo = DemoBackend()
        val first = vm(demo)
        first.connectNew(); advanceUntilIdle()
        assertEquals("first time: the get-ready steps", Step.GET_READY, first.ui.value.wizard.step)
        first.ready(); advanceTimeBy(4_000)
        first.connect(ConnectEvent.Pick(DemoBackend.DEMO)); advanceUntilIdle()
        assertEquals(Step.HUB, first.ui.value.wizard.step)
        val again = vm(demo)
        again.connectNew(); advanceTimeBy(100)
        assertEquals("later: straight to the scan", Step.CONNECT, again.ui.value.wizard.step)
    }

    @Test fun blockAdsTurnsOnAndOffFromTheHubWithoutHanging() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openAds(); advanceUntilIdle()
        assertEquals(Step.ADS, vm.ui.value.wizard.step)
        assertTrue(!vm.ui.value.ads.loading)
        vm.blockAds(dev.tidytv.core.ops.AdBlockProvider.ADS); advanceUntilIdle()
        assertTrue(!vm.ui.value.ads.busy)
        assertEquals("dns.adguard-dns.com", tv.settings["global/private_dns_specifier"])
        assertEquals(dev.tidytv.core.ops.AdBlockProvider.ADS, vm.ui.value.ads.dns?.provider)
        vm.unblockAds(); advanceUntilIdle()
        assertTrue(!vm.ui.value.ads.busy)
        assertEquals(null, vm.ui.value.ads.dns?.provider)
    }

    @Test fun checkMyTvCanTurnTheAssistantBackOn() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openApps(); advanceUntilIdle()
        vm.changeApp("com.google.android.katniss", uninstall = false); advanceUntilIdle()
        vm.toHub(); advanceUntilIdle()
        vm.openCheck(); advanceUntilIdle()
        val off = vm.ui.value.check.report?.remoteOff
        if (off != null && off.isNotEmpty()) {
            vm.turnOnRemoteHelper(off.first()); advanceUntilIdle()
            assertTrue(!vm.ui.value.check.running)
            assertTrue(off.first() !in tv.disabled)
        }
    }

    @Test fun aSlowOrDroppedFirstReadAfterAllowIsRetriedInsteadOfFailing() = runTest(dispatcher) {
        val demo = DemoBackend()
        demo.tv.dropOnPrefix = "getprop" // the link drops on the first read, as some TVs do right after Allow
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
        assertEquals("no problem shown", null, vm.ui.value.problem)
        assertTrue(vm.ui.value.tv != null)
    }

    @Test fun aTvWithNoListStillGetsOtherTvsAppsUntickedAndAWayToSendItsDetails() = runTest(dispatcher) {
        val demo = DemoBackend()
        demo.tv.props["ro.product.model"] = "Zzyx Qwerty 9000"
        demo.tv.props["ro.product.brand"] = "Zzyx"
        demo.tv.props["ro.product.manufacturer"] = "Zzyx"
        demo.tv.props["ro.product.device"] = "zq9000"
        demo.tv.props["ro.product.name"] = "zq9000_stb"
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        val r = vm.ui.value.review
        assertTrue("no list matched", r.noList)
        assertEquals(BackupStatus.Saved, r.backup)
        assertTrue("other TVs' apps are offered", r.extra.isNotEmpty())
        assertTrue("none are ticked", r.selected.isEmpty())
        assertTrue(r.extra.none { dev.tidytv.core.lists.Protected.isProtected(it.pkg) || dev.tidytv.core.lists.Launchers.isHomeApp(it.pkg) })
        assertTrue(r.tvDetails.contains("Zzyx Qwerty 9000") && r.tvDetails.contains("Zzyx"))
        assertTrue("no serial or address in what we ask them to send", !r.tvDetails.contains("SERIAL", ignoreCase = true) && !r.tvDetails.contains("192.168"))
        // The owner can still pick one and turn it off, with the backup already saved.
        val pick = r.extra.first().pkg
        vm.toggleApp(pick); vm.startTidy(); advanceUntilIdle()
        assertTrue("$pick is off", pick in demo.tv.disabled)
    }

    @Test fun theHubShowsFreeStorageAndMemory() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        advanceUntilIdle()
        val f = vm.ui.value.hubFacts
        assertTrue("storage is read: $f", f.freeStorageMb != null && f.freeStorageMb!! > 0)
        assertTrue("memory is read: $f", f.freeRamMb != null && f.freeRamMb!! > 0)
    }

    @Test fun theChosenFolderIsUsedAndRemembered() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openFiles(); advanceUntilIdle()
        assertEquals(dev.tidytv.core.ops.SendFolder.DOWNLOAD, vm.ui.value.files.folder)
        vm.setSendFolder(dev.tidytv.core.ops.SendFolder.MOVIES)
        vm.sendFiles(listOf(PickedFile("clip.mp4", 3) { byteArrayOf(1, 2, 3).inputStream() })); advanceUntilIdle()
        assertTrue(tv.pushed.containsKey("/sdcard/Movies/TidyTV/clip.mp4"))
        vm.toHub(); advanceUntilIdle()
        val again = vm(demo)
        again.choose(TvKind.ANDROID_TV)
        connectToDemo(again, tidy = false)
        again.openFiles(); advanceUntilIdle()
        assertEquals("remembered for this TV", dev.tidytv.core.ops.SendFolder.MOVIES, again.ui.value.files.folder)
    }

    @Test fun aTvThatTurnsItsOwnHomeScreenBackOnAfterARestartCanBeFixedFromCheckMyTv() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv: FakeTv = demo.tv.also { it.strictPriority = false }
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.SIMPLE), vm.ui.value.home)
        // A restart: the firmware turns the stock launcher back on and Home goes back to it.
        val stock = dev.tidytv.core.lists.Launchers.LAUNCHERX
        tv.disabled.remove(stock)
        tv.setHomePreference("$stock/.home.HomeActivity")
        vm.toDone(); vm.toHub(); vm.openCheck(); advanceUntilIdle()
        val report = vm.ui.value.check.report!!
        assertTrue("home change is noticed", report.homeChanged)
        assertEquals(dev.tidytv.core.lists.HomeApp.SIMPLE, report.repairableHome)
        vm.repairHome(); advanceUntilIdle()
        assertTrue(vm.ui.value.check.message!!.ok)
        assertEquals(dev.tidytv.core.lists.HomeApp.SIMPLE.component, tv.home())
        assertTrue("the stock launcher is off again", stock in tv.disabled)
        assertTrue("checked again afterwards", vm.ui.value.check.report!!.repairableHome == null)
    }

    @Test fun oneAppCanBeTurnedBackOnByItself() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        val chosen = vm.ui.value.review.selected.toList()
        vm.startTidy()
        advanceUntilIdle()
        vm.skipHome()
        advanceUntilIdle()
        assertEquals(Step.DONE, vm.ui.value.wizard.step)
        val pkg = chosen.first()
        assertTrue(pkg in tv.disabled)

        vm.turnOn(pkg)
        advanceUntilIdle()
        assertTrue("that app is back on", pkg !in tv.disabled)
        assertTrue("the rest stay off", chosen.drop(1).all { it in tv.disabled })
        val note = vm.ui.value.done.turnOnNote!!
        assertTrue(note.message, note.ok && note.message.endsWith("is back on."))
        assertTrue(vm.ui.value.done.turningOn.isEmpty())
        assertTrue(pkg !in vm.ui.value.done.records.disabledByUs())

        vm.restoreEverything()
        advanceUntilIdle()
        assertTrue((vm.ui.value.done.restore as RestoreUi.Finished).ok)
        assertTrue(chosen.none { it in tv.disabled })
    }

    @Test fun styledHomeScreenIsHiddenBelowAndroid8() = runTest(dispatcher) {
        val demo = DemoBackend(dev.tidytv.core.testing.FakeTv.googleTv(sdk = 25))
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        vm.startTidy()
        advanceUntilIdle()
        val offer = vm.ui.value.home as HomeState.Offer
        assertEquals("styled needs Android 8; Projectivy runs from Android 5.1",
            listOf(dev.tidytv.core.lists.HomeApp.SIMPLE, dev.tidytv.core.lists.HomeApp.PROJECTIVY), offer.available)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.STYLED)
        assertEquals(dev.tidytv.core.lists.HomeApp.SIMPLE, (vm.ui.value.home as HomeState.Offer).app)
    }

    @Test fun restoreFromAFreshStartUsesThePhoneLog() = runTest(dispatcher) {
        val demo = DemoBackend()
        val first = vm(demo)
        first.choose(TvKind.ANDROID_TV)
        connectToDemo(first)
        first.startTidy()
        advanceUntilIdle()
        val off = demo.tv.disabled.toSet()
        assertTrue(off.isNotEmpty())

        // App restarted: new view model, same phone storage, same TV.
        val second = vm(demo)
        second.restoreTapped()
        connectToDemo(second, tidy = false)
        second.openRestore()
        advanceUntilIdle()
        assertEquals(Step.DONE, second.ui.value.wizard.step)
        assertTrue(second.ui.value.done.records.isNotEmpty())
        second.restoreEverything()
        advanceUntilIdle()
        assertTrue((second.ui.value.done.restore as RestoreUi.Finished).ok)
        assertTrue(demo.tv.disabled.isEmpty())
    }

    @Test fun nothingChangesBeforeTheBackupIsSaved() = runTest(dispatcher) {
        val demo = DemoBackend()
        demo.tv.settings.clear() // settings list still answers; break the backup instead:
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        vm.ready()
        advanceTimeBy(4_000)
        demo.tv.dropOnPrefix = "settings list"
        vm.connect(ConnectEvent.Pick(DemoBackend.DEMO))
        advanceUntilIdle()
        vm.openTidy()
        advanceUntilIdle()
        val s = vm.ui.value
        assertTrue(s.review.backup is BackupStatus.Failed)
        vm.startTidy()
        advanceUntilIdle()
        assertEquals(Step.REVIEW, vm.ui.value.wizard.step)
        assertTrue(demo.tv.commands.none { it.startsWith("pm disable-user") })
    }

    @Test fun backFromReviewReturnsToTheHubStillConnected() = runTest(dispatcher) {
        val vm = vm(DemoBackend())
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        assertTrue(vm.back())
        advanceTimeBy(100)
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
        assertTrue(vm.ui.value.tv != null)
        assertTrue("Back from the hub goes home", vm.back())
        advanceTimeBy(100)
        assertEquals(Step.WELCOME, vm.ui.value.wizard.step)
        assertEquals(null, vm.ui.value.tv)
    }

    @Test fun savedTvReconnectsInOneTapAndTheRemoteWorks() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.back(); advanceTimeBy(100)
        val saved = vm.ui.value.savedTvs.single()
        assertEquals(DemoBackend.DEMO.host, saved.host)

        vm.connectSaved(saved)
        advanceUntilIdle()
        assertEquals(Step.HUB, vm.ui.value.wizard.step)

        vm.openRemote(); advanceUntilIdle()
        assertTrue(vm.ui.value.remote.apps.any { it.pkg == "com.netflix.ninja" })
        vm.press(dev.tidytv.core.ops.RemoteKey.UP)
        vm.type("hi")
        vm.launchApp(vm.ui.value.remote.apps.first { it.pkg == "com.netflix.ninja" })
        advanceUntilIdle()
        assertEquals(listOf("KEYCODE_DPAD_UP"), demo.tv.keys)
        assertEquals(listOf("hi"), demo.tv.typed)
        assertEquals(listOf("com.netflix.ninja/.MainActivity"), demo.tv.launched)
        vm.back(); advanceTimeBy(100)
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
    }

    @Test fun filesAreSentOneByOneIntoTheTvFolder() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openFiles(); advanceUntilIdle()
        assertTrue(vm.ui.value.files.freeBytes!! > 0)
        val a = ByteArray(2000) { 1 }
        val b = ByteArray(10) { 2 }
        vm.sendFiles(listOf(PickedFile("photo.jpg", a.size.toLong()) { a.inputStream() }, PickedFile("photo.jpg", b.size.toLong()) { b.inputStream() }))
        advanceUntilIdle()
        val items = vm.ui.value.files.items
        assertEquals(listOf(SendStatus.SENT, SendStatus.SENT), items.map { it.status })
        assertEquals(listOf("photo.jpg", "photo (2).jpg"), items.map { it.savedAs })
        assertTrue(a.contentEquals(demo.tv.pushed["/sdcard/Download/TidyTV/photo.jpg"]))
    }

    /** Tidies the Demo TV and returns to the hub; the apps turned off. */
    private fun TestScope.tidyThenHub(vm: WizardViewModel): List<String> {
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        val chosen = vm.ui.value.review.selected.toList()
        vm.startTidy(); advanceUntilIdle()
        vm.skipHome(); advanceUntilIdle()
        vm.toHub(); advanceTimeBy(100)
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
        return chosen
    }

    @Test fun checkFindsAppsAnUpdateTurnedBackOnAndTidiesThemAgain() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        val chosen = tidyThenHub(vm)
        vm.openCheck(); advanceUntilIdle()
        assertEquals(Step.CHECK, vm.ui.value.wizard.step)
        assertTrue("fresh tidy is all good", vm.ui.value.check.report!!.allGood)

        val back = chosen.first()
        demo.tv.disabled -= back // a firmware update switched it on
        vm.recheck(); advanceUntilIdle()
        assertEquals(listOf(back), vm.ui.value.check.report!!.backOn)

        vm.retidy(); advanceUntilIdle()
        assertTrue(back in demo.tv.disabled)
        assertTrue(vm.ui.value.check.message!!.ok)
        assertTrue(vm.ui.value.check.report!!.allGood)
    }

    @Test fun rebootTestReconnectsAndReportsWhatCameBack() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        val chosen = tidyThenHub(vm)
        vm.openCheck(); advanceUntilIdle()
        demo.tv.reEnableOnReboot = mutableSetOf(chosen.first())
        vm.rebootTest(); advanceUntilIdle()
        assertEquals(1, demo.tv.reboots)
        assertEquals(null, vm.ui.value.check.rebooting)
        assertEquals(Step.CHECK, vm.ui.value.wizard.step)
        assertEquals(listOf(chosen.first()), vm.ui.value.check.report!!.backOn)
    }

    @Test fun rescueTurnsEverythingBackOnWhenTheTvAnswers() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        val chosen = tidyThenHub(vm)
        vm.back(); advanceTimeBy(100)
        assertEquals(Step.WELCOME, vm.ui.value.wizard.step)
        val saved = vm.ui.value.savedTvs.single()

        vm.startRescue(saved); advanceUntilIdle()
        assertEquals(Step.RESCUE, vm.ui.value.wizard.step)
        val r = vm.ui.value.rescue
        assertTrue(r.result!!.message, r.result!!.ok)
        assertTrue(chosen.none { it in demo.tv.disabled })
        vm.stopRescue(); advanceTimeBy(100)
        assertEquals(Step.WELCOME, vm.ui.value.wizard.step)
    }

    @Test fun projectivyComesFromTheTvsPlayStoreAndStaysAfterRestore() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        tv.playStoreInstalls = false
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        vm.startTidy(); advanceUntilIdle()
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.PROJECTIVY)
        vm.installHome()
        advanceTimeBy(1_000)
        assertEquals(HomeState.WaitingForPlayStore(dev.tidytv.core.lists.HomeApp.PROJECTIVY), vm.ui.value.home)
        assertEquals(listOf(dev.tidytv.core.lists.HomeApp.PROJECTIVY.pkg), tv.playStoreOpened)

        tv.installProjectivy() // the owner pressed Install
        advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.PROJECTIVY), vm.ui.value.home)
        assertTrue(tv.home()!!.startsWith(dev.tidytv.core.lists.HomeApp.PROJECTIVY.pkg))

        vm.homeContinue(); advanceUntilIdle()
        vm.restoreEverything(); advanceUntilIdle()
        assertTrue((vm.ui.value.done.restore as RestoreUi.Finished).ok)
        assertTrue("we never uninstall what we didn't install", dev.tidytv.core.lists.HomeApp.PROJECTIVY.pkg in tv.installed)
        assertTrue(!tv.home()!!.startsWith(dev.tidytv.core.lists.HomeApp.PROJECTIVY.pkg))
    }

    @Test fun looksAndYourOwnPhotoGoOnTheAppleTvStyleHomeScreen() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        vm.startTidy(); advanceUntilIdle()
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.STYLED)
        vm.installHome(); advanceUntilIdle()
        val installed = vm.ui.value.home
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.STYLED), installed)
        val dir = "/sdcard/Android/data/com.gathin.tvhome/files"
        val dock = String(tv.pushed["$dir/config.json"]!!)
        val restartsAtInstall = tv.lookRestarts // the handover restarts the launcher once to read the dock

        vm.openLook()
        vm.applyLook(LookId.GLASS); advanceUntilIdle()
        var look = (vm.ui.value.home as HomeState.Look).look
        assertTrue(look.message!!.message, look.message!!.ok)
        assertEquals("glass", look.current)
        val config = String(tv.pushed["$dir/config.json"]!!)
        assertTrue(config, config.contains("dock_glass") && !config.contains("com.sample.app"))
        assertTrue("the owner's dock stays", dock.contains("com.netflix.ninja") && config.contains("com.netflix.ninja"))
        assertEquals(restartsAtInstall + 1, tv.lookRestarts)

        vm.applyPhoto(PickedFile("IMG_2041.jpg", 4_200_000) { java.io.ByteArrayInputStream(ByteArray(0)) }); advanceUntilIdle()
        look = (vm.ui.value.home as HomeState.Look).look
        assertEquals("photo", look.current)
        assertEquals(310_000, tv.pushed["$dir/wallpaper.jpg"]!!.size)
        assertEquals("Your photo is on your TV. Sent at 1920×1080, 303 KB, from 4.0 MB.", look.message!!.message)

        vm.applyLook(LookId.CLASSIC); advanceUntilIdle()
        assertTrue("Classic takes the wallpaper away", tv.pushed["$dir/wallpaper.jpg"] == null)

        assertTrue(vm.back())
        assertEquals("Back leaves the look picker, not the step", installed, vm.ui.value.home)
        assertEquals(Step.HOME_SCREEN, vm.ui.value.wizard.step)

        vm.homeContinue(); advanceUntilIdle()
        vm.toHub(); advanceTimeBy(100)
        vm.openHomeScreen(); advanceUntilIdle()
        assertTrue("the hub offers the look when that home screen is on", (vm.ui.value.home as HomeState.Offer).styledIsHome)
    }

    @Test fun allAppsCanUninstallForTheTvAndRestoreBringsThemBack() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        tv.installed += "com.stremio.one"; tv.userApps += "com.stremio.one"
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        vm.openAllApps(); advanceUntilIdle()
        val all = vm.ui.value.review.all!!
        assertEquals("every app TidyTV can name, each once", (tv.installed - tv.disabled).filter(dev.tidytv.core.lists.PackageName::isValid).size, all.rows.size)
        assertTrue(!all.loading)
        assertEquals(151, all.facts["com.google.android.apps.tv.launcherx"]!!.ramMb)
        val tiers = all.rows.groupBy { it.tier }
        assertTrue(tiers.keys.containsAll(listOf(dev.tidytv.core.lists.Tier.RECOMMENDED, dev.tidytv.core.lists.Tier.LOCKED, dev.tidytv.core.lists.Tier.YOURS)))

        val picked = vm.ui.value.review.selected
        assertTrue(picked.isNotEmpty())
        vm.toggleApp("com.stremio.one")
        vm.setUninstall(true)
        vm.startTidy(); advanceUntilIdle()
        assertEquals("apps you installed are confirmed first", listOf("com.stremio.one"), vm.ui.value.review.confirmPermanent)
        assertEquals(Step.REVIEW, vm.ui.value.wizard.step)
        vm.confirmPermanent(); advanceUntilIdle()
        assertTrue(picked.all { it !in tv.installed })
        assertTrue("com.stremio.one" !in tv.installed && "com.stremio.one" !in tv.removedForUser)
        assertEquals(picked.size + 1, vm.ui.value.tidy.disabledCount)

        vm.skipHome(); advanceUntilIdle()
        vm.restoreEverything(); advanceUntilIdle()
        val back = vm.ui.value.done.restore as RestoreUi.Finished
        assertTrue(back.message, back.ok)
        assertTrue("preinstalled apps are back", picked.all { it in tv.installed })
        assertTrue(back.message, back.message.contains("Google Play"))
    }

    @Test fun freeUpSpaceClearsEveryCache() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openSpace(); advanceUntilIdle()
        val space = vm.ui.value.space
        assertEquals(Step.SPACE, vm.ui.value.wizard.step)
        assertEquals(269, space.storage!!.cacheMb)
        assertEquals("com.google.android.apps.tv.launcherx" to 167, space.topCache.first())
        vm.clearCaches(); advanceUntilIdle()
        assertEquals(1, demo.tv.cachesTrimmed)
        assertEquals("Freed 269 MB on your TV.", vm.ui.value.space.message!!.message)
        assertEquals(0, vm.ui.value.space.storage!!.cacheMb)
        assertTrue(vm.back()); advanceTimeBy(100)
        assertEquals(Step.HUB, vm.ui.value.wizard.step)
    }

    // ---------- The Home button really goes to the new home screen (the owner's TCL G06) ----------

    private fun TestScope.tidyThenOfferHome(vm: WizardViewModel) {
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm)
        vm.startTidy(); advanceUntilIdle()
        assertEquals(Step.HOME_SCREEN, vm.ui.value.wizard.step)
    }

    @Test fun useSimpleMakesTheHomeButtonOpenTheNewHomeScreenInOneStep() = runTest(dispatcher) {
        val demo = DemoBackend()
        val tv = demo.tv
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.SIMPLE), vm.ui.value.home)
        // What the owner does next: leave to Settings and press Home on the remote.
        tv.run(dev.tidytv.core.ops.Commands.OPEN_SETTINGS)
        vm.homeContinue(); advanceUntilIdle()
        vm.toHub(); advanceTimeBy(100)
        vm.openRemote(); advanceUntilIdle()
        vm.press(dev.tidytv.core.ops.RemoteKey.HOME); advanceUntilIdle()
        assertEquals("the remote's Home key opens the new home screen, not Google TV", dev.tidytv.core.lists.HomeApp.SIMPLE.pkg, tv.focused)
        assertTrue(dev.tidytv.core.lists.Launchers.SETUPWRAITH in tv.disabled && dev.tidytv.core.lists.Launchers.LAUNCHERX in tv.disabled)
    }

    @Test fun whenTheSetupAppOwnsTheWifiTheOwnerIsToldAndCanSayTheTvIsOnACable() = runTest(dispatcher) {
        val demo = DemoBackend().apply { tv.wifiOwner = "com.google.android.tungsten.setupwraith:10130" }
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); advanceUntilIdle()
        val problem = vm.ui.value.home as HomeState.Problem
        assertEquals(HomeAction.TURN_OFF_SETUP_APP, problem.action)
        assertTrue(problem.message, problem.message.contains("Wi-Fi") && problem.message.contains("old home screen stays"))
        assertTrue("it says how to fix it", problem.message.contains("Forget") && problem.message.contains("join it again"))
        assertTrue("nothing was turned off", demo.tv.disabled.none { it.contains("launcherx") || it.contains("setupwraith") })

        // The owner forgets the Wi-Fi on the TV and joins again: the network now belongs to the system.
        demo.tv.wifiOwner = "com.android.settings:1000"
        vm.homeAction(HomeAction.TURN_OFF_SETUP_APP, onEthernet = false); advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.SIMPLE), vm.ui.value.home)
    }

    @Test fun theCableOverrideStillWorksWhenTheWifiIsOwnedBySetup() = runTest(dispatcher) {
        val demo = DemoBackend().apply { tv.wifiOwner = "com.google.android.tungsten.setupwraith:10130" }
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); advanceUntilIdle()

        vm.homeAction(HomeAction.TURN_OFF_SETUP_APP, onEthernet = true); advanceUntilIdle()
        assertEquals(HomeState.Installed(dev.tidytv.core.lists.HomeApp.SIMPLE), vm.ui.value.home)
    }

    @Test fun doubleTappingUseSimpleRunsTheHandoverOnce() = runTest(dispatcher) {
        val demo = DemoBackend()
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); vm.installHome(); vm.installHome(); advanceUntilIdle()
        assertEquals(1, demo.tv.commands.count { it == dev.tidytv.core.ops.Commands.INSTALL_TMP })
    }

    @Test fun anotherHomeScreenAppGetsAnHonestMessageAndNothingChanges() = runTest(dispatcher) {
        val demo = DemoBackend().apply { tv.installProjectivy() }
        val vm = vm(demo)
        tidyThenOfferHome(vm)
        vm.chooseHome(dev.tidytv.core.lists.HomeApp.SIMPLE); vm.installHome(); advanceUntilIdle()
        val problem = vm.ui.value.home as HomeState.Problem
        assertTrue(problem.message, problem.message.contains("another home screen app"))
        assertTrue(demo.tv.disabled.none { it.contains("launcherx") || it.contains("setupwraith") })
    }

    // ---------- Free up space tells the truth ----------

    @Test fun freeUpSpaceShowsLiveProgressAndZeroCachesAfterwardsEvenWhenAndroidStillReportsTheOldNumber() = runTest(dispatcher) {
        val demo = DemoBackend().apply { tv.trimMbPerPoll = 100 }
        val vm = vm(demo)
        vm.choose(TvKind.ANDROID_TV)
        connectToDemo(vm, tidy = false)
        vm.openSpace(); advanceUntilIdle()
        val before = vm.ui.value.space.storage!!
        assertEquals(269, before.cacheMb)
        assertTrue(vm.ui.value.space.estimated)
        vm.clearCaches()
        advanceTimeBy(2_000)
        assertTrue("running, with progress from live free space", vm.ui.value.space.clearing && vm.ui.value.space.progressMb in 1..269)
        assertEquals(before.freeMb!! + vm.ui.value.space.progressMb, vm.ui.value.space.storage!!.freeMb)
        advanceUntilIdle()
        val after = vm.ui.value.space
        assertEquals("Freed 269 MB on your TV.", after.message!!.message)
        assertTrue(!after.clearing && !after.estimated)
        assertEquals("the screen shows zero, though Android's diskstats still says 269", 0, after.storage!!.cacheMb)
        assertTrue(after.topCache.isEmpty())
        assertEquals(before.freeMb!! + 269, after.storage.freeMb)
    }
}
