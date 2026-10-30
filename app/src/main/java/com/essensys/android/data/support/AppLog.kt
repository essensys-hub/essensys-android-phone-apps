package com.essensys.android.data.support

import com.essensys.android.data.http.EssensysJson
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant

/** Une ligne du journal : requête HTTP (méthode, chemin, code, durée) ou événement de l'app. Jamais de corps ni d'en-tête. */
@Serializable
data class LogEntry(
    val t: String,
    val kind: String,
    val msg: String,
    val method: String? = null,
    val path: String? = null,
    val code: Int? = null,
    val ms: Long? = null,
    val error: String? = null,
)

/**
 * Journal interne en anneau (design D1 de app-support-bundle-2026-10-013) : les [capacity] dernières entrées,
 * écrites en JSON lignes dans [file] toutes les [FLUSH_EVERY] entrées et à la mise en arrière-plan.
 * [file] nul : journal en mémoire seulement (tests).
 */
class AppLog(
    private val file: File? = null,
    private val capacity: Int = CAPACITY,
    private val clock: () -> Instant = Instant::now,
) {
    private val entries = ArrayDeque<LogEntry>()
    private var unsaved = 0

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList())
                .mapNotNull { line -> runCatching { EssensysJson.decodeFromString(LogEntry.serializer(), line) }.getOrNull() }
                .takeLast(capacity)
                .forEach { entries.addLast(it) }
        }
    }

    fun http(method: String, path: String, code: Int?, ms: Long, error: String? = null) =
        add(LogEntry(clock().toString(), "http", "$method $path", method, path, code, ms, error))

    fun event(kind: String, msg: String) = add(LogEntry(clock().toString(), kind, msg))

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    fun toJsonLines(): String = snapshot().joinToString("\n") { EssensysJson.encodeToString(LogEntry.serializer(), it) }

    @Synchronized
    fun flush() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(toJsonLines())
            unsaved = 0
        }
    }

    private fun add(entry: LogEntry) {
        val save = synchronized(this) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
            ++unsaved >= FLUSH_EVERY
        }
        if (save) flush()
    }

    companion object {
        const val CAPACITY = 500
        const val FLUSH_EVERY = 50
    }
}

/**
 * Note chaque requête dans [log]. Installé avant `SessionInterceptor` : il ne voit donc jamais l'en-tête
 * `Authorization` ni le cookie, et il n'enregistre ni corps ni paramètres (`encodedPath` seulement).
 */
class LoggingInterceptor(private val log: AppLog) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.nanoTime()
        try {
            val response = chain.proceed(request)
            log.http(request.method, request.url.encodedPath, response.code, elapsed(start))
            return response
        } catch (e: IOException) {
            log.http(request.method, request.url.encodedPath, null, elapsed(start), e.javaClass.simpleName)
            throw e
        } catch (e: RuntimeException) {
            log.http(request.method, request.url.encodedPath, null, elapsed(start), e.javaClass.simpleName)
            throw e
        }
    }

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000
}
