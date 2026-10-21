package `in`.roomcontroller.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceJobsTest {
    private fun status(state: String, id: Long = 4_000_000_001L, boot: Long = 77L) =
        obj("boot_id" to boot, "job" to obj("id" to id, "status" to state, "detail" to "API result"))

    @Test fun queuedAndRunningAreNotReportedAsSuccess() = runBlocking {
        val states = listOf("queued", "running", "succeeded")
        var reads = 0
        val pauses = mutableListOf<Long>()
        val result = awaitDeviceJob(4_000_000_001L, 77L,
            read={ id -> assertEquals(4_000_000_001L, id); status(states[reads++]) },
            pause={ pauses.add(it) })
        assertEquals(3, reads)
        assertEquals("succeeded", result.getString("status"))
        assertEquals(listOf(250L,250L,250L), pauses)
    }

    @Test fun failedOperationPreservesItsError() = runBlocking {
        val result = awaitDeviceJob(4_000_000_001L, 77L, read={ status("failed") }, pause={})
        assertEquals("failed", result.getString("status"))
        assertEquals("API result", result.getString("detail"))
    }

    @Test fun rebootCannotBeMistakenForCompletion() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { awaitDeviceJob(4_000_000_001L, 77L, read={ status("succeeded", boot=78L) }, pause={}) }
        }
        assertTrue(error.message!!.contains("restarted"))
    }

    @Test fun unrelatedOperationCannotCompleteThePendingAction() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { awaitDeviceJob(4_000_000_001L, 77L, read={ status("succeeded", id=9L) }, pause={}) }
        }
    }

    @Test fun pollingStopsWithoutRetryingTheAction() {
        var reads = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking { awaitDeviceJob(4_000_000_001L, 77L,
                read={ reads++; status("running") }, pause={}, maxPolls=3) }
        }
        assertEquals(3, reads)
    }

    @Test fun droppedConnectionLeavesTheActionUnconfirmed() {
        var reads = 0
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { awaitDeviceJob(4_000_000_001L, 77L,
                read={ reads++; throw java.io.IOException("Connection lost") }, pause={}) }
        }
        assertEquals(1, reads)
        assertTrue(error.message!!.contains("result is unknown"))
    }

    @Test fun confirmedActionStaysSuccessfulIfDisplayRefreshFails() = runBlocking {
        val fresh = refreshCompletedDeviceJob(status("succeeded").getJSONObject("job")) {
            throw java.io.IOException("State refresh failed")
        }
        assertFalse(fresh)
    }

    @Test fun originalSpotifyFailureSurvivesALaterRefreshFailure() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { refreshCompletedDeviceJob(status("failed").getJSONObject("job")) {
                throw java.io.IOException("Secondary refresh failure")
            } }
        }
        assertEquals("API result", error.message)
    }
}
