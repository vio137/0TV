package dev.tidytv.core.flow

import dev.tidytv.core.flow.ConnectEvent as E
import dev.tidytv.core.flow.ConnectState as S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectMachineTest {
    private val tv = FoundTv("192.168.1.42", name = "Living room TV", model = "TCL 55C735", legacyOpen = true)
    private fun r(s: S, vararg es: E): S = es.fold(s) { acc, e -> ConnectMachine.reduce(acc, e) }

    @Test fun scanThenTapThenConnected() {
        val s = r(S.Scanning(), E.Found(tv), E.ScanDone, E.Pick(tv), E.Result(Attempt.CONNECTED))
        assertEquals(S.Connected(tv), s)
    }

    @Test fun waitingForAllowShowsPromptThenAutoAdvances() {
        val s = r(S.Connecting(tv), E.AwaitingAllow)
        assertEquals(S.PressAllow(tv, canRetry = false), s)
        assertEquals(S.Connected(tv), r(s, E.Result(Attempt.CONNECTED)))
    }

    @Test fun retryOnlyAfterThirtySeconds() {
        val s = r(S.Connecting(tv), E.AwaitingAllow, E.AllowRetryAvailable)
        assertEquals(S.PressAllow(tv, canRetry = true), s)
        assertEquals(S.Connecting(tv), r(s, E.Retry))
    }

    @Test fun allowTimeoutOffersRetry() {
        assertEquals(S.PressAllow(tv, canRetry = true), r(S.Connecting(tv), E.AwaitingAllow, E.Result(Attempt.ALLOW_TIMED_OUT)))
    }

    @Test fun refusedWithWirelessDebuggingGoesToPairing() {
        val t = tv.copy(legacyOpen = false, tlsPort = 37099)
        assertEquals(S.Pairing(t), r(S.Connecting(t), E.Result(Attempt.PORT_CLOSED)))
    }

    @Test fun refusedWithoutWirelessDebuggingShowsHelp() {
        assertEquals(S.TurnOnDebugging(tv), r(S.Connecting(tv), E.Result(Attempt.PORT_CLOSED)))
    }

    @Test fun wirelessDebuggingAppearingWhileOnHelpMovesToPairing() {
        val s = r(S.TurnOnDebugging(tv), E.Found(FoundTv(tv.host, tlsPort = 41000)))
        assertTrue(s is S.Pairing && s.tv.tlsPort == 41000)
    }

    @Test fun nothingFoundGoesToTypeTheIp() {
        assertEquals(S.NothingFound(), r(S.Scanning(), E.ScanDone))
    }

    @Test fun typedIpConnectsAndBadIpIsRefused() {
        assertEquals(S.Connecting(FoundTv("192.168.1.9")), r(S.NothingFound(), E.TypedIp(" 192.168.1.9 ")))
        assertEquals(S.NothingFound(badIp = true), r(S.NothingFound(), E.TypedIp("192.168.1.300")))
        assertEquals(S.NothingFound(badIp = true), r(S.NothingFound(), E.TypedIp("tv; rm -rf /")))
    }

    @Test fun typedIpThatDoesNotAnswerShowsSameWifiHelp() {
        val t = FoundTv("10.0.0.5")
        assertEquals(S.Unreachable(t), r(S.Connecting(t), E.Result(Attempt.UNREACHABLE)))
    }

    @Test fun deniedIsShownAndCanRetry() {
        val s = r(S.Connecting(tv), E.AwaitingAllow, E.Result(Attempt.DENIED))
        assertEquals(S.Denied(tv), s)
        assertEquals(S.Connecting(tv), r(s, E.Retry))
    }

    @Test fun pairingCodeWithWrongDigitCountIsRefusedLocally() {
        val t = tv.copy(tlsPort = 37099, pairPort = 40111)
        val s = r(S.Pairing(t), E.SubmitCode("12345", null))
        assertTrue(s is S.Pairing && s.error != null && !s.busy)
    }

    @Test fun wrongPairingCodeShowsErrorAndAllowsAnotherTry() {
        val t = tv.copy(tlsPort = 37099, pairPort = 40111)
        val busy = r(S.Pairing(t), E.SubmitCode("123 456", null))
        assertTrue(busy is S.Pairing && busy.busy && busy.error == null)
        val failed = r(busy, E.Result(Attempt.FAILED, "Wrong code"))
        assertEquals(S.Pairing(t, error = "Wrong code", busy = false), failed)
        assertEquals(S.Connected(t), r(failed, E.SubmitCode("123457", null), E.Result(Attempt.CONNECTED)))
    }

    @Test fun pairingNeedsAPortWhenTheTvDidNotAdvertiseOne() {
        val t = tv.copy(tlsPort = 37099)
        val s = r(S.Pairing(t), E.SubmitCode("123456", null))
        assertTrue(s is S.Pairing && s.error!!.contains("port"))
        val ok = r(S.Pairing(t), E.SubmitCode("123456", 40111))
        assertTrue(ok is S.Pairing && ok.busy && ok.tv.pairPort == 40111)
    }

    @Test fun ipChangedRescansAndReconnectsToTheSameTv() {
        val s = r(S.Connecting(tv), E.Result(Attempt.UNREACHABLE))
        assertEquals(S.Scanning(lookingFor = "Living room TV"), s)
        val moved = tv.copy(host = "192.168.1.77")
        assertEquals(S.Connecting(moved), r(s, E.Found(FoundTv("192.168.1.20", name = "Kitchen")), E.Found(moved)))
    }

    @Test fun scanMergesNsdAndPortProbeByHost() {
        val s = r(S.Scanning(), E.Found(FoundTv("192.168.1.42", legacyOpen = true)), E.Found(FoundTv("192.168.1.42", name = "Living room TV", model = "TCL 55C735")))
        assertEquals(listOf(tv), (s as S.Scanning).found)
        assertEquals("TCL 55C735 · 192.168.1.42", tv.subtitle)
    }

    @Test fun reachableTvsAreListedFirst() {
        val cast = FoundTv("192.168.1.3", name = "Speaker")
        val s = r(S.Scanning(), E.Found(cast), E.Found(tv)) as S.Scanning
        assertEquals(listOf(tv, cast), s.found)
    }

    @Test fun backReturnsToScanning() {
        assertEquals(S.Scanning(), r(S.Pairing(tv), E.Back))
    }

    @Test fun lateResultsAfterLeavingAreIgnored() {
        assertEquals(S.Scanning(), r(S.Scanning(), E.Result(Attempt.CONNECTED)))
    }
}
