package com.essensys.android.ui

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import com.essensys.android.di.AppContainer
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Backend simulé (contrats de essensys-user-portal-backend, design D5 bis) : aucune armoire réelle,
 * garde no-armoire active. Les réponses JSON reprennent les fixtures du portail (mockFetch.ts).
 */
class FakeBackend(
    var linked: Boolean = true,
    var temporaryPassword: Boolean = false,
    var gatewayOnline: Boolean = true,
    var injectDelayMs: Long = 0,
) : AutoCloseable {
    val injections = CopyOnWriteArrayList<String>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                return when {
                    path == "/api/auth/login" -> json(
                        """{"token":"jwt-test","user":{"id":1,"email":"demo@essensys.fr","role":"guest_local"},"password_change_required":$temporaryPassword}""",
                    )
                    path == "/api/auth/password/change" -> { temporaryPassword = false; json("""{"message":"Mot de passe mis à jour."}""") }
                    path == "/api/auth/logout" -> MockResponse.Builder().code(200).build()
                    path == "/api/portal/link-request/status" -> json(
                        if (linked) """{"status":"none","portal_access":true,"linked_gateway_id":"gw-demo"}"""
                        else """{"status":"none","portal_access":false}""",
                    )
                    path == "/api/portal/session" -> json(
                        """{"portal_access":true,"gateway":{"id":"gw-demo","hostname":"demo","online":$gatewayOnline}}""",
                    )
                    path == "/api/portal/history/latest" -> json("""{"lastAction":null,"message":"No actions yet"}""")
                    path == "/api/portal/inject" -> {
                        injections += request.body?.utf8().orEmpty()
                        if (injectDelayMs > 0) Thread.sleep(injectDelayMs)
                        if (request.headers["X-Essensys-Test-Mode"] == "dry-run") {
                            json("""{"status":"test_ok","dry_run":true,"validated_params":[],"message":"Validation OK"}""")
                        } else json("""{"guid":"g","params":[]}""")
                    }
                    path == "/api/portal/exchange" -> json("""{"values":[{"k":566,"v":"25"}],"stale":false,"source":"gateway_cache"}""")
                    else -> MockResponse.Builder().code(404).body("Not found").build()
                }
            }
        }
        start()
    }

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    fun container(state: SessionState = SessionState()): AppContainer {
        val base = server.url("/").toString().trimEnd('/')
        val store = InMemorySessionStore(state.copy(cloudHost = base, lanHost = if (state.mode == ConnectionMode.LAN) base else state.lanHost))
        return AppContainer(store, guardNoArmoire = true, configureLan = {})
    }

    override fun close() = server.close()
}
