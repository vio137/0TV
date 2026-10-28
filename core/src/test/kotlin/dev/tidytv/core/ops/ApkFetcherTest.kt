package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFetcherTest {
    @Test fun fetchesASmallSystemApk() = runTest {
        val tv = FakeTv.googleTv()
        val bytes = ByteArray(4096) { it.toByte() }
        tv.apkPaths["com.tcl.ttvs"] = "/system/priv-app/TTVS/TTVS.apk"
        tv.apkFiles["/system/priv-app/TTVS/TTVS.apk"] = bytes
        assertArrayEquals(bytes, ApkFetcher(tv).fetch("com.tcl.ttvs"))
    }

    @Test fun skipsLargeApks() = runTest {
        val tv = FakeTv.googleTv()
        tv.apkPaths["com.netflix.ninja"] = "/data/app/~~x==/com.netflix.ninja-1/base.apk"
        tv.apkFiles["/data/app/~~x==/com.netflix.ninja-1/base.apk"] = ByteArray(2048)
        assertNull(ApkFetcher(tv, maxBytes = 1024).fetch("com.netflix.ninja"))
        assertTrue(tv.commands.none { it.startsWith("pull ") })
    }

    @Test fun unknownPackageOrOddPathGivesNull() = runTest {
        val tv = FakeTv.googleTv()
        assertNull(ApkFetcher(tv).fetch("com.not.there"))
        tv.apkPaths["com.weird.one"] = "/system/app/x;reboot.apk"
        assertNull(ApkFetcher(tv).fetch("com.weird.one"))
        assertTrue(tv.commands.none { it.contains("reboot") && it.startsWith("stat") })
    }

    @Test fun droppedConnectionGivesNull() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "pm path" }
        assertNull(ApkFetcher(tv).fetch("com.tcl.ttvs"))
    }
}
