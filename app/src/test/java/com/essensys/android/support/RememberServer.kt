package com.essensys.android.support

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import com.essensys.android.di.AppContainer
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Portail cloud simulé pour la session mémorisée (spec remember-me) : un jeton d'accès n'est accepté que s'il figure
 * dans [validAccess], et le renouvellement répond selon [refresh]. Garde no-armoire active, aucune armoire réelle.
 */
class RememberServer(
    mode: ConnectionMode = ConnectionMode.CLOUD,
    token: String? = "jwt-old",
    rememberToken: String? = "remember-old",
    rememberExpiresAtMs: Long? = null,
    clock: () -> Long = System::currentTimeMillis,
    state: SessionState? = null,
) : AutoCloseable {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val refreshCount = AtomicInteger()

    /** Jetons d'accès acceptés par les routes du portail ; les autres reçoivent un 401. */
    @Volatile var validAccess: Set<String> = setOf("jwt-new")
    @Volatile var refreshDelayMs = 0L
    @Volatile var refresh: () -> MockResponse = { json(200, """{"token":"jwt-new","user":{"id":1,"email":"demo@essensys.fr"},"password_change_required":false}""") }
    @Volatile var passwordChangeCode = 200
    @Volatile var login: () -> MockResponse = { MockResponse.Builder().code(401).body("Invalid credentials\n").build() }

    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when (request.url.encodedPath) {
                    "/api/auth/remember/refresh" -> {
                        refreshCount.incrementAndGet()
                        if (refreshDelayMs > 0) Thread.sleep(refreshDelayMs)
                        refresh()
                    }
                    "/api/portal/session" -> {
                        val bearer = request.headers["Authorization"]?.removePrefix("Bearer ")
                        if (bearer in validAccess) json(200, SESSION) else MockResponse.Builder().code(401).body("Unauthorized\n").build()
                    }
                    "/api/user/me" -> MockResponse.Builder().code(401).body("Unauthorized\n").build()
                    "/api/auth/logout" -> MockResponse.Builder().code(200).build()
                    "/api/auth/password/change", "/api/user/me/password" -> json(passwordChangeCode, """{"message":"ok"}""")
                    "/api/auth/login" -> login()
                    "/api/portal/health", "/health" -> json(200, """{"status":"ok"}""")
                    else -> MockResponse.Builder().code(404).body("Not found").build()
                }
            }
        }
        start()
    }
    private val base = server.url("/").toString().trimEnd('/')
    val store = InMemorySessionStore(
        (state ?: SessionState(mode = mode, token = token, rememberToken = rememberToken, rememberExpiresAtMs = rememberExpiresAtMs))
            .copy(cloudHost = base, lanHost = base),
    )
    val container = AppContainer(store, guardNoArmoire = true, configureLan = {}, clock = clock)

    fun paths() = requests.map { "${it.method} ${it.url.encodedPath}" }

    fun refreshRequests() = requests.filter { it.url.encodedPath == "/api/auth/remember/refresh" }

    override fun close() = server.close()

    companion object {
        const val SESSION = """{"user":{"id":1,"email":"demo@essensys.fr"},"portal_access":true,"gateway":{"id":"gw","online":true},"armoire":{"no_serie":"SC944D-0001","online":true}}"""

        fun json(code: Int, body: String): MockResponse =
            MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
    }
}
