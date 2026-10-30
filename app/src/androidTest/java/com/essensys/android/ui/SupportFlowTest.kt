package com.essensys.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.SessionState
import com.essensys.android.data.support.SupportMail
import com.essensys.android.data.support.SupportPackage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Bouton Support (tâche 2.2 de app-support-bundle-2026-10-013), contre le backend simulé : aucune armoire réelle. */
@RunWith(AndroidJUnit4::class)
class SupportFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun sendWhenReady(): SupportPackage {
        compose.waitUntil(30_000) { exists("support-send") }
        compose.onNodeWithTag("support-send").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.supportMails.isNotEmpty() }
        return backend.supportMails.single()
    }

    @Test
    fun settings_support_runs_diagnostics_and_prepares_the_email() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", testMode = true))) }
        compose.waitUntil(5_000) { exists("nav-SETTINGS") }
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("open-support").performScrollTo().performClick()

        val pkg = sendWhenReady()
        compose.onNodeWithTag("check-test-mode-status").assertTextEquals("Attention")
        compose.onNodeWithTag("check-online-status").assertTextEquals("OK")
        assertTrue(pkg.subject, pkg.subject.startsWith("Support Essensys — "))
        assertTrue(pkg.summary.contains("Mode test actif"))
        val names = ZipFile(pkg.zip).use { z -> z.entries().toList().map { it.name } }
        assertEquals(listOf("resume.txt", "diagnostics.json", "journal.jsonl"), names)
        // Lecture seule : le diagnostic n'a envoyé aucune commande.
        assertTrue(backend.injections.isEmpty())
        assertTrue(backend.mutations.isEmpty())
    }

    @Test
    fun support_is_reachable_before_login() {
        compose.setContent { EssensysRoot(backend.container(SessionState())) }
        compose.waitUntil(5_000) { exists("open-support") }
        compose.onNodeWithTag("open-support").performScrollTo().performClick()

        val pkg = sendWhenReady()
        compose.onNodeWithTag("check-session-status").assertTextEquals("Non applicable")
        compose.onNodeWithTag("check-health-status").assertTextEquals("OK")
        assertTrue(pkg.summary.contains("Non connecté"))
        compose.onNodeWithTag("back").performClick()
        compose.waitUntil(5_000) { exists("login") }
    }

    @Test
    fun email_intent_targets_support_with_the_zip_attached() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val zip = File(context.cacheDir, "support/essensys-support-android-test.zip").apply { parentFile?.mkdirs(); writeText("zip") }
        val intent = SupportMail.emailIntent(context, SupportPackage(zip, "Résumé", "Support Essensys — test"))

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(listOf("support_essensys@rhinosys.fr"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList())
        assertEquals("Support Essensys — test", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Résumé", intent.getStringExtra(Intent.EXTRA_TEXT))
        @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals("${context.packageName}.support", uri?.authority)
        assertEquals("mailto", intent.selector?.data?.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
