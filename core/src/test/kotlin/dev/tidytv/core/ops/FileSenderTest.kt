package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSenderTest {
    private fun bytes(n: Int) = ByteArray(n) { it.toByte() }

    @Test fun sendsIntoTheTidyTvFolderWithProgress() = runTest {
        val tv = FakeTv.googleTv()
        val data = bytes(5000)
        val progress = mutableListOf<Long>()
        val out = FileSender(tv).send("Holiday photo.jpg", data.size.toLong(), { data.inputStream() }, mutableSetOf()) { progress += it }
        assertEquals(SendResult.Sent("/sdcard/Download/TidyTV/Holiday photo.jpg"), out)
        assertTrue(data.contentEquals(tv.pushed["/sdcard/Download/TidyTV/Holiday photo.jpg"]))
        assertEquals(5000L, progress.last())
    }

    @Test fun sameNameNeverOverwrites() = runTest {
        val tv = FakeTv.googleTv()
        val sender = FileSender(tv)
        val taken = sender.takenNames().toMutableSet()
        sender.send("a.mp4", 10, { bytes(10).inputStream() }, taken)
        val taken2 = sender.takenNames().toMutableSet()
        assertEquals(setOf("a.mp4"), taken2)
        val out = sender.send("a.mp4", 10, { bytes(10).inputStream() }, taken2)
        assertEquals(SendResult.Sent("/sdcard/Download/TidyTV/a (2).mp4"), out)
    }

    @Test fun theOwnerCanChooseTheFolderAndEachFolderKeepsItsOwnNames() = runTest {
        val tv = FakeTv.googleTv()
        val movies = FileSender(tv, SendFolder.MOVIES)
        assertEquals(SendResult.Sent("/sdcard/Movies/TidyTV/film.mp4"), movies.send("film.mp4", 10, { bytes(10).inputStream() }, movies.takenNames().toMutableSet()))
        assertTrue(tv.pushed.containsKey("/sdcard/Movies/TidyTV/film.mp4"))
        // The same name in another folder is not a clash.
        val docs = FileSender(tv, SendFolder.DOCUMENTS)
        assertEquals(emptySet<String>(), docs.takenNames())
        assertEquals(SendResult.Sent("/sdcard/Documents/TidyTV/film.mp4"), docs.send("film.mp4", 10, { bytes(10).inputStream() }, mutableSetOf()))
        assertEquals(SendFolder.DOWNLOAD, SendFolder.of("nonsense"))
        assertEquals(SendFolder.PICTURES, SendFolder.of("PICTURES"))
    }

    @Test fun onlyKnownFolderPathsReachTheShell() {
        for (f in SendFolder.entries) Commands.listSendDir(f.path)
        for (bad in listOf("/sdcard/../x/TidyTV", "/sdcard/Movies;rm -rf /", "/sdcard/Movies/TidyTV/../..")) {
            val threw = runCatching { Commands.listSendDir(bad) }.isFailure
            assertTrue("$bad should be refused", threw)
        }
    }

    @Test fun unsafeNamesAreCleaned() = runTest {
        val tv = FakeTv.googleTv()
        val out = FileSender(tv).send("../../etc/'evil'.apk", 3, { bytes(3).inputStream() }, mutableSetOf()) as SendResult.Sent
        val name = out.remotePath.removePrefix("/sdcard/Download/TidyTV/")
        assertTrue(out.remotePath, out.remotePath.startsWith("/sdcard/Download/TidyTV/") && '/' !in name && '\'' !in name && !name.startsWith("."))
    }

    @Test fun tooBigForTheTvIsRefusedBeforeSending() = runTest {
        val tv = FakeTv.googleTv().apply { freeKb = 300 * 1024 } // 300 MB free
        val out = FileSender(tv).send("film.mkv", 250L * 1024 * 1024, { error("must not open") }, mutableSetOf())
        assertTrue(out is SendResult.NoSpace)
        assertTrue(tv.pushed.isEmpty())
    }

    @Test fun aDropIsReportedNotThrown() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "df " }
        assertEquals(SendResult.Disconnected, FileSender(tv).send("a.txt", 1, { bytes(1).inputStream() }, mutableSetOf()))
    }

    @Test fun readsDfFromOldAndNewAndroid() {
        val toybox = "Filesystem     1K-blocks    Used Available Use% Mounted on\n/dev/fuse       30000000 22000000 8000000  74% /storage/emulated\n"
        val toolbox = "Filesystem               Size     Used     Free   Blksize\n/sdcard                  5.6G     2.1G     3.5G   4096\n"
        assertEquals(8_000_000L, Parse.dfAvailableKb(toybox))
        assertEquals((3.5 * 1024 * 1024).toLong(), Parse.dfAvailableKb(toolbox))
        assertEquals(null, Parse.dfAvailableKb("df: /sdcard: Permission denied"))
    }
}
