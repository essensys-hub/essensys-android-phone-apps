package com.essensys.android.data.support

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import com.essensys.android.di.AppContainer
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Portail et gateway simulés pour le bouton Support : routage par chemin (les diagnostics lancent
 * cloud et LAN en parallèle, une file de réponses ne suffit pas). Garde no-armoire active.
 */
class SupportServer(
    state: SessionState = SessionState(),
    var healthCode: Int = 200,
    var sessionCode: Int = 200,
    var sessionJson: String = """{"user":{"id":1,"email":"demo@essensys.fr"},"portal_access":true,""" +
        """"gateway":{"id":"gw","online":false},"armoire":{"no_serie":"SC944D-0001","last_seen":"2026-10-30T10:00:00Z","online":true}}""",
    var lanHealthCode: Int = 200,
    val network: NetworkType = NetworkType.WIFI,
    supportDir: File = File(System.getProperty("java.io.tmpdir"), "essensys-support-test"),
) : AutoCloseable {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val date = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
                fun json(code: Int, body: String) = MockResponse.Builder().code(code).addHeader("Date", date).body(body).build()
                return when (request.url.encodedPath) {
                    "/api/portal/health" -> json(healthCode, """{"status":"ok"}""")
                    "/api/portal/session" -> json(sessionCode, if (sessionCode == 200) sessionJson else """{"error":"unauthorized"}""")
                    "/api/portal/history/latest" -> json(200, """{"lastAction":{"actionInfo":"Lumière salon","isDone":true,"timestamp":"2026-10-30T09:59:00Z"}}""")
                    "/health" -> json(lanHealthCode, """{"status":"ok"}""")
                    "/api/auth/login" -> json(200, """{"token":"jwt-test","user":{"id":1,"email":"demo@essensys.fr"}}""")
                    else -> json(404, "{}")
                }
            }
        }
        start()
    }
    private val base = server.url("/").toString().trimEnd('/')
    val store = InMemorySessionStore(state.copy(cloudHost = base, lanHost = base))
    val container = AppContainer(store, guardNoArmoire = true, configureLan = {}, device = StaticDeviceProbe(network = network), supportDir = supportDir)

    fun paths() = requests.map { "${it.method} ${it.url.encodedPath}" }

    override fun close() = server.close()

    companion object {
        fun cloud(token: String? = "jwt-test", testMode: Boolean = false) =
            SessionState(mode = ConnectionMode.CLOUD, token = token, testMode = testMode)
    }
}
