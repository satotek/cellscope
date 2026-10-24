package dev.satotek.cellscope

import dev.satotek.cellscope.data.update.Releases
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test fun comparesNumericallyNotAsText() {
        assertTrue(Releases.isNewer("v0.1.10", "0.1.9"))
        assertTrue(Releases.isNewer("v1.0.0", "0.9.9"))
        assertFalse(Releases.isNewer("v0.1.1", "0.1.1"))
        assertFalse(Releases.isNewer("v0.1.0", "0.1.1"))
    }

    @Test fun debugSuffixDoesNotHideOrFakeAnUpdate() {
        assertFalse(Releases.isNewer("v0.1.1", "0.1.1-debug"))
        assertTrue(Releases.isNewer("v0.1.2", "0.1.1-debug"))
    }
}
