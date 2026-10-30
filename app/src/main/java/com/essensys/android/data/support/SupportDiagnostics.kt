package com.essensys.android.data.support

import com.essensys.android.data.http.ArmoireInfo
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.HistoryLatest
import com.essensys.android.data.http.PortalSession
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import com.essensys.android.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import javax.net.ssl.SSLException
import kotlin.math.abs

/** Statuts communs aux deux apps (décision C1 de app-support-bundle-2026-10-013). */
enum class CheckStatus(val code: String, val label: String) {
    OK("ok", "OK"),
    WARNING("warning", "Attention"),
    ERROR("error", "Échec"),
    NA("n/a", "Non applicable");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: NA
    }
}

@Serializable
data class CheckResult(val id: String, val group: String, val label: String, val status: String, val detail: String) {
    val checkStatus get() = CheckStatus.of(status)
}

@Serializable
data class DiagnosticReport(
    val platform: String,
    val generatedAt: String,
    val appVersion: String,
    val appBuild: String,
    val connectionMode: String,
    val checks: List<CheckResult>,
)

/**
 * Tests de diagnostic du bouton Support, en lecture seule (spec support-bundle) : seules des requêtes GET
 * (`health`, `session`, `history/latest`, `/health` de la gateway). Jamais d'injection vers l'armoire.
 *
 * Les clients HTTP sont dédiés : ils portent la session mais pas le journal, pour ne pas le remplir de tests.
 */
