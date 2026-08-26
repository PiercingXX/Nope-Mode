package com.piercingxx.nopemode.enforce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagePrunerTest {

    @Test
    fun `gone packages are those missing from installed`() {
        val known = setOf("com.keep", "com.gone")
        val installed = setOf("com.keep", "com.other")
        assertEquals(setOf("com.gone"), PackagePruner.gone(known, installed))
    }

    @Test
    fun `nothing is gone when every known package is still installed`() {
        val known = setOf("a", "b")
        assertTrue(PackagePruner.gone(known, setOf("a", "b", "c")).isEmpty())
    }

    @Test
    fun `empty known yields empty gone`() {
        assertTrue(PackagePruner.gone(emptySet(), setOf("a")).isEmpty())
    }
}
