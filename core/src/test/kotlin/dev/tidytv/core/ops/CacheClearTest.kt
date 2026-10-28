package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Free up space on a TV that behaves like the owner's: the clear takes a while, free space (df) is live, and
 * `dumpsys diskstats` keeps reporting the old per-app cache sizes for hours afterwards.
 */
class CacheClearTest {
    @Test fun progressComesFromLiveFreeSpaceAndEndsWhenTheTvIsDone() = runTest {
        val tv = FakeTv.googleTv().apply { trimMbPerPoll = 100 }
        val seen = mutableListOf<Int>()
        val r = TvFacts.freeCaches(tv, onProgress = { seen += it }, wait = {})
        assertTrue(r.finished)
        assertEquals(269, r.freedMb)
        assertEquals(listOf(100, 200, 269, 269), seen)
        assertEquals("one clear, started once", 1, tv.trimStarted)
        assertEquals(r.freeBeforeMb!! + 269, r.freeAfterMb)
    }

    @Test fun theStaleDiskstatsNumberDoesNotMoveButTheFreedAmountIsRight() = runTest {
        val tv = FakeTv.googleTv()
        val before = TvFacts.read(tv).storage
        val r = TvFacts.freeCaches(tv, wait = {})
        val after = TvFacts.read(tv).storage
        assertEquals("what the owner saw: Android still reports the old cache total", before.cacheMb, after.cacheMb)
        assertEquals("free space is live and did change", before.freeMb!! + 269, after.freeMb)
        assertEquals(269, r.freedMb)
    }

    @Test fun aClearThatNeverFinishesStopsAtTheTimeLimitAndSaysSo() = runTest {
        val tv = FakeTv.googleTv().apply { trimMbPerPoll = 1 }
        val r = TvFacts.freeCaches(tv, wait = {}, pollMs = 1_000, maxWaitMs = 5_000)
        assertFalse(r.finished)
        assertEquals("only what was freed so far", 5, r.freedMb)
    }

    @Test fun nothingToFreeFinishesAtOnceWithZero() = runTest {
        val tv = FakeTv.googleTv().apply { diskMb.replaceAll { _, v -> Triple(v.first, v.second, 0) } }
        val r = TvFacts.freeCaches(tv, wait = {})
        assertTrue(r.finished)
        assertEquals(0, r.freedMb)
    }

    @Test fun aDroppedConnectionIsReportedNotSwallowed() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "df /data" }
        val failed = runCatching { TvFacts.freeCaches(tv, wait = {}) }.exceptionOrNull()
        assertTrue(failed is TvConnectionLost)
    }

    @Test fun theBackgroundCommandIsOneShortLineSoTheConnectionIsNeverHeldOpen() {
        val c = Commands.TRIM_CACHES_BACKGROUND
        assertTrue(c.contains("nohup") && c.endsWith("echo started"))
        assertTrue(c.contains("&)"))
    }

    @Test fun dfOutputFromARealTvIsParsed() {
        val out = "Filesystem      1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-12  10243384 2717056   7378872  27% /data/user/0\n"
        assertEquals(7378872L, Parse.dfAvailableKb(out))
    }
}
