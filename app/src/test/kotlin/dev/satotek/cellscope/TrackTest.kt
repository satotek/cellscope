package dev.satotek.cellscope

import dev.satotek.cellscope.data.replay.Track
import dev.satotek.cellscope.data.replay.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {
    @Test fun projectsTheOriginToTheMiddleOfTheWorld() {
        val (x, y) = Track.project(0.0, 0.0, 1)
        assertEquals(256.0, x, 1e-6)
        assertEquals(256.0, y, 1e-6)
    }

    @Test fun fitZoomKeepsTheWholeTrackInsideTheView() {
        // About 2 km east–west across Tokyo Station.
        val pts = listOf(TrackPoint(0, 35.681, 139.757, null), TrackPoint(1, 35.681, 139.779, null))
        val z = Track.fitZoom(pts, 360.0, 240.0)
        val (x0, _, x1, _) = Track.bounds(pts, z)
        assertTrue("fits at $z", x1 - x0 <= 360.0)
        val (a0, _, a1, _) = Track.bounds(pts, z + 1)
        assertTrue("one deeper would overflow", a1 - a0 > 360.0)
    }
}
