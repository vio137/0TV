package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsTest {
    @Test fun turnsOnReadsBackAndTurnsOffToWhatWasThere() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        val dns = PrivateDns(tv, log) { 1L }
        assertNull(dns.read().mode)
        assertEquals(AdBlockProvider.ADS, dns.turnOn(AdBlockProvider.ADS).provider)
        assertEquals("hostname", tv.settings["global/private_dns_mode"])
        assertEquals("dns.adguard-dns.com", tv.settings["global/private_dns_specifier"])
        val off = dns.turnOff()
        assertNull(off.provider)
        assertTrue("never set before: the specifier is removed, mode is off", "global/private_dns_specifier" !in tv.settings && tv.settings["global/private_dns_mode"] == "off")
    }

    @Test fun anOwnersExistingPrivateDnsIsRememberedAndComesBack() = runTest {
        val tv = FakeTv.googleTv().apply { settings["global/private_dns_mode"] = "hostname"; settings["global/private_dns_specifier"] = "dns.quad9.net" }
        val log = MemoryActionLog()
        val dns = PrivateDns(tv, log) { 1L }
        assertTrue(dns.read().someoneElses)
        dns.turnOn(AdBlockProvider.FAMILY)
        assertEquals(AdBlockProvider.FAMILY, dns.read().provider)
        dns.turnOff()
        assertEquals(DnsState("hostname", "dns.quad9.net"), dns.read())
    }

    @Test fun turningOnTwiceKeepsTheOriginalValues() = runTest {
        val tv = FakeTv.googleTv().apply { settings["global/private_dns_mode"] = "opportunistic" }
        val dns = PrivateDns(tv, MemoryActionLog()) { 1L }
        dns.turnOn(AdBlockProvider.ADS); dns.turnOn(AdBlockProvider.FAMILY)
        dns.turnOff()
        assertEquals("opportunistic", tv.settings["global/private_dns_mode"])
    }

    @Test fun fullRestoreAlsoPutsPrivateDnsBack() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        PrivateDns(tv, log) { 1L }.turnOn(AdBlockProvider.ADS)
        val r = Restorer(tv, log) { 2L }.run()
        assertTrue(r.toString(), r is RestoreOutcome.Done)
        assertTrue("global/private_dns_mode" !in tv.settings)
    }

    @Test fun aTvThatIgnoresTheSettingIsReportedAndLeftAsItWas() = runTest {
        val tv = FakeTv.googleTv()
        val stubborn = object : TvShell by tv {
            override suspend fun run(command: String) = if (command.startsWith("settings put global private_dns_mode hostname")) "" else tv.run(command)
        }
        val dns = PrivateDns(stubborn, MemoryActionLog()) { 1L }
        assertNull(dns.turnOn(AdBlockProvider.ADS).provider)
        assertNull("the specifier was put back", tv.settings["global/private_dns_specifier"])
    }
}

class AdBlockInfoTest {
    @Test fun `discloses the third party and the restore`() {
        assertTrue(AdBlockInfo.DISCLOSURE.contains("AdGuard"))
        assertTrue(AdBlockInfo.DISCLOSURE.contains("third party"))
        assertTrue(AdBlockInfo.DISCLOSURE.contains("put back exactly"))
    }

    @Test fun theScreenSaysPlainlyThatYouTubeAdsStay() {
        assertTrue(AdBlockInfo.YOUTUBE_WARNING.contains("YouTube ads will still show"))
        assertTrue(AdBlockInfo.DOES_NOT_BLOCK.first().startsWith("YouTube video ads"))
        assertTrue(AdBlockInfo.BLOCKS.none { it.contains("YouTube", ignoreCase = true) })
    }
}