class SupportDiagnostics(
    private val store: SessionStore,
    private val cloudClient: OkHttpClient,
    private val lanClient: OkHttpClient,
    private val device: DeviceProbe,
    private val dns: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val clock: () -> Instant = Instant::now,
    private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun run(onResult: (CheckResult) -> Unit = {}): DiagnosticReport = withContext(Dispatchers.IO) {
        val state = store.state.value
        val results = Collections.synchronizedList(mutableListOf<CheckResult>())
        val emit: (String, CheckStatus, String) -> Unit = { id, status, detail ->
            val (group, label) = CHECKS.getValue(id)
            CheckResult(id, group, label, status.code, detail).also { results += it; onResult(it) }
        }

        emit("app", CheckStatus.OK, "Version ${device.appVersion} (build ${device.appBuild})")
        emit("os", CheckStatus.OK, device.osVersion)
        emit("model", CheckStatus.OK, device.model)
        emit("locale", CheckStatus.OK, "${device.locale} · ${device.timeZone}")
        emit(
            "mode", CheckStatus.OK,
            if (state.mode == ConnectionMode.CLOUD) "Cloud (${state.cloudHost.substringAfter("://")})" else "Réseau local (${state.lanHost.substringAfter("://")})",
        )
        if (state.testMode) emit("test-mode", CheckStatus.WARNING, "Mode test actif : les commandes ne sont pas exécutées par l'armoire")
        else emit("test-mode", CheckStatus.OK, "Désactivé")
        val net = device.networkType()
        when {
            net == NetworkType.NONE -> emit("network", CheckStatus.ERROR, net.label)
            state.mode == ConnectionMode.LAN && net != NetworkType.WIFI ->
                emit("network", CheckStatus.WARNING, "${net.label} : le mode réseau local demande le Wi-Fi de la maison")
            else -> emit("network", CheckStatus.OK, net.label)
        }

        withTimeoutOrNull(totalTimeoutMs) {
            coroutineScope {
                val cloud = async { cloudChecks(state, emit) }
                val lan = async { lanCheck(state, emit) }
                cloud.await(); lan.await()
            }
        }
        val done = synchronized(results) { results.map { it.id }.toSet() }
        CHECKS.keys.filter { it !in done }.forEach { emit(it, CheckStatus.ERROR, "Délai dépassé") }

        val ordered = synchronized(results) { results.sortedBy { CHECKS.keys.indexOf(it.id) } }
        DiagnosticReport("android", clock().toString(), device.appVersion, device.appBuild, state.mode.name.lowercase(), ordered)
    }

    private suspend fun cloudChecks(state: SessionState, emit: (String, CheckStatus, String) -> Unit) {
        val base = state.cloudHost.trimEnd('/')
        val host = runCatching { URI(base).host }.getOrNull() ?: base
        val addresses = withTimeoutOrNull(STEP_TIMEOUT_MS) { runCatching { runInterruptible { dns(host) } } }
        when {
            addresses == null || addresses.exceptionOrNull() is CancellationException -> emit("dns", CheckStatus.ERROR, "$host : délai dépassé")
            addresses.isSuccess -> emit("dns", CheckStatus.OK, "$host → ${addresses.getOrThrow().size} adresse(s)")
            else -> emit("dns", CheckStatus.ERROR, "$host : ${cause(addresses.exceptionOrNull())}")
        }

        val tries = (1..LATENCY_TRIES).map { timedGet(cloudClient, "$base/api/portal/health") }
        val ok = tries.filter { it.code in 200..299 }
        val firstFailure = tries.firstOrNull { it.code !in 200..299 }
        if (ok.isNotEmpty()) emit("health", CheckStatus.OK, "Répond (HTTP ${ok.first().code})")
        else emit("health", CheckStatus.ERROR, firstFailure?.describe() ?: "Pas de réponse")

        if (ok.isEmpty()) {
            emit("latency", CheckStatus.ERROR, "0/$LATENCY_TRIES réponses : ${firstFailure?.describe()}")
        } else {
            val ms = ok.map { it.ms }
            val detail = "min ${ms.min()} ms · moyenne ${ms.average().toLong()} ms · max ${ms.max()} ms · ${ok.size}/$LATENCY_TRIES réponses"
            val slow = ms.average() >= SLOW_MS
            emit("latency", if (ok.size == LATENCY_TRIES && !slow) CheckStatus.OK else CheckStatus.WARNING, detail)
        }

        when {
            base.startsWith("http://") -> emit("tls", CheckStatus.NA, "Connexion non chiffrée (serveur de test)")
            ok.isNotEmpty() -> emit("tls", CheckStatus.OK, "Certificat valide")
            tries.any { it.error is SSLException } -> emit("tls", CheckStatus.ERROR, "Certificat refusé : ${tries.first { it.error is SSLException }.error?.message}")
            else -> emit("tls", CheckStatus.NA, "Non vérifié : serveur injoignable")
        }

        val serverDate = ok.firstNotNullOfOrNull { it.date }
        if (serverDate == null) emit("clock", CheckStatus.NA, "Heure du serveur inconnue")
        else {
            val skew = (clock().epochSecond - serverDate.epochSecond)
            val detail = if (skew == 0L) "À l'heure" else "Écart de ${abs(skew)} s ${if (skew > 0) "en avance" else "en retard"} sur le serveur"
            emit("clock", if (abs(skew) <= MAX_SKEW_S) CheckStatus.OK else CheckStatus.WARNING, detail)
        }

        portalChecks(state, base, emit)
    }

    private suspend fun portalChecks(state: SessionState, base: String, emit: (String, CheckStatus, String) -> Unit) {
        val portalIds = listOf("session", "armoire", "online", "last-action")
        if (state.mode != ConnectionMode.CLOUD || state.token == null) {
            val why = if (state.mode == ConnectionMode.LAN) "Mode réseau local" else "Non connecté"
            portalIds.forEach { emit(it, CheckStatus.NA, why) }
            return
        }
        val s = timedGet(cloudClient, "$base/api/portal/session")
        if (s.code !in 200..299) {
            emit("session", CheckStatus.ERROR, if (s.code == 401) "Session expirée : reconnectez-vous" else s.describe())
            portalIds.drop(1).forEach { emit(it, CheckStatus.NA, "Session indisponible") }
            return
        }
        val session = runCatching { EssensysJson.decodeFromString(PortalSession.serializer(), s.body) }.getOrNull()
        if (session == null) {
            emit("session", CheckStatus.ERROR, "Réponse illisible")
            portalIds.drop(1).forEach { emit(it, CheckStatus.NA, "Session indisponible") }
            return
        }
        emit("session", CheckStatus.OK, "Session valide" + (session.user?.email?.let { " — $it" } ?: ""))

        val armoire = session.armoire
        if (armoire == null) emit("armoire", CheckStatus.WARNING, "Aucune armoire vue par le serveur")
        else armoireCheck(armoire, session.gateway?.online == true, clock(), zone).let { (status, detail) -> emit("armoire", status, detail) }

        val gateway = when (session.gateway?.online) { true -> "en ligne"; false -> "hors ligne"; null -> "absente" }
        val rule = "gateway $gateway · armoire ${if (armoire?.online == true) "vue" else "non vue"} depuis 2 min"
        if (session.armoireOnline()) emit("online", CheckStatus.OK, "En ligne ($rule)")
        else emit("online", CheckStatus.ERROR, "Hors ligne ($rule)")

        val h = timedGet(cloudClient, "$base/api/portal/history/latest")
        val last = if (h.code in 200..299) runCatching { EssensysJson.decodeFromString(HistoryLatest.serializer(), h.body) }.getOrNull() else null
        when {
            h.code !in 200..299 -> emit("last-action", CheckStatus.WARNING, h.describe())
            last?.lastAction == null -> emit("last-action", CheckStatus.OK, "Aucune action récente")
            else -> last.lastAction.let { a ->
                emit("last-action", CheckStatus.OK, "${a.actionInfo ?: "—"} (${if (a.isDone) "terminée" else "en cours"})" + (a.timestamp?.let { " · $it" } ?: ""))
            }
        }
    }

    private suspend fun lanCheck(state: SessionState, emit: (String, CheckStatus, String) -> Unit) {
        if (state.mode != ConnectionMode.LAN) {
            emit("lan", CheckStatus.NA, "Mode cloud : gateway non testée")
            return
        }
        val r = timedGet(lanClient, state.lanHost.trimEnd('/') + "/health")
        if (r.code in 200..299) emit("lan", CheckStatus.OK, "Joignable en ${r.ms} ms")
        else emit("lan", CheckStatus.ERROR, r.describe())
    }

    private class Timed(val code: Int?, val ms: Long, val body: String = "", val date: Instant? = null, val error: Throwable? = null) {
        fun describe() = when {
            error != null -> cause(error)
            else -> "HTTP $code"
        }
    }

    /** Interruptible : le délai global coupe aussi une requête bloquée. */
    private suspend fun timedGet(client: OkHttpClient, url: String): Timed = runInterruptible {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                val date = r.header("Date")?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() }
                Timed(r.code, elapsed(), r.body.string(), date)
            }
        } catch (e: Exception) {
            Timed(null, elapsed(), error = e)
        }
    }

    companion object {
        const val TOTAL_TIMEOUT_MS = 25_000L
        const val STEP_TIMEOUT_MS = 5_000L
        const val LATENCY_TRIES = 5
        const val SLOW_MS = 1_500.0
        const val MAX_SKEW_S = 120L
        val STALE_AFTER: Duration = Duration.ofHours(24)
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        /** Ordre d'affichage et libellés, identiques sur l'iPhone. */
        val CHECKS: LinkedHashMap<String, Pair<String, String>> = linkedMapOf(
            "app" to ("Appareil" to "Appli"),
            "os" to ("Appareil" to "Système"),
            "model" to ("Appareil" to "Modèle"),
            "locale" to ("Appareil" to "Langue et fuseau"),
            "clock" to ("Appareil" to "Heure du téléphone"),
            "network" to ("Réseau" to "Connexion"),
            "dns" to ("Réseau" to "Résolution DNS"),
            "latency" to ("Réseau" to "Temps de réponse HTTPS"),
            "tls" to ("Réseau" to "Certificat TLS"),
            "health" to ("Portail" to "Serveur Essensys"),
            "session" to ("Portail" to "Session"),
            "armoire" to ("Portail" to "Armoire associée"),
            "online" to ("Portail" to "Armoire en ligne"),
            "last-action" to ("Portail" to "Dernière action"),
            "lan" to ("Mode local" to "Gateway"),
            "mode" to ("Réglages" to "Mode de connexion"),
            "test-mode" to ("Réglages" to "Mode test"),
        )

        /**
         * Test « Armoire associée » (#33) : date locale et durée écoulée. « Attention » si la date manque, ou si
         * elle a plus de 24 h sans gateway en ligne pour assurer le contact. `online` vient du serveur (2 min).
         */
        internal fun armoireCheck(armoire: ArmoireInfo, gatewayOnline: Boolean, now: Instant, zone: ZoneId): Pair<CheckStatus, String> {
            val prefix = "Armoire ${armoire.serial ?: "—"} · dernier contact"
            val seen = armoire.lastSeen?.let { parseInstant(it) } ?: return CheckStatus.WARNING to "$prefix inconnu"
            val age = Duration.between(seen, now).coerceAtLeast(Duration.ZERO)
            val detail = "$prefix ${DATE.format(seen.atZone(zone))} (${elapsed(age)})"
            return when {
                armoire.online -> CheckStatus.OK to detail
                gatewayOnline -> CheckStatus.OK to "$detail · contact assuré par la gateway"
                age > STALE_AFTER -> CheckStatus.WARNING to detail
                else -> CheckStatus.OK to detail
            }
        }

        /** Libellé identique sur l'iPhone. */
        internal fun elapsed(d: Duration): String = when {
            d < Duration.ofMinutes(1) -> "il y a moins d'1 min"
            d < Duration.ofHours(1) -> "il y a ${d.toMinutes()} min"
            d < Duration.ofHours(48) -> "il y a ${d.toHours()} h"
            else -> "il y a ${d.toDays()} j"
        }

        private fun parseInstant(raw: String): Instant? =
            runCatching { Instant.parse(raw) }.recoverCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()

        internal fun cause(e: Throwable?): String = when (e) {
            null -> "Erreur inconnue"
            is UnknownHostException -> "Nom introuvable (DNS)"
            is SSLException -> "Certificat refusé"
            is ConnectException -> "Connexion refusée"
            is InterruptedIOException -> "Délai dépassé"
            else -> e.javaClass.simpleName
        }
    }
}
