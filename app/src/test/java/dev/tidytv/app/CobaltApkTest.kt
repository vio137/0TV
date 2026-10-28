package dev.tidytv.app

import dev.tidytv.app.data.CobaltApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CobaltApkTest {
    private val a = listOf(byteArrayOf(1)); private val b = listOf(byteArrayOf(2))
    private fun boom(): List<ByteArray>? = error("wrong branch used")

    @Test fun api27UsesOnlyLegacySignatures() = assertEquals(b, CobaltApk.signerBytes(27, modern = ::boom, legacy = { b }))
    @Test fun api26UsesOnlyLegacySignatures() = assertEquals(b, CobaltApk.signerBytes(26, modern = ::boom, legacy = { b }))
    @Test fun api28UsesOnlySigningInfo() = assertEquals(a, CobaltApk.signerBytes(28, modern = { a }, legacy = ::boom))
    @Test fun noSignersFailsClosedOnBothBranches() {
        assertNull(CobaltApk.signerBytes(27, modern = ::boom, legacy = { emptyList() }))
        assertNull(CobaltApk.signerBytes(34, modern = { null }, legacy = ::boom))
    }
}
