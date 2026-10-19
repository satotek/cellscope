package dev.satotek.cellscope

import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.speed.SpeedPhase
import dev.satotek.cellscope.data.speed.SpeedProgress
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.data.speed.SpeedResultStore
import dev.satotek.cellscope.data.speed.SpeedTestController
import dev.satotek.cellscope.data.telephony.SimInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SpeedTestControllerTest {
    private class MemoryStore(var rows: List<SpeedResult> = emptyList()) : SpeedResultStore {
        override fun load() = rows
        override fun save(results: List<SpeedResult>) { rows = results }
    }

    private fun sim(id: Int, label: String) = SimInfo(id, id, 0, label, false, id == 1, false)
    private fun result(t: Long = 1, subId: Int? = 2) = SpeedResult(
        t, 100.0, 20.0, 10.0, "test", "LTE", "B3", 10, -90, subId = subId,
    )

    @Test fun labelsFollowTheMeasuredNetworkEvenAfterSelectionChanges() = runBlocking {
        val first = sim(1, "first")
        val second = sim(2, "second")
        var snapshot = Snapshot(sim = first, sims = listOf(first, second))
        val events = Channel<SpeedProgress>(Channel.UNLIMITED)
        val store = MemoryStore()
        var requestedSub: Int? = null
        var requestedServer: String? = "unset"
        val controller = SpeedTestController(this, store, { snapshot }, { "" }, { 0 }) { server, _, sub ->
            requestedSub = sub
            requestedServer = server
            events.receiveAsFlow()
        }
        controller.start()
        yield()
        snapshot = Snapshot(sim = second, sims = listOf(second))
        events.send(SpeedProgress(SpeedPhase.DONE, result = result()))
        yield()
        assertEquals(1, requestedSub)
        assertNull(requestedServer)
        assertEquals("second", store.rows.single().simLabel)
        assertEquals(2, store.rows.single().subId)
        controller.cancel()
    }

    @Test fun unknownNetworkDoesNotBorrowTheSelectedSimLabel() = runBlocking {
        val selected = sim(1, "selected")
        val events = Channel<SpeedProgress>(Channel.UNLIMITED)
        val store = MemoryStore()
        val controller = SpeedTestController(this, store,
            { Snapshot(sim = selected, sims = listOf(selected)) }, { "" }, { 0 }) { _, _, _ -> events.receiveAsFlow() }
        controller.start()
        yield()
        events.send(SpeedProgress(SpeedPhase.DONE, result = result(subId = null)))
        yield()
        assertNull(store.rows.single().simLabel)
        controller.cancel()
    }

    @Test fun duplicateStartsAreIgnoredAndCancellationAllowsRestart() = runBlocking {
        val events = Channel<SpeedProgress>(Channel.UNLIMITED)
        var starts = 0
        val controller = SpeedTestController(this, MemoryStore(), { Snapshot() }, { "custom" }, { 0 }) { server, _, _ ->
            assertEquals("custom", server)
            starts++
            events.receiveAsFlow()
        }
        controller.start()
        controller.start()
        yield()
        assertEquals(1, starts)
        events.send(SpeedProgress(SpeedPhase.DOWNLOAD))
        yield()
        assertEquals(SpeedPhase.DOWNLOAD, controller.speedState.value?.phase)
        controller.cancel()
        assertNull(controller.speedState.value)
        controller.start()
        yield()
        assertEquals(2, starts)
        controller.cancel()
    }

    @Test fun historyIsBoundedAndDeletionAndRestoreUpdateTheStore() = runBlocking {
        val store = MemoryStore((50L downTo 1L).map { result(it) })
        val events = Channel<SpeedProgress>(Channel.UNLIMITED)
        val controller = SpeedTestController(this, store, { Snapshot() }, { "" }, { 0 }) { _, _, _ -> events.receiveAsFlow() }
        controller.start()
        yield()
        events.send(SpeedProgress(SpeedPhase.DONE, result = result(51)))
        yield()
        assertEquals(50, store.rows.size)
        assertEquals(51L, store.rows.first().t)
        assertFalse(store.rows.any { it.t == 1L })
        controller.delete(store.rows.first())
        assertFalse(store.rows.any { it.t == 51L })
        assertEquals(store.rows, controller.speedResults.value)
        store.rows = listOf(result(99))
        controller.reload()
        assertEquals(99L, controller.speedResults.value.single().t)
        controller.cancel()
    }

    @Test fun csvEscapesSimLabelsAndWritesChronologically() = runBlocking {
        val store = MemoryStore(listOf(result(2).copy(simLabel = "SIM, \"two\""), result(1)))
        val controller = SpeedTestController(this, store, { Snapshot() }, { "" }, { 0 }) { _, _, _ -> error("unused") }
        val dir = Files.createTempDirectory("cellscope-csv").toFile()
        try {
            val rows = controller.writeSpeedCsv(dir).readLines()
            assertEquals(3, rows.size)
            assertTrue(rows[2].endsWith(",\"SIM, \"\"two\"\"\""))
            assertTrue(rows[1].endsWith(",2,"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
