package `in`.roomcontroller.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Observe an accepted command. This function never submits or retries an action. */
internal suspend fun awaitDeviceJob(
    id: Long,
    bootId: Long,
    read: suspend (Long) -> JSONObject,
    pause: suspend (Long) -> Unit = { delay(it) },
    maxPolls: Int = 48
): JSONObject {
    require(id > 0 && bootId > 0)
    repeat(maxPolls) { attempt ->
        pause(if (attempt < 4) 250 else 500)
        val response = try {
            read(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IllegalStateException(
                "Connection interrupted. The action's result is unknown; check playback before sending it again.", failure)
        }
        check(response.optLong("boot_id") == bootId) {
            "Controller restarted. The action's result is unknown; check playback before sending it again."
        }
        val job = response.getJSONObject("job")
        check(job.optLong("id") == id) { "Controller returned a different operation" }
        when (job.getString("status")) {
            "succeeded", "failed" -> return job
            "queued", "running" -> Unit
            else -> error("Unknown controller operation status")
        }
    }
    error("Still waiting for Spotify. Check playback before sending the action again.")
}

/** A display-refresh failure must not change an already confirmed action result. */
internal suspend fun refreshCompletedDeviceJob(job: JSONObject, refresh: suspend () -> Unit): Boolean {
    val status = job.getString("status")
    require(status == "succeeded" || status == "failed")
    val fresh = try {
        refresh()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    if (status == "failed") error(job.optString("detail", "Spotify request failed"))
    return fresh
}
