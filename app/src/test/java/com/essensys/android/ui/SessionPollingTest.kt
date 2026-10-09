package com.essensys.android.ui

import com.essensys.android.support.TestServer
import com.essensys.android.ui.screens.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rafraîchissement cloud (design D6) : la dernière action est interrogée bien plus souvent que la session,
 * et plus aucune requête ne part une fois la collecte annulée (app en arrière-plan).
 * Temps réel avec périodes courtes : pas de temps virtuel, car les appels HTTP passent par Dispatchers.IO.
 */
class SessionPollingTest {

    @Test
    fun polls_at_portal_cadence_and_stops_when_cancelled() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val sessions = AtomicInteger()
            val history = AtomicInteger()
            t.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/api/portal/session" -> { sessions.incrementAndGet(); MockResponse.Builder().body("""{"gateway":{"online":true}}""").build() }
                    else -> { history.incrementAndGet(); MockResponse.Builder().body("""{"lastAction":null}""").build() }
                }
            }
            val vm = SessionViewModel(
                t.store, t.container.auth, t.container.portal,
                sessionPeriodMs = 60_000, lastActionPeriodMs = 50,
            )
            val job = launch(Dispatchers.Default) { vm.pollWhileVisible() }
            waitFor { history.get() >= 4 }
            assertEquals("session interrogée une seule fois pendant la fenêtre", 1, sessions.get())
            assertTrue("dernière action interrogée en boucle", history.get() >= 4)

            job.cancel() // repeatOnLifecycle annule la collecte quand l'app passe en arrière-plan
            job.join()
            val frozen = history.get() to sessions.get()
            Thread.sleep(300)
            assertEquals("aucune requête après annulation", frozen, history.get() to sessions.get())
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition non atteinte en 5 s", condition())
    }
}
