package com.essensys.android.data.support

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Journal interne du bouton Support (tâche 1.1 de app-support-bundle-2026-10-013). */
class AppLogTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun ring_keeps_only_the_last_entries() {
        val log = AppLog(capacity = 3)
        repeat(5) { log.event("test", "e$it") }
        assertEquals(listOf("e2", "e3", "e4"), log.snapshot().map { it.msg })
    }

    @Test
    fun journal_is_reloaded_after_restart() {
        val file = tmp.root.resolve("support/journal.jsonl")
        AppLog(file).apply { event("app", "avant"); http("GET", "/api/portal/session", 200, 42); flush() }
        val reloaded = AppLog(file)
        assertEquals(listOf("avant", "GET /api/portal/session"), reloaded.snapshot().map { it.msg })
        assertEquals(42L, reloaded.snapshot().last().ms)
        file.readLines().forEach { EssensysJson.decodeFromString(LogEntry.serializer(), it) }
    }

    @Test
    fun journal_is_written_every_fifty_entries() {
        val file = tmp.root.resolve("journal.jsonl")
        val log = AppLog(file)
        repeat(49) { log.event("test", "e$it") }
        assertFalse(file.exists())
        log.event("test", "e49")
        assertEquals(50, file.readLines().size)
    }

    @Test
    fun requests_are_logged_without_headers_body_or_query() = runBlocking {
        SupportServer(SupportServer.cloud(token = "jwt-secret-token")).use { s ->
            val api = com.essensys.android.data.http.ApiCaller { s.container.cloudClient }
            val body = buildJsonObject { put("email", "demo@essensys.fr"); put("password", "mot-de-passe-fictif") }
            assertTrue(api.send(s.store.state.value.baseUrl, "POST", "/api/auth/login?debug=1", body) is ApiResult.Ok)
            val entry = s.container.appLog.snapshot().single()
            assertEquals("POST /api/auth/login", entry.msg)
            assertEquals(200, entry.code)
            val text = s.container.appLog.toJsonLines()
            listOf("jwt-secret-token", "mot-de-passe-fictif", "debug=1", "Authorization").forEach { assertFalse(it, text.contains(it)) }
        }
    }

    @Test
    fun network_failure_is_logged_with_its_cause() = runBlocking {
        val s = SupportServer()
        val api = com.essensys.android.data.http.ApiCaller { s.container.cloudClient }
        val base = s.store.state.value.baseUrl
        s.close()
        assertTrue(api.get(base, "/api/portal/health") is ApiResult.Err)
        val entry = s.container.appLog.snapshot().single()
        assertEquals(null, entry.code)
        assertEquals("ConnectException", entry.error)
    }
}
