package com.essensys.android.data.support

import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.session.SessionState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipFile

/** Bundle zip du support (tâche 2.1 de app-support-bundle-2026-10-013). */
class SupportBundleTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun unzip(zip: File): Map<String, String> = ZipFile(zip).use { z ->
        z.entries().toList().associate { it.name to z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
    }

    private fun bundle(s: SupportServer) = SupportBundle(tmp.root, s.store, s.container.appLog, ZoneId.of("Europe/Paris"))

    @Test
    fun zip_holds_summary_diagnostics_and_journal() = SupportServer(SupportServer.cloud(testMode = true)).use { s ->
        s.container.appLog.event("session", "Connecté (cloud)")
        val report = runBlocking { s.container.supportDiagnostics.run() }.copy(generatedAt = "2026-10-30T10:15:30Z")
        val pkg = bundle(s).create(report)

        assertEquals("essensys-support-android-${report.appVersion}-20261030-111530.zip", pkg.zip.name)
        val files = unzip(pkg.zip)
        assertEquals(listOf("resume.txt", "diagnostics.json", "journal.jsonl"), files.keys.toList())
        assertTrue(files.getValue("resume.txt").contains("[Attention] Mode test : Mode test actif"))
        assertTrue(files.getValue("diagnostics.json").contains("\"platform\": \"android\""))
        assertTrue(files.getValue("journal.jsonl").contains("Connecté (cloud)"))
        assertEquals(pkg.summary, files.getValue("resume.txt"))
        assertEquals("Support Essensys — ${report.appVersion} — 30/10/2026 11:15", pkg.subject)
    }

    @Test
    fun a_new_bundle_replaces_the_previous_one() = SupportServer(SupportServer.cloud()).use { s ->
        val b = bundle(s)
        val report = runBlocking { s.container.supportDiagnostics.run() }
        b.create(report.copy(generatedAt = "2026-10-30T10:00:00Z"))
        val second = b.create(report.copy(generatedAt = "2026-10-30T10:05:00Z"))
        assertEquals(listOf(second.zip.name), tmp.root.list()!!.filter { it.endsWith(".zip") })
    }

    // NR: NR-android-20 essensys-hub/essensys-android-phone-apps#29
    // Le bundle part par email : aucun secret de session ni saisi par l'utilisateur ne doit s'y trouver.
    @Test
    fun bundle_contains_no_secret_NR_android_20() {
        val token = "jwt-SECRET-token-0123456789"
        val cookie = "lan-SECRET-cookie-abcdef"
        val password = "mot-de-passe-fictif!"
        val accessKey = "73195624081357924680135792468013"
        val alarmCode = "4826"
        SupportServer(SessionState(token = token, lanCookie = cookie)).use { s ->
            val api = ApiCaller { s.container.cloudClient }
            runBlocking {
                api.send(s.store.state.value.baseUrl, "POST", "/api/auth/login", buildJsonObject { put("email", "demo@essensys.fr"); put("password", password) })
                api.send(s.store.state.value.baseUrl, "POST", "/api/portal/web/actions", buildJsonObject { put("alarme", "on"); put("codealarme", alarmCode) })
                api.send(s.store.state.value.baseUrl, "PUT", "/api/portal/remote-key", buildJsonObject { put("key", accessKey) })
            }
            s.container.appLog.event("session", "Connecté (cloud)")
            val report = runBlocking { s.container.supportDiagnostics.run() }
            val files = unzip(bundle(s).create(report).zip)
            assertTrue(files.getValue("journal.jsonl").contains("POST /api/auth/login"))
            files.forEach { (name, text) ->
                listOf(token, cookie, password, accessKey, alarmCode, "Authorization", "Cookie").forEach { secret ->
                    assertFalse("$secret dans $name", text.contains(secret))
                }
            }
        }
    }
}
