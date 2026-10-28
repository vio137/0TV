package dev.tidytv.app

import dev.tidytv.app.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class PinnedAppsStoreTest {
    @Test fun pinnedAppsAreKeptPerTvInOrder() {
        val store = Store(Files.createTempDirectory("tidytv").toFile())
        assertNull(store.pinnedApps("TV-A"))
        store.savePinned("TV-A", listOf("com.netflix.ninja", "org.xbmc.kodi"))
        store.savePinned("TV-B", listOf("com.spotify.tv.android"))
        assertEquals(listOf("com.netflix.ninja", "org.xbmc.kodi"), store.pinnedApps("TV-A"))
        assertEquals(listOf("com.spotify.tv.android"), store.pinnedApps("TV-B"))
        store.savePinned("TV-A", emptyList())
        assertEquals(emptyList<String>(), store.pinnedApps("TV-A"))
    }
}
