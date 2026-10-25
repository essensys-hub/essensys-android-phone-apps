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
                        """{"user":{"id":1,"email":"demo@essensys.fr","first_name":"Camille","last_name":"Démo"},"portal_access":true,"gateway":{"id":"gw-demo","hostname":"demo","online":$gatewayOnline},"armoire":{"id":1,"no_serie":"SC944D-0001","last_seen":"2026-10-25T08:00:00Z"}}""",
                    )
                    path == "/api/user/me" -> json("""{"user":{"id":1,"email":"demo@essensys.fr","role":"admin"}}""")
                    path == "/api/portal/history/latest" -> json("""{"lastAction":null,"message":"No actions yet"}""")
                    path == "/api/portal/inject" || path == "/api/portal/inject/batch" || path == "/api/admin/inject" -> {
                        injections += request.body?.utf8().orEmpty()
                        if (injectDelayMs > 0) Thread.sleep(injectDelayMs)
                        if (failNextInject) { failNextInject = false; return MockResponse.Builder().code(429).body("Rate limit exceeded\n").build() }
                        if (dryRun(request)) testOk() else json("""{"guid":"g","guids":["g"],"params":[]}""")
                    }
                    path == "/api/portal/exchange" || path == "/api/admin/exchange" -> {
                        val keys = request.url.queryParameter("keys").orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
                        val values = keys.filter { exchange.containsKey(it) }.joinToString(",") { """{"k":$it,"v":"${exchange.getValue(it)}"}""" }
                        json("""{"values":[$values],"stale":$exchangeStale,"source":"gateway_cache"}""")
                    }
                    path == "/api/admin/heating/sync" -> json(
                        """{"status":"started","sync":{"active":true,"startIndex":13,"byteCount":84,"received":0,"total":84,"chunksTotal":3,"chunksCompleted":0}}""",
                    )
                    path == "/api/admin/heating/sync/status" -> json(
                        """{"active":false,"startIndex":13,"byteCount":84,"received":84,"total":84,"chunksTotal":3,"chunksCompleted":3}""",
                    )
                    path == "/api/portal/web/actions" -> {
                        mutationBodies += request.body?.utf8().orEmpty()
                        mutations += "${request.method} $path"
                        if (dryRun(request)) testOk() else json("""{"status":"ok","message":"Action sent to queue"}""")
                    }
                    SCENARIOS.matches(path) -> scenarios(request, path)
                    else -> MockResponse.Builder().code(404).body("Not found").build()
                }
            }
        }
        start()
    }

    /** Valeurs de la table d'échange servies par `exchange` (planning, chauffe-eau, arrosage). */
    val exchange = java.util.concurrent.ConcurrentHashMap<Int, String>(mapOf(566 to "25"))
    var exchangeStale = false
    @Volatile var failNextInject = false

    /** Mutations hors inject (scénarios, alarme) : « METHOD chemin », et leurs corps dans le même ordre. */
    val mutations = CopyOnWriteArrayList<String>()
    val mutationBodies = CopyOnWriteArrayList<String>()

    private fun scenarios(request: RecordedRequest, path: String): MockResponse {
        val rest = path.substringAfter("/scenarios").trim('/')
        return when {
            rest.isEmpty() -> json(fixture("scenarios_list.json"))
            rest == "meta/bitmasks" -> json(fixture("scenario_bitmasks.json"))
            request.method == "GET" -> json(fixture("scenario_4.json"))
            else -> {
                mutationBodies += request.body?.utf8().orEmpty()
                mutations += "${request.method} $path"
                if (dryRun(request)) testOk() else json("""{"status":"ok","guid":"g","guids":["g"],"slot":${rest.substringBefore('/')}}""")
            }
        }
    }

    private fun dryRun(request: RecordedRequest) = request.headers["X-Essensys-Test-Mode"] == "dry-run"

    private fun testOk() = json("""{"status":"test_ok","dry_run":true,"validated_params":[],"message":"Validation OK"}""")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "fixture $name" }.readText()

    private companion object {
        val SCENARIOS = Regex("^/api/(portal/)?scenarios(/.*)?$")
    }

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    fun container(state: SessionState = SessionState()): AppContainer {
        val base = server.url("/").toString().trimEnd('/')
        val store = InMemorySessionStore(state.copy(cloudHost = base, lanHost = if (state.mode == ConnectionMode.LAN) base else state.lanHost))
        return AppContainer(store, guardNoArmoire = true, configureLan = {})
    }

    override fun close() = server.close()
}
