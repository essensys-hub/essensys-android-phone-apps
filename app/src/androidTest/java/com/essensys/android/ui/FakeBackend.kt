package com.essensys.android.ui

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.InMemorySessionStore
import com.essensys.android.data.session.SessionState
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.support.SupportPackage
import com.essensys.android.di.AppContainer
import java.io.File
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
    /** Installation avec gateway ; sans, la session n'a pas de bloc `gateway` (essensys-feature-lifecycle#37). */
    var hasGateway: Boolean = true,
    /** Session sans bloc `armoire` (armoire jamais vue). */
    var hasArmoire: Boolean = true,
    /** `armoire.online` ; par défaut, l'armoire est vue si la gateway est en ligne. */
    var armoireOnline: Boolean? = null,
    /** Session mémorisée (spec remember-me) : le login la renvoie si la case a été cochée. */
    var rememberSupported: Boolean = true,
    /** Jetons d'accès que le portail refuse (401), pour simuler un accès de 24 h expiré. */
    var unauthorizedTokens: Set<String> = emptySet(),
    /** `gateway_state` du cloud (`none`, `offline`, `online`) ; nul : champ absent (ancien cloud). Sonnette (#61). */
    var gatewayState: String? = null,
    /** Jeton push renvoyé par la source factice ; nul : Firebase non configuré. */
    var pushToken: String? = "fake-fcm-token-0123456789",
) : AutoCloseable {
    /** Appareils enregistrés par `PUT /api/portal/push/devices` : id → doorbell_notify (jamais de jeton conservé). */
    val pushDevices = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    val injections = CopyOnWriteArrayList<String>()

    /** Fin de la session mémorisée renvoyée au login : 30 jours après le test. */
    val rememberExpiresAt: java.time.Instant = java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS)
    val refreshCount = java.util.concurrent.atomic.AtomicInteger()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                val bearer = request.headers["Authorization"]?.removePrefix("Bearer ")
                if (bearer != null && bearer in unauthorizedTokens && !path.startsWith("/api/auth/")) {
                    return MockResponse.Builder().code(401).body("Unauthorized\n").build()
                }
                return when {
                    path == "/api/auth/login" -> {
                        val remember = rememberSupported && request.body?.utf8().orEmpty().contains(""""remember_me":true""")
                        val extra = if (remember) ""","remember_token":"remember-fake-0123456789","remember_expires_at":"$rememberExpiresAt"""" else ""
                        json(
                            """{"token":"jwt-test","user":{"id":1,"email":"demo@essensys.fr","role":"guest_local"},"password_change_required":$temporaryPassword$extra}""",
                        )
                    }
                    path == "/api/auth/remember/refresh" -> {
                        refreshCount.incrementAndGet()
                        json("""{"token":"jwt-test","user":{"id":1,"email":"demo@essensys.fr"},"password_change_required":false}""")
                    }
                    path == "/api/auth/password/change" -> { temporaryPassword = false; json("""{"message":"Mot de passe mis à jour."}""") }
                    path == "/api/auth/logout" -> MockResponse.Builder().code(200).build()
                    path == "/api/portal/link-request/status" -> json(
                        if (linked) """{"status":"none","portal_access":true,"linked_gateway_id":"gw-demo"}"""
                        else """{"status":"none","portal_access":false}""",
                    )
                    path == "/api/portal/session" -> json(sessionJson())
                    // Sonnette (sonnette-plages-push-2026-11-008) : contrat cloud, serveur factice, aucun fournisseur réel.
                    path == "/api/portal/push/devices" && request.method == "PUT" -> {
                        val body = request.body?.utf8().orEmpty()
                        val notify = body.contains(""""doorbell_notify":true""")
                        pushDevices["dev-1"] = notify
                        json("""{"id":"dev-1","platform":"android","doorbell_notify":$notify}""")
                    }
                    path.startsWith("/api/portal/push/devices/") && request.method == "DELETE" ->
                        if (pushDevices.remove(path.substringAfterLast('/')) != null) MockResponse.Builder().code(204).build()
                        else MockResponse.Builder().code(404).body("""{"error":"not_found"}""").build()
                    path == "/api/portal/doorbell/preferences" -> json(
                        """{"available":${gatewayState != null && gatewayState != "none"},"devices":[${
                            pushDevices.entries.joinToString(",") { """{"id":"${it.key}","platform":"android","doorbell_notify":${it.value}}""" }
                        }]}""",
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
                    // Temporisations des lumières (lumieres-temporisees-2026-11-003) : cloud et LAN, même contrat.
                    path == "/api/portal/lights/timers" || path == "/api/admin/lights/timers" -> json(timersJson())
                    LIGHT_TIMER_PUT.matches(path) && request.method == "PUT" -> {
                        val id = path.substringAfterLast('/')
                        val minutes = request.body?.utf8().orEmpty().substringAfter("\"minutes\":").takeWhile { it.isDigit() }.toIntOrNull()
                        timerPuts += "$path ${request.body?.utf8().orEmpty()}"
                        when {
                            !lightTimers.containsKey(id) -> MockResponse.Builder().code(400).body("""{"error":"unknown_light"}""").build()
                            minutes == null || minutes !in 0..255 -> MockResponse.Builder().code(400).body("""{"error":"invalid_minutes"}""").build()
                            dryRun(request) -> json("""{"id":"$id","minutes":$minutes,"status":"dry_run"}""")
                            else -> { lightTimers[id] = minutes; json("""{"id":"$id","minutes":$minutes,"status":"queued"}""") }
                        }
                    }
                    // Noms des lumières (noms-lumieres-2026-11-004) : cloud et LAN, même contrat.
                    (path == "/api/portal/lights/names" || path == "/api/lights/names") && request.method == "GET" -> {
                        if (namesUnavailable) MockResponse.Builder().code(503).body("""{"error":"names_unavailable"}""").build() else json(namesJson())
                    }
                    LIGHT_NAME_PUT.matches(path) && request.method == "PUT" -> {
                        val id = path.substringAfterLast('/')
                        val body = request.body?.utf8().orEmpty()
                        namePuts += "$path $body"
                        val raw = Regex("\"name\":(null|\"(.*)\")").find(body)
                        val name = raw?.groupValues?.get(2)?.takeIf { raw.groupValues[1] != "null" }
                        when {
                            nameForbidden -> MockResponse.Builder().code(403).body("""{"error":"forbidden"}""").build()
                            !lightNames.containsKey(id) -> MockResponse.Builder().code(400).body("""{"error":"unknown_light"}""").build()
                            raw == null || (name != null && (name.isBlank() || name.codePointCount(0, name.length) > 40)) ->
                                MockResponse.Builder().code(400).body("""{"error":"invalid_name"}""").build()
                            dryRun(request) -> json(nameJson(id, name, status = "dry_run"))
                            else -> { lightNames[id] = name.orEmpty(); json(nameJson(id, name, status = "saved")) }
                        }
                    }
                    // Noms des volets (noms-volets-2026-11-005) : cloud et LAN, même contrat que les lumières.
                    (path == "/api/portal/shutters/names" || path == "/api/shutters/names") && request.method == "GET" -> {
                        if (namesUnavailable) MockResponse.Builder().code(503).body("""{"error":"names_unavailable"}""").build() else json(shutterNamesJson())
                    }
                    SHUTTER_NAME_PUT.matches(path) && request.method == "PUT" -> {
                        val id = path.substringAfterLast('/')
                        val body = request.body?.utf8().orEmpty()
                        shutterNamePuts += "$path $body"
                        val raw = Regex("\"name\":(null|\"(.*)\")").find(body)
                        val name = raw?.groupValues?.get(2)?.takeIf { raw.groupValues[1] != "null" }
                        when {
                            nameForbidden -> MockResponse.Builder().code(403).body("""{"error":"forbidden"}""").build()
                            !shutterNames.containsKey(id) -> MockResponse.Builder().code(400).body("""{"error":"unknown_shutter"}""").build()
                            raw == null || (name != null && (name.isBlank() || name.codePointCount(0, name.length) > 40)) ->
                                MockResponse.Builder().code(400).body("""{"error":"invalid_name"}""").build()
                            dryRun(request) -> json(shutterNameJson(id, name, status = "dry_run"))
                            else -> { shutterNames[id] = name.orEmpty(); json(shutterNameJson(id, name, status = "saved")) }
                        }
                    }
                    // Disposition des lumières et des volets (affichage-objets-2026-11-006) : cloud et LAN, même contrat.
                    (path == "/api/portal/lights/layout" || path == "/api/lights/layout" || path == "/api/portal/shutters/layout" || path == "/api/shutters/layout") &&
                        request.method == "GET" -> {
                        if (layoutAbsent) MockResponse.Builder().code(404).body("Not found").build() else json(layoutJson(path.contains("/lights/")))
                    }
                    LAYOUT_PUT.matches(path) && request.method == "PUT" -> {
                        val lights = path.contains("/lights/")
                        val body = request.body?.utf8().orEmpty()
                        layoutPuts += "$path $body"
                        val json = runCatching { org.json.JSONObject(body) }.getOrNull()
                        val catalogue = if (lights) com.essensys.android.data.control.LayoutKind.LIGHTS.catalogue() else com.essensys.android.data.control.LayoutKind.SHUTTERS.catalogue()
                        val order = json?.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                        val disabled = json?.optJSONArray("disabled")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                        val reset = json?.optBoolean("reset") == true
                        when {
                            layoutForbidden -> MockResponse.Builder().code(403).body("""{"error":"forbidden"}""").build()
                            layoutUnavailable -> MockResponse.Builder().code(503).body("""{"error":"names_unavailable"}""").build()
                            !reset && (order == null || disabled == null) -> MockResponse.Builder().code(400).body("""{"error":"invalid_layout"}""").build()
                            !reset && (order.orEmpty() + disabled.orEmpty()).any { it !in catalogue } ->
                                MockResponse.Builder().code(400).body("""{"error":"${if (lights) "unknown_light" else "unknown_shutter"}"}""").build()
                            dryRun(request) -> json(layoutDoc(order.orEmpty(), disabled.orEmpty(), 1, "dry_run"))
                            else -> {
                                val state = if (lights) lightLayout else shutterLayout
                                state.order = if (reset) emptyList() else order.orEmpty()
                                state.disabled = if (reset) emptyList() else disabled.orEmpty()
                                state.version += 1
                                json(layoutDoc(state.order, state.disabled, state.version, "saved"))
                            }
                        }
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
                    // Bouton Support : santé du portail (cloud) et de la gateway (LAN).
                    path == "/api/portal/health" || path == "/health" -> json("""{"status":"ok"}""")
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

    /** Temporisations servies par `lights/timers` (id → minutes ; -1 = jamais lue, servie `null`). */
    val lightTimers = java.util.concurrent.ConcurrentHashMap<String, Int>(
        mapOf("dressing" to -1, "wc1" to -1, "wc2" to -1, "pieceserv" to -1, "ann1" to -1, "ann2" to -1),
    )
    val timerPuts = CopyOnWriteArrayList<String>()
    private val timerLabels = linkedMapOf(
        "dressing" to ("Dressing" to 521), "wc1" to ("WC 1" to 555), "wc2" to ("WC 2" to 556),
        "pieceserv" to ("Service" to 557), "ann1" to ("Annexe 1" to 561), "ann2" to ("Annexe 2" to 562),
    )

    /** Noms personnalisés servis par `lights/names` (id → nom ; "" = nom d'origine, servi `null`). Seules les lumières testées sont listées. */
    val lightNames = java.util.concurrent.ConcurrentHashMap<String, String>(mapOf("dressing" to "", "wc1" to "", "salon" to ""))
    val namePuts = CopyOnWriteArrayList<String>()
    @Volatile var namesUnavailable = false
    @Volatile var nameForbidden = false
    private val nameDefaults = mapOf("dressing" to "Dressing", "wc1" to "WC 1", "salon" to "Salon")

    private fun nameJson(id: String, name: String?, status: String? = null): String {
        val version = if (name == null) 0 else 1
        val at = if (name == null) "null" else "\"2026-11-07T10:15:30.123Z\""
        val st = status?.let { ""","status":"$it"""" }.orEmpty()
        return """{"id":"$id","default":"${nameDefaults.getValue(id)}","name":${name?.let { "\"$it\"" } ?: "null"},"version":$version,"updated_at":$at$st}"""
    }

    private fun namesJson() = lightNames.entries.joinToString(",", """{"lights":[""", "]}") { (id, name) -> nameJson(id, name.ifEmpty { null }) }

    /** Dispositions servies par `{lights|shutters}/layout` (ordre, désactivés, version). */
    class LayoutState(@Volatile var order: List<String> = emptyList(), @Volatile var disabled: List<String> = emptyList(), @Volatile var version: Int = 0)
    val lightLayout = LayoutState()
    val shutterLayout = LayoutState()
    val layoutPuts = CopyOnWriteArrayList<String>()
    /** Ancien serveur : la route de disposition répond 404. */
    @Volatile var layoutAbsent = false
    @Volatile var layoutForbidden = false
    @Volatile var layoutUnavailable = false

    private fun layoutDoc(order: List<String>, disabled: List<String>, version: Int, status: String? = null): String {
        fun list(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }
        val at = if (version == 0) "null" else "\"2026-11-07T10:15:30.123Z\""
        val st = status?.let { ""","status":"$it"""" }.orEmpty()
        return """{"order":${list(order)},"disabled":${list(disabled)},"version":$version,"updated_at":$at$st}"""
    }

    private fun layoutJson(lights: Boolean) = (if (lights) lightLayout else shutterLayout).let { layoutDoc(it.order, it.disabled, it.version) }

    /** Noms personnalisés servis par `shutters/names` (id → nom ; "" = nom d'origine, servi `null`). Seuls les volets testés sont listés. */
    val shutterNames = java.util.concurrent.ConcurrentHashMap<String, String>(mapOf("volet1salon" to "", "volet2salon" to "", "voletstore" to "", "store" to ""))
    val shutterNamePuts = CopyOnWriteArrayList<String>()
    private val shutterDefaults = mapOf("volet1salon" to "Volet 1 Salon", "volet2salon" to "Volet 2 Salon", "voletstore" to "Volet \\\"Store\\\"", "store" to "Store (banne)")

    private fun shutterNameJson(id: String, name: String?, status: String? = null): String {
        val version = if (name == null) 0 else 1
        val at = if (name == null) "null" else "\"2026-11-07T10:15:30.123Z\""
        val st = status?.let { """,\"status\":\"$it\"""" }.orEmpty()
        return """{"id":"$id","default":"${shutterDefaults.getValue(id)}","name":${name?.let { "\"$it\"" } ?: "null"},"version":$version,"updated_at":$at$st}"""
    }

    private fun shutterNamesJson() = shutterNames.entries.joinToString(",", """{"shutters":[""", "]}") { (id, name) -> shutterNameJson(id, name.ifEmpty { null }) }

    private fun timersJson() = timerLabels.entries.joinToString(",", """{"timers":[""", "]}") { (id, lk) ->
        """{"id":"$id","label":"${lk.first}","key":${lk.second},"minutes":${lightTimers[id]?.takeIf { it >= 0 } ?: "null"}}"""
    }

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

    private fun sessionJson(): String {
        val gateway = if (hasGateway) ""","gateway":{"id":"gw-demo","hostname":"demo","online":$gatewayOnline}""" else ""
        val seen = armoireOnline ?: (hasGateway && gatewayOnline)
        val armoire = if (hasArmoire) ""","armoire":{"id":1,"no_serie":"SC944D-0001","last_seen":"2026-10-25T08:00:00Z","online":$seen}""" else ""
        return """{"user":{"id":1,"email":"demo@essensys.fr","first_name":"Camille","last_name":"Démo"},"portal_access":true$gateway$armoire${gatewayState?.let { ""","gateway_state":"$it"""" } ?: ""}}"""
    }

    private fun dryRun(request: RecordedRequest) = request.headers["X-Essensys-Test-Mode"] == "dry-run"

    private fun testOk() = json("""{"status":"test_ok","dry_run":true,"validated_params":[],"message":"Validation OK"}""")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "fixture $name" }.readText()

    private companion object {
        val SHUTTER_NAME_PUT = Regex("^/api/(portal/admin|admin)/shutters/names/[^/]+$")
        val LAYOUT_PUT = Regex("^/api/(portal/admin|admin)/(lights|shutters)/layout$")
        val LIGHT_NAME_PUT = Regex("^/api/(portal/admin|admin)/lights/names/[^/]+$")
        val LIGHT_TIMER_PUT = Regex("^/api/(portal/admin|admin)/lights/timers/[^/]+$")
        val SCENARIOS = Regex("^/api/(portal/)?scenarios(/.*)?$")
    }

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    /** Messages « envoyés » au support : l'appli email n'est jamais ouverte en test. */
    val supportMails = CopyOnWriteArrayList<SupportPackage>()

    fun container(state: SessionState = SessionState()): AppContainer {
        val base = server.url("/").toString().trimEnd('/')
        val store = InMemorySessionStore(state.copy(cloudHost = base, lanHost = if (state.mode == ConnectionMode.LAN) base else state.lanHost))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return AppContainer(
            store, guardNoArmoire = true, configureLan = {},
            supportDir = File(context.cacheDir, "support"),
            mailer = { _, pkg -> supportMails += pkg },
            pushTokens = { pushToken },
        )
    }

    override fun close() = server.close()
}
