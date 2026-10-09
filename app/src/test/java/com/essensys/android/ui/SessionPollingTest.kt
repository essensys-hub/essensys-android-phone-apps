package com.essensys.android.ui

import com.essensys.android.support.TestServer
import com.essensys.android.ui.screens.SessionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Rafraîchissement cloud : session 60 s, dernière action 2 s, arrêt dès l'annulation (arrière-plan). Design D6. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPollingTest {

    @Test
    fun polls_at_portal_cadence_and_stops_when_cancelled() = runTest {
        TestServer(token = "jwt").use { t ->
            val sessions = AtomicInteger()
            val history = AtomicInteger()
            t.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/api/portal/session" -> { sessions.incrementAndGet(); MockResponse.Builder().body("""{"gateway":{"online":true}}""").build() }
                    else -> { history.incrementAndGet(); MockResponse.Builder().body("""{"lastAction":null}""").build() }
                }
            }
            val vm = SessionViewModel(t.store, t.container.auth, t.container.portal)
            val job = launch { vm.pollWhileVisible() }
            runCurrent()
            waitFor { sessions.get() == 1 && history.get() == 1 }
            advanceTimeBy(SessionViewModel.LAST_ACTION_PERIOD_MS); runCurrent()
            waitFor { history.get() == 2 }
            assertEquals(1, sessions.get())

            job.cancel() // app en arrière-plan : repeatOnLifecycle annule la collecte
            advanceTimeBy(SessionViewModel.SESSION_PERIOD_MS * 2); runCurrent()
            Thread.sleep(200)
            assertEquals(1, sessions.get())
            assertEquals(2, history.get())
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }
}
