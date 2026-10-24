package dev.satotek.cellscope

import dev.satotek.cellscope.data.update.NoteLine
import dev.satotek.cellscope.data.update.Releases
import org.junit.Assert.assertEquals
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

    @Test fun notesDropMarkupHashesAndTheCompareLink() {
        val notes = """
            ## Features

            - **update**: install releases in the app (45472d3)
            - remove the old digest (1efe9cd)

            **Full changelog**: https://github.com/satotek/cellscope/compare/v0.1.1...v0.1.2
        """.trimIndent()
        assertEquals(
            listOf(
                NoteLine("Features", heading = true),
                NoteLine("update: install releases in the app", heading = false),
                NoteLine("remove the old digest", heading = false),
            ),
            Releases.noteLines(notes),
        )
    }
}
