package dev.tidytv.core.lists

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBarTest {
    private val nf = "com.netflix.ninja"
    private val yt = "com.google.android.youtube.tv"
    private val kids = "com.google.android.youtube.tvkids"
    private val prime = "com.amazon.amazonvideo.livingroom"
    private val all = listOf("a.app", "b.app", "c.app", "d.app", kids, nf, prime, yt, "z.app")

    @Test fun streamingAppsComeFirstWhenNothingIsSaved() {
        assertEquals(listOf(nf, yt, kids, prime), RemoteBar.pinned(all, null))
        assertEquals(listOf(nf, yt, kids, prime, "a.app", "b.app"), RemoteBar.bar(all, null))
    }

    @Test fun appsTheTvDoesntHaveAreSkipped() {
        assertEquals(listOf(yt), RemoteBar.pinned(listOf(yt, "x"), null))
        assertEquals(listOf("b.app"), RemoteBar.pinned(all, listOf("gone.app", "b.app", "b.app")))
    }

    @Test fun theOwnersOrderWins() {
        val saved = listOf("z.app", yt)
        assertEquals(listOf("z.app", yt, "a.app", "b.app", "c.app", "d.app"), RemoteBar.bar(all, saved))
        assertEquals(listOf("z.app", yt, "a.app", "b.app", "c.app", "d.app", kids, nf, prime), RemoteBar.order(all, saved))
    }

    @Test fun pinningStartsFromWhatWasShownAndMoves() {
        val t = RemoteBar.toggle(all, null, "z.app")
        assertEquals(listOf(nf, yt, kids, prime, "z.app"), t)
        assertEquals(listOf(nf, yt, kids, prime), RemoteBar.toggle(all, t, "z.app"))
        assertEquals(listOf(yt, nf, kids, prime), RemoteBar.move(all, null, nf, +1))
        assertEquals(listOf(nf, yt, kids, prime), RemoteBar.move(all, null, nf, -1))
    }

    @Test fun barNeverShowsMoreThanTwelve() {
        val many = (1..20).map { "p$it" }
        assertEquals(12, RemoteBar.bar(many, many).size)
    }
}
