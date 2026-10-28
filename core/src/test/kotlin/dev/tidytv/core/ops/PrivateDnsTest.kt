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
        assertTrue(AdBlockInfo.DISCLOSURE.contains("put back"))
    }

    @Test fun theScreenSaysPlainlyThatYouTubeAdsStay() {
        assertTrue(AdBlockInfo.YOUTUBE_WARNING.contains("YouTube ads will still show"))
        assertTrue(AdBlockInfo.DOES_NOT_BLOCK.first().startsWith("YouTube video ads"))
        assertTrue(AdBlockInfo.BLOCKS.none { it.contains("YouTube", ignoreCase = true) })
    }

    @Test fun aStaleSavedAdGuardValueStillTurnsOffToAutomatic() = runTest {
        val tv = FakeTv.googleTv().apply { settings["global/private_dns_mode"] = "hostname"; settings["global/private_dns_specifier"] = "dns.adguard-dns.com" }
        val dns = PrivateDns(tv, MemoryActionLog()) { 1L }
        dns.turnOn(AdBlockProvider.ADS)
        val off = dns.turnOff()
        assertEquals("opportunistic", off.mode)
        assertNull(off.provider)
    }

    @Test fun customServerIsSetReadBackAndReplacedWithoutAsking() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        val dns = PrivateDns(tv, log) { 1L }
        val s1 = dns.useCustom("DNS.Example.com.")
        assertTrue(s1.custom); assertEquals("dns.example.com", s1.host)
        assertEquals("dns.example.com", dns.lastCustomHost())
        assertEquals("other.example.net", dns.useCustom("other.example.net").host)
        assertEquals(listOf("dns.example.com", "other.example.net"), PrivateDns.hostsSetIn(log.records()))
    }

    @Test fun hostChecksRefuseUrlsPortsAndIps() {
        for (bad in listOf("", "https://dns.example.com", "dns.example.com:853", "dns.example.com/dns-query", "1.1.1.1", "localhost", "a b.example.com", "-x.example.com", "x_y.example.com", "user@dns.example.com"))
            assertTrue(bad, DotHost.check(bad) is DotHost.Result.Bad)
        assertEquals(DotHost.Result.Ok("one.one.one.one"), DotHost.check(" One.One.One.One "))
    }

    @Test fun aServerThatCannotResolveIsUndoneAndReported() = runTest {
        val tv = FakeTv.googleTv().apply { settings["global/private_dns_mode"] = "opportunistic" }
        val broken = object : TvShell by tv {
            override suspend fun run(command: String) = if (command.startsWith("ping ") && tv.settings["global/private_dns_mode"] == "hostname") "ping: unknown host example.com" else tv.run(command)
        }
        val dns = PrivateDns(broken, MemoryActionLog()) { 1L }
        assertNull(dns.useCustom("dead.example.com").provider)
        assertTrue(dns.lookupFailed)
        assertEquals("opportunistic", tv.settings["global/private_dns_mode"])
    }
}
